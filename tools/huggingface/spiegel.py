#!/usr/bin/env python3
"""
Hugging-Face-Spiegel für Lora (Lora Edition): lädt jede Modelldatei am festen Stand von der Quelle, prüft Größe und
SHA-256, legt Lizenztext(e) und eine Modellkarte (README.md) bei und lädt alles nach huggingface.co/thepytales hoch –
zuerst privat. Nach dem Hochladen vergleicht es den SHA-256 auf Hugging Face; erst dann (mit --oeffentlich) wird das Repo
öffentlich. Die App lädt zuerst vom Spiegel (ModelMirror.kt, speech/…/Models.java), dann vom Original.

Anmeldung: am PC `pip install -U huggingface_hub` und `hf auth login` (Token mit Schreibrecht für thepytales); in einer
Cloud-Sitzung der Umgebung „Standard“ setzt der Proxy den Token ein (AP-117) – dort `HF_TOKEN=x` als Platzhalter setzen.

Beispiele:
    python tools/huggingface/spiegel.py --liste
    python tools/huggingface/spiegel.py --nur multilingual-e5-small-lora-edition            # privat
    python tools/huggingface/spiegel.py --nur multilingual-e5-small-lora-edition --oeffentlich
    python tools/huggingface/spiegel.py --alle --oeffentlich
    python tools/huggingface/spiegel.py --probelauf --alle      # nur prüfen und Karten schreiben

Neues Modell: in DATEN eintragen (Quelle an festem Commit, Größe, SHA-256, Lizenz), spiegeln, dann die ausgegebene Zeile
in ModelMirror.REPOS (bzw. Models.MIRRORS für die Spracherkennung) übernehmen. Arbeitsordner: hf-spiegel-arbeit/ im
aktuellen Ordner (danach gelöscht), Ergebnis: spiegel-ergebnis.json.
"""
import argparse, hashlib, json, os, shutil, sys, urllib.request
from pathlib import Path

ORG = "thepytales"
ORG_NAME = "PyTales"
ARBEIT = Path("hf-spiegel-arbeit")
ERGEBNIS = Path("spiegel-ergebnis.json")
SATZ = "Gewichte unverändert, von uns ausgewählt, gemessen und für Lora eingestellt."

