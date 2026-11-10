#!/usr/bin/env python3
"""Messung der Suche nach Bedeutung (AP-107) auf dem Messkorpus (messkorpus/).

Rechnet für jedes Modell die Einbettungen der Abschnitte und Fragen und misst, wie oft der Abschnitt mit dem Beleg unter
den ersten fünf (bzw. an erster) Stelle steht – mit der Suche der App (BM25 wie data/Knowledge.kt) allein, mit dem Modell
allein und gemischt wie in der App (0,65 Bedeutung + 0,35 Wörter).

Zwei Einteilungen: „app“ – Abschnitte wie KnowledgeStore.split (≈ 1 200 Zeichen), „absatz“ – jeder Absatz einzeln
(strenger, ≈ 150 Einheiten).

    python3 tools/suche/messen.py                       # Entwicklungsteil, alle Modelle
    python3 tools/suche/messen.py --modelle e5-small    # nur eines
    python3 tools/suche/messen.py --teil pruefung       # Prüfteil – NUR für die Kennzahlen am Ende

Der Prüfteil wird nie zum Wählen von Modell oder Gewichtung benutzt.
"""
import argparse, glob, json, math, os, re, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
KORPUS = os.path.join(HERE, "messkorpus")

# Kandidaten: Hugging Face an festem Commit; nur MIT / Apache-2.0.
MODELLE = {
    "e5-small": dict(repo="intfloat/multilingual-e5-small", rev="614241f622f53c4eeff9890bdc4f31cfecc418b3",
                     onnx="onnx/model_qint8_avx512_vnni.onnx", pool="mean", query="query: ", passage="passage: ", lizenz="MIT"),
    "e5-base": dict(repo="intfloat/multilingual-e5-base", rev="d128750597153bb5987e10b1c3493a34e5a4502a",
                    onnx="onnx/model_qint8_avx512_vnni.onnx", pool="mean", query="query: ", passage="passage: ", lizenz="MIT"),
    "minilm": dict(repo="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", rev="e8f8c211226b894fcb81acc59f3b34ba3efd5f42",
                   onnx="onnx/model_qint8_avx512_vnni.onnx", pool="mean", query="", passage="", lizenz="Apache-2.0"),
    "granite-107m": dict(repo="ibm-granite/granite-embedding-107m-multilingual", rev="d6cffd338414d6a1c1f5decfad5fec62eebc90d5",
                         onnx="model.onnx", pool="cls", query="", passage="", lizenz="Apache-2.0"),
    # Dasselbe Modell wie auf dem Uni-Server (AssistantTools.PREFERRED_EMBEDDING), hier lokal in PyTorch gerechnet,
    # Fragen ohne Anweisung – so wie die App sie an den Server schickt.
    "qwen3-4b": dict(repo="Qwen/Qwen3-Embedding-4B", rev="5cf2132abc99cad020ac570b19d031efec650f2b", torch=True,
                     pool="last", query="", passage="", lizenz="Apache-2.0"),
}

CHUNK, OVERLAP = 1200, 200


