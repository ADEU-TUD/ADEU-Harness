"""Export of Lora Befehle, the command model, for the app (AP-71, Stufe e): ONNX, int8, model card.

    python export.py modelle/<name>/best

Writes modelle/<name>/onnx/: lora-befehle-pytales.onnx (fp32), lora-befehle-pytales.int8.onnx (dynamic int8
quantization; up to 1.0.65 the file was called befehle-modell.int8.onnx), the
tokenizer (vocab.txt), labels.json, schwelle.json, tokenizer-erwartet.jsonl (for the test of the tokenizer in the app)
and MODELLKARTE.md. The int8 model may lose at most 0.5 percentage points of hits on the test part compared with
fp32, and must not carry out more wrong actions – otherwise the export stops with an error.
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

import corpus  # noqa: E402
import evaluate  # noqa: E402
import modell  # noqa: E402

MAX_LOSS = 0.5  # percentage points
NAME = "Lora Befehle"
FILE = "lora-befehle-pytales"
#: Base models: licence and authors (as on their model cards on Hugging Face).
BASES = {
    "distilbert/distilbert-base-german-cased": ("Apache-2.0", "Hugging Face (DistilBERT nach Sanh, Debut, Chaumond und Wolf, 2019)"),
    "deepset/gbert-base": ("MIT", "deepset GmbH (Branden Chan, Stefan Schweter, Timo Möller)"),
    "deepset/gelectra-base": ("MIT", "deepset GmbH"),
}


def export_onnx(folder: Path, target: Path) -> list[str]:
    import torch
    from transformers import AutoModelForSequenceClassification, AutoTokenizer

    model = AutoModelForSequenceClassification.from_pretrained(folder).eval()
    model.config.return_dict = False
    tok = AutoTokenizer.from_pretrained(folder)
    sample = tok(["Tabelle"], ["sortiere nach nachname"], return_tensors="pt")
    import inspect

    accepted = inspect.signature(model.forward).parameters
    # DistilBERT has no token_type_ids, although its tokenizer may return them.
    names = [n for n in ("input_ids", "attention_mask", "token_type_ids") if n in sample and n in accepted]

    class Logits(torch.nn.Module):
        def __init__(self, inner):
            super().__init__()
            self.inner = inner

        def forward(self, *inputs):
            return self.inner(**dict(zip(names, inputs)))[0]

    axes = {n: {0: "batch", 1: "tokens"} for n in names} | {"logits": {0: "batch"}}
    args = tuple(sample[n] for n in names)
    kwargs = dict(input_names=names, output_names=["logits"], dynamic_axes=axes, opset_version=17)
    try:
        torch.onnx.export(Logits(model), args, str(target), dynamo=False, **kwargs)
    except TypeError:
        torch.onnx.export(Logits(model), args, str(target), **kwargs)
    return names


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("modell", help="Ordner des trainierten Modells (…/best)")
    ap.add_argument("--aus", default=None)
    a = ap.parse_args()

    from onnxruntime.quantization import QuantType, quantize_dynamic
    from transformers import AutoTokenizer

    folder = Path(a.modell)
    out = Path(a.aus) if a.aus else folder.parent / "onnx"
    out.mkdir(parents=True, exist_ok=True)
    fp32, int8 = out / f"{FILE}.onnx", out / f"{FILE}.int8.onnx"
    print("Export nach ONNX …")
    inputs = export_onnx(folder, fp32)
    print("int8 …")
    quantize_dynamic(str(fp32), str(int8), weight_type=QuantType.QInt8)

    tok = AutoTokenizer.from_pretrained(folder)
    tok.save_pretrained(out)
    vocab = sorted(tok.get_vocab().items(), key=lambda kv: kv[1])
    (out / "vocab.txt").write_text("".join(t + "\n" for t, _ in vocab), encoding="utf-8")
    labels = modell.load_labels(folder)
    (out / "labels.json").write_text(json.dumps(labels, ensure_ascii=False), encoding="utf-8")
    threshold = modell.load_threshold(folder)
    (out / "schwelle.json").write_text(json.dumps({"schwelle": threshold, "eingaben": inputs}), encoding="utf-8")
    # What the tokenizer of training makes of the wishes of the Messkorpus – the app must do the same (CommandModelTest).
    with (out / "tokenizer-erwartet.jsonl").open("w", encoding="utf-8") as f:
        for s in corpus.load():
            enc = modell.encode(tok, s.text, s.area)
            f.write(json.dumps({"text": s.text, "bereich": s.area, "ids": enc["input_ids"]}, ensure_ascii=False) + "\n")

    # The threshold is chosen for the model that is shipped: quantization shifts the probabilities a little, so each
    # file gets its own threshold by the same rule on the development part (modell.tune_threshold).
    measures = {}
    for name, path in (("fp32", fp32), ("int8", int8)):
        predictor = modell.Predictor(path)
        own, _ = modell.tune_threshold(modell.predictions(predictor, "entwicklung"))
        row = {section: modell.score(modell.predictions(predictor, section), own).numbers() for section in ("entwicklung", "pruef")}
        row["schwelle"] = own
        row["zeit"] = evaluate.timing(predictor, runs=2)
        measures[name] = row
        print(name, json.dumps(row, ensure_ascii=False))
    threshold = measures["int8"]["schwelle"]
    (out / "schwelle.json").write_text(json.dumps({"schwelle": threshold, "eingaben": inputs}), encoding="utf-8")
    loss = measures["fp32"]["pruef"]["treffer"] - measures["int8"]["pruef"]["treffer"]
    more_wrong = measures["int8"]["pruef"]["falsch_ausgefuehrt"] - measures["fp32"]["pruef"]["falsch_ausgefuehrt"]
    report = json.loads((folder.parent / "bericht.json").read_text(encoding="utf-8")) if (folder.parent / "bericht.json").exists() else {}
    write_card(out, report, measures, int8.stat().st_size, threshold, len(labels))
    (out / "messung.json").write_text(json.dumps(measures, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Fertig: {out}  (int8 {int8.stat().st_size / 1e6:.0f} MB, Verlust gegenüber fp32: {loss:.1f} Prozentpunkte)")
    if loss > MAX_LOSS or more_wrong > MAX_LOSS:
        sys.exit(f"int8 verliert {loss:.1f} Prozentpunkte Treffer bzw. führt {more_wrong:.1f} Punkte mehr falsch aus (erlaubt {MAX_LOSS}) – nicht ausliefern")


def write_card(out: Path, report: dict, measures: dict, size: int, threshold: float, n_labels: int) -> None:
    base = report.get("basis", "?")
    lic_name, authors = BASES.get(base, ("vor Auslieferung prüfen", "?"))
    m = measures["int8"]
    line = lambda n: f"{n['treffer']} % | {n['falsch_ausgefuehrt']} % | {n['frei_weitergegeben']} % | {n['frei_art_richtig']} %"  # noqa: E731
    layers = f", die ersten {report['schichten']} Schichten" if report.get("schichten") else ""
    card = f"""# Modellkarte: {NAME} (Befehlsmodell für Lora, AP-71)