DATEN = json.loads(r'''{
 "qwen3.5-0.8b-lora-edition": {
  "files": [
   {
    "label": "Qwen 3.5 0.8B – Lora Edition",
    "size": 811843840,
    "sha256": "0ad885ffd4bb022fc4f0d33a3308fa108ef8613159d3b3a67e23abca056b7a6c",
    "hf_repo": "unsloth/Qwen3.5-0.8B-GGUF",
    "revision": "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0",
    "path": "Qwen3.5-0.8B-Q8_0.gguf",
    "target": "Qwen3.5-0.8B-Q8_0.gguf"
   },
   {
    "label": "Qwen 3.5 0.8B Bildteil – Lora Edition",
    "size": 204987232,
    "sha256": "56e4c6cfe73b0c82e3e82bc518d7591997e61d81f723fc41a586f4fa69ea2453",
    "hf_repo": "unsloth/Qwen3.5-0.8B-GGUF",
    "revision": "6ab461498e2023f6e3c1baea90a8f0fe38ab64d0",
    "path": "mmproj-F16.gguf",
    "target": "mmproj-F16.gguf"
   }
  ],
  "title": "Qwen 3.5 0.8B – Lora Edition",
  "base": "Qwen/Qwen3.5-0.8B",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen3.5-0.8B-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen3.5-0.8B",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen3.5-0.8B/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "qwen3.5-2b-lora-edition": {
  "files": [
   {
    "label": "Qwen 3.5 2B – Lora Edition",
    "size": 1280835840,
    "sha256": "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
    "hf_repo": "unsloth/Qwen3.5-2B-GGUF",
    "revision": "f6d5376be1edb4d416d56da11e5397a961aca8ae",
    "path": "Qwen3.5-2B-Q4_K_M.gguf",
    "target": "Qwen3.5-2B-Q4_K_M.gguf"
   },
   {
    "label": "Qwen 3.5 2B Bildteil – Lora Edition",
    "size": 668227264,
    "sha256": "7035e9cb8d7c6a9681d07eef9a364783e86ea4cd73faab2eabb4f43a101830c7",
    "hf_repo": "unsloth/Qwen3.5-2B-GGUF",
    "revision": "f6d5376be1edb4d416d56da11e5397a961aca8ae",
    "path": "mmproj-F16.gguf",
    "target": "mmproj-F16.gguf"
   }
  ],
  "title": "Qwen 3.5 2B – Lora Edition",
  "base": "Qwen/Qwen3.5-2B",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen3.5-2B-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen3.5-2B",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen3.5-2B/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "gemma-4-e2b-lora-edition": {
  "files": [
   {
    "label": "Gemma 4 E2B – Lora Edition",
    "size": 2841481184,
    "sha256": "8e30dff3ac4c8434c49a7036fa15564bdbb6044e42bf04550bf1a096ad7e6a52",
    "hf_repo": "ggml-org/gemma-4-E2B-it-GGUF",
    "revision": "6938c58e698502037401a5f19f6905f2fb2f7dee",
    "path": "gemma-4-E2B-it-Q4_0.gguf",
    "target": "gemma-4-E2B-it-Q4_0.gguf"
   },
   {
    "label": "Gemma 4 E2B Bildteil – Lora Edition",
    "size": 557368064,
    "sha256": "9406f99c16d68cda4f1f0552192dcc99021ea1fc6d2fd50b1dc3ccf30d04b292",
    "hf_repo": "ggml-org/gemma-4-E2B-it-GGUF",
    "revision": "6938c58e698502037401a5f19f6905f2fb2f7dee",
    "path": "mmproj-gemma-4-E2B-it-Q8_0.gguf",
    "target": "mmproj-gemma-4-E2B-it-Q8_0.gguf"
   }
  ],
  "title": "Gemma 4 E2B – Lora Edition",
  "base": "google/gemma-4-E2B-it",
  "authors": "Google DeepMind",
  "quant": "ggml-org/gemma-4-E2B-it-GGUF",
  "license_id": "apache-2.0",
  "base_model": "google/gemma-4-E2B-it",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
   ]
  ],
  "license_note": "Apache-2.0 – Gemma-4-Lizenzhinweise von Google: https://ai.google.dev/gemma/docs/gemma_4_license",
  "authors_short": "Google DeepMind"
 },
 "qwen3.5-4b-lora-edition": {
  "files": [
   {
    "label": "Qwen 3.5 4B – Lora Edition",
    "size": 2740937888,
    "sha256": "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
    "hf_repo": "unsloth/Qwen3.5-4B-GGUF",
    "revision": "e87f176479d0855a907a41277aca2f8ee7a09523",
    "path": "Qwen3.5-4B-Q4_K_M.gguf",
    "target": "Qwen3.5-4B-Q4_K_M.gguf"
   },
   {
    "label": "Qwen 3.5 4B Bildteil – Lora Edition",
    "size": 672423616,
    "sha256": "cd88edcf8d031894960bb0c9c5b9b7e1fea6ebee02b9f7ce925a00d12891f864",
    "hf_repo": "unsloth/Qwen3.5-4B-GGUF",
    "revision": "e87f176479d0855a907a41277aca2f8ee7a09523",
    "path": "mmproj-F16.gguf",
    "target": "mmproj-F16.gguf"
   },
   {
    "label": "Qwen 3.5 4B (genauer) – Lora Edition",
    "size": 4482403488,
    "sha256": "10cc391b403021dd11c614679d2fd92f611c3681d29e29651b717316965d61e1",
    "hf_repo": "unsloth/Qwen3.5-4B-GGUF",
    "revision": "e87f176479d0855a907a41277aca2f8ee7a09523",
    "path": "Qwen3.5-4B-Q8_0.gguf",
    "target": "Qwen3.5-4B-Q8_0.gguf"
   }
  ],
  "title": "Qwen 3.5 4B – Lora Edition",
  "base": "Qwen/Qwen3.5-4B",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen3.5-4B-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen3.5-4B",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen3.5-4B/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "gemma-4-e4b-lora-edition": {
  "files": [
   {
    "label": "Gemma 4 E4B – Lora Edition",
    "size": 4590807392,
    "sha256": "a555b900214b477d8880e7832e0b8925e139b0159640036b09fe472b6f2097f2",
    "hf_repo": "ggml-org/gemma-4-E4B-it-GGUF",
    "revision": "a69f018d7678a2b9f5e705f8903d6fb29b9b25dc",
    "path": "gemma-4-E4B-it-Q4_0.gguf",
    "target": "gemma-4-E4B-it-Q4_0.gguf"
   },
   {
    "label": "Gemma 4 E4B Bildteil – Lora Edition",
    "size": 559874816,
    "sha256": "197f49a93027f9843772bd24a6a9e0be2a32a788de5a3def330e9c585d86edd1",
    "hf_repo": "ggml-org/gemma-4-E4B-it-GGUF",
    "revision": "a69f018d7678a2b9f5e705f8903d6fb29b9b25dc",
    "path": "mmproj-gemma-4-E4B-it-Q8_0.gguf",
    "target": "mmproj-gemma-4-E4B-it-Q8_0.gguf"
   }
  ],
  "title": "Gemma 4 E4B – Lora Edition",
  "base": "google/gemma-4-E4B-it",
  "authors": "Google DeepMind",
  "quant": "ggml-org/gemma-4-E4B-it-GGUF",
  "license_id": "apache-2.0",
  "base_model": "google/gemma-4-E4B-it",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
   ]
  ],
  "license_note": "Apache-2.0 – Gemma-4-Lizenzhinweise von Google: https://ai.google.dev/gemma/docs/gemma_4_license",
  "authors_short": "Google DeepMind"
 },
 "qwen2.5-coder-7b-lora-edition": {
  "files": [
   {
    "label": "Qwen 2.5 Coder 7B – Lora Edition",
    "size": 4683073504,
    "sha256": "9a961bb225cb2b9fd84b2297df0d53089895c049d7d9dc5f5f8aebbcd3247872",
    "hf_repo": "unsloth/Qwen2.5-Coder-7B-Instruct-GGUF",
    "revision": "0ecf11859560b2bf42e703207f9371186d02245f",
    "path": "Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf",
    "target": "Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf"
   }
  ],
  "title": "Qwen 2.5 Coder 7B – Lora Edition",
  "base": "Qwen/Qwen2.5-Coder-7B-Instruct",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen2.5-Coder-7B-Instruct-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen2.5-Coder-7B-Instruct",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "qwen3.5-9b-lora-edition": {
  "files": [
   {
    "label": "Qwen 3.5 9B – Lora Edition",
    "size": 5680522464,
    "sha256": "03b74727a860a56338e042c4420bb3f04b2fec5734175f4cb9fa853daf52b7e8",
    "hf_repo": "unsloth/Qwen3.5-9B-GGUF",
    "revision": "3885219b6810b007914f3a7950a8d1b469d598a5",
    "path": "Qwen3.5-9B-Q4_K_M.gguf",
    "target": "Qwen3.5-9B-Q4_K_M.gguf"
   },
   {
    "label": "Qwen 3.5 9B Bildteil – Lora Edition",
    "size": 918166080,
    "sha256": "f70dc3509053962b0d0d3ee8a7eacebf5d60aa560cad78254ae8698516ae029f",
    "hf_repo": "unsloth/Qwen3.5-9B-GGUF",
    "revision": "3885219b6810b007914f3a7950a8d1b469d598a5",
    "path": "mmproj-F16.gguf",
    "target": "mmproj-F16.gguf"
   },
   {
    "label": "Qwen 3.5 9B (genauer) – Lora Edition",
    "size": 9527502048,
    "sha256": "809626574d0cb43d4becfa56169980da2bb448f2299270f7be443cb89d0a6ae4",
    "hf_repo": "unsloth/Qwen3.5-9B-GGUF",
    "revision": "3885219b6810b007914f3a7950a8d1b469d598a5",
    "path": "Qwen3.5-9B-Q8_0.gguf",
    "target": "Qwen3.5-9B-Q8_0.gguf"
   }
  ],
  "title": "Qwen 3.5 9B – Lora Edition",
  "base": "Qwen/Qwen3.5-9B",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen3.5-9B-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen3.5-9B",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen3.5-9B/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "gemma-4-12b-lora-edition": {
  "files": [
   {
    "label": "Gemma 4 12B – Lora Edition",
    "size": 7219673216,
    "sha256": "3712b9bd32cae83a22f67ee7a4466d8d7a4f21646ac8a07d19bf9418e8767a70",
    "hf_repo": "ggml-org/gemma-4-12B-it-GGUF",
    "revision": "a29b2a6b584a5bdcb672ccdcda99ea9faeacc51a",
    "path": "gemma-4-12B-it-Q4_0.gguf",
    "target": "gemma-4-12B-it-Q4_0.gguf"
   },
   {
    "label": "Gemma 4 12B Bildteil – Lora Edition",
    "size": 158987616,
    "sha256": "59e62255435dda870e2d1de97cc031330b31a898bac12b38a182cecff9cd3738",
    "hf_repo": "ggml-org/gemma-4-12B-it-GGUF",
    "revision": "a29b2a6b584a5bdcb672ccdcda99ea9faeacc51a",
    "path": "mmproj-gemma-4-12B-it-Q8_0.gguf",
    "target": "mmproj-gemma-4-12B-it-Q8_0.gguf"
   }
  ],
  "title": "Gemma 4 12B – Lora Edition",
  "base": "google/gemma-4-12B-it",
  "authors": "Google DeepMind",
  "quant": "ggml-org/gemma-4-12B-it-GGUF",
  "license_id": "apache-2.0",
  "base_model": "google/gemma-4-12B-it",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
   ]
  ],
  "license_note": "Apache-2.0 – Gemma-4-Lizenzhinweise von Google: https://ai.google.dev/gemma/docs/gemma_4_license",
  "authors_short": "Google DeepMind"
 },
 "qwen2.5-coder-14b-lora-edition": {
  "files": [
   {
    "label": "Qwen 2.5 Coder 14B – Lora Edition",
    "size": 8988110240,
    "sha256": "13dec282d3ac9a87896248d5f69187102fad7c56315c553d2fab5403121702fe",
    "hf_repo": "unsloth/Qwen2.5-Coder-14B-Instruct-GGUF",
    "revision": "388f3f20271ef903bb2dbe7d8236158903e7edb3",
    "path": "Qwen2.5-Coder-14B-Instruct-Q4_K_M.gguf",
    "target": "Qwen2.5-Coder-14B-Instruct-Q4_K_M.gguf"
   }
  ],
  "title": "Qwen 2.5 Coder 14B – Lora Edition",
  "base": "Qwen/Qwen2.5-Coder-14B-Instruct",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen2.5-Coder-14B-Instruct-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen2.5-Coder-14B-Instruct",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen2.5-Coder-14B-Instruct/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "gemma-4-26b-a4b-lora-edition": {
  "files": [
   {
    "label": "Gemma 4 26B A4B – Lora Edition",
    "size": 14618145824,
    "sha256": "d208665ab1cd3a69f7a9a4bc59430e8448c8093d9b06334f566ac59d6d504a03",
    "hf_repo": "ggml-org/gemma-4-26B-A4B-it-GGUF",
    "revision": "9cf8c8db0f83fb4582f15381f8fb1a7c1b0b9ca2",
    "path": "gemma-4-26B-A4B-it-Q4_0.gguf",
    "target": "gemma-4-26B-A4B-it-Q4_0.gguf"
   },
   {
    "label": "Gemma 4 26B A4B Bildteil – Lora Edition",
    "size": 806408320,
    "sha256": "cc4e855736da450bf1e162d8cccfe0ad685727d0c9e04ef7dd8d884f3121039b",
    "hf_repo": "ggml-org/gemma-4-26B-A4B-it-GGUF",
    "revision": "9cf8c8db0f83fb4582f15381f8fb1a7c1b0b9ca2",
    "path": "mmproj-gemma-4-26B-A4B-it-Q8_0.gguf",
    "target": "mmproj-gemma-4-26B-A4B-it-Q8_0.gguf"
   }
  ],
  "title": "Gemma 4 26B A4B – Lora Edition",
  "base": "google/gemma-4-26B-A4B-it",
  "authors": "Google DeepMind",
  "quant": "ggml-org/gemma-4-26B-A4B-it-GGUF",
  "license_id": "apache-2.0",
  "base_model": "google/gemma-4-26B-A4B-it",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
   ]
  ],
  "license_note": "Apache-2.0 – Gemma-4-Lizenzhinweise von Google: https://ai.google.dev/gemma/docs/gemma_4_license",
  "authors_short": "Google DeepMind"
 },
 "qwen3.6-27b-lora-edition": {
  "files": [
   {
    "label": "Qwen 3.6 27B – Lora Edition",
    "size": 16817244384,
    "sha256": "5ed60d0af4650a854b1755bd392f9aef4872643dc25a254bc68043fa638392a0",
    "hf_repo": "unsloth/Qwen3.6-27B-GGUF",
    "revision": "82d411acf4a06cfb8d9b073a5211bf410bfc29bf",
    "path": "Qwen3.6-27B-Q4_K_M.gguf",
    "target": "Qwen3.6-27B-Q4_K_M.gguf"
   },
   {
    "label": "Qwen 3.6 27B Bildteil – Lora Edition",
    "size": 927607360,
    "sha256": "eacf610d1ee4bd5ed0197a0777dd8f4fceb8eefa27009067c7d496cb68fbde45",
    "hf_repo": "unsloth/Qwen3.6-27B-GGUF",
    "revision": "82d411acf4a06cfb8d9b073a5211bf410bfc29bf",
    "path": "mmproj-F16.gguf",
    "target": "mmproj-F16.gguf"
   }
  ],
  "title": "Qwen 3.6 27B – Lora Edition",
  "base": "Qwen/Qwen3.6-27B",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen3.6-27B-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen3.6-27B",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen3.6-27B/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "gemma-4-31b-lora-edition": {
  "files": [
   {
    "label": "Gemma 4 31B – Lora Edition",
    "size": 18323733440,
    "sha256": "38bd64c852c4b460434cc7162fa9bdcf242faf86502581a754cb72956bb17f84",
    "hf_repo": "unsloth/gemma-4-31B-it-GGUF",
    "revision": "c1ac76e99d5513b141e8adde7288b85c3f9c32ec",
    "path": "gemma-4-31B-it-Q4_K_M.gguf",
    "target": "gemma-4-31B-it-Q4_K_M.gguf"
   },
   {
    "label": "Gemma 4 31B Bildteil – Lora Edition",
    "size": 1198957024,
    "sha256": "6edcca228213c28d3567a35d22f849eea52d8360875093851959adf5d2f270eb",
    "hf_repo": "unsloth/gemma-4-31B-it-GGUF",
    "revision": "c1ac76e99d5513b141e8adde7288b85c3f9c32ec",
    "path": "mmproj-F16.gguf",
    "target": "mmproj-F16.gguf"
   }
  ],
  "title": "Gemma 4 31B – Lora Edition",
  "base": "google/gemma-4-31B-it",
  "authors": "Google DeepMind",
  "quant": "unsloth/gemma-4-31B-it-GGUF",
  "license_id": "apache-2.0",
  "base_model": "google/gemma-4-31B-it",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
   ]
  ],
  "license_note": "Apache-2.0 – Gemma-4-Lizenzhinweise von Google: https://ai.google.dev/gemma/docs/gemma_4_license",
  "authors_short": "Google DeepMind"
 },
 "qwen3-coder-30b-a3b-lora-edition": {
  "files": [
   {
    "label": "Qwen 3 Coder 30B A3B – Lora Edition",
    "size": 18556689568,
    "sha256": "fadc3e5f8d42bf7e894a785b05082e47daee4df26680389817e2093056f088ad",
    "hf_repo": "unsloth/Qwen3-Coder-30B-A3B-Instruct-GGUF",
    "revision": "b17cb02dd882d5b6ab62fc777ad2995f19668350",
    "path": "Qwen3-Coder-30B-A3B-Instruct-Q4_K_M.gguf",
    "target": "Qwen3-Coder-30B-A3B-Instruct-Q4_K_M.gguf"
   }
  ],
  "title": "Qwen 3 Coder 30B A3B – Lora Edition",
  "base": "Qwen/Qwen3-Coder-30B-A3B-Instruct",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "unsloth/Qwen3-Coder-30B-A3B-Instruct-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen3-Coder-30B-A3B-Instruct",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen3-Coder-30B-A3B-Instruct/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "qwen3.6-35b-a3b-lora-edition": {
  "files": [
   {
    "label": "Qwen 3.6 35B A3B – Lora Edition",
    "size": 20419565568,
    "sha256": "671e47e0ec53c665d048b98c3ecbfd5236b5ca9c3e02ed19fc8f81f7b85140c7",
    "hf_repo": "ggml-org/Qwen3.6-35B-A3B-GGUF",
    "revision": "7878dc9a905c7c9379e5e8d6a1aa9affdd6839c5",
    "path": "Qwen3.6-35B-A3B-Q4_K_M.gguf",
    "target": "Qwen3.6-35B-A3B-Q4_K_M.gguf"
   },
   {
    "label": "Qwen 3.6 35B A3B Bildteil – Lora Edition",
    "size": 614194304,
    "sha256": "904cbf8c8e876220066ab3bf676c7efa40f3da372276fdaf8b01d2fb2a37a51d",
    "hf_repo": "ggml-org/Qwen3.6-35B-A3B-GGUF",
    "revision": "7878dc9a905c7c9379e5e8d6a1aa9affdd6839c5",
    "path": "mmproj-Qwen3.6-35B-A3B-Q8_0.gguf",
    "target": "mmproj-Qwen3.6-35B-A3B-Q8_0.gguf"
   }
  ],
  "title": "Qwen 3.6 35B A3B – Lora Edition",
  "base": "Qwen/Qwen3.6-35B-A3B",
  "authors": "Qwen-Team (Alibaba Cloud)",
  "quant": "ggml-org/Qwen3.6-35B-A3B-GGUF",
  "license_id": "apache-2.0",
  "base_model": "Qwen/Qwen3.6-35B-A3B",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "gguf",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://huggingface.co/Qwen/Qwen3.6-35B-A3B/resolve/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0",
  "authors_short": "Qwen-Team (Alibaba Cloud)"
 },
 "multilingual-e5-small-lora-edition": {
  "files": [
   {
    "label": "multilingual-e5-small – Lora Edition",
    "size": 118346824,
    "sha256": "dd476dd0c2514e9b9be83aeb3853fac0763e0bdf4a71645407587d77c48a2d88",
    "hf_repo": "intfloat/multilingual-e5-small",
    "revision": "614241f622f53c4eeff9890bdc4f31cfecc418b3",
    "path": "onnx/model_qint8_avx512_vnni.onnx",
    "target": "onnx/model_qint8_avx512_vnni.onnx"
   },
   {
    "label": "multilingual-e5-small – Lora Edition (Wortschatz)",
    "size": 5069051,
    "sha256": "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865",
    "hf_repo": "intfloat/multilingual-e5-small",
    "revision": "614241f622f53c4eeff9890bdc4f31cfecc418b3",
    "path": "sentencepiece.bpe.model",
    "target": "sentencepiece.bpe.model"
   }
  ],
  "title": "multilingual-e5-small – Lora Edition",
  "base": "intfloat/multilingual-e5-small",
  "authors": "Microsoft (Wang u. a., intfloat)",
  "quant": "intfloat/multilingual-e5-small",
  "license_id": "mit",
  "base_model": "intfloat/multilingual-e5-small",
  "relation": null,
  "lang": [
   "multilingual",
   "de"
  ],
  "tags": [
   "onnx",
   "sentence-similarity",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://raw.githubusercontent.com/microsoft/unilm/master/LICENSE"
   ]
  ],
  "license_note": "MIT",
  "authors_short": "Microsoft (Liang Wang u. a.)"
 },
 "whisper-large-v3-turbo-lora-edition": {
  "files": [
   {
    "label": "Whisper Large v3 Turbo – Lora Edition",
    "size": 674716297,
    "sha256": "b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-turbo",
    "revision": "2ca6ff69fc878651b770880507669577ac41c2ff",
    "path": "turbo-encoder.int8.onnx",
    "target": "turbo-encoder.int8.onnx"
   },
   {
    "label": "Whisper Large v3 Turbo – Lora Edition",
    "size": 361080764,
    "sha256": "20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-turbo",
    "revision": "2ca6ff69fc878651b770880507669577ac41c2ff",
    "path": "turbo-decoder.int8.onnx",
    "target": "turbo-decoder.int8.onnx"
   },
   {
    "label": "Whisper Large v3 Turbo – Lora Edition (Zeichen)",
    "size": 816730,
    "sha256": "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-turbo",
    "revision": "2ca6ff69fc878651b770880507669577ac41c2ff",
    "path": "turbo-tokens.txt",
    "target": "turbo-tokens.txt"
   }
  ],
  "title": "Whisper Large v3 Turbo – Lora Edition",
  "base": "openai/whisper-large-v3-turbo",
  "authors": "OpenAI · ONNX: k2-fsa/sherpa-onnx (csukuangfj)",
  "quant": "csukuangfj/sherpa-onnx-whisper-turbo",
  "license_id": "mit",
  "base_model": "openai/whisper-large-v3-turbo",
  "relation": "quantized",
  "lang": [
   "multilingual",
   "de"
  ],
  "tags": [
   "onnx",
   "sherpa-onnx",
   "automatic-speech-recognition",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://raw.githubusercontent.com/openai/whisper/main/LICENSE"
   ]
  ],
  "license_note": "MIT",
  "authors_short": "OpenAI"
 },
 "parakeet-tdt-0.6b-v3-lora-edition": {
  "files": [
   {
    "label": "Parakeet TDT 0.6B v3 – Lora Edition",
    "size": 652184281,
    "sha256": "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247",
    "hf_repo": "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
    "revision": "2bda32ec70b097a55adaa07d9a7173915b43cc78",
    "path": "encoder.int8.onnx",
    "target": "encoder.int8.onnx"
   },
   {
    "label": "Parakeet TDT 0.6B v3 – Lora Edition",
    "size": 11845275,
    "sha256": "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e",
    "hf_repo": "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
    "revision": "2bda32ec70b097a55adaa07d9a7173915b43cc78",
    "path": "decoder.int8.onnx",
    "target": "decoder.int8.onnx"
   },
   {
    "label": "Parakeet TDT 0.6B v3 – Lora Edition",
    "size": 6355277,
    "sha256": "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3",
    "hf_repo": "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
    "revision": "2bda32ec70b097a55adaa07d9a7173915b43cc78",
    "path": "joiner.int8.onnx",
    "target": "joiner.int8.onnx"
   },
   {
    "label": "Parakeet TDT 0.6B v3 – Lora Edition (Zeichen)",
    "size": 93939,
    "sha256": "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
    "hf_repo": "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
    "revision": "2bda32ec70b097a55adaa07d9a7173915b43cc78",
    "path": "tokens.txt",
    "target": "tokens.txt"
   }
  ],
  "title": "Parakeet TDT 0.6B v3 – Lora Edition",
  "base": "nvidia/parakeet-tdt-0.6b-v3",
  "authors": "NVIDIA · ONNX: k2-fsa/sherpa-onnx (csukuangfj)",
  "quant": "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
  "license_id": "cc-by-4.0",
  "base_model": "nvidia/parakeet-tdt-0.6b-v3",
  "relation": "quantized",
  "lang": [
   "de",
   "en"
  ],
  "tags": [
   "onnx",
   "sherpa-onnx",
   "automatic-speech-recognition",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://creativecommons.org/licenses/by/4.0/legalcode.txt"
   ]
  ],
  "license_note": "CC-BY-4.0 – Namensnennung: NVIDIA, Parakeet TDT 0.6B v3",
  "authors_short": "NVIDIA"
 },
 "whisper-small-lora-edition": {
  "files": [
   {
    "label": "Whisper Small – Lora Edition",
    "size": 112442483,
    "sha256": "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-small",
    "revision": "8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
    "path": "small-encoder.int8.onnx",
    "target": "small-encoder.int8.onnx"
   },
   {
    "label": "Whisper Small – Lora Edition",
    "size": 262226114,
    "sha256": "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-small",
    "revision": "8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
    "path": "small-decoder.int8.onnx",
    "target": "small-decoder.int8.onnx"
   },
   {
    "label": "Whisper Small – Lora Edition (Zeichen)",
    "size": 816730,
    "sha256": "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-small",
    "revision": "8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
    "path": "small-tokens.txt",
    "target": "small-tokens.txt"
   }
  ],
  "title": "Whisper Small – Lora Edition",
  "base": "openai/whisper-small",
  "authors": "OpenAI · ONNX: k2-fsa/sherpa-onnx (csukuangfj)",
  "quant": "csukuangfj/sherpa-onnx-whisper-small",
  "license_id": "apache-2.0",
  "base_model": "openai/whisper-small",
  "relation": "quantized",
  "lang": [
   "multilingual",
   "de"
  ],
  "tags": [
   "onnx",
   "sherpa-onnx",
   "automatic-speech-recognition",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
   ],
   [
    "LICENSE-openai-whisper-code",
    "https://raw.githubusercontent.com/openai/whisper/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0 (Modellkarte auf Hugging Face); der Code von OpenAI steht unter MIT",
  "authors_short": "OpenAI"
 },
 "whisper-base-lora-edition": {
  "files": [
   {
    "label": "Whisper Base – Lora Edition",
    "size": 29120534,
    "sha256": "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-base",
    "revision": "bb53ee204431c90d314c1cc08d28d23e5b7927cc",
    "path": "base-encoder.int8.onnx",
    "target": "base-encoder.int8.onnx"
   },
   {
    "label": "Whisper Base – Lora Edition",
    "size": 130672026,
    "sha256": "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-base",
    "revision": "bb53ee204431c90d314c1cc08d28d23e5b7927cc",
    "path": "base-decoder.int8.onnx",
    "target": "base-decoder.int8.onnx"
   },
   {
    "label": "Whisper Base – Lora Edition (Zeichen)",
    "size": 816730,
    "sha256": "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
    "hf_repo": "csukuangfj/sherpa-onnx-whisper-base",
    "revision": "bb53ee204431c90d314c1cc08d28d23e5b7927cc",
    "path": "base-tokens.txt",
    "target": "base-tokens.txt"
   }
  ],
  "title": "Whisper Base – Lora Edition",
  "base": "openai/whisper-base",
  "authors": "OpenAI · ONNX: k2-fsa/sherpa-onnx (csukuangfj)",
  "quant": "csukuangfj/sherpa-onnx-whisper-base",
  "license_id": "apache-2.0",
  "base_model": "openai/whisper-base",
  "relation": "quantized",
  "lang": [
   "multilingual",
   "de"
  ],
  "tags": [
   "onnx",
   "sherpa-onnx",
   "automatic-speech-recognition",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://www.apache.org/licenses/LICENSE-2.0.txt"
   ],
   [
    "LICENSE-openai-whisper-code",
    "https://raw.githubusercontent.com/openai/whisper/main/LICENSE"
   ]
  ],
  "license_note": "Apache-2.0 (Modellkarte auf Hugging Face); der Code von OpenAI steht unter MIT",
  "authors_short": "OpenAI"
 },
 "wespeaker-resnet34-lm-lora-edition": {
  "files": [
   {
    "label": "WeSpeaker ResNet34-LM (Sprechererkennung) – Lora Edition",
    "size": 26530550,
    "sha256": "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012",
    "hf_repo": "csukuangfj/speaker-embedding-models",
    "revision": "0743f301363dec56491a490f6d6cbc9d67f9a3bf",
    "path": "wespeaker_en_voxceleb_resnet34_LM.onnx",
    "target": "wespeaker_en_voxceleb_resnet34_LM.onnx"
   }
  ],
  "title": "WeSpeaker ResNet34-LM (Sprechererkennung) – Lora Edition",
  "base": "wenet-e2e/wespeaker (VoxCeleb ResNet34-LM)",
  "authors": "WeNet/WeSpeaker-Team",
  "quant": "k2-fsa/sherpa-onnx",
  "license_id": "cc-by-4.0",
  "base_model": null,
  "relation": null,
  "lang": [
   "en"
  ],
  "tags": [
   "onnx",
   "speaker-recognition",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://creativecommons.org/licenses/by/4.0/legalcode.txt"
   ]
  ],
  "license_note": "CC-BY-4.0 wie der Datensatz VoxCeleb (https://mm.kaist.ac.kr/datasets/voxceleb/) – Namensnennung: WeSpeaker (wenet-e2e), VoxCeleb",
  "authors_short": "WeNet/WeSpeaker-Team"
 },
 "silero-vad-lora-edition": {
  "files": [
   {
    "label": "Silero VAD (Spracherkennung: Pausen) – Lora Edition",
    "size": 643854,
    "sha256": "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    "url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
    "target": "silero_vad.onnx"
   }
  ],
  "title": "Silero VAD (Spracherkennung: Pausen) – Lora Edition",
  "base": "snakers4/silero-vad",
  "authors": "Silero-Team · Paket: k2-fsa/sherpa-onnx",
  "quant": "k2-fsa/sherpa-onnx (Release-Asset, kein Commit)",
  "license_id": "mit",
  "base_model": null,
  "relation": null,
  "lang": null,
  "tags": [
   "onnx",
   "voice-activity-detection",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://raw.githubusercontent.com/snakers4/silero-vad/master/LICENSE"
   ]
  ],
  "license_note": "MIT",
  "authors_short": "Silero-Team"
 },
 "pyannote-segmentation-3.0-lora-edition": {
  "files": [
   {
    "label": "pyannote segmentation 3.0 (Sprecherwechsel) – Lora Edition",
    "size": 6958444,
    "sha256": "24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488",
    "url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
    "target": "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
   }
  ],
  "title": "pyannote segmentation 3.0 (Sprecherwechsel) – Lora Edition",
  "base": "pyannote/segmentation-3.0",
  "authors": "pyannote (Hervé Bredin u. a.) · Paket: k2-fsa/sherpa-onnx",
  "quant": "k2-fsa/sherpa-onnx (Release-Asset, kein Commit)",
  "license_id": "mit",
  "base_model": "pyannote/segmentation-3.0",
  "relation": "quantized",
  "lang": null,
  "tags": [
   "onnx",
   "sherpa-onnx",
   "speaker-diarization",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://raw.githubusercontent.com/pyannote/pyannote-audio/develop/LICENSE"
   ]
  ],
  "license_note": "MIT (das Original auf Hugging Face ist zugangsbeschränkt; die Lizenz erlaubt die Weitergabe)",
  "authors_short": "pyannote (Hervé Bredin u. a.)"
 },
 "yunet-face-detection-lora-edition": {
  "files": [
   {
    "label": "YuNet Gesichtserkennung 2023mar – Lora Edition",
    "size": 232589,
    "sha256": "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
    "url": "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx",
    "target": "face_detection_yunet_2023mar.onnx"
   }
  ],
  "title": "YuNet Gesichtserkennung 2023mar – Lora Edition",
  "base": "opencv/opencv_zoo models/face_detection_yunet",
  "authors": "Shiqi Yu u. a. (OpenCV Zoo)",
  "quant": "opencv/opencv_zoo (Branch main, kein Commit)",
  "license_id": "mit",
  "base_model": null,
  "relation": null,
  "lang": null,
  "tags": [
   "onnx",
   "face-detection",
   "lora-edition"
  ],
  "licenses": [
   [
    "LICENSE",
    "https://raw.githubusercontent.com/opencv/opencv_zoo/main/models/face_detection_yunet/LICENSE"
   ]
  ],
  "license_note": "MIT",
  "authors_short": "Shiqi Yu u. a. (OpenCV Zoo)"
 },
 "rnnoise-somnolent-hogwash-lora-edition": {
  "files": [
   {
    "label": "RNNoise somnolent-hogwash (Rauschfilter) – Lora Edition",
    "size": 297646,
    "sha256": "70bb6685eb0c2a1d18e2918dca3fbfbd39317010b1802eb1b6ea73a92f3fdec0",
    "url": "https://raw.githubusercontent.com/GregorR/rnnoise-models/master/somnolent-hogwash-2018-09-01/sh.rnnn",
    "target": "sh.rnnn"
   }
  ],
  "title": "RNNoise somnolent-hogwash (Rauschfilter) – Lora Edition",
  "base": "GregorR/rnnoise-models somnolent-hogwash-2018-09-01",
  "authors": "Gregor Richards (rnnoise-nu)",
  "quant": "GregorR/rnnoise-models (Branch master, kein Commit)",
  "license_id": "unknown",
  "base_model": null,
  "relation": null,
  "lang": null,
  "tags": [
   "noise-suppression",
   "lora-edition"
  ],
  "licenses": [
   [
    "README-rnnoise-models.md",
    "https://raw.githubusercontent.com/GregorR/rnnoise-models/master/README.md"
   ]
  ],
  "license_note": "keine Lizenz – laut README der Quelle nicht urheberrechtlich geschützt (siehe README-rnnoise-models.md)",
  "authors_short": "Gregor Richards (rnnoise-nu)"
 }
}''')