def split(text):
    """Wie KnowledgeStore.split (data/Knowledge.kt)."""
    clean = re.sub(r"\n{3,}", "\n\n", re.sub(r"[ \t]+", " ", text.replace("\r\n", "\n"))).strip()
    if len(clean) <= CHUNK:
        return [clean] if clean.strip() else []
    out, start = [], 0
    while start < len(clean):
        end = min(start + CHUNK, len(clean))
        if end < len(clean):
            window = clean[start:end]
            cut = next((c for c in (window.rfind("\n\n"), window.rfind(". "), window.rfind("\n")) if c > CHUNK // 2), None)
            if cut is not None:
                end = start + cut + 1
        out.append(clean[start:end].strip())
        if end >= len(clean):
            break
        start = max(end - OVERLAP, start + 1)
    return [o for o in out if o.strip()]


WORD = re.compile(r"[^\W_]{2,}", re.UNICODE)


def tokens(text):
    """Wie KnowledgeStore.tokens: klein, grobe deutsche Stammform."""
    out = []
    for v in WORD.findall(text.lower()):
        if len(v) > 6 and v.endswith("ungen"): v = v[:-2]
        elif len(v) > 5 and (v.endswith("en") or v.endswith("er") or v.endswith("es")): v = v[:-2]
        elif len(v) > 4 and (v.endswith("e") or v.endswith("n") or v.endswith("s")): v = v[:-1]
        out.append(v)
    return out


def bm25(query, units):
    terms = list(dict.fromkeys(tokens(query)))
    toks = [tokens(u) for u in units]
    avg = sum(len(t) for t in toks) / len(toks)
    df = {t: sum(1 for tk in toks if t in tk) for t in terms}
    scores = []
    for tk in toks:
        tf = {}
        for w in tk: tf[w] = tf.get(w, 0) + 1
        s = 0.0
        for t in terms:
            f = tf.get(t, 0)
            if f:
                idf = math.log(1 + (len(units) - df[t] + 0.5) / (df[t] + 0.5))
                s += idf * f * 2.2 / (f + 1.2 * (0.25 + 0.75 * len(tk) / avg))
        scores.append(s)
    m = max(scores) if max(scores) > 0 else 1.0
    return [s / m for s in scores]


def korpus():
    docs = {}
    for f in sorted(glob.glob(os.path.join(KORPUS, "dokumente", "*.md"))):
        docs[os.path.basename(f)[:2]] = open(f, encoding="utf-8").read()
    def fragen(teil):
        out = []
        for line in open(os.path.join(KORPUS, teil + ".txt"), encoding="utf-8"):
            if line.startswith("#") or not line.strip(): continue
            q, d, b = [x.strip() for x in line.split("|")]
            assert b in docs[d], (d, b)
            out.append((q, d, b))
        return out
    return docs, fragen


def einheiten(docs, art):
    units = []  # (doc, text)
    for d, text in docs.items():
        if art == "app":
            units += [(d, u) for u in split(text)]
        else:
            units += [(d, p.strip()) for p in text.split("\n\n") if p.strip() and not p.startswith("# ")]
    return units


class OnnxEmbedder:
    def __init__(self, cfg, threads=2):
        import onnxruntime as ort
        from huggingface_hub import hf_hub_download
        from tokenizers import Tokenizer
        self.cfg = cfg
        path = hf_hub_download(cfg["repo"], cfg["onnx"], revision=cfg["rev"])
        self.tok = Tokenizer.from_file(hf_hub_download(cfg["repo"], "tokenizer.json", revision=cfg["rev"]))
        self.tok.no_padding(); self.tok.enable_truncation(512)
        o = ort.SessionOptions(); o.intra_op_num_threads = threads; o.inter_op_num_threads = 1
        self.s = ort.InferenceSession(path, o, providers=["CPUExecutionProvider"])
        self.inputs = [i.name for i in self.s.get_inputs()]
        self.size = os.path.getsize(path)

    def one(self, text):
        import numpy as np
        e = self.tok.encode(text)
        ids = np.array([e.ids], dtype=np.int64)
        feed = {"input_ids": ids, "attention_mask": np.ones_like(ids)}
        if "token_type_ids" in self.inputs: feed["token_type_ids"] = np.zeros_like(ids)
        h = self.s.run(None, feed)[0][0]
        v = h[0] if self.cfg["pool"] == "cls" else h.mean(axis=0)
        return v / np.linalg.norm(v), len(e.ids)

    def embed(self, texts, prefix):
        return [self.one(prefix + t)[0] for t in texts]


class TorchEmbedder:
    def __init__(self, cfg):
        import torch
        from transformers import AutoModel, AutoTokenizer
        torch.set_num_threads(os.cpu_count())
        self.torch = torch
        self.tok = AutoTokenizer.from_pretrained(cfg["repo"], revision=cfg["rev"], padding_side="left")
        self.m = AutoModel.from_pretrained(cfg["repo"], revision=cfg["rev"], torch_dtype=torch.bfloat16).eval()
        self.size = 0

    def embed(self, texts, prefix):
        import numpy as np
        out = []
        with self.torch.no_grad():
            for t in texts:
                b = self.tok([prefix + t], return_tensors="pt", truncation=True, max_length=2048)
                h = self.m(**b).last_hidden_state[0, -1].float().numpy()
                out.append(h / np.linalg.norm(h))
        return out


def cache_path(name, art):
    d = os.path.join(HERE, "daten"); os.makedirs(d, exist_ok=True)
    return os.path.join(d, f"vektoren-{name}-{art}.json")


def vektoren(name, art, units, fragen_alle):
    """Vektoren für alle Einheiten und alle Fragen (beider Teile – gerechnet wird nur, gewählt nur mit der Entwicklung)."""
    import numpy as np
    p = cache_path(name, art)
    if os.path.exists(p):
        c = json.load(open(p))
        if c["units"] == [u for _, u in units] and set(c["fragen"]) >= set(fragen_alle):
            return np.array(c["uv"]), {q: np.array(v) for q, v in c["fragen"].items()}, c.get("ms")
    cfg = MODELLE[name]
    emb = TorchEmbedder(cfg) if cfg.get("torch") else OnnxEmbedder(cfg)
    t0 = time.time()
    uv = emb.embed([u for _, u in units], cfg["passage"])
    ms = (time.time() - t0) * 1000 / len(units)
    fv = dict(zip(fragen_alle, emb.embed(fragen_alle, cfg["query"])))
    json.dump({"units": [u for _, u in units], "uv": [v.tolist() for v in uv], "fragen": {q: v.tolist() for q, v in fv.items()}, "ms": ms}, open(p, "w"))
    return np.array(uv), fv, ms


def mischen(cos, kw, art, w=0.65):
    """Bedeutung und Wörter zusammen. „app“: wie data/Knowledge.kt bis 1.0.66 (Kosinus roh). „norm“: Kosinus je Anfrage
    auf 0..1 gestreckt (min–max), dann gewichtet – die Kosinuswerte mancher Modelle liegen eng beieinander (e5: 0,7–0,9)."""
    if art == "app":
        return [w * cos[i] + (1 - w) * kw[i] for i in range(len(cos))]
    lo, hi = min(cos), max(cos)
    span = (hi - lo) or 1.0
    return [w * (cos[i] - lo) / span + (1 - w) * kw[i] for i in range(len(cos))]


def messen(fragen, units, sem=None, fv=None, w=0.65, art="app"):
    import numpy as np
    top1 = top5 = 0; rr = 0.0
    for q, d, b in fragen:
        kw = bm25(q, [u for _, u in units])
        if sem is not None:
            cos = list(sem @ fv[q])
            s = mischen(cos, kw, art, w) if w < 1 else cos
        else:
            s = kw
        order = sorted(range(len(units)), key=lambda i: -s[i])
        rank = next((r for r, i in enumerate(order) if units[i][0] == d and b in units[i][1]), None)
        if rank is not None:
            top1 += rank == 0; top5 += rank < 5; rr += 1 / (rank + 1)
    n = len(fragen)
    return dict(top1=round(100 * top1 / n, 1), top5=round(100 * top5 / n, 1), mrr=round(rr / n, 3))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--teil", default="entwicklung", choices=["entwicklung", "pruefung"])
    ap.add_argument("--modelle", default="e5-small,e5-base,minilm,granite-107m,qwen3-4b")
    ap.add_argument("--json", help="Ergebnis zusätzlich als JSON schreiben")
    a = ap.parse_args()
    docs, fragen = korpus()
    alle = [q for q, _, _ in fragen("entwicklung") + fragen("pruefung")]
    teil = fragen(a.teil)
    result = {"teil": a.teil, "fragen": len(teil), "ergebnisse": {}}
    for art in ("app", "absatz"):
        units = einheiten(docs, art)
        r = {"einheiten": len(units), "nur Wörter (App heute ohne Server)": messen(teil, units)}
        print(f"\n== {a.teil}, Einteilung {art}: {len(units)} Einheiten, {len(teil)} Fragen")
        print(f"   nur Wörter (BM25):            {r['nur Wörter (App heute ohne Server)']}")
        for name in [m for m in a.modelle.split(",") if m]:
            uv, fv, ms = vektoren(name, art, units, alle)
            r[name] = {"nur Bedeutung": messen(teil, units, uv, fv, 1.0), "gemischt wie bisher": messen(teil, units, uv, fv),
                       "ms je Einheit": round(ms or 0, 1)}
            for w in (0.5, 0.65, 0.8, 0.9):
                r[name][f"normiert {w}"] = messen(teil, units, uv, fv, w, "norm")
            print(f"   {name:14s} {r[name]['ms je Einheit']} ms/Einheit")
            for k, v in r[name].items():
                if k != "ms je Einheit": print(f"      {k:22s} {v}")
        result["ergebnisse"][art] = r
    if a.json:
        json.dump(result, open(a.json, "w"), ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
