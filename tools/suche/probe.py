#!/usr/bin/env python3
"""Schreibt modell/token-probe.json: Texte und ihre Ids nach dem Tokenizer von multilingual-e5-small (Hugging Face) –
der Kotlin-Tokenizer der App (data/LocalEmbedding.kt) muss dieselben Ids liefern (LocalEmbeddingTest)."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import messen
from huggingface_hub import hf_hub_download
from tokenizers import Tokenizer

cfg = messen.MODELLE["e5-small"]
tok = Tokenizer.from_file(hf_hub_download(cfg["repo"], "tokenizer.json", revision=cfg["rev"]))
docs, fragen = messen.korpus()
texte = [cfg["query"] + q for q, _, _ in fragen("entwicklung") + fragen("pruefung")]
for art in ("app", "absatz"):
    texte += [cfg["passage"] + u for _, u in messen.einheiten(docs, art)]
texte += [
    "Straße", "ÄÖÜ äöü ß", "  doppelte   Leerzeichen  ", "Zeile\nneue Zeile\tTab", "ﬁ Ligatur und ① Zahl", "€ 1.234,56 – „Zitat“",
    "E-Mail: a.b@example.org", "😀 Emoji", "中文字符", "x" * 40, "Müller-Lüdenscheidt", "",
]
probe = [{"text": t, "ids": tok.encode(t).ids} for t in texte]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "modell", "token-probe.json")
json.dump(probe, open(out, "w", encoding="utf-8"), ensure_ascii=False)
print(len(probe), "Texte →", out)