def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def gb(n: int) -> str:
    return f"{n / 1e9:.2f} GB" if n >= 1e9 else f"{n / 1e6:.1f} MB" if n >= 1e6 else f"{n / 1e3:.0f} KB"


def url_laden(url: str, ziel: Path):
    ziel.parent.mkdir(parents=True, exist_ok=True)
    teil = ziel.with_name(ziel.name + ".teil")
    req = urllib.request.Request(url, headers={"User-Agent": "lora-spiegel"})
    with urllib.request.urlopen(req, timeout=120) as r, open(teil, "wb") as out:
        shutil.copyfileobj(r, out, 1 << 20)
    teil.replace(ziel)


def datei_holen(f: dict, ordner: Path) -> Path:
    ziel = ordner / f["target"]
    if ziel.is_file() and ziel.stat().st_size == f["size"] and sha256(ziel) == f["sha256"]:
        print(f"    schon da, geprüft: {f['target']}")
        return ziel
    print(f"    lade {f['target']} ({gb(f['size'])}) …", flush=True)
    if "hf_repo" in f:
        from huggingface_hub import hf_hub_download
        p = Path(hf_hub_download(f["hf_repo"], f["path"], revision=f["revision"], local_dir=ordner))
        if p.resolve() != ziel.resolve():
            ziel.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(p), ziel)
    else:
        url_laden(f["url"], ziel)
    groesse, pruef = ziel.stat().st_size, sha256(ziel)
    if groesse != f["size"] or pruef != f["sha256"]:
        raise SystemExit(f"FEHLER: {f['target']} passt nicht zur Liste (Größe {groesse}, SHA-256 {pruef}). Abbruch – nichts hochgeladen.")
    print(f"    ✓ Größe und SHA-256 stimmen: {f['target']}")
    return ziel


