"""Training texts for the recognition model (AP-67, Stufe b).

The teacher (Claude, in the cloud session) wrote templates with placeholders in ``vorlagen/<Textart>.txt``; this script
fills them with invented names, institutions and places and writes the exact character spans – no labelling by a model,
so the labels are right by construction.

    python gen.py check                 # templates: syntax, overlap with the Messkorpus, names written literally
    python gen.py make [-n 30000]       # daten/train.jsonl, daten/dev.jsonl, daten/beispiele.txt

Placeholders (an optional w/m fixes the gender, a digit the person – {Vw1} or {V1w} –, ``:gen`` makes a genitive):
    {V} first name · {N} surname · {VN} full name · {NV} "Surname, First name" · {IN} "A. Surname" · {P} any of them
    ({v}, {n}, {vn}: the same in lower case, as typed quickly in a chat)
    {SCHULE} {KITA} {PRAXIS} {VEREIN} {EINR} institution · {ORT} place with personal reference      → labelled
    {AUT} cited surname · {AUTVN} author's full name · {OEFF} public person · {OEINR} public institution
    {OORT} place without personal reference · {WERK} title of a book                                 → stay (O)
    {KLASSE} {FACH} {JAHR} {DATUM} {UHR} {ZAHL} {NOTE} {MAIL} {MAIL1} {TEL} {INI} (an initial "H.")   → stay (O)

The test part of the Messkorpus stays out: names and words of its details are never drawn (``--ohne-sperre`` for the
final model), and templates that share five words in a row with it are reported.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus  # noqa: E402
import namen  # noqa: E402

TEMPLATES = HERE / "vorlagen"
OUT = HERE / "daten"

SLOT = re.compile(r"\{([A-Z]+|vn|v|n)([wm])?(\d)?([wm])?(:gen)?\}")
PERSON_FORMS = {"V", "N", "VN", "NV", "IN", "P"}
INSTITUTIONS = {"SCHULE", "KITA", "PRAXIS", "VEREIN", "EINR"}
PUBLIC = {"AUT", "AUTVN", "OEFF", "OEINR", "OORT", "WERK"}
FILLERS = {"KLASSE", "FACH", "JAHR", "DATUM", "UHR", "ZAHL", "NOTE", "MAIL", "TEL", "INI"}
ALL_SLOTS = PERSON_FORMS | INSTITUTIONS | PUBLIC | FILLERS | {"ORT"}

#: How the samples are spread over the kinds of text (the rest of the weight goes to the others equally).
WEIGHTS = {"chat": 1.4, "mail": 1.2, "tabelle": 0.8}
#: Kinds of text whose templates are joined with line breaks.
LINES = {"mail", "protokoll", "tabelle", "elterngespraech"}


@dataclass
class Template:
    textart: str
    n: int
    text: str

    @property
    def id(self) -> str:
        return f"{self.textart}:{self.n}"

    @property
    def dev(self) -> bool:
        """About a tenth of the templates are held out for the development set."""
        return int(hashlib.sha1(self.text.encode()).hexdigest(), 16) % 10 == 0


def load_templates() -> list[Template]:
    out = []
    for path in sorted(TEMPLATES.glob("*.txt")):
        block: list[str] = []
        n = 0

        def flush():
            nonlocal block, n
            if block:
                n += 1
                out.append(Template(path.stem, n, "\n".join(block)))
            block = []

        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("#"):
                continue
            if not line.strip():
                flush()
            else:
                block.append(line.rstrip())
        flush()
    return out


def blocked_words() -> set[str]:
    """Words of the details in the test part (core words of private ones, capitalized words of public ones)."""
    out: set[str] = set()
    for s in corpus.load("pruef"):
        for sp in s.spans:
            idx = corpus.core_words(sp, s.words) if sp.private else [
                i for i in sp.words if s.words[i][2][:1].isupper() and s.words[i][2].lower() not in corpus.GENERAL]
            for i in idx:
                w = s.words[i][2].lower()
                out.add(w)
                if w.endswith("s") and len(w) > 3:
                    out.add(w[:-1])
    return out


def translit(s: str) -> str:
    s = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    s = unicodedata.normalize("NFKD", s)
    return "".join(c for c in s if c.isascii() and (c.isalnum() or c in ".-")).lower()


def genitive(s: str) -> str:
    return s + "’" if s[-1] in "sßxz" else s + "s"


@dataclass
class Person:
    first: str
    last: str
    gender: str


@dataclass
class Filler:
    rng: random.Random
    blocked: set[str]
    people: dict = field(default_factory=dict)
    places: dict = field(default_factory=dict)
    institutions: dict = field(default_factory=dict)

    def ok(self, text: str) -> bool:
        return not any(w.lower() in self.blocked for _, _, w in corpus.split_words(text))

    def draw(self, f, tries: int = 60) -> str:
        for _ in range(tries):
            v = f()
            if self.ok(v):
                return v
        return v

    def first(self, gender: str, free: bool = False) -> str:
        """A first name of [gender]; with [free] (no gender in the template) also from the app's list."""
        r = self.rng
        pool = namen.FIRST_W if gender == "w" else namen.FIRST_M
        return self.draw(lambda: r.choice(namen.APP_FIRST) if free and r.random() < 0.2 else r.choice(pool))

    def last(self) -> str:
        r = self.rng

        def one() -> str:
            x = r.random()
            return r.choice(namen.SURNAMES) if x < 0.68 else r.choice(namen.APP_LAST) if x < 0.83 else namen.invented_surname(r)

        return self.draw(lambda: f"{one()}-{one()}" if r.random() < 0.04 else one())

    def person(self, gender: str | None, index: str | None) -> Person:
        if index and index in self.people:
            return self.people[index]
        g = gender or self.rng.choice("wm")
        p = Person(self.first(g, free=gender is None), self.last(), g)
        if self.rng.random() < 0.05:  # two first names: "Anna Lena", "Jan Niklas"
            p.first = f"{p.first} {self.first(g)}"
        if index:
            self.people[index] = p
        return p

    def person_text(self, form: str, p: Person, gen: bool) -> list[tuple[str, str | None]]:
        """Pieces of text with their label (None = O)."""
        r = self.rng
        if form == "P":
            form = r.choices(["V", "N", "VN", "IN"], [35, 20, 40, 5])[0]
        g = genitive if gen else (lambda s: s)
        if form == "V":
            return [(g(p.first), "PER")]
        if form == "N":
            return [(g(p.last), "PER")]
        if form == "VN":
            return [(g(f"{p.first} {p.last}"), "PER")]
        if form == "NV":
            return [(p.last, "PER"), (", ", None), (g(p.first), "PER")]
        return [(g(f"{p.first[0]}. {p.last}"), "PER")]

    def fill(self, kind: str, gender: str | None, index: str | None, gen: bool, textart: str) -> list[tuple[str, str | None]]:
        r = self.rng
        if kind in PERSON_FORMS:
            return self.person_text(kind, self.person(gender, index), gen)
        if kind in INSTITUTIONS:
            key = f"{kind}{index}"
            if index and key in self.institutions:
                return [(self.institutions[key], "EINR")]
            v = self.draw(lambda: namen.institution(r, None if kind == "EINR" else kind))
            if index:
                self.institutions[key] = v
            return [(v, "EINR")]
        if kind == "ORT":
            if index and index in self.places:
                return [(self.places[index], "ORT")]
            v = self.draw(lambda: namen.place(r))
            if index:
                self.places[index] = v
            return [(v, "ORT")]
        if kind == "AUT":
            v = self.draw(lambda: r.choice(namen.AUTHORS) if r.random() < 0.6 else self.last())
            return [(genitive(v) if gen else v, "AUT")]
        if kind == "AUTVN":
            v = self.draw(lambda: f"{self.first(r.choice('wm'))} {r.choice(namen.AUTHORS) if r.random() < 0.6 else self.last()}")
            return [(genitive(v) if gen else v, "AUT")]
        if kind == "OEFF":
            v = self.draw(lambda: r.choice(namen.PUBLIC_PERSONS))
            return [(genitive(v) if gen else v, "OEFF")]
        if kind == "OEINR":
            return [(self.draw(lambda: namen.public_institution(r)), "OEINR")]
        if kind == "OORT":
            return [(self.draw(lambda: r.choice(namen.PUBLIC_PLACES) if r.random() < 0.5 else namen.place(r)), "OORT")]
        if kind == "WERK":
            return [(self.draw(lambda: r.choice(namen.WORKS)), "WERK")]
        if kind == "KLASSE":
            return [(f"{r.randint(1, 12)}{r.choice('abcdef')}", None)]
        if kind == "FACH":
            return [(r.choice(namen.SUBJECTS), None)]
        if kind == "JAHR":
            return [(str(r.randint(1965, 2025)), None)]
        if kind == "DATUM":
            return [(f"{r.randint(1, 28)}.{r.randint(1, 12):02d}.", None)]
        if kind == "UHR":
            return [(f"{r.randint(7, 18)}:{r.choice(['00', '15', '30', '45'])}", None)]
        if kind == "INI":
            return [(r.choice("ABCDEFGHIJKLMNOPRSTUVWZ") + ".", None)]
        if kind == "ZAHL":
            return [(str(r.randint(2, 40)), None)]
        if kind == "NOTE":
            return [(r.choice(["1", "2", "3", "4", "5", "1,3", "1,7", "2,0", "2,3", "2,7", "3,0", "3,3"]), None)]
        if kind == "TEL":
            return [(r.choice(["0351 ", "0176 ", "0152 ", "+49 351 "]) + "".join(str(r.randint(0, 9)) for _ in range(7)), None)]
        if kind == "MAIL":
            p = self.people.get(index) if index else None
            p = p or self.person(None, None)
            local = translit(p.first.split()[0]) + r.choice([".", "_", ""]) + translit(p.last)
            return [(f"{local}@{r.choice(['web.de', 'gmx.de', 'mailbox.tu-dresden.de', 't-online.de', 'posteo.de'])}", None)]
        raise ValueError(kind)


