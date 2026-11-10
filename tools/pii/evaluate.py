"""Measures a trained or exported recognition model on the Messkorpus (AP-67).

    python evaluate.py modelle/gbert-base/best                     # Hugging Face folder
    python evaluate.py modelle/<name>/onnx/lora-namensschutz-pytales.int8.onnx # exported model (ONNX Runtime, CPU)
    python evaluate.py … --teil pruef                             # the test part: numbers only, no error lists

Shows the model alone, with all rules of the app and as planned (fixed patterns of the rules + model, cited authors
stay); the rules come from desktop/build/pii/regeln.jsonl (PseudonymCorpusTest). Also measures the time per paragraph.
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus  # noqa: E402
import modell  # noqa: E402


def timing(predictor: modell.Predictor, runs: int = 3) -> dict:
    """Milliseconds per sample of the Messkorpus (a sample is about one paragraph)."""
    texts = [s.text for s in corpus.load()]
    predictor.spans(texts[0])  # warm up
    times = []
    for _ in range(runs):
        for t in texts:
            predictor._cache.clear()  # measure the model, not the cache
            t0 = time.perf_counter()
            predictor.spans(t)
            times.append(1000 * (time.perf_counter() - t0))
    times.sort()
    return {"median_ms": round(statistics.median(times), 1), "p95_ms": round(times[int(0.95 * len(times))], 1)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("modell", help="Ordner eines Hugging-Face-Modells oder .onnx-Datei")
    ap.add_argument("--teil", choices=["entwicklung", "pruef"], default="entwicklung")
    ap.add_argument("--zeit", action="store_true", help="auch die Zeit je Absatz messen")
    ap.add_argument("--json", help="Ergebnis zusätzlich als JSON hierhin schreiben")
    a = ap.parse_args()

    predictor = modell.Predictor(Path(a.modell), device="cpu" if a.modell.endswith(".onnx") else None)
    result = {"modell": a.modell, "teil": a.teil}
    for name, sc in modell.variants(predictor.spans, a.teil, modell.load_rules()).items():
        print(sc.report(f"{a.teil} – {modell.VARIANTS[name]}"))
        found, n = sc.recall()
        wrong, m = sc.false_rate()
        result[name] = {"gefunden": round(100 * found / n, 2), "falsch_ersetzt": round(100 * wrong / m, 2)}
        if a.teil == "entwicklung" and name == "geplant":
            print("  übersehen:")
            for x in sc.missed:
                print("    ", *x)
            print("  fälschlich ersetzt:")
            for x in sc.false_alarms:
                print("    ", *x)
    if a.zeit:
        result["zeit"] = timing(predictor)
        print(f"Zeit je Absatz (CPU): Median {result['zeit']['median_ms']} ms, 95 % unter {result['zeit']['p95_ms']} ms")
    if a.json:
        Path(a.json).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