def quelle(f: dict) -> str:
    if "hf_repo" in f:
        return f"[{f['hf_repo']}](https://huggingface.co/{f['hf_repo']}) @ `{f['revision'][:7]}`"
    return f["url"]


def karte(slug: str, g: dict) -> str:
    meta = [f"license: {g['license_id']}"]
    if g.get("base_model"):
        meta.append(f"base_model: {g['base_model']}")
        if g.get("relation"):
            meta.append(f"base_model_relation: {g['relation']}")
    if g.get("lang"):
        meta.append("language: [" + ", ".join(g["lang"]) + "]")
    meta.append("tags: [" + ", ".join(g["tags"]) + "]")
    basis = f"[{g['base_model']}](https://huggingface.co/{g['base_model']})" if g.get("base_model") else g["base"]
    dateien = "\n".join(
        f"| {f['label']} | `{f['target']}` | {gb(f['size'])} | `{f['sha256']}` | {quelle(f)} |" for f in g["files"])
    lizenzdateien = ", ".join(f"`{name}`" for name, _ in g["licenses"])
    return f"""---
{chr(10).join(meta)}
---

# {g['title']}

{SATZ}

| | |
| --- | --- |
| Basismodell | {basis} |
| Urheber | {g['authors_short']} |
| Lizenz | {g['license_note']} – Lizenztext liegt bei ({lizenzdateien}) |
| Bereitgestellt von | {ORG_NAME} für die App Lora |

## Dateien

| Teil | Datei | Größe | SHA-256 | Quelle (fester Stand) |
| --- | --- | --- | --- | --- |
{dateien}

## Was „Lora Edition“ heißt

Wir haben das Modell für die App Lora ausgewählt, auf unseren Geräten gemessen und für Lora eingestellt (Kontext,
Vorlage). Die Dateien sind byte-gleich mit der genannten Quelle – die SHA-256-Werte oben lassen sich nachprüfen. Basismodell,
Urheber und Lizenz bleiben wie im Original. Die Urheber haben diese Ausgabe weder geprüft noch gebilligt; für die Nutzung
gilt allein die Lizenz des Originals.
"""


