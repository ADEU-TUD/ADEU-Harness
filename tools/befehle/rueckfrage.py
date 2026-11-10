"""Rückfrage mit zwei Knöpfen (AP-73): when the command model is not sure enough to act, Lora shows the two likeliest
actions as buttons („Meinst du … oder …?“) plus „Etwas anderes“ – instead of passing the wish on to a language model.

    python rueckfrage.py <modell.onnx>                    # development part: chooses the rule and the lower bound
    python rueckfrage.py <modell.onnx> --teil pruef       # test part: numbers only, with the chosen values (no lists)

The rule (the same as CommandRecognizer.choices in the app): a wish the model is not sure of (likeliest label below the
threshold, or a free label below it) gets buttons when the two likeliest **commands** together reach the lower bound
(``untergrenze``). Free requests carry most of their probability on ``frei.*`` and stay below it.

The lower bound is chosen **only on the development part**: the lowest one (most wishes with buttons) at which at most
MAX_FREE_ASKED % of the free requests there would get buttons. The test part is measured once with the chosen value and
never used to choose anything.

Measures (AP-73):
  - **richtig unter den Knöpfen**: of the commands the model passed on so far, the share whose right action is one of
    the two buttons (goal on the test part: at least 80 %),
  - **freie mit Knöpfen**: share of the free requests that get buttons instead of going on (goal: at most 5 %),
  - **mit Rückfrage zusammen**: commands carried out at once or right under the buttons (the user's choice is a tap,
    not a guess); carried out wrongly does not change – the buttons only ask.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus  # noqa: E402
import modell  # noqa: E402

BOUNDS = (0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75)
#: Free requests that may get buttons on the development part, in percent (the goal on the test part is 5 %). Strict on
#: purpose, like modell.MAX_WRONG: the development part looks better than unseen wishes.
MAX_FREE_ASKED = 2.0


def two_commands(row, labels: list[str]) -> list[tuple[str, float]]:
    """The two likeliest labels that are not free, with their probabilities."""
    order = row.argsort()[::-1]
    out = []
    for i in order:
        if not corpus.is_free(labels[i]):
            out.append((labels[i], float(row[i])))
            if len(out) == 2:
                break
    return out


def asks(row, labels: list[str], threshold: float, bound: float) -> list[str] | None:
    """The labels of the two buttons, or None: the model is sure (it acts or passes a free request on), or the two
    likeliest commands together stay below [bound]."""
    best = int(row.argmax())
    if float(row[best]) >= threshold:
        return None
    pair = two_commands(row, labels)
    if len(pair) < 2 or pair[0][1] + pair[1][1] < bound:
        return None
    return [pair[0][0], pair[1][0]]


def measure(samples, probs, labels: list[str], threshold: float, bound: float) -> dict:
    passed = right = free = free_asked = commands = hit = 0
    for s, row in zip(samples, probs):
        best = int(row.argmax())
        label, sure = labels[best], float(row[best]) >= threshold
        acts = sure and not corpus.is_free(label)
        buttons = asks(row, labels, threshold, bound)
        if s.free:
            free += 1
            free_asked += int(buttons is not None)
            continue
        commands += 1
        if acts:
            hit += int(label in s.gold)
            continue
        passed += 1
        right += int(buttons is not None and any(b in s.gold for b in buttons))
    pct = lambda a, b: round(100 * a / max(b, 1), 1)  # noqa: E731
    return {"untergrenze": bound, "befehle": commands, "bisher_weitergegeben": passed,
            "richtig_unter_knoepfen": pct(right, passed), "frei": free, "frei_mit_knoepfen": pct(free_asked, free),
            "sofort_getroffen": pct(hit, commands), "mit_rueckfrage_zusammen": pct(hit + right, commands)}


def choose(rows: list[dict]) -> dict:
    """The lowest bound with at most MAX_FREE_ASKED % free requests asked; among equal results the higher bound."""
    ok = [r for r in rows if r["frei_mit_knoepfen"] <= MAX_FREE_ASKED]
    if not ok:
        return max(rows, key=lambda r: r["untergrenze"])
    return max(ok, key=lambda r: (r["richtig_unter_knoepfen"], r["untergrenze"]))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("modell", help=".onnx-Datei (mit vocab.txt, labels.json, schwelle.json daneben) oder Hugging-Face-Ordner")
    ap.add_argument("--teil", choices=["entwicklung", "pruef"], default="entwicklung")
    ap.add_argument("--untergrenze", type=float, default=None, help="sonst aus modell/rueckfrage.json (Prüfteil) bzw. gewählt")
    ap.add_argument("--json")
    a = ap.parse_args()

    predictor = modell.Predictor(Path(a.modell), device="cpu")
    samples = corpus.load(a.teil)
    probs = predictor.probs([(s.text, s.area) for s in samples])
    labels, threshold = predictor.labels, predictor.threshold

    if a.teil == "entwicklung":
        rows = [measure(samples, probs, labels, threshold, b) for b in BOUNDS]
        print(f"Entwicklungsteil (Schwelle {threshold}):")
        for r in rows:
            print(f"  Untergrenze {r['untergrenze']:.2f}: richtig unter den Knöpfen {r['richtig_unter_knoepfen']} % "
                  f"von {r['bisher_weitergegeben']}, freie mit Knöpfen {r['frei_mit_knoepfen']} %, "
                  f"zusammen {r['mit_rueckfrage_zusammen']} %")
        pick = choose(rows) if a.untergrenze is None else measure(samples, probs, labels, threshold, a.untergrenze)
        print(f"Gewählt: Untergrenze {pick['untergrenze']}")
        bound = pick["untergrenze"]
        # Error list for the development part only: commands passed on whose right action is not under the buttons.
        for s, row in zip(samples, probs):
            best = int(row.argmax())
            if s.free or (float(row[best]) >= threshold and not corpus.is_free(labels[best])):
                continue
            buttons = asks(row, labels, threshold, bound)
            if not buttons or not any(b in s.gold for b in buttons):
                print("   ", s.id, s.text, "/".join(s.gold), buttons or "keine Knöpfe", sep="  ")
        for s, row in zip(samples, probs):
            if s.free and asks(row, labels, threshold, bound):
                print("    frei mit Knöpfen:", s.id, s.text, asks(row, labels, threshold, bound), sep="  ")
        result = {"teil": "entwicklung", "schwelle": threshold, **pick, "alle": rows}
    else:
        bound = a.untergrenze
        if bound is None:
            bound = json.loads((HERE / "modell" / "rueckfrage.json").read_text(encoding="utf-8"))["untergrenze"]
        result = {"teil": "pruef", "schwelle": threshold, **measure(samples, probs, labels, threshold, bound)}
        print(f"Prüfteil (Schwelle {threshold}, Untergrenze {bound}): richtig unter den Knöpfen "
              f"{result['richtig_unter_knoepfen']} % von {result['bisher_weitergegeben']} bisher weitergegebenen Befehlen, "
              f"freie mit Knöpfen {result['frei_mit_knoepfen']} % von {result['frei']}; sofort getroffen "
              f"{result['sofort_getroffen']} %, mit Rückfrage zusammen {result['mit_rueckfrage_zusammen']} %")
    if a.json:
        Path(a.json).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
