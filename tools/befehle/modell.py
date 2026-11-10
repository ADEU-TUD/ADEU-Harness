"""The command model: labels, encoding of a wish and prediction (AP-71, Stufe e).

A sentence classifier: input is what is open (one word, e.g. "Tabelle") and the wish in lower case, as a pair
``[CLS] area [SEP] wish [SEP]``; output one label of ``etiketten.json``. The confidence is the probability of the
likeliest label; below the threshold (schwelle.json, chosen on the development part) the app passes the wish on.
The tokenizer and the loading of old BERT models are the ones of tools/pii.
"""
from __future__ import annotations

import importlib.util
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import corpus  # noqa: E402


def _pii(name: str):
    """A module of tools/pii under its own name (both folders have a corpus.py and a modell.py)."""
    spec = importlib.util.spec_from_file_location(f"pii_{name}", HERE.parent / "pii" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    saved = {k: sys.modules.get(k) for k in ("corpus", "modell")}
    sys.path.insert(0, str(HERE.parent / "pii"))
    try:
        for k in saved:
            sys.modules.pop(k, None)
        spec.loader.exec_module(mod)
    finally:
        sys.path.remove(str(HERE.parent / "pii"))
        for k, v in saved.items():
            sys.modules.pop(k, None)
            if v is not None:
                sys.modules[k] = v
    return mod


MAX_LEN = 64


def normalize(text: str) -> str:
    """Lower case, single spaces – the same as CommandRecognizer.normalize in the app and gen.py."""
    return re.sub(r"\s+", " ", text.lower()).strip()


def load_labels(folder: Path | None = None) -> list[str]:
    f = Path(folder) / "labels.json" if folder else None
    return json.loads(f.read_text(encoding="utf-8")) if f and f.exists() else corpus.labels()


def load_tokenizer(name: str, revision: str | None = None):
    return _pii("modell").load_tokenizer(name, revision)


def load_model(name: str, labels: list[str], revision: str | None = None, layers: int | None = None):
    """A sentence classifier on top of [name]; [layers] keeps only the first encoder layers (a smaller, faster model)."""
    from transformers import AutoConfig, AutoModelForSequenceClassification

    extra = {"num_labels": len(labels), "id2label": dict(enumerate(labels)), "label2id": {l: i for i, l in enumerate(labels)}}
    try:
        config = AutoConfig.from_pretrained(name, revision=revision, **extra)
    except ValueError:  # old configs without "model_type" (gbert, gelectra)
        from huggingface_hub import hf_hub_download

        cfg = json.loads(Path(hf_hub_download(name, "config.json", revision=revision)).read_text(encoding="utf-8"))
        arch = " ".join(cfg.get("architectures") or []).lower() + " " + name.lower()
        config = AutoConfig.for_model("electra" if "electra" in arch else "bert", **{**cfg, **extra})
    model = AutoModelForSequenceClassification.from_pretrained(name, revision=revision, config=config)
    if layers:
        stack = model.distilbert.transformer.layer if hasattr(model, "distilbert") else model.base_model.encoder.layer
        del stack[layers:]
        if hasattr(model.config, "n_layers"):
            model.config.n_layers = layers
        else:
            model.config.num_hidden_layers = layers
    return model


def encode(tokenizer, text: str, area: str, max_len: int = MAX_LEN) -> dict:
    return tokenizer(corpus.AREAS.get(area, "App"), normalize(text), truncation="only_second", max_length=max_len)


class Predictor:
    """Prediction with a Hugging Face folder (torch) or an exported ONNX file."""

    def __init__(self, path: Path, device: str | None = None, threshold: float | None = None):
        from transformers import AutoTokenizer

        self.path = Path(path)
        onnx_file = self.path if self.path.suffix == ".onnx" else None
        folder = self.path.parent if onnx_file else self.path
        self.tokenizer = AutoTokenizer.from_pretrained(folder)
        self.labels = load_labels(folder)
        self.threshold = threshold if threshold is not None else load_threshold(folder)
        if onnx_file:
            import onnxruntime as ort

            opts = ort.SessionOptions()
            opts.intra_op_num_threads = 2
            self.session = ort.InferenceSession(str(onnx_file), opts, providers=["CPUExecutionProvider"])
            self.inputs = {i.name for i in self.session.get_inputs()}
            self.model = None
        else:
            import torch
            from transformers import AutoModelForSequenceClassification

            self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
            self.model = AutoModelForSequenceClassification.from_pretrained(folder).to(self.device).eval()

    @classmethod
    def of(cls, model, tokenizer, labels: list[str], device: str, threshold: float = 0.5) -> "Predictor":
        p = cls.__new__(cls)
        p.tokenizer, p.model, p.labels, p.device, p.threshold = tokenizer, model, labels, device, threshold
        return p

    def probs(self, texts: list[tuple[str, str]]):
        """Probabilities of the labels for (wish, area) pairs."""
        import numpy as np

        out = []
        for i in range(0, len(texts), 64):
            chunk = texts[i:i + 64]
            enc = self.tokenizer([corpus.AREAS.get(a, "App") for _, a in chunk], [normalize(t) for t, _ in chunk],
                                 truncation="only_second", max_length=MAX_LEN, padding=True, return_tensors="np")
            if self.model is None:
                logits = self.session.run(None, {k: v.astype("int64") for k, v in enc.items() if k in self.inputs})[0]
            else:
                import torch

                with torch.no_grad():
                    feed = {k: torch.tensor(v, device=self.device) for k, v in enc.items()}
                    logits = self.model(**feed).logits.float().cpu().numpy()
            p = np.exp(logits - logits.max(-1, keepdims=True))
            out.append(p / p.sum(-1, keepdims=True))
        return np.concatenate(out)

    def predict(self, text: str, area: str = "app") -> tuple[str, float]:
        p = self.probs([(text, area)])[0]
        i = int(p.argmax())
        return self.labels[i], float(p[i])


def load_threshold(folder: Path) -> float:
    f = Path(folder) / "schwelle.json"
    return float(json.loads(f.read_text(encoding="utf-8"))["schwelle"]) if f.exists() else 0.5


def predictions(predictor: Predictor, section: str) -> list[tuple[corpus.Sample, str, float]]:
    samples = corpus.load(section)
    p = predictor.probs([(s.text, s.area) for s in samples])
    return [(s, predictor.labels[int(row.argmax())], float(row.max())) for s, row in zip(samples, p)]


def score(preds: list, threshold: float) -> corpus.Score:
    sc = corpus.Score()
    for s, label, conf in preds:
        sc.add(s, label, conf >= threshold)
    return sc


THRESHOLDS = (0.3, 0.4, 0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.9, 0.93, 0.95, 0.97, 0.98, 0.99)
#: Wrongly carried out actions the threshold may allow on the development part (the goal on the test part is 2 %).
#: Strict on purpose: templates were improved after reading the errors of the development part, so it looks better than
#: unseen wishes do (a first choice with 1.5 gave 2.8 % on the test part, 10.11.2026).
MAX_WRONG = 0.5


def tune_threshold(preds: list) -> tuple[float, list[dict]]:
    """The lowest threshold (most hits) that keeps wrongly carried out actions at or below MAX_WRONG on the development
    part; if none does, the one with the fewest wrong actions."""
    rows = []
    for t in THRESHOLDS:
        n = score(preds, t).numbers()
        rows.append({"schwelle": t, "treffer": n["treffer"], "falsch_ausgefuehrt": n["falsch_ausgefuehrt"], "frei_weitergegeben": n["frei_weitergegeben"]})
    ok = [r for r in rows if r["falsch_ausgefuehrt"] <= MAX_WRONG]
    pick = max(ok, key=lambda r: (r["treffer"], -r["schwelle"])) if ok else min(rows, key=lambda r: (r["falsch_ausgefuehrt"], -r["treffer"]))
    return pick["schwelle"], rows