def repo_spiegeln(slug: str, g: dict, args):
    from huggingface_hub import HfApi
    api = HfApi()
    repo_id = f"{ORG}/{slug}"
    groesse = sum(f["size"] for f in g["files"])
    print(f"\n== {repo_id}  ({len(g['files'])} Dateien, {gb(groesse)})")
    ordner = ARBEIT / slug
    ordner.mkdir(parents=True, exist_ok=True)
    frei = shutil.disk_usage(ordner).free
    if frei < groesse * 1.1:
        raise SystemExit(f"FEHLER: zu wenig Platz ({gb(frei)} frei, {gb(groesse)} nötig). Abbruch.")
    for f in g["files"]:
        datei_holen(f, ordner)
    for name, url in g["licenses"]:
        url_laden(url, ordner / name)
    (ordner / "README.md").write_text(karte(slug, g), encoding="utf-8")
    # Nur die Dateien dieses Repos hochladen (kein Cache von hf_hub_download).
    erlaubt = [f["target"] for f in g["files"]] + [n for n, _ in g["licenses"]] + ["README.md"]
    if args.probelauf:
        print(f"    Probelauf: Karte geschrieben ({ordner / 'README.md'}), nichts hochgeladen.")
        return None
    api.create_repo(repo_id, repo_type="model", private=True, exist_ok=True)
    info = api.upload_folder(repo_id=repo_id, folder_path=ordner, allow_patterns=erlaubt,
                             commit_message="Lora Edition: Dateien unverändert von der Quelle, Lizenz und Modellkarte")
    # Auf Hugging Face nachprüfen: Größe und SHA-256 jeder großen Datei.
    stand = api.model_info(repo_id, revision=info.oid, files_metadata=True)
    auf_hf = {s.rfilename: s for s in stand.siblings}
    for f in g["files"]:
        s = auf_hf.get(f["target"])
        hf_sha = s.lfs.sha256 if s and s.lfs else None
        if not s or s.size != f["size"] or (hf_sha and hf_sha != f["sha256"]):
            raise SystemExit(f"FEHLER: {repo_id}/{f['target']} auf Hugging Face passt nicht zur Liste. Repo bleibt privat.")
    print(f"    ✓ hochgeladen und auf Hugging Face geprüft, Commit {info.oid}")
    for quelle_repo, quelle_stand in sorted({(f["hf_repo"], f["revision"]) for f in g["files"] if "hf_repo" in f}):
        print(f'    für ModelMirror.REPOS: "{quelle_repo}@{quelle_stand}" to "{slug}@{info.oid}",')
    if args.oeffentlich:
        api.update_repo_settings(repo_id, private=False)
        print("    ✓ jetzt öffentlich")
    if not args.behalten:
        shutil.rmtree(ordner, ignore_errors=True)
    return {"repo": repo_id, "commit": info.oid, "public": bool(args.oeffentlich),
            "files": [{"file": f["target"], "size": f["size"], "sha256": f["sha256"],
                       "url": f"https://huggingface.co/{repo_id}/resolve/{info.oid}/{f['target']}"} for f in g["files"]]}