#: Institutions that are no feminine nouns: the template says "an der {SCHULE}", the text then says "am Gymnasium …".
NEUTER = ("Gymnasium", "Förderzentrum", "Berufliches", "Jugendhaus")
MASCULINE = ("Hort", "Kindergarten", "Verein", "Jugendclub", "SV ", "FSV ")
ARTICLES = {
    "n": [("an der ", "am "), ("in der ", "im "), ("von der ", "vom "), ("zur ", "zum "), ("auf der ", "auf dem "),
          ("auf die ", "aufs "), ("in die ", "ins "), ("die ", "das ")],
    "m": [("an der ", "am "), ("in der ", "im "), ("von der ", "vom "), ("zur ", "zum "), ("auf der ", "auf dem "),
          ("in die ", "in den "), ("die ", "den ")],
}


def fix_article(before: str, institution: str) -> str:
    g = "n" if institution.startswith(NEUTER) or institution.endswith("-Gymnasium") else \
        "m" if institution.startswith(MASCULINE) or institution.endswith("e.V.") else None
    if g is None:
        return before
    for old, new in ARTICLES[g]:
        if before.lower().endswith(old) and (len(before) == len(old) or not before[-len(old) - 1].isalpha()):
            keep = before[:-len(old)]
            return keep + (new[0].upper() + new[1:] if not keep.strip() or keep.rstrip()[-1] in ".!?:\n" else new)
    return before


