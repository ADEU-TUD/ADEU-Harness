"""Test corpus of the pseudonymization (AP-67) and its measures.

Reads the annotated texts in ``messkorpus/`` (format: ``messkorpus/README.md``) and computes the measures exactly like
``PseudonymCorpusTest`` in the app: words, core words, word alignment.

    python corpus.py check     # check the format and count
    python corpus.py bio       # the corpus as BIO (JSONL) on stdout, e.g. to evaluate a model
"""
from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
CORPUS_DIR = HERE / "messkorpus"

PRIVATE_KINDS = ("PER", "EINR", "ORT", "MUSTER")
PUBLIC_KINDS = ("AUT", "OEFF", "EINR", "ORT", "WORT")
#: Kinds the recognition model learns (MUSTER stays with the rules).
MODEL_KINDS = ("PER", "EINR", "ORT")
#: Sections of a corpus file.
SECTIONS = {"prüfteil": "pruef", "entwicklung": "entwicklung"}

# A word: letters/digits joined by hyphen, apostrophe, dot or @ ("Goethe-Gymnasium", "e.V", "anna.k@web.de").
# The same in the Kotlin test: [\p{L}\p{N}]+(?:[-'’.@][\p{L}\p{N}]+)*
WORD = re.compile(r"[^\W_]+(?:[-'’.@][^\W_]+)*")

#: General words that need not be replaced inside a detail ("Grundschule [Einrichtung 1]" is fine).
GENERAL = {
    w.lower()
    for w in """
    am an auf der die das des dem den im in zum zur von vom und bei
    e.V Dr Prof Frau Herr Herrn Familie Oma Opa
    Grundschule Oberschule Gymnasium Schule Kita Kindertagesstätte Kindergarten Hort Förderschule Förderzentrum
    Gesamtschule Berufsschule Mittelschule Realschule Schulzentrum Montessori-Schule Freie Freien Evangelisches
    Praxis Ergotherapiepraxis Musikschule Beratungsstelle Jugendhaus Verein SV
    """.split()
}

MARKUP = re.compile(r"\[\[(.+?)\|([A-Z]+)\]\]|\{\{(.+?)\|([A-Z]+)\}\}")


@dataclass
class Span:
    text: str
    kind: str
    private: bool
    start: int  # characters in the plain text
    end: int
    words: list[int] = field(default_factory=list)  # indices into Sample.words


@dataclass
class Sample:
    file: str
    section: str  # "pruef" or "entwicklung"
    n: int
    text: str
    spans: list[Span]
    words: list[tuple[int, int, str]]  # (start, end, word)

    @property
    def id(self) -> str:
        return f"{self.file}:{self.section}:{self.n}"

    def bio(self) -> list[str]:
        """Labels per word in the BIO scheme (only private PER/EINR/ORT, else O)."""
        tags = ["O"] * len(self.words)
        for s in self.spans:
            if not s.private or s.kind not in MODEL_KINDS:
                continue
            for k, i in enumerate(s.words):
                tags[i] = ("B-" if k == 0 else "I-") + s.kind
        return tags


def split_words(text: str) -> list[tuple[int, int, str]]:
    return [(m.start(), m.end(), m.group()) for m in WORD.finditer(text)]


def core_words(span: Span, words: list[tuple[int, int, str]]) -> list[int]:
    """The words of [span] that must be replaced: all but single letters and GENERAL ones (all, if nothing is left)."""
    core = [i for i in span.words if len(words[i][2]) > 1 and words[i][2].lower() not in GENERAL]
    return core or list(span.words)


def parse_sample(raw: str, file: str, section: str, n: int) -> Sample:
    parts, spans, pos, length = [], [], 0, 0
    where = f"{file}:{section}:{n}"
    for m in MARKUP.finditer(raw):
        before = raw[pos:m.start()]
        parts.append(before)
        length += len(before)
        private = m.group(1) is not None
        content = m.group(1) if private else m.group(3)
        kind = m.group(2) if private else m.group(4)
        if kind not in (PRIVATE_KINDS if private else PUBLIC_KINDS):
            raise ValueError(f"{where}: Art {kind!r} passt nicht zu {'[[ ]]' if private else '{{ }}'} in {m.group()!r}")
        spans.append(Span(content, kind, private, length, length + len(content)))
        parts.append(content)
        length += len(content)
        pos = m.end()
    parts.append(raw[pos:])
    plain = "".join(parts)
    if any(t in plain for t in ("[[", "{{", "]]", "}}")):
        raise ValueError(f"{where}: unvollständige Auszeichnung in {raw!r}")
    words = split_words(plain)
    for s in spans:
        s.words = [i for i, (a, b, _) in enumerate(words) if a >= s.start and b <= s.end]
        # Whole words only: no word may cross the border of a span.
        for a, b, w in words:
            if a < s.start < b or a < s.end < b:
                raise ValueError(f"{where}: Angabe {s.text!r} schneidet das Wort {w!r}")
        if not s.words:
            raise ValueError(f"{where}: Angabe {s.text!r} ohne Wort")
    return Sample(file, section, n, plain, spans, words)


