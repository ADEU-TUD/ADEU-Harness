"""Messkorpus of the command model (AP-71, Stufe b): invented everyday wishes with the right action.

    python corpus.py check        # format, labels known to the code, counts per part and kind

One wish per line in messkorpus/*.txt (format: messkorpus/README.md):

    wish => label[|label2] [@bereich] [ ; name=value …]

Sections ``== prüfteil ==`` (never used for training, choosing settings or as a model for templates) and
``== entwicklung ==``. The measures are the same for the rules of the app, a Python model and the model in the app
(CommandCorpus in the Kotlin tests reads the same files).
"""
from __future__ import annotations

import json
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
CORPUS = HERE / "messkorpus"
SECTIONS = {"prüfteil": "pruef", "entwicklung": "entwicklung"}
FREE = ("frei.einfach", "frei.komplex")
#: What is open while the wish is typed (ActionArea of the app); "app" = none of the tools with actions.
AREAS = {"app": "App", "dateien": "Dateien", "pdf": "PDF", "dokument": "Dokument", "tabelle": "Tabelle", "folien": "Folien",
         "video": "Video", "mail": "Mail"}


def labels() -> list[str]:
    """All labels the code offers (written by CommandLabelsTest from the registers of the app)."""
    return [e["label"] for e in json.loads((HERE / "etiketten.json").read_text(encoding="utf-8"))]


@dataclass
class Sample:
    id: str
    section: str
    text: str
    gold: tuple[str, ...]
    area: str = "app"
    values: dict[str, str] = field(default_factory=dict)

    @property
    def free(self) -> bool:
        """A free request: every accepted label that is not free is only a tolerated alternative."""
        return self.gold[0].startswith("frei")


def is_free(label: str) -> bool:
    return label.startswith("frei")


def parse_line(line: str) -> tuple[str, tuple[str, ...], str, dict[str, str]]:
    text, _, rest = line.rpartition("=>")
    parts = [p.strip() for p in rest.split(" ; ")]
    head = parts[0].split()
    gold = tuple(g for g in head[0].split("|"))
    gold = tuple(x for g in gold for x in (FREE if g == "frei" else (g,)))
    area = next((h[1:] for h in head[1:] if h.startswith("@")), "app")
    values = dict((k.strip(), v.strip()) for k, v in (p.split("=", 1) for p in parts[1:] if p))
    return text.strip(), gold, area, values


def load(section: str | None = None) -> list[Sample]:
    out = []
    for path in sorted(CORPUS.glob("*.txt")):
        current, n = None, 0
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            m = re.fullmatch(r"==\s*(.+?)\s*==", line)
            if m:
                current = SECTIONS[m.group(1).lower()]
                continue
            if current is None:
                raise ValueError(f"{path.name}: Zeile vor dem ersten Abschnitt: {line}")
            n += 1
            text, gold, area, values = parse_line(line)
            if section is None or section == current:
                out.append(Sample(f"{path.stem}:{n}", current, text, gold, area, values))
    return out


def norm(s: str) -> str:
    """Lower case, no accents, only letters and digits – for the overlap check with the training data."""
    s = unicodedata.normalize("NFD", s.lower().replace("ß", "ss"))
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9 ]", " ", s)).strip()


@dataclass
class Score:
    """Counts for one way of recognizing. A prediction is (label, confidence); below the threshold – or with a free
    label – the wish is passed on to a language model."""
    commands: int = 0
    hit: int = 0            # command recognized and carried out
    passed: int = 0         # command passed on (a question back, not harmful)
    wrong: int = 0          # command: another action carried out
    free: int = 0
    free_passed: int = 0    # free request passed on
    free_wrong: int = 0     # free request: an action carried out
    free_kind: int = 0      # free requests with one kind (einfach/komplex) in the corpus …
    free_kind_hit: int = 0  # … whose kind was right
    errors: list = field(default_factory=list)

    def add(self, s: Sample, label: str, sure: bool) -> None:
        act = sure and not is_free(label)
        if s.free:
            self.free += 1
            if act and label not in s.gold:
                self.free_wrong += 1
                self.errors.append((s.id, s.text, "/".join(s.gold), label, "frei → ausgeführt"))
            else:
                self.free_passed += 1
            kinds = [g for g in s.gold if is_free(g)]
            if len(kinds) == 1:
                self.free_kind += 1
                self.free_kind_hit += int(label == kinds[0])
        else:
            self.commands += 1
            if act and label in s.gold:
                self.hit += 1
            elif act:
                self.wrong += 1
                self.errors.append((s.id, s.text, "/".join(s.gold), label, "falsch ausgeführt"))
            else:
                self.passed += 1
                self.errors.append((s.id, s.text, "/".join(s.gold), label, "weitergegeben"))

    @property
    def total(self) -> int:
        return self.commands + self.free

    def numbers(self) -> dict:
        pct = lambda a, b: round(100 * a / max(b, 1), 1)  # noqa: E731
        return {"befehle": self.commands, "treffer": pct(self.hit, self.commands),
                "befehl_weitergegeben": pct(self.passed, self.commands),
                "falsch_ausgefuehrt": pct(self.wrong + self.free_wrong, self.total),
                "frei": self.free, "frei_weitergegeben": pct(self.free_passed, self.free),
                "frei_art_richtig": pct(self.free_kind_hit, self.free_kind)}

    def report(self, title: str) -> str:
        n = self.numbers()
        return (f"{title}\n  Befehle: {n['treffer']} % getroffen ({self.hit}/{self.commands}), "
                f"{n['befehl_weitergegeben']} % weitergegeben\n"
                f"  falsch ausgeführt: {n['falsch_ausgefuehrt']} % aller Eingaben ({self.wrong + self.free_wrong}/{self.total})\n"
                f"  freie Anfragen: {n['frei_weitergegeben']} % weitergegeben ({self.free_passed}/{self.free}), "
                f"Art (einfach/komplex) richtig: {n['frei_art_richtig']} %")


def check() -> int:
    known = set(labels())
    bad = 0
    samples = load()
    for s in samples:
        for g in s.gold:
            if g not in known:
                print(f"{s.id}: unbekanntes Etikett {g}")
                bad += 1
        if s.area not in AREAS:
            print(f"{s.id}: unbekannter Bereich @{s.area}")
            bad += 1
    seen: dict[tuple[str, str], str] = {}
    for s in samples:
        key = (norm(s.text), s.area)
        if key in seen:
            print(f"{s.id}: doppelt mit {seen[key]}: {s.text}")
            bad += 1
        seen[key] = s.id
    for section in ("pruef", "entwicklung"):
        part = [s for s in samples if s.section == section]
        free = sum(s.free for s in part)
        covered = {s.gold[0] for s in part}
        missing = sorted(known - covered - {g for s in part for g in s.gold})
        print(f"{section}: {len(part)} Wünsche, davon {len(part) - free} Befehle und {free} freie; "
              f"{len(covered)} von {len(known)} Etiketten an erster Stelle; mit Werten {sum(bool(s.values) for s in part)}")
        if missing:
            print("  ohne Beispiel:", ", ".join(missing))
    return bad


if __name__ == "__main__":
    if sys.argv[1:] == ["check"]:
        sys.exit(1 if check() else 0)
    print(__doc__)