def render(t: Template, filler: Filler) -> tuple[str, list, list]:
    """Text, private spans (start, end, kind) and public spans of one filled template."""
    text, spans, public, pos = [], [], [], 0
    length = 0
    for m in SLOT.finditer(t.text):
        lit = t.text[pos:m.start()]
        kind, gender, index, gen = m.group(1), m.group(2) or m.group(4), m.group(3), bool(m.group(5))
        lower = kind.islower()  # {v}, {n}, {vn}: written in lower case, as in a quick chat
        pieces = filler.fill(kind.upper(), gender, index, gen, t.textart)
        if lower:
            pieces = [(p.lower(), label) for p, label in pieces]
        if kind in INSTITUTIONS:
            lit = fix_article(lit, pieces[0][0])
        text.append(lit)
        length += len(lit)
        for piece, label in pieces:
            if label in ("PER", "EINR", "ORT"):
                spans.append((length, length + len(piece), label))
            elif label:
                public.append((length, length + len(piece), label))
            text.append(piece)
            length += len(piece)
        pos = m.end()
    text.append(t.text[pos:])
    return "".join(text), spans, public


def make_sample(templates: list[Template], rng: random.Random, blocked: set[str]) -> dict:
    textart = templates[0].textart
    sep = "\n" if textart in LINES else " "
    text, spans, public, ids = "", [], [], []
    for t in templates:
        part, s, p = render(t, Filler(rng, blocked))
        off = len(text) + (len(sep) if text else 0)
        text = text + sep + part if text else part
        spans += [(a + off, b + off, k) for a, b, k in s]
        public += [(a + off, b + off, k) for a, b, k in p]
        ids.append(t.id)
    # Chat: often all lower case; elsewhere now and then a name in lower or upper case.
    if textart == "chat" and rng.random() < 0.25:
        text = text.lower()
    elif spans and rng.random() < 0.04:
        a, b, k = rng.choice(spans)
        text = text[:a] + (text[a:b].upper() if textart == "tabelle" else text[a:b].lower()) + text[b:]
    return {"text": text, "spans": spans, "public": public, "textart": textart, "vorlagen": ids}


