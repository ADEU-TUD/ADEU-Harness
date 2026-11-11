# Spiegel der Modelle auf Hugging Face

Die kuratierten Modelle der App („<Original> – Lora Edition“) liegen byte-gleich, mit Lizenztext und Modellkarte, unter
**[huggingface.co/thepytales](https://huggingface.co/thepytales)** – 24 Repos, 47 Dateien, ≈ 151 GB, je an festem
Commit (eingerichtet 11.11.2026). Die App lädt zuerst vom Spiegel und nur, wenn das scheitert, vom Original
(`app/…/data/ModelMirror.kt`, `speech/…/Models.java`; Build: `speech/natives.gradle.kts`,
`.github/scripts/extension-videoeditor.sh` mit dem Spiegel als zweiter Quelle). Tests: `ModelMirrorTest`,
`ModelsMirrorTest`.

| Bereich | Repos |
| --- | --- |
| Sprachmodelle mit Bildteil (AP-106, AP-110) | 14 (16 Modelle; „genauer“ und Bildteil im Repo des Grundmodells) |
| Suche nach Bedeutung (AP-107) | `multilingual-e5-small-lora-edition` |
| Spracherkennung | Whisper Large v3 Turbo, Whisper Small, Whisper Base, Parakeet TDT 0.6B v3, WeSpeaker, Silero VAD, pyannote |
| Videoeditor-Paket | YuNet, RNNoise |

Nicht gespiegelt: Piper Thorsten (Herkunft aus „lessac“ klären). **Eigene Modelle** (Lora Namensschutz, Lora Befehle)
bleiben auf GitHub (Entscheidung 11.11.2026, AP-117).

**Neues Modell spiegeln**: in `spiegel.py` unter `DATEN` eintragen (Quelle an festem Commit, Größe, SHA-256, Lizenz),
`python tools/huggingface/spiegel.py --nur <repo>` (privat) prüfen, dann `--oeffentlich`; die ausgegebene Zeile in
`ModelMirror.REPOS` übernehmen. `ModelMirrorTest` schlägt fehl, solange ein Modell des Katalogs keinen Spiegel hat.
Anmeldung und Beispiele: Kopf von `spiegel.py`.