def main():
    ap = argparse.ArgumentParser(description="Lora-Modelle nach Hugging Face spiegeln (thepytales).")
    wahl = ap.add_mutually_exclusive_group(required=True)
    wahl.add_argument("--liste", action="store_true", help="Repos mit Größe zeigen")
    wahl.add_argument("--nur", nargs="+", metavar="REPO", help="nur diese Repos (Name ohne thepytales/)")
    wahl.add_argument("--alle", action="store_true", help="alle Repos nacheinander")
    ap.add_argument("--oeffentlich", action="store_true", help="nach erfolgreicher Prüfung öffentlich stellen")
    ap.add_argument("--probelauf", action="store_true", help="laden, prüfen, Karte schreiben – nichts hochladen")
    ap.add_argument("--behalten", action="store_true", help="geladene Dateien danach nicht löschen")
    args = ap.parse_args()

    if args.liste:
        for slug, g in DATEN.items():
            print(f"{gb(sum(f['size'] for f in g['files'])):>10}  {ORG}/{slug}")
        print(f"{gb(sum(f['size'] for g in DATEN.values() for f in g['files'])):>10}  zusammen ({len(DATEN)} Repos)")
        return
    namen = list(DATEN) if args.alle else args.nur
    unbekannt = [n for n in namen if n not in DATEN]
    if unbekannt:
        raise SystemExit("Unbekannt: " + ", ".join(unbekannt) + " – siehe --liste")
    if not args.probelauf:
        from huggingface_hub import HfApi
        try:
            wer = HfApi().whoami()
        except Exception:
            raise SystemExit("Nicht angemeldet – zuerst: hf auth login")
        print(f"Angemeldet als {wer.get('name')}; Ziel: {ORG}")
    ergebnis = json.loads(ERGEBNIS.read_text(encoding="utf-8")) if ERGEBNIS.is_file() else {}
    for slug in namen:
        if not args.probelauf and ergebnis.get(slug, {}).get("public") and args.oeffentlich:
            print(f"\n== {ORG}/{slug}: schon fertig (spiegel-ergebnis.json), übersprungen")
            continue
        r = repo_spiegeln(slug, DATEN[slug], args)
        if r:
            ergebnis[slug] = r
            ERGEBNIS.write_text(json.dumps(ergebnis, ensure_ascii=False, indent=1), encoding="utf-8")
    print("\nFertig." + ("" if args.probelauf else f" Ergebnis: {ERGEBNIS.resolve()}"))


if __name__ == "__main__":
    main()
