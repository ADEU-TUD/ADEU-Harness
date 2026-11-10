"""Training wishes for the command model (AP-71, Stufe c).

The teacher (Claude, in the cloud session) wrote names, sentence patterns and whole sentences per label in
``vorlagen/``; this script combines them, fills the placeholders with invented file names, columns, topics … and adds
what people really type: typos, missing umlauts, fillers. The label is right by construction.

    python gen.py check                # labels, placeholders, overlap with the Messkorpus
    python gen.py make [-n 140]        # daten/train.jsonl, daten/dev.jsonl, daten/beispiele.txt (not in the repo)

Files in vorlagen/: ``traeger.txt`` (patterns with {o} per kind of label), ``listen.txt`` (fillings), the others hold
blocks ``== <label>`` with lines
    o: name | name            names for the patterns of the label's kind
    s: sentence | sentence    whole sentences (``s@pdf,dateien:`` only while this is open)
    v:<value>: sentence | …   sentences for one value of a setting (stell:*)

The Messkorpus stays out: a wish that equals one of its wishes (both parts, without case, accents and punctuation) is
never written, and ``check`` reports every line of the templates that shares five words in a row with it.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import sys
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus  # noqa: E402

TEMPLATES = HERE / "vorlagen"
OUT = HERE / "daten"
SLOT = re.compile(r"\{([A-Z0-9]+)\}")
#: ActionArea.key of the app → area of the corpus.
AREA_OF = {"files": "dateien", "pdf": "pdf", "doc": "dokument", "sheet": "tabelle", "slides": "folien", "video": "video", "mail": "mail"}
ON_OFF = {"an": "true", "aus": "false"}
PREFIX = ["bitte ", "hey lora, ", "lora, ", "hallo, ", "kurze frage: ", "sag mal, ", "hi, ", "hey, ", "moin, ", "ähm ", "also ", "ok, ", "so, ",
          "du, ", "lora bitte ", "hallo lora, ",
          # zweite Runde (Fehler im Entwicklungsteil: Grüße aus dem Dialekt, Entschuldigungen, „na“)
          "servus, ", "grüß dich, ", "na, ", "moinsen, ", "hallöchen, ", "sorry, ", "ey, ", "huhu, ", "guten morgen, ",
          "hey du, ", "tach, ", "hi lora, ", "please ", "hey lora ", "eine frage, "]
#: Spoken forms people type ("wo is", "ich hab", "ich brauch") – second round, after errors in the development part.
COLLOQUIAL = [(" ist ", " is "), ("ich habe ", "ich hab "), ("ich brauche ", "ich brauch "), ("ich möchte ", "ich will "),
              (" nicht ", " nich "), ("kriege ", "krieg "), (" eine ", " ne "), (" einen ", " nen "), ("gibt es ", "gibts ")]
SUFFIX = [" bitte", " mal", ", danke", " pls", " bitte, danke", " mal bitte", " jetzt", " kurz"]


def normalize(text: str) -> str:
    """What the model sees: lower case, single spaces – the app does the same (CommandRecognizer.normalize)."""
    return re.sub(r"\s+", " ", text.lower()).strip()


@dataclass
class Phrase:
    label: str
    kind: str          # "o", "s" or "v"
    text: str
    areas: tuple[str, ...] | None = None
    value: str | None = None

    @property
    def dev(self) -> bool:
        """About a tenth of the lines also feeds the small development file."""
        return int(hashlib.sha1((self.label + self.text).encode()).hexdigest(), 16) % 10 == 0


def read_blocks(path: Path) -> dict[str, list[str]]:
    blocks: dict[str, list[str]] = {}
    current = None
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        m = re.fullmatch(r"==\s*(\S+)", line)
        if m:
            current = m.group(1)
            blocks.setdefault(current, [])
        elif current is None:
            raise ValueError(f"{path.name}: Zeile vor dem ersten Block: {line}")
        else:
            blocks[current].append(line)
    return blocks


def load() -> tuple[list[Phrase], dict[str, list[str]], dict[str, list[str]]]:
    carriers = read_blocks(TEMPLATES / "traeger.txt")
    lists = {k: [x.strip() for line in v for x in line.split("|") if x.strip()] for k, v in read_blocks(TEMPLATES / "listen.txt").items()}
    phrases: list[Phrase] = []
    for path in sorted(TEMPLATES.glob("*.txt")):
        if path.name in ("traeger.txt", "listen.txt"):
            continue
        for label, lines in read_blocks(path).items():
            for line in lines:
                head, _, body = line.partition(":")
                items = [x.strip() for x in body.split("|") if x.strip()]
                if head == "o":
                    phrases += [Phrase(label, "o", x) for x in items]
                elif head.startswith("s"):
                    areas = tuple(head[2:].split(",")) if head.startswith("s@") else None
                    phrases += [Phrase(label, "s", x, areas) for x in items]
                elif head == "v":
                    value, _, rest = body.partition(":")
                    phrases += [Phrase(label, "v", x.strip(), value=value.strip()) for x in rest.split("|") if x.strip()]
                else:
                    raise ValueError(f"{path.name}: {label}: unbekannte Zeile {line[:40]}")
    return phrases, carriers, lists


def carrier_kind(label: str) -> list[str]:
    if label.startswith("ort:option."):
        return ["ort.option"]
    if label.startswith("ort:place."):
        return ["ort.knopf"]
    if label.startswith("ort:mode."):
        return ["ort.modus"]
    if label.startswith("ort:"):
        return ["ort.bereich"]
    if label.startswith("stell:"):
        return ["an", "aus"]
    return []


def typo(rng: random.Random, text: str) -> str:
    words = text.split(" ")
    idx = [i for i, w in enumerate(words) if len(w) >= 5 and w.isalpha()]
    if not idx:
        return text
    i = rng.choice(idx)
    w = words[i]
    p = rng.randrange(1, len(w) - 1)
    how = rng.random()
    if how < 0.35:
        w = w[:p] + w[p + 1] + w[p] + w[p + 2:]
    elif how < 0.65:
        w = w[:p] + w[p + 1:]
    elif how < 0.85:
        w = w[:p] + w[p] + w[p:]
    else:
        w = w[:p] + rng.choice("aeiounrst") + w[p + 1:]
    words[i] = w
    return " ".join(words)


def translit(text: str) -> str:
    return text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")


class Generator:
    def __init__(self, seed: int, blocked: set[str]):
        self.rng = random.Random(seed)
        self.phrases, self.carriers, self.lists = load()
        self.entries = {e["label"]: e for e in json.loads((HERE / "etiketten.json").read_text(encoding="utf-8"))}
        self.blocked = blocked
        # The title of a place in the app is one of its names.
        for label, e in self.entries.items():
            if e["art"] == "ort":
                self.phrases.append(Phrase(label, "o", e["titel"]))
        self.by_label: dict[str, list[Phrase]] = {}
        for p in self.phrases:
            self.by_label.setdefault(p.label, []).append(p)

    def fill(self, text: str) -> str:
        return SLOT.sub(lambda m: self.rng.choice(self.lists[m.group(1)]), text)

    def area(self, label: str, phrase: Phrase) -> str:
        rng = self.rng
        if phrase.areas:
            return rng.choice(phrase.areas)
        own = AREA_OF.get(self.entries[label].get("bereich", ""))
        if own:
            return own if rng.random() < 0.9 else "app"
        return "app" if rng.random() < 0.6 else rng.choice(list(AREA_OF.values()))

    def one(self, label: str, dev: bool) -> dict | None:
        rng = self.rng
        # Training uses every line; the development file only the held-out tenth (a check of the training itself –
        # the choice of model and threshold is made on the Messkorpus).
        pool = [p for p in self.by_label.get(label, []) if p.dev or not dev] or self.by_label.get(label, [])
        if not pool:
            return None
        names = [p for p in pool if p.kind == "o"]
        rest = [p for p in pool if p.kind != "o"]
        kinds = carrier_kind(label)
        value = None
        if names and kinds and (not rest or rng.random() < 0.5):
            p = rng.choice(names)
            kind = rng.choice(kinds)
            # A setting with on/off patterns: the pattern carries the value. A bare name is no order to change it.
            # In Strg+K people mostly type the bare name of a place ("literatur", "schriftgröße").
            bare = kind.startswith("ort.") and rng.random() < 0.3
            text = p.text if bare else rng.choice(self.carriers[kind]).replace("{o}", p.text)
            value = ON_OFF.get(kind)
        elif rest:
            p = rng.choice(rest)
            text, value = p.text, p.value
        else:
            return None
        text = self.fill(text)
        if rng.random() < 0.12:
            for a, b in COLLOQUIAL:
                if a in " " + text + " " and rng.random() < 0.6:
                    text = (" " + text + " ").replace(a, b, 1).strip()
        if rng.random() < 0.18:
            text = rng.choice(PREFIX) + text
        if rng.random() < 0.12:
            text += rng.choice(SUFFIX)
        if rng.random() < 0.15:
            text = typo(rng, text)
        if rng.random() < 0.05:
            text = typo(rng, text)
        if rng.random() < 0.07:
            text = translit(text)
        if rng.random() < 0.12:
            text += rng.choice(["?", ".", "!", " ?"])
        text = normalize(text)
        if corpus.norm(text) in self.blocked:
            return None
        out = {"text": text, "bereich": self.area(label, p), "label": label}
        if value is not None:
            out["wert"] = value
        return out


def blocked_texts() -> set[str]:
    return {corpus.norm(s.text) for s in corpus.load()}


def check() -> int:
    bad = 0
    phrases, carriers, lists = load()
    known = set(corpus.labels())
    used = {p.label for p in phrases}
    for label in sorted(used - known):
        print(f"Vorlagen für unbekanntes Etikett: {label}")
        bad += 1
    for label in sorted(known - used):
        print(f"keine Vorlagen für: {label}")
        bad += 1
    for p in phrases:
        for slot in SLOT.findall(p.text):
            if slot not in lists:
                print(f"{p.label}: Platzhalter {{{slot}}} ohne Liste")
                bad += 1
    for label in sorted(used & known):
        n = len({p.text for p in phrases if p.label == label})
        if n < 8:
            print(f"wenig Stoff ({n} Zeilen): {label}")
    # Overlap with the Messkorpus: equal wishes are dropped by make. Commands are short and alike by nature ("wo ist die
    # option für …"), so shared words are no fault; reported is a whole sentence of five or more words that contains a
    # wish of the Messkorpus or lies inside one.
    wishes = [(s.id, " " + corpus.norm(s.text) + " ") for s in corpus.load()]
    same = 0
    blocked = blocked_texts()
    for p in phrases:
        if p.kind == "o":
            continue
        n = corpus.norm(p.text)
        if n in blocked:
            same += 1
            continue
        if SLOT.search(p.text) or len(n.split()) < 5:
            continue
        hit = next((sid for sid, w in wishes if (" " + n + " " in w or (len(w.split()) >= 5 and w in " " + n + " "))), None)
        if hit:
            print(f"Satz steckt in einem Wunsch des Messkorpus ({hit}): {p.label}: {p.text}")
            bad += 1
    print(f"{len(phrases)} Zeilen für {len(used)} Etiketten; {same} Sätze stehen wörtlich im Messkorpus und werden nie erzeugt (Sperre).")
    return bad


def make(per_label: int, free: int, seed: int, out: Path = OUT) -> None:
    gen = Generator(seed, blocked_texts())
    OUT = out
    OUT.mkdir(exist_ok=True)
    counts = {}
    for name, dev, share in (("train", False, 1.0), ("dev", True, 0.12)):
        rows, seen = [], set()
        for label in corpus.labels():
            want = int((free if corpus.is_free(label) else per_label) * share)
            got = tries = 0
            while got < want and tries < want * 30:
                tries += 1
                row = gen.one(label, dev)
                if row is None:
                    continue
                key = (row["text"], row["bereich"])
                # The same wish may come a few times (short commands are typed again and again), not endlessly.
                if key in seen and gen.rng.random() < 0.85:
                    continue
                seen.add(key)
                rows.append(row)
                got += 1
            counts[(name, label)] = got
        gen.rng.shuffle(rows)
        with (OUT / f"{name}.jsonl").open("w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
        print(f"{name}: {len(rows)} Wünsche, {len({r['text'] for r in rows})} verschiedene")
        if name == "train":
            sample = gen.rng.sample(rows, 80)
            (OUT / "beispiele.txt").write_text("\n".join(f"{r['label']:38} @{r['bereich']:9} {r['text']}" for r in sample) + "\n", encoding="utf-8")
    thin = sorted((n, l) for (name, l), n in counts.items() if name == "train" and n < per_label * 0.6 and not corpus.is_free(l))
    if thin:
        print("wenige Trainingswünsche:", ", ".join(f"{l} ({n})" for n, l in thin))
    none = [l for (name, l), n in counts.items() if name == "dev" and n == 0]
    if none:
        print("ohne Entwicklungswünsche:", ", ".join(none))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("was", choices=["check", "make"])
    ap.add_argument("-n", type=int, default=140, help="Wünsche je Befehl")
    ap.add_argument("--frei", type=int, default=4500, help="Wünsche je freier Klasse")
    ap.add_argument("--seed", type=int, default=71)
    ap.add_argument("--aus", default=str(OUT), help="Zielordner")
    a = ap.parse_args()
    if a.was == "check":
        sys.exit(1 if check() else 0)
    make(a.n, a.frei, a.seed, Path(a.aus))
