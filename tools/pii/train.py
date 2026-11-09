"""Fine-tuning of a pretrained encoder for the recognition model (AP-67, Stufe c).

    python train.py --basis deepset/gbert-base                  # on the PC (RTX 5080: bf16, some minutes per epoch)
    python train.py --basis deepset/gbert-base --limit 2000     # short trial run (CPU)
    python train.py --basis deepset/gbert-base --epochen 3 --nur 1 [--fortsetzen]
                                                                # in stages: one epoch per run, then continue

Reads tools/pii/daten/{train,dev}.jsonl (gen.py make). After every epoch the model is measured on the development part
of the Messkorpus; the best epoch is kept in modelle/<name>/best. At the end it is measured once on the test part,
alone and together with the rules of the app (if desktop/build/pii/regeln.jsonl exists), and everything goes to
modelle/<name>/bericht.json. The base model is chosen by the development part, never by the test part.
"""
from __future__ import annotations

import argparse
import json
import math
import random
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import modell  # noqa: E402



def entity_f1(model, batches, device) -> float:
    """Entity F1 on the generated development set (exact spans of subword tokens)."""
    import torch

    tp = fp = fn = 0
    model.eval()
    with torch.no_grad():
        for batch in batches:
            labels = batch.pop("labels")
            pred = model(**{k: v.to(device) for k, v in batch.items()}).logits.argmax(-1).cpu()
            for p_row, l_row in zip(pred.tolist(), labels.tolist()):
                gold = _entities([x for x in l_row])
                got = _entities([p if l != -100 else -100 for p, l in zip(p_row, l_row)])
                tp += len(gold & got)
                fp += len(got - gold)
                fn += len(gold - got)
            batch["labels"] = labels
    model.train()
    return 2 * tp / max(2 * tp + fp + fn, 1)


def _entities(ids: list[int]) -> set[tuple[int, int, str]]:
    out, start, kind = set(), None, None
    for i, x in enumerate(ids + [0]):
        tag = modell.LABELS[x] if x >= 0 else "O"
        if x == -100:
            continue
        if start is not None and (tag == "O" or tag.startswith("B-") or tag[2:] != kind):
            out.add((start, i, kind))
            start = None
        if tag != "O" and start is None:
            start, kind = i, tag[2:]
    return out


def length_batches(lengths: list[int], batch: int, seed: int) -> list[list[int]]:
    """Batches of similar length (less padding – faster, above all on the CPU), in random order."""
    rng = random.Random(seed)
    idx = list(range(len(lengths)))
    rng.shuffle(idx)
    out = []
    for c in range(0, len(idx), batch * 50):
        chunk = sorted(idx[c:c + batch * 50], key=lambda i: lengths[i])
        out += [chunk[i:i + batch] for i in range(0, len(chunk), batch)]
    rng.shuffle(out)
    return out


