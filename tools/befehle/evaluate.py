"""Measures a trained or exported command model on the Messkorpus (AP-71).

    python evaluate.py modelle/<name>/best                         # Hugging Face folder
    python evaluate.py modelle/<name>/onnx/befehle-modell.int8.onnx # exported model (ONNX Runtime, CPU)
    python evaluate.py … --teil pruef                             # the test part: numbers only, no error lists
    python evaluate.py … --zeit                                   # also the time per wish
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
    """Milliseconds per wish of the Messkorpus, one at a time as in the app."""
    samples = corpus.load()
    predictor.predict(samples[0].text, samples[0].area)
    times = []
    for _ in range(runs):
        for s in samples:
            t0 = time.perf_counter()
            predictor.predict(s.text, s.area)
            times.append(1000 * (time.perf_counter() - t0))
    times.sort()
    return {"median_ms": round(statistics.median(times), 1), "p95_ms": round(times[int(0.95 * len(times))], 1)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("modell", help="Ordner eines Hugging-Face-Modells oder .onnx-Datei")
    ap.add_argument("--teil", choices=["entwicklung", "pruef"], default="entwicklung")
    ap.add_argument("--schwelle", type=float, default=None)
    ap.add_argument("--zeit", action="store_true")
    ap.add_argument("--json")
    a = ap.parse_args()

    predictor = modell.Predictor(Path(a.modell), device="cpu", threshold=a.schwelle)
    sc = modell.score(modell.predictions(predictor, a.teil), predictor.threshold)
    print(sc.report(f"{a.teil} (Schwelle {predictor.threshold})"))
    result = {"modell": a.modell, "teil": a.teil, "schwelle": predictor.threshold, **sc.numbers()}
    if a.teil == "entwicklung":
        for e in sc.errors:
            print("   ", *e, sep="  ")
    if a.zeit:
        result["zeit"] = timing(predictor)
        print(f"Zeit je Wunsch (CPU): Median {result['zeit']['median_ms']} ms, 95 % unter {result['zeit']['p95_ms']} ms")
    if a.json:
        Path(a.json).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