def markup(sample: dict) -> str:
    """The sample in the notation of the Messkorpus, to read it."""
    marks = [(a, b, f"[[", f"|{k}]]") for a, b, k in sample["spans"]] + [(a, b, "{{", f"|{k}}}}}") for a, b, k in sample["public"]]
    text = sample["text"]
    for a, b, o, c in sorted(marks, reverse=True):
        text = text[:a] + o + text[a:b] + c + text[b:]
    return text


def ngrams(words: list[str], n: int = 5) -> set[tuple[str, ...]]:
    return {tuple(words[i:i + n]) for i in range(len(words) - n + 1)}


def check() -> int:
    templates = load_templates()
    problems = 0
    for t in templates:
        for m in SLOT.finditer(t.text):
            if m.group(1).upper() not in ALL_SLOTS:
                print(f"{t.id}: unbekannter Platzhalter {m.group(0)}")
                problems += 1
        rest = SLOT.sub("", t.text)
        if "{" in rest or "}" in rest:
            print(f"{t.id}: Klammer ohne Platzhalter: {t.text!r}")
            problems += 1
    # Overlap with the Messkorpus: five words in a row (without placeholders and letter formulas).
    test = {s.id: ngrams([w.lower() for _, _, w in s.words]) for s in corpus.load()}
    for t in templates:
        grams = {g for g in ngrams([w.lower() for _, _, w in corpus.split_words(SLOT.sub(" ", t.text))])
                 if sum(w in FORMULA for w in g) < 4}
        for sid, g in test.items():
            common = grams & g
            if common:
                print(f"{t.id}: fünf gleiche Wörter wie {sid}: {' '.join(next(iter(common)))}")
                problems += 1
    # Names and words of the test part written literally in a template.
    blocked = blocked_words()
    names = {w.lower() for w in namen.FIRST_W + namen.FIRST_M + namen.SURNAMES}
    for t in templates:
        for _, _, w in corpus.split_words(SLOT.sub(" ", t.text)):
            if w.lower() in blocked and w[:1].isupper() and w.lower() not in GENERIC_OK:
                print(f"{t.id}: Wort aus dem Prüfteil wörtlich in der Vorlage: {w}")
                problems += 1
            elif w.lower() in names and w[:1].isupper() and w.lower() not in LITERAL_OK:
                print(f"{t.id}: Name wörtlich in der Vorlage (bleibt unbeschriftet!): {w}")
    by = {}
    for t in templates:
        by[t.textart] = by.get(t.textart, 0) + 1
    print(f"{len(templates)} Vorlagen: " + ", ".join(f"{k} {v}" for k, v in sorted(by.items())))
    print(f"davon Entwicklung (zurückgehalten): {sum(t.dev for t in templates)}")
    return problems


