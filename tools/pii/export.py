"""Export of Lora Namensschutz, the recognition model, for the app (AP-67, Stufe d): ONNX, int8, model card.

    python export.py modelle/gbert-base/best

Writes modelle/<name>/onnx/: lora-namensschutz-pytales.onnx (fp32), lora-namensschutz-pytales.int8.onnx (dynamic int8
quantization; up to 1.0.65 the file was called pii-modell.int8.onnx), the tokenizer
(vocab.txt and tokenizer.json), labels.json and MODELLKARTE.md. The int8 model may lose at most 0.5 percentage points
of found private details on the test part, compared with fp32 – otherwise the export stops with an error.
"""
from __future__ import annotations

import argparse
import datetime
import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import evaluate  # noqa: E402
import modell  # noqa: E402

MAX_LOSS = 0.5  # percentage points
NAME = "Lora Namensschutz"
FILE = "lora-namensschutz-pytales"
#: Base models: licence and authors (as on their model cards on Hugging Face).
BASES = {
    "deepset/gbert-base": ("MIT", "deepset GmbH (Branden Chan, Stefan Schweter, Timo Möller)"),
    "deepset/gelectra-base": ("MIT", "deepset GmbH"),
    "distilbert/distilbert-base-german-cased": ("Apache-2.0", "Hugging Face (DistilBERT nach Sanh, Debut, Chaumond und Wolf, 2019)"),
}