def tune_threshold(predictor, rules) -> tuple[float, list]:
    """The threshold on the development part (as planned for the app): the highest one that still finds 98 % of the
    private details – fewer false alarms –, else the best trade-off (found − 2 × wrongly replaced)."""
    rows = []
    for t in (0.3, 0.4, 0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.9, 0.93, 0.95, 0.97):
        v = modell.variants(lambda text: predictor.spans(text, t), "entwicklung", rules)
        sc = v.get("geplant", v["modell"])
        found, n = sc.recall()
        wrong, m = sc.false_rate()
        rows.append({"schwelle": t, "gefunden": round(100 * found / n, 2), "falsch_ersetzt": round(100 * wrong / m, 2)})
    ok = [r for r in rows if r["gefunden"] >= 98.0]
    pick = max(ok, key=lambda r: r["schwelle"]) if ok else max(rows, key=lambda r: r["gefunden"] - 2 * r["falsch_ersetzt"])
    return pick["schwelle"], rows


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--basis", default="deepset/gbert-base", help="vortrainiertes Modell auf Hugging Face")
    ap.add_argument("--revision", default=None, help="feste Version (Commit) des Basismodells")
    ap.add_argument("--daten", default=str(HERE / "daten"))
    ap.add_argument("--aus", default=None, help="Ausgabeordner (Standard: modelle/<Name des Basismodells>)")
    ap.add_argument("--epochen", type=int, default=4, help="Epochen insgesamt (bestimmt auch den Verlauf der Lernrate)")
    ap.add_argument("--nur", type=int, default=None, help="in diesem Lauf höchstens so viele Epochen (dann --fortsetzen)")
    ap.add_argument("--fortsetzen", action="store_true", help="beim letzten Stand in <aus>/letzte weitermachen")
    ap.add_argument("--lr", type=float, default=5e-5)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--max-len", type=int, default=256)
    ap.add_argument("--limit", type=int, default=None, help="nur so viele Trainingsbeispiele (Probelauf)")
    ap.add_argument("--seed", type=int, default=67)
    a = ap.parse_args()

    import torch
    from torch.utils.data import DataLoader
    from transformers import AutoModelForTokenClassification, AutoTokenizer, DataCollatorForTokenClassification

    random.seed(a.seed)
    torch.manual_seed(a.seed)
    out = Path(a.aus) if a.aus else HERE / "modelle" / a.basis.split("/")[-1]
    out.mkdir(parents=True, exist_ok=True)
    last = out / "letzte"
    device = "cuda" if torch.cuda.is_available() else "cpu"
    bf16 = device == "cuda" and torch.cuda.is_bf16_supported()
    print(f"Basismodell {a.basis} auf {device}{' (bf16)' if bf16 else ''}" +
          (f" – {torch.cuda.get_device_name(0)}" if device == "cuda" else f", {torch.get_num_threads()} Kerne"), flush=True)

    resume = a.fortsetzen and (last / "state.pt").exists()
    if resume:
        tok = AutoTokenizer.from_pretrained(last)
        model = AutoModelForTokenClassification.from_pretrained(last).to(device)
    else:
        tok = modell.load_tokenizer(a.basis, a.revision)
        model = modell.load_model(a.basis, a.revision).to(device)

    daten = Path(a.daten)
    train = [modell.encode(tok, d["text"], d["spans"], a.max_len) for d in modell.read_jsonl(daten / "train.jsonl", a.limit)]
    dev = [modell.encode(tok, d["text"], d["spans"], a.max_len)
           for d in modell.read_jsonl(daten / "dev.jsonl", max(500, (a.limit or 0) // 9) if a.limit else None)]
    lengths = [len(x["input_ids"]) for x in train]
    collate = DataCollatorForTokenClassification(tok, pad_to_multiple_of=8)
    dev_loader = DataLoader(dev, batch_size=a.batch * 2, collate_fn=collate)

    per_epoch = len(length_batches(lengths, a.batch, a.seed))
    steps = a.epochen * per_epoch
    warmup = max(1, steps // 10)
    no_decay = ("bias", "LayerNorm.weight", "layer_norm.weight")
    params = [
        {"params": [p for n, p in model.named_parameters() if not any(x in n for x in no_decay)], "weight_decay": 0.01},
        {"params": [p for n, p in model.named_parameters() if any(x in n for x in no_decay)], "weight_decay": 0.0},
    ]
    opt = torch.optim.AdamW(params, lr=a.lr)
    sched = torch.optim.lr_scheduler.LambdaLR(
        opt, lambda s: s / warmup if s < warmup else max(0.0, (steps - s) / max(1, steps - warmup)))

    log, best, step, done = [], None, 0, 0
    if resume:
        state = torch.load(last / "state.pt", map_location=device, weights_only=False)
        opt.load_state_dict(state["opt"])
        sched.load_state_dict(state["sched"])
        log, best, step, done = state["log"], state["best"], state["step"], state["epoch"]
        print(f"weiter nach Epoche {done} (Schritt {step}/{steps})", flush=True)
    rules = modell.load_rules()

    t0, step0 = time.time(), step
    model.train()
    for epoch in range(done + 1, min(a.epochen, done + (a.nur or a.epochen)) + 1):
        loader = DataLoader(train, batch_sampler=length_batches(lengths, a.batch, a.seed + epoch), collate_fn=collate)
        total, n_batches = 0.0, 0
        for batch in loader:
            batch = {k: v.to(device) for k, v in batch.items()}
            with torch.autocast(device_type="cuda", dtype=torch.bfloat16, enabled=bf16):
                loss = model(**batch).loss
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            sched.step()
            opt.zero_grad(set_to_none=True)
            total += loss.item()
            n_batches += 1
            step += 1
            if step % 50 == 0:
                rate = (step - step0) / (time.time() - t0)
                print(f"  Epoche {epoch}, Schritt {n_batches}/{per_epoch}  Verlust {total / n_batches:.4f}  "
                      f"noch {(per_epoch - n_batches) / rate / 60:.0f} min in dieser Epoche", flush=True)
        f1 = entity_f1(model, dev_loader, device)
        model.eval()
        v = modell.variants(modell.Predictor.of(model, tok, device).spans, "entwicklung", rules)
        model.train()
        sc = v.get("geplant", v["modell"])
        found, n = sc.recall()
        wrong, m = sc.false_rate()
        quality = 100 * found / n - 2 * 100 * wrong / m
        entry = {"epoche": epoch, "verlust": round(total / max(n_batches, 1), 4), "f1_dev": round(f1, 4),
                 "entwicklung_gefunden": round(100 * found / n, 1), "entwicklung_falsch": round(100 * wrong / m, 1),
                 "guete": round(quality, 2), "minuten": round((time.time() - t0) / 60, 1)}
        log.append(entry)
        print(f"Epoche {epoch}: {json.dumps(entry, ensure_ascii=False)}", flush=True)
        if best is None or quality > best["guete"]:
            best = entry
            model.save_pretrained(out / "best")
            tok.save_pretrained(out / "best")
        model.save_pretrained(last)
        tok.save_pretrained(last)
        torch.save({"epoch": epoch, "opt": opt.state_dict(), "sched": sched.state_dict(), "log": log, "best": best,
                    "step": step}, last / "state.pt")
        done = epoch

    if done < a.epochen:
        print(f"Etappe fertig nach Epoche {done} von {a.epochen} – weiter mit --fortsetzen", flush=True)
        return

    # The best epoch: threshold on the development part, then both parts. Errors are listed for the development part
    # only: the test part gives numbers, so nobody tunes templates or settings to it.
    predictor = modell.Predictor(out / "best", device)
    threshold, table = tune_threshold(predictor, rules)
    (out / "best" / "schwelle.json").write_text(json.dumps({"schwelle": threshold}), encoding="utf-8")
    predictor.threshold = threshold
    print(f"Schwelle {threshold} (Entwicklung): " + ", ".join(f"{r['schwelle']}: {r['gefunden']} %/{r['falsch_ersetzt']} %" for r in table))
    result = {"basis": a.basis, "revision": a.revision, "daten": len(train), "epochen": log, "beste_epoche": best["epoche"],
              "schwelle": threshold, "schwellen_entwicklung": table}
    for section in ("entwicklung", "pruef"):
        for name, sc in modell.variants(predictor.spans, section, rules).items():
            result[f"{section}_{name}"] = _numbers(sc, details=section == "entwicklung")
            print(sc.report(f"{section} – {modell.VARIANTS[name]}"))
    if not rules:
        print("(Für „Regeln + Modell“ fehlt desktop/build/pii/regeln.jsonl – einmal PseudonymCorpusTest laufen lassen.)")
    (out / "bericht.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Fertig: {out / 'best'} und {out / 'bericht.json'}")


def _numbers(sc, details: bool) -> dict:
    found, n = sc.recall()
    wrong, m = sc.false_rate()
    out = {"gefunden": round(100 * found / n, 1), "falsch_ersetzt": round(100 * wrong / m, 1),
           "je_art_gefunden": {k: f"{a}/{b}" for k, (a, b) in sc.found.items()},
           "je_art_falsch": {k: f"{a}/{b}" for k, (a, b) in sc.wrong.items()},
           "sonstige_je_1000": round(1000 * sc.other / max(sc.other_words, 1), 1)}
    if details:
        out["uebersehen"] = [list(x) for x in sc.missed]
        out["falsch"] = [list(x) for x in sc.false_alarms]
    return out


if __name__ == "__main__":
    main()