Stand: {datetime.date.today():%d.%m.%Y} · Datei `{FILE}.int8.onnx` (bis 1.0.65: „Befehlsmodell“, `befehle-modell.int8.onnx`)

## Was es tut
Ordnet einen kurzen Wunsch an Lora oder Strg+K einer von {n_labels} Aktionen der App zu – Bereich oder Werkzeug öffnen,
Modus starten, Einstellung zeigen oder umstellen, TU Vorlage, Aktion im offenen Werkzeug, Tour, frühere Chats, Frist –
oder erkennt eine **freie Anfrage** (einfach bzw. komplex), die wie bisher an ein Sprachmodell geht. Es läuft nur auf dem
Gerät. Werte (an/aus, Seiten, Spalte, Dateiname …) liest die App danach mit Regeln aus dem Wunsch.
Ist die Sicherheit kleiner als die Schwelle, führt die App nichts aus, sondern gibt den Wunsch weiter.

## Herkunft
- **Basismodell**: `{base}` (Version `{report.get("revision") or "neueste"}`{layers}) von {authors}, Lizenz **{lic_name}**.
  {NAME} ist eine abgeleitete Arbeit: Das Basismodell wurde **von uns feinabgestimmt und verändert**. {NAME} ist kein
  Produkt der Urheber des Basismodells; sie haben es weder geprüft noch gebilligt.
- **Feintuning**: Satz-Klassifikation, {report.get("daten", "?")} erfundene Trainingswünsche, beste Epoche
  {report.get("beste_epoche", "?")} von {len(report.get("epochen", []))}. Eingabe: was offen ist (ein Wort) und der Wunsch in Kleinbuchstaben.
- **Etiketten**: aus dem Code der App erzeugt (`tools/befehle/etiketten.json`, `CommandLabelsTest`).
- **Daten**: ausschließlich erfundene Wünsche. Claude (Anthropic) hat als Lehrer-Modell Bezeichnungen, Satzmuster und
  Sätze je Etikett geschrieben (`tools/befehle/vorlagen`), ein Skript hat sie kombiniert und Tippfehler, fehlende
  Umlaute und Füllwörter ergänzt (`tools/befehle/gen.py`). Keine echten Eingaben von Nutzer:innen.
- **Format**: ONNX, int8 (dynamische Quantisierung), {size / 1e6:.0f} MB; Tokenizer `vocab.txt` (WordPiece).

## Kennzahlen (Messkorpus `tools/befehle/messkorpus`, int8, Schwelle {threshold})
| | Befehle getroffen | falsch ausgeführt (alle Eingaben) | freie Anfragen weitergegeben | Art einfach/komplex richtig |
| --- | --- | --- | --- | --- |
| Entwicklung | {line(m["entwicklung"])} |
| Prüfteil | {line(m["pruef"])} |

Zeit je Wunsch (CPU, ONNX Runtime, 2 Kerne): Median {m["zeit"]["median_ms"]} ms, 95 % unter {m["zeit"]["p95_ms"]} ms.
Ziel AP-71 (Prüfteil): mindestens 90 % getroffen, höchstens 2 % falsch ausgeführt, mindestens 95 % der freien Anfragen
weitergegeben. Die Schwelle wurde auf dem Entwicklungsteil gewählt (`schwelle.json`).

## Grenzen
- Kennt nur die Aktionen, die es beim Training gab. Neue Bereiche, Optionen und Aktionen der App gehen bis zum nächsten
  Training an das Sprachmodell (`CommandLabelsTest` meldet sie).
- Deutsch und einfaches Englisch, kurze Wünsche. Lange oder verschachtelte Aufträge („erst …, dann …“) sind freie Anfragen.
- Welche TU Vorlage, welcher Chat, welche Datei gemeint ist, entscheidet nicht das Modell, sondern die Suche der App.
- Riskante Aktionen (löschen, verschieben, überschreiben) zeigen weiter zuerst eine Vorschau; Einstellungen ändert die
  App erst nach „Ja“.
"""
    lic = HERE / "modell" / f"LIZENZ-{base.split('/')[-1]}.txt"
    if lic.exists():
        card += f"\n## Lizenz des Basismodells ({base}, {lic_name})\n\n" + lic.read_text(encoding="utf-8").strip() + "\n"
        shutil.copy(lic, out / lic.name)
    (out / "MODELLKARTE.md").write_text(card, encoding="utf-8")


if __name__ == "__main__":
    main()