def export_onnx(folder: Path, target: Path) -> None:
    import torch
    from transformers import AutoModelForTokenClassification

    model = AutoModelForTokenClassification.from_pretrained(folder).eval()
    model.config.return_dict = False
    tok = modell.Predictor(folder, device="cpu").tokenizer
    sample = tok(["Frau Müller war mit Ben in der Grundschule am Park."], return_tensors="pt")
    names = [n for n in ("input_ids", "attention_mask", "token_type_ids") if n in sample]
    axes = {n: {0: "batch", 1: "tokens"} for n in names} | {"logits": {0: "batch", 1: "tokens"}}
    args = tuple(sample[n] for n in names)
    kwargs = dict(input_names=names, output_names=["logits"], dynamic_axes=axes, opset_version=17)
    try:
        torch.onnx.export(model, args, str(target), dynamo=False, **kwargs)
    except TypeError:  # older torch without the dynamo switch
        torch.onnx.export(model, args, str(target), **kwargs)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("modell", help="Ordner des trainierten Modells (…/best)")
    ap.add_argument("--aus", default=None, help="Zielordner (Standard: neben dem Modell, Ordner onnx)")
    a = ap.parse_args()

    from onnxruntime.quantization import QuantType, quantize_dynamic

    folder = Path(a.modell)
    out = Path(a.aus) if a.aus else folder.parent / "onnx"
    out.mkdir(parents=True, exist_ok=True)
    fp32 = out / f"{FILE}.onnx"
    int8 = out / f"{FILE}.int8.onnx"
    print("Export nach ONNX …")
    export_onnx(folder, fp32)
    print("int8 …")
    # Per channel (10.11.2026): on the development part like fp32, the plain dynamic quantization lost 0.3 points there.
    quantize_dynamic(str(fp32), str(int8), weight_type=QuantType.QInt8, per_channel=True)

    tok = modell.Predictor(folder, device="cpu").tokenizer
    tok.save_pretrained(out)
    # vocab.txt for the WordPiece tokenizer of the app (one token per line, line number = id).
    vocab = sorted(tok.get_vocab().items(), key=lambda kv: kv[1])
    (out / "vocab.txt").write_text("".join(t + "\n" for t, _ in vocab), encoding="utf-8")
    (out / "labels.json").write_text(json.dumps(modell.LABELS), encoding="utf-8")
    # The threshold chosen on the development part goes with the model (the app reads it too).
    (out / "schwelle.json").write_text(json.dumps({"schwelle": modell.load_threshold(folder)}), encoding="utf-8")

    rules = modell.load_rules()
    measures = {}
    for name, path in (("fp32", fp32), ("int8", int8)):
        predictor = modell.Predictor(path)
        row = {}
        for section in ("entwicklung", "pruef"):
            for variant, sc in modell.variants(predictor.spans, section, rules).items():
                found, n = sc.recall()
                wrong, m = sc.false_rate()
                row[f"{section}_{variant}"] = (round(100 * found / n, 1), round(100 * wrong / m, 1))
        row["zeit"] = evaluate.timing(predictor, runs=1)
        measures[name] = row
        print(name, json.dumps(row, ensure_ascii=False))
    loss = measures["fp32"]["pruef_modell"][0] - measures["int8"]["pruef_modell"][0]
    report = json.loads((folder.parent / "bericht.json").read_text(encoding="utf-8")) if (folder.parent / "bericht.json").exists() else {}
    write_card(out, folder, report, measures, int8.stat().st_size)
    # Messbericht for the publication (AP-117, tools/modelle/veroeffentlichen.py checks the goals with it).
    (out / "messung.json").write_text(json.dumps(measures, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Fertig: {out}  (int8 {int8.stat().st_size / 1e6:.0f} MB, Verlust gegenüber fp32: {loss:.1f} Prozentpunkte)")
    if loss > MAX_LOSS:
        sys.exit(f"int8 verliert {loss:.1f} Prozentpunkte (erlaubt {MAX_LOSS}) – nicht ausliefern")


def write_card(out: Path, folder: Path, report: dict, measures: dict, size: int) -> None:
    base = report.get("basis", "?")
    lic_name, authors = BASES.get(base, ("vor Auslieferung prüfen", "?"))
    m = measures["int8"]
    pct = lambda v: f"{v[0]} % gefunden, {v[1]} % fälschlich ersetzt"  # noqa: E731
    card = f"""# Modellkarte: {NAME} (Erkennungsmodell der Pseudonymisierung, AP-67)

Stand: {datetime.date.today():%d.%m.%Y} · Datei `{FILE}.int8.onnx` (bis 1.0.65: „Erkennungsmodell“, `pii-modell.int8.onnx`)

## Was es tut
Findet in deutschen Texten private **Personen** (`PER`), **Einrichtungen** wie Schulen, Kitas, Praxen (`EINR`) und
**Orte mit Personenbezug** (`ORT`), damit die App sie vor dem Senden durch Platzhalter ersetzt. Feste Muster (Mail,
Telefon, IBAN, Matrikelnummer …) und die eigene Liste bleiben bei den Regeln von `PiiDetector`.

## Herkunft
- **Basismodell**: `{base}` (Version `{report.get("revision") or "neueste"}`) von {authors}, Lizenz **{lic_name}**.
  {NAME} ist eine abgeleitete Arbeit: Das Basismodell wurde **von uns feinabgestimmt und verändert**. {NAME} ist kein
  Produkt der Urheber des Basismodells; sie haben es weder geprüft noch gebilligt.
- **Lizenz von {NAME}**: Bedingungen von PyTales in `License.md` im Repository ADEU-TUD/Lora (kostenlos nutzbar;
  Änderungen und Weiterverbreitung nur mit Genehmigung). Das Basismodell bleibt unter seiner eigenen Lizenz (unten).
- **Feintuning**: Token-Klassifikation (BIO), {report.get("daten", "?")} erfundene Trainingstexte, beste Epoche
  {report.get("beste_epoche", "?")} von {len(report.get("epochen", []))}.
- **Daten**: ausschließlich erfundene Texte. Claude (Anthropic) hat als Lehrer-Modell Vorlagen mit Platzhaltern
  geschrieben (`tools/pii/vorlagen`), ein Skript hat erfundene Namen, Einrichtungen und Orte eingesetzt
  (`tools/pii/gen.py`). Keine echten Interviews, Abgaben, Mails oder Schülerdaten.
- **Format**: ONNX, int8 (dynamische Quantisierung pro Kanal), {size / 1e6:.0f} MB; Tokenizer `vocab.txt`/`tokenizer.json`.

## Kennzahlen (Messkorpus `tools/pii/messkorpus`, int8)
| | Entwicklung | Prüfteil |
| --- | --- | --- |
| Modell allein | {pct(m["entwicklung_modell"])} | {pct(m["pruef_modell"])} |
| wie in der App geplant (Regeln ohne Vornamen-Raten + Modell) | {pct(m.get("entwicklung_geplant", ("–", "–")))} | {pct(m.get("pruef_geplant", ("–", "–")))} |

Schwelle {modell.load_threshold(out)} (auf dem Entwicklungsteil gewählt, `schwelle.json`). Zeit je Absatz (CPU, ONNX
Runtime, 4 Kerne): Median {m["zeit"]["median_ms"]} ms, 95 % unter {m["zeit"]["p95_ms"]} ms.
Ziel AP-67 (Prüfteil, wie geplant): mindestens 98 % gefunden, höchstens 2 % fälschlich ersetzt.

## Grenzen
- Kennt nur, was in den Vorlagen vorkommt: Deutsch, Schul- und Hochschulalltag. Andere Sprachen und Fachgebiete sind
  nicht geprüft.
- Öffentliche Personen erkennt es am Zusammenhang (Amt, Werk, Zitat), nicht an einem Weltwissen; unbekannte
  öffentliche Namen ohne solchen Zusammenhang werden eher ersetzt (im Zweifel für den Datenschutz).
- Indirekte Merkmale („die Schulleiterin der einzigen Schule im Ort“) erkennt es nicht.
- Ein Mensch prüft im Zweifel die Vorschau; „nicht ersetzen“/„auch ersetzen“ gehen vor.
"""
    lic = HERE / "modell" / f"LIZENZ-{base.split('/')[-1]}.txt"
    if lic.exists():
        card += f"\n## Lizenz des Basismodells ({base}, {lic_name})\n\n" + lic.read_text(encoding="utf-8").strip() + "\n"
        shutil.copy(lic, out / lic.name)
    (out / "MODELLKARTE.md").write_text(card, encoding="utf-8")


if __name__ == "__main__":
    main()