def load(section: str | None = None) -> list[Sample]:
    """All samples, or only those of ``pruef`` or ``entwicklung``."""
    samples: list[Sample] = []
    for path in sorted(CORPUS_DIR.glob("*.txt")):
        current, block, n = None, [], 0

        def flush():
            nonlocal block, n
            if block and current:
                n += 1
                samples.append(parse_sample("\n".join(block), path.stem, current, n))
            block = []

        for line in path.read_text(encoding="utf-8").splitlines():
            t = line.strip()
            if t.startswith("#"):
                continue
            if t.startswith("== ") and t.endswith(" =="):
                flush()
                current = SECTIONS[t.strip("= ").lower()]
                n = 0
                continue
            if not t:
                flush()
                continue
            block.append(line.rstrip())
        flush()
    return [s for s in samples if section is None or s.section == section]


def replaced_words(original: str, masked: str) -> set[int]:
    """Indices of the words of [original] missing in [masked] (longest common subsequence)."""
    a = [w for _, _, w in split_words(original)]
    b = [w for _, _, w in split_words(masked)]
    n, m = len(a), len(b)
    dp = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n - 1, -1, -1):
        for j in range(m - 1, -1, -1):
            dp[i][j] = dp[i + 1][j + 1] + 1 if a[i] == b[j] else max(dp[i + 1][j], dp[i][j + 1])
    kept, i, j = set(), 0, 0
    while i < n and j < m:
        if a[i] == b[j]:
            kept.add(i)
            i += 1
            j += 1
        elif dp[i + 1][j] >= dp[i][j + 1]:
            i += 1
        else:
            j += 1
    return set(range(n)) - kept


@dataclass
class Score:
    """Found private details and wrongly replaced public ones, per kind; other replaced words."""
    found: dict = field(default_factory=dict)  # kind -> [found, total]
    wrong: dict = field(default_factory=dict)  # kind -> [replaced, total]
    other: int = 0
    other_words: int = 0
    missed: list = field(default_factory=list)
    false_alarms: list = field(default_factory=list)

    def add(self, s: Sample, replaced: set[int]) -> None:
        in_span = set()
        for sp in s.spans:
            in_span.update(sp.words)
            if sp.private:
                ok = all(i in replaced for i in core_words(sp, s.words))
                c = self.found.setdefault(sp.kind, [0, 0])
                c[0] += ok
                c[1] += 1
                if not ok:
                    self.missed.append((s.id, sp.kind, sp.text))
            else:
                bad = any(i in replaced for i in sp.words)
                c = self.wrong.setdefault(sp.kind, [0, 0])
                c[0] += bad
                c[1] += 1
                if bad:
                    self.false_alarms.append((s.id, sp.kind, sp.text))
        rest = [i for i in range(len(s.words)) if i not in in_span]
        self.other_words += len(rest)
        self.other += sum(1 for i in rest if i in replaced)

    def recall(self, kinds=MODEL_KINDS) -> tuple[int, int]:
        v = [c for k, c in self.found.items() if k in kinds]
        return sum(c[0] for c in v), sum(c[1] for c in v)

    def false_rate(self) -> tuple[int, int]:
        v = list(self.wrong.values())
        return sum(c[0] for c in v), sum(c[1] for c in v)

    def report(self, title: str) -> str:
        lines = [title]
        f, n = self.recall()
        lines.append(f"  private Angaben gefunden (PER/EINR/ORT): {f}/{n} = {100 * f / max(n, 1):.1f} %")
        lines += [f"    {k:<6} {a}/{b} = {100 * a / max(b, 1):.1f} %" for k, (a, b) in sorted(self.found.items())]
        f, n = self.false_rate()
        lines.append(f"  öffentliche Angaben fälschlich ersetzt: {f}/{n} = {100 * f / max(n, 1):.1f} %")
        lines += [f"    {k:<6} {a}/{b} = {100 * a / max(b, 1):.1f} %" for k, (a, b) in sorted(self.wrong.items())]
        lines.append(f"  sonstige Fehlalarme: {self.other} von {self.other_words} Wörtern "
                     f"= {1000 * self.other / max(self.other_words, 1):.1f} je 1000")
        return "\n".join(lines)


def sentences(text: str) -> int:
    return max(1, len([t for t in re.split(r"(?<=[.!?…])\s+|\n", text) if WORD.search(t)]))


def _check() -> None:
    samples = load()
    for section in SECTIONS.values():
        ss = [s for s in samples if s.section == section]
        kinds: dict[str, int] = {}
        for s in ss:
            for sp in s.spans:
                key = ("+" if sp.private else "-") + sp.kind
                kinds[key] = kinds.get(key, 0) + 1
        print(f"{section}: {len(ss)} Beispiele, {sum(sentences(s.text) for s in ss)} Sätze/Zeilen, "
              f"{sum(len(s.words) for s in ss)} Wörter")
        print("   ", "  ".join(f"{k} {v}" for k, v in sorted(kinds.items())))
        for file in sorted({s.file for s in ss}):
            print(f"    {file:<16} {sum(1 for s in ss if s.file == file)}")


def _bio() -> None:
    for s in load():
        print(json.dumps({"id": s.id, "section": s.section, "words": [w for _, _, w in s.words], "tags": s.bio()},
                         ensure_ascii=False))


if __name__ == "__main__":
    {"check": _check, "bio": _bio}[sys.argv[1] if len(sys.argv) > 1 else "check"]()
