"""The recognition model: labels, encoding of the training texts and prediction (AP-67, Stufe c/d).

The model labels subword tokens in the BIO scheme (PER, EINR, ORT). A detail found by the model is widened to whole
words – the app does the same –, so the measures of ``corpus.py`` (replaced words) apply directly.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus  # noqa: E402

LABELS = ["O", "B-PER", "I-PER", "B-EINR", "I-EINR", "B-ORT", "I-ORT"]
LABEL_ID = {l: i for i, l in enumerate(LABELS)}


def load_tokenizer(name: str, revision: str | None = None):
    """The fast tokenizer of [name]. Older BERT models (gbert, gelectra) only have a vocab.txt, which newer
    Transformers versions cannot convert on their own – then it is built from vocab.txt and tokenizer_config.json."""
    from transformers import AutoTokenizer

    try:
        return AutoTokenizer.from_pretrained(name, revision=revision)
    except ValueError:
        pass
    from huggingface_hub import hf_hub_download
    from transformers import BertTokenizer

    cfg = json.loads(Path(hf_hub_download(name, "tokenizer_config.json", revision=revision)).read_text(encoding="utf-8"))
    tok = BertTokenizer(vocab=hf_hub_download(name, "vocab.txt", revision=revision),
                        do_lower_case=cfg.get("do_lower_case", False), strip_accents=cfg.get("strip_accents"))
    probe = tok("Frau Müller ging nach Hause.")["input_ids"]
    if probe.count(tok.unk_token_id) > 1:
        raise RuntimeError(f"Tokenizer von {name} liefert nur [UNK] – vocab.txt nicht gelesen")
    return tok


def load_model(name: str, revision: str | None = None):
    """A token classifier with our labels on top of [name]. Old configs without "model_type" (gbert, gelectra) get it
    from their architecture."""
    from transformers import AutoConfig, AutoModelForTokenClassification

    labels = {"num_labels": len(LABELS), "id2label": dict(enumerate(LABELS)), "label2id": LABEL_ID}
    try:
        config = AutoConfig.from_pretrained(name, revision=revision, **labels)
    except ValueError:
        from huggingface_hub import hf_hub_download

        cfg = json.loads(Path(hf_hub_download(name, "config.json", revision=revision)).read_text(encoding="utf-8"))
        arch = " ".join(cfg.get("architectures") or []).lower() + " " + name.lower()
        config = AutoConfig.for_model("electra" if "electra" in arch else "bert", **{**cfg, **labels})
    return AutoModelForTokenClassification.from_pretrained(name, revision=revision, config=config)


def read_jsonl(path: Path, limit: int | None = None) -> list[dict]:
    out = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            out.append(json.loads(line))
            if limit and len(out) >= limit:
                break
    return out


def encode(tokenizer, text: str, spans: list, max_len: int) -> dict:
    """Token ids and one label per subword token: B- on the token where a detail starts, I- on the rest of it."""
    enc = tokenizer(text, truncation=True, max_length=max_len, return_offsets_mapping=True)
    labels = []
    for s, e in enc["offset_mapping"]:
        if s == e:  # special tokens
            labels.append(-100)
            continue
        tag = "O"
        for a, b, kind in spans:
            if a <= s < b:
                tag = ("B-" if s == a else "I-") + kind
                break
        labels.append(LABEL_ID[tag])
    # A detail whose first token starts inside a word ("„Sonnenschein" after the quote) still begins with B-.
    for i, l in enumerate(labels):
        if l > 0 and LABELS[l].startswith("I-") and (i == 0 or labels[i - 1] <= 0 or LABELS[labels[i - 1]][2:] != LABELS[l][2:]):
            labels[i] = LABEL_ID["B-" + LABELS[l][2:]]
    return {"input_ids": enc["input_ids"], "attention_mask": enc["attention_mask"], "labels": labels,
            **({"token_type_ids": enc["token_type_ids"]} if "token_type_ids" in enc else {})}


def group_spans(tokens: list[tuple[int, int, str]], text: str) -> list[tuple[int, int, str]]:
    """Details from labelled tokens (start, end, label), widened to whole words."""
    spans: list[list] = []
    for s, e, tag in tokens:
        if tag == "O":
            continue
        kind = tag[2:]
        last = spans[-1] if spans else None
        joined = last and last[2] == kind and (tag.startswith("I-") or text[last[1]:s] == "") and not text[last[1]:s].strip(" -")
        if joined:
            last[1] = e
        else:
            spans.append([s, e, kind])
    words = corpus.split_words(text)
    out = []
    for s, e, kind in spans:
        for a, b, _ in words:
            if a < e and b > s:
                s, e = min(s, a), max(e, b)
        out.append((s, e, kind))
    return out


def replaced_words(text: str, spans: list[tuple[int, int, str]]) -> set[int]:
    """Indices of the words of [text] covered by [spans] (the measure of corpus.py)."""
    return {i for i, (a, b, _) in enumerate(corpus.split_words(text)) if any(a < e and b > s for s, e, _ in spans)}


class Predictor:
    """Prediction with a Hugging Face model (torch) or an exported ONNX file, on windows of 512 tokens."""

    def __init__(self, path: Path, device: str | None = None):
        from transformers import AutoTokenizer

        self.path = Path(path)
        onnx_file = self.path if self.path.suffix == ".onnx" else None
        folder = self.path.parent if onnx_file else self.path
        self.tokenizer = AutoTokenizer.from_pretrained(folder)
        if onnx_file:
            import onnxruntime as ort

            opts = ort.SessionOptions()
            opts.intra_op_num_threads = 4
            self.session = ort.InferenceSession(str(onnx_file), opts, providers=["CPUExecutionProvider"])
            self.inputs = {i.name for i in self.session.get_inputs()}
            self.model = None
        else:
            import torch
            from transformers import AutoModelForTokenClassification

            self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
            self.model = AutoModelForTokenClassification.from_pretrained(folder).to(self.device).eval()

    def tokens(self, text: str, max_len: int = 512, stride: int = 64) -> list[tuple[int, int, str]]:
        enc = self.tokenizer(text, truncation=True, max_length=max_len, stride=stride, return_overflowing_tokens=True,
                             return_offsets_mapping=True, padding=True, return_tensors="np")
        offsets = enc.pop("offset_mapping")
        enc.pop("overflow_to_sample_mapping", None)
        if self.model is None:
            feed = {k: v.astype("int64") for k, v in enc.items() if k in self.inputs}
            logits = self.session.run(None, feed)[0]
        else:
            import torch

            with torch.no_grad():
                feed = {k: torch.tensor(v, device=self.device) for k, v in enc.items()}
                logits = self.model(**feed).logits.float().cpu().numpy()
        best: dict[tuple[int, int], str] = {}
        for w in range(logits.shape[0]):
            ids = logits[w].argmax(-1)
            for (s, e), i, m in zip(offsets[w], ids, enc["attention_mask"][w]):
                if m == 0 or s == e:
                    continue
                tag = LABELS[int(i)]
                key = (int(s), int(e))
                # Overlapping windows: a token is a detail if one window says so.
                if best.get(key, "O") == "O":
                    best[key] = tag
        return sorted((s, e, t) for (s, e), t in best.items())

    def spans(self, text: str) -> list[tuple[int, int, str]]:
        return group_spans(self.tokens(text), text)


def score(predict, section: str, combine=None) -> corpus.Score:
    """Measures of a model on a section of the Messkorpus; [combine] (sample id, model's words) → replaced words."""
    sc = corpus.Score()
    for s in corpus.load(section):
        replaced = replaced_words(s.text, predict(s.text))
        sc.add(s, combine(s.id, replaced) if combine else replaced)
    return sc


#: The ways the model is measured (name in bericht.json → title).
VARIANTS = {
    "modell": "Modell allein",
    "regeln_und_modell": "alle Regeln + Modell",
    "geplant": "wie geplant: Regeln ohne Vornamen-Raten + Modell, zitierte Autor:innen bleiben",
}


def variants(predict, section: str, rules: dict | None) -> dict[str, corpus.Score]:
    """The model alone, with all rules (union), and as planned for the app: the rules without guessing names from
    frequent first names (Pseudonymizer.nameChains = false: patterns, Frau/Herr, roles, greetings, signatures, tables,
    institutions stay), the model for names, institutions and places, and authors cited in the text
    (PiiDetector.citedAuthors) are never replaced."""
    out = {"modell": score(predict, section)}
    if rules:
        out["regeln_und_modell"] = score(predict, section, lambda sid, w: w | rules.get(sid, {}).get("ersetzt", set()))
        out["geplant"] = score(predict, section, lambda sid, w: (w | rules.get(sid, {}).get("muster", set()))
                               - rules.get(sid, {}).get("zitiert", set()))
    return out


#: Results of the rules per sample: fresh from PseudonymCorpusTest, else the copy in the repo.
RULES_FILES = [HERE.parents[1] / "desktop/build/pii/regeln.jsonl", HERE / "messkorpus/regeln.jsonl"]


def load_rules(path: Path | None = None) -> dict[str, dict[str, set[int]]] | None:
    """Per sample the words the rules replace ("ersetzt"), the words they replace without guessing names from first
    names ("muster") and the words of cited authors ("zitiert") – written by PseudonymCorpusTest to
    desktop/build/pii/regeln.jsonl; a copy for PCs without the Kotlin build is in messkorpus/regeln.jsonl."""
    path = path or next((p for p in RULES_FILES if p.exists()), RULES_FILES[0])
    if not path.exists():
        return None
    return {d["id"]: {k: set(v) for k, v in d.items() if k != "id"} for d in read_jsonl(path)}
