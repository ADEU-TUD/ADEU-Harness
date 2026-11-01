#!/usr/bin/env bash
# Builds the reloadable extension „Videoeditor-Paket“ (AP-25) for the automatic updates: the Windows app sets it up on
# first use of the video editor into %LOCALAPPDATA%\ADEU LLM\tools\videoeditor (desktop/…/Extensions.kt).
#
#   ffprobe.exe          exact details of videos – from the same ffmpeg build as the app's ffmpeg.exe
#                        (speech/natives.gradle.kts, same version and SHA-256), with its licence
#   rnnoise/sh.rnnn      speech model of the noise reduction (ffmpeg filter arnndn), „somnolent-hogwash“ of
#                        github.com/GregorR/rnnoise-models (recordings with speech; the models are not copyrighted)
#
# Usage: .github/scripts/extension-videoeditor.sh <dist folder>
# Writes dist/Lora-Videoeditor-<EXT_VERSION>.zip and dist/erweiterungen.json (read by publish-update.sh). Raise
# EXT_VERSION when the content changes – installed apps then update the package with their next update check.
set -euo pipefail
dist="${1:?Ordner für das Paket}"
EXT_VERSION="1"
FFMPEG_URL="https://github.com/GyanD/codexffmpeg/releases/download/9.0.2/ffmpeg-9.0.2-essentials_build.zip"
FFMPEG_SHA="60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba"
RNN_URL="https://raw.githubusercontent.com/GregorR/rnnoise-models/master/somnolent-hogwash-2018-09-01/sh.rnnn"
RNN_SHA="70bb6685eb0c2a1d18e2918dca3fbfbd39317010b1802eb1b6ea73a92f3fdec0"

root="$(cd "$(dirname "$0")/../.." && pwd)"
cache="${EXT_CACHE:-$root/build/speech-downloads}"
mkdir -p "$cache" "$dist"

fetch() { # url sha target
  local url="$1" sha="$2" target="$3"
  if [ -f "$target" ] && [ "$(sha256sum "$target" | cut -d' ' -f1)" = "$sha" ]; then return 0; fi
  curl -fsSL --retry 4 --retry-delay 5 -o "$target.part" "$url"
  [ "$(sha256sum "$target.part" | cut -d' ' -f1)" = "$sha" ] || { echo "Prüfsumme falsch: $url" >&2; rm -f "$target.part"; return 1; }
  mv "$target.part" "$target"
}

fetch "$FFMPEG_URL" "$FFMPEG_SHA" "$cache/ffmpeg-9.0.2-essentials_build.zip"
fetch "$RNN_URL" "$RNN_SHA" "$cache/rnnoise-sh.rnnn"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
pkg="$work/pkg"
mkdir -p "$pkg/rnnoise"
python3 - "$cache/ffmpeg-9.0.2-essentials_build.zip" "$pkg" <<'PY'
import sys, zipfile, os
src, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z:
    for n in z.namelist():
        base = os.path.basename(n)
        if n.endswith("/bin/ffprobe.exe"):
            open(os.path.join(out, "ffprobe.exe"), "wb").write(z.read(n))
        elif base == "LICENSE" and n.count("/") == 1:
            open(os.path.join(out, "ffmpeg-LICENSE.txt"), "wb").write(z.read(n))
assert os.path.isfile(os.path.join(out, "ffprobe.exe")), "ffprobe.exe fehlt im ffmpeg-Paket"
PY
cp "$cache/rnnoise-sh.rnnn" "$pkg/rnnoise/sh.rnnn"
cat > "$pkg/LIESMICH.txt" <<TXT
Videoeditor-Paket $EXT_VERSION für Lora (Windows)

ffprobe.exe       – aus ffmpeg 9.0.2 (Essentials-Build von gyan.dev), Lizenz: ffmpeg-LICENSE.txt (GPL v3)
rnnoise/sh.rnnn   – Sprachmodell „somnolent-hogwash“ aus github.com/GregorR/rnnoise-models für den
                    ffmpeg-Filter arnndn (Rauschminderung); laut Projekt nicht urheberrechtlich geschützt.

Wird von Lora unter %LOCALAPPDATA%\ADEU LLM\tools\videoeditor eingerichtet und lässt sich unter
Einstellungen › Werkzeuge › Erweiterungen wieder entfernen.
TXT

zip_name="Lora-Videoeditor-$EXT_VERSION.zip"
rm -f "$dist/$zip_name"
(cd "$pkg" && zip -q -r -9 "$dist/$zip_name" .)
python3 - "$dist/erweiterungen.json" "$zip_name" "$EXT_VERSION" <<'PY'
import json, sys
out, name, version = sys.argv[1:4]
data = [{
    "id": "videoeditor", "name": "Videoeditor-Paket", "version": version, "file": name,
    "description": "ffprobe für genaue Angaben zu Videos und das Sprachmodell der Rauschminderung (RNNoise).",
}]
json.dump(data, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
ls -l "$dist/$zip_name"
