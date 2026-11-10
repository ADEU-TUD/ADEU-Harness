"""Fine-tuning of a pretrained encoder for the command model (AP-71, Stufe e).

    python train.py --basis distilbert/distilbert-base-german-cased --epochen 4
    python train.py --basis … --epochen 4 --nur 1 [--fortsetzen]     # in stages: one epoch per run, then continue
    python train.py --basis deepset/gbert-base --eingefroren        # only a head on the frozen encoder (two-heads check)
    python train.py --basis … --schichten 4                         # keep the first 4 encoder layers only

Reads daten/{train,dev}.jsonl (gen.py make). After every epoch the model is measured on the development part of the
Messkorpus; the best epoch is kept in modelle/<name>/best. At the end the threshold is chosen on the development part
and both parts are measured once (bericht.json; error lists for the development part only). The base model and all
settings are chosen by the development part, never by the test part.
"""
from __future__ import annotations

import argparse
import json
import random
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus  # noqa: E402
import modell  # noqa: E402

length_batches = modell._pii("train").length_batches


def quality(n: dict) -> float:
    """One number per epoch: hits, with wrongly carried out actions counting five times."""
    return n["treffer"] - 5 * n["falsch_ausgefuehrt"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--basis", default="distilbert/distilbert-base-german-cased", help="vortrainiertes Modell auf Hugging Face")
    ap.add_argument("--revision", default=None, help="feste Version (Commit) des Basismodells")
    ap.add_argument("--daten", default=str(HERE / "daten"))
    ap.add_argument("--aus", default=None, help="Ausgabeordner (Standard: modelle/<Name>)")
    ap.add_argument("--epochen", type=int, default=4)
    ap.add_argument("--nur", type=int, default=None, help="in diesem Lauf höchstens so viele Epochen (dann --fortsetzen)")
    ap.add_argument("--fortsetzen", action="store_true")
    ap.add_argument("--schichten", type=int, default=None, help="nur die ersten N Schichten des Encoders behalten")
    ap.add_argument("--eingefroren", action="store_true", help="Encoder einfrieren, nur den Satz-Kopf trainieren")
    ap.add_argument("--lr", type=float, default=None)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--glaettung", type=float, default=0.05, help="Label-Smoothing (vorsichtigere Sicherheiten)")
    ap.add_argument("--limit", type=int, default=None)
    ap.add_argument("--seed", type=int, default=71)
    a = ap.parse_args()

    import torch
    from torch.utils.data import DataLoader
    from transformers import AutoModelForSequenceClassification, AutoTokenizer, DataCollatorWithPadding

    random.seed(a.seed)
    torch.manual_seed(a.seed)
    name = a.basis.split("/")[-1] + (f"-{a.schichten}" if a.schichten else "") + ("-kopf" if a.eingefroren else "")
    out = Path(a.aus) if a.aus else HERE / "modelle" / name
    out.mkdir(parents=True, exist_ok=True)
    last = out / "letzte"
    device = "cuda" if torch.cuda.is_available() else "cpu"
    lr = a.lr or (1e-3 if a.eingefroren else 5e-5)
    labels = corpus.labels()
    label_id = {l: i for i, l in enumerate(labels)}
    print(f"Basismodell {a.basis} auf {device}, {torch.get_num_threads()} Kerne, {len(labels)} Etiketten", flush=True)

    resume = a.fortsetzen and (last / "state.pt").exists()
    if resume:
        tok = AutoTokenizer.from_pretrained(last)
        model = AutoModelForSequenceClassification.from_pretrained(last).to(device)
    else:
        tok = modell.load_tokenizer(a.basis, a.revision)
        model = modell.load_model(a.basis, labels, a.revision, a.schichten).to(device)
    if a.eingefroren:
        for p in model.base_model.parameters():
            p.requires_grad = False

    def rows(file: str, limit=None):
        data = modell._pii("modell").read_jsonl(Path(a.daten) / file, limit)
        return [{**modell.encode(tok, d["text"], d["bereich"]), "labels": label_id[d["label"]]} for d in data]

    train, dev = rows("train.jsonl", a.limit), rows("dev.jsonl", 600 if a.limit else None)
    lengths = [len(x["input_ids"]) for x in train]
    collate = DataCollatorWithPadding(tok, pad_to_multiple_of=8)
    dev_loader = DataLoader(dev, batch_size=a.batch * 4, collate_fn=collate)
    per_epoch = len(length_batches(lengths, a.batch, a.seed))
    steps = a.epochen * per_epoch
    warmup = max(1, steps // 10)
    params = [p for p in model.parameters() if p.requires_grad]
    opt = torch.optim.AdamW(params, lr=lr, weight_decay=0.01)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: s / warmup if s < warmup else max(0.0, (steps - s) / max(1, steps - warmup)))
    loss_fn = torch.nn.CrossEntropyLoss(label_smoothing=a.glaettung)

    log, best, step, done = [], None, 0, 0
    if resume:
        state = torch.load(last / "state.pt", map_location=device, weights_only=False)
        opt.load_state_dict(state["opt"])
        sched.load_state_dict(state["sched"])
        log, best, step, done = state["log"], state["best"], state["step"], state["epoch"]
        print(f"weiter nach Epoche {done} (Schritt {step}/{steps})", flush=True)

    t0, step0 = time.time(), step
    model.train()
    for epoch in range(done + 1, min(a.epochen, done + (a.nur or a.epochen)) + 1):
        loader = DataLoader(train, batch_sampler=length_batches(lengths, a.batch, a.seed + epoch), collate_fn=collate)
        total, n_batches = 0.0, 0
        for batch in loader:
            batch = {k: v.to(device) for k, v in batch.items()}
            y = batch.pop("labels")
            loss = loss_fn(model(**batch).logits, y)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(params, 1.0)
            opt.step()
            sched.step()
            opt.zero_grad(set_to_none=True)
            total += loss.item()
            n_batches += 1
            step += 1
            if step % 200 == 0:
                rate = (step - step0) / (time.time() - t0)
                print(f"  Epoche {epoch}, Schritt {n_batches}/{per_epoch}  Verlust {total / n_batches:.4f}  "
                      f"noch {(per_epoch - n_batches) / rate / 60:.0f} min in dieser Epoche", flush=True)
        model.eval()
        right = n = 0
        with torch.no_grad():
            for batch in dev_loader:
                y = batch.pop("labels")
                right += int((model(**{k: v.to(device) for k, v in batch.items()}).logits.argmax(-1).cpu() == y).sum())
                n += len(y)
        preds = modell.predictions(modell.Predictor.of(model, tok, labels, device), "entwicklung")
        model.train()
        threshold, _ = modell.tune_threshold(preds)
        nums = modell.score(preds, threshold).numbers()
        entry = {"epoche": epoch, "verlust": round(total / max(n_batches, 1), 4), "genau_dev": round(100 * right / max(n, 1), 1),
                 "schwelle": threshold, "treffer": nums["treffer"], "falsch_ausgefuehrt": nums["falsch_ausgefuehrt"],
                 "frei_weitergegeben": nums["frei_weitergegeben"], "guete": round(quality(nums), 2),
                 "minuten": round((time.time() - t0) / 60, 1)}
        log.append(entry)
        print(f"Epoche {epoch}: {json.dumps(entry, ensure_ascii=False)}", flush=True)
        if best is None or entry["guete"] > best["guete"]:
            best = entry
            model.save_pretrained(out / "best")
            tok.save_pretrained(out / "best")
        model.save_pretrained(last)
        tok.save_pretrained(last)
        torch.save({"epoch": epoch, "opt": opt.state_dict(), "sched": sched.state_dict(), "log": log, "best": best, "step": step}, last / "state.pt")
        done = epoch

    if done < a.epochen:
        print(f"Etappe fertig nach Epoche {done} von {a.epochen} – weiter mit --fortsetzen", flush=True)
        return

    (out / "best" / "labels.json").write_text(json.dumps(labels, ensure_ascii=False), encoding="utf-8")
    predictor = modell.Predictor(out / "best", device)
    dev_preds = modell.predictions(predictor, "entwicklung")
    threshold, table = modell.tune_threshold(dev_preds)
    (out / "best" / "schwelle.json").write_text(json.dumps({"schwelle": threshold}), encoding="utf-8")
    print(f"Schwelle {threshold} (Entwicklung): " + ", ".join(f"{r['schwelle']}: {r['treffer']} %/{r['falsch_ausgefuehrt']} %" for r in table))
    result = {"basis": a.basis, "revision": a.revision, "schichten": a.schichten, "eingefroren": a.eingefroren, "daten": len(train),
              "parameter": sum(p.numel() for p in model.parameters()), "epochen": log, "beste_epoche": best["epoche"],
              "schwelle": threshold, "schwellen_entwicklung": table}
    for section, preds in (("entwicklung", dev_preds), ("pruef", modell.predictions(predictor, "pruef"))):
        sc = modell.score(preds, threshold)
        result[section] = sc.numbers()
        # Error lists for the development part only: nobody tunes templates or settings to the test part.
        if section == "entwicklung":
            result["fehler_entwicklung"] = [list(e) for e in sc.errors]
        print(sc.report(section))
    (out / "bericht.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Fertig: {out / 'best'} und {out / 'bericht.json'}")


if __name__ == "__main__":
    main()