#: Words of letter formulas ("Sehr geehrte Damen und Herren", "Mit freundlichen Grüßen i. A.") – shared five words of
#: these are no copy.
FORMULA = set("sehr geehrte damen und herren liebe kolleginnen kollegen mit freundlichen grüßen i a tag der offenen tür "
              "beim am vielen dank für ihre nachricht".split())

#: Ordinary words that are also in the test part (e.g. "Lehrer" from "Lehrer Lämpel") and may be written in templates.
GENERIC_OK = set("lehrer zusammenarbeit zeitung kultusministerium elbe rathaus ende mama".split())

#: Words that are names in the pools but are ordinary words in the templates.
LITERAL_OK = {w.lower() for w in """
Winter Sommer Frank Fuchs Koch Bauer Wolf Vogel Engel Kaiser König Graf Klein Lange Lang Jung Stark Weiß Schwarz Braun
Roth Stein Horn Busch Baum Kern Fink Kraft Ritter Rose Martin Otto Paul Simon Thomas Ernst Bach Mai Marx Kurz Bock
Nagel Mohr Hahn Mann Kirsch Witt Wilde Stahl Pilz Pech Rabe Scharf Mittag Freitag Heil Glaser Herzog Meister Schade
Ehrlich Schöne Do Igel Nelke
""".split()}


def template_weight(t: Template) -> float:
    """Places and institutions are rarer than names: templates with them are drawn more often."""
    kinds = {m.group(1).upper() for m in SLOT.finditer(t.text)}
    return 2.5 if kinds & (INSTITUTIONS | {"ORT"}) else 1.0


def make(n: int, seed: int, sperre: bool) -> None:
    rng = random.Random(seed)
    templates = load_templates()
    blocked = blocked_words() if sperre else set()
    by_art: dict[str, dict[bool, list[Template]]] = {}
    for t in templates:
        by_art.setdefault(t.textart, {True: [], False: []})[t.dev].append(t)
    arts = sorted(by_art)
    weights = [WEIGHTS.get(a, 1.0) for a in arts]
    OUT.mkdir(exist_ok=True)
    counts = {}
    with (OUT / "train.jsonl").open("w", encoding="utf-8") as train, (OUT / "dev.jsonl").open("w", encoding="utf-8") as dev:
        for i in range(n):
            is_dev = i % 10 == 0
            art = rng.choices(arts, weights)[0]
            pool = by_art[art][is_dev] or by_art[art][not is_dev]
            k = rng.choices([1, 2, 3], [60, 30, 10])[0]
            sample = make_sample(rng.choices(pool, [template_weight(t) for t in pool], k=k), rng, blocked)
            (dev if is_dev else train).write(json.dumps(sample, ensure_ascii=False) + "\n")
            for _, _, kind in sample["spans"]:
                counts[kind] = counts.get(kind, 0) + 1
    with (OUT / "beispiele.txt").open("w", encoding="utf-8") as f:
        prev = random.Random(seed + 1)
        for t in prev.sample(templates, min(150, len(templates))):
            f.write(markup(make_sample([t], prev, blocked)) + "\n\n")
    dev_n = len(range(0, n, 10))
    print(f"{n} Beispiele ({n - dev_n} Training, {dev_n} Entwicklung) in {OUT}; Angaben: {counts}")
    print(f"Sperre aus dem Prüfteil: {len(blocked)} Wörter" if sperre else "ohne Sperre (für das endgültige Modell)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["check", "make"])
    ap.add_argument("-n", type=int, default=30000)
    ap.add_argument("--seed", type=int, default=67)
    ap.add_argument("--ohne-sperre", action="store_true")
    a = ap.parse_args()
    if a.cmd == "check":
        sys.exit(1 if check() else 0)
    make(a.n, a.seed, not a.ohne_sperre)
