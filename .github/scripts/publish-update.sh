#!/usr/bin/env bash
# Publishes a version for the automatic update of the apps (Einstellungen › Updates) in the PUBLIC repository
# ADEU-TUD/ADEU-Harness-Updater – it holds only the finished files, no code. The apps read
#   https://github.com/ADEU-TUD/ADEU-Harness-Updater/releases/latest/download/version.json
# (a plain download: no GitHub account, no API limit) and then download their file and check its SHA-256.
#
# Usage: .github/scripts/publish-update.sh <version, e.g. 1.0.37> <folder with the files> ["Was ist neu"]
#   The folder holds ADEU-LLM-<version>-Windows.zip and/or ADEU-LLM-<version>.apk (optional also the -Windows.exe),
#   and since AP-31 optionally ADEU-LLM-<version>-macOS.tar.gz (+ .dmg) and ADEU-LLM-<version>-Linux.tar.gz (+ .deb).
#   Each platform whose file is there gets its key in version.json ("windows", "android", "macos", "linux"); the
#   update of the apps uses the ZIP/APK/tar.gz, the EXE/DMG/DEB are only uploaded for first installations.
#   The reloadable extensions (AP-25, Videoeditor-Paket) are built here with extension-videoeditor.sh and listed under
#   "extensions" in version.json.
#   The text "Was ist neu" appears in the update popup; without it UPDATE_NOTES or a general sentence.
#
# Needs python3 and the GitHub CLI "gh" with write access to the updater repository: locally "gh auth login",
# in GitHub Actions GH_TOKEN=<token> (secret UPDATER_TOKEN, see README › Automatische Updates).
# With NO_UPLOAD=1 it only writes version.json into the folder – e.g. to copy the folder to a network drive that
# serves as update source for PCs without access to GitHub.
set -euo pipefail
version="${1:?Versionsnummer, z.B. 1.0.37}"
dist="${2:?Ordner mit den Dateien}"
notes="${3:-${UPDATE_NOTES:-}}"
[ -n "$notes" ] || notes="Fehlerbehebungen und Verbesserungen."
repo="${UPDATER_REPO:-ADEU-TUD/ADEU-Harness-Updater}"
tag="v$version"
zip="$dist/ADEU-LLM-$version-Windows.zip"
apk="$dist/ADEU-LLM-$version.apk"
exe="$dist/ADEU-LLM-$version-Windows.exe"
mac="$dist/ADEU-LLM-$version-macOS.tar.gz"
dmg="$dist/ADEU-LLM-$version-macOS.dmg"
linux="$dist/ADEU-LLM-$version-Linux.tar.gz"
deb="$dist/ADEU-LLM-$version-Linux.deb"
[ -f "$zip" ] || [ -f "$apk" ] || [ -f "$mac" ] || [ -f "$linux" ] \
  || { echo "Weder $zip noch $apk (noch die tar.gz für macOS/Linux) gefunden." >&2; exit 1; }

# Nachladbare Erweiterungen (AP-25): the Videoeditor-Paket comes with every release (NO_EXTENSIONS=1 leaves it out).
# A failed download only warns – the release itself must not fail because of it.
if [ "${NO_EXTENSIONS:-}" != "1" ] && [ ! -f "$dist/erweiterungen.json" ]; then
  bash "$(dirname "$0")/extension-videoeditor.sh" "$dist" || { echo "Warnung: Videoeditor-Paket nicht gebaut." >&2; rm -f "$dist/erweiterungen.json"; }
fi

python3 - "$version" "$repo" "$tag" "$notes" "$dist/version.json" "$dist/erweiterungen.json" \
  "windows=$zip" "android=$apk" "macos=$mac" "linux=$linux" <<'PY'
import hashlib, json, os, sys, datetime
version, repo, tag, notes, out, ext_path = sys.argv[1:7]
platforms = [arg.split("=", 1) for arg in sys.argv[7:]]
def entry(path):
    if not os.path.isfile(path):
        return None
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    name = os.path.basename(path)
    return {"file": name, "url": f"https://github.com/{repo}/releases/download/{tag}/{name}",
            "sha256": h.hexdigest(), "size": os.path.getsize(path)}
data = {"version": version, "published": datetime.date.today().isoformat(), "notes": notes}
for key, path in platforms:
    e = entry(path)
    if e:
        data[key] = e
if os.path.isfile(ext_path):
    exts = []
    for x in json.load(open(ext_path, encoding="utf-8")):
        e = entry(os.path.join(os.path.dirname(out), x["file"]))
        if e:
            exts.append({k: x[k] for k in ("id", "name", "version", "description")} | e)
    if exts:
        data["extensions"] = exts
with open(out, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(json.dumps(data, ensure_ascii=False, indent=2))
PY

if [ "${NO_UPLOAD:-}" = "1" ]; then
  echo "version.json liegt in $dist (nicht hochgeladen)."
  exit 0
fi

# Releases need at least one commit in the repository; its README (for colleagues and for the upkeep) comes from
# .github/updater/README.md and is brought up to date here.
readme="$(cd "$(dirname "$0")/.." && pwd)/updater/README.md"
if [ -f "$readme" ]; then
  remote_sha="$(gh api "repos/$repo/contents/README.md" --jq .sha 2>/dev/null || true)"
  [[ "$remote_sha" =~ ^[0-9a-f]{40}$ ]] || remote_sha=""
  remote_text="$(gh api "repos/$repo/contents/README.md" -H "Accept: application/vnd.github.raw" 2>/dev/null || true)"
  if [ "$remote_text" != "$(cat "$readme")" ]; then
    content="$(python3 -c 'import base64, sys; print(base64.b64encode(open(sys.argv[1], "rb").read()).decode())' "$readme")"
    args=(-X PUT "repos/$repo/contents/README.md" -f message="README aktualisiert" -f content="$content")
    [ -n "$remote_sha" ] && args+=(-f sha="$remote_sha")
    gh api "${args[@]}" >/dev/null
    echo "README im Update-Repository aktualisiert."
  fi
fi

files=("$dist/version.json")
for f in "$zip" "$apk" "$exe" "$mac" "$dmg" "$linux" "$deb" "$dist"/Lora-*.zip; do [ -f "$f" ] && files+=("$f"); done
body="$notes

**Installation:** Die App aktualisiert sich selbst (Popup beim Start). Zum ersten Installieren:
- Windows: \`ADEU-LLM-$version-Windows.exe\` herunterladen und starten – keine Installation, keine Admin-Rechte. Alternativ die ZIP entpacken und \`ADEU LLM.exe\` starten.
- Android: \`ADEU-LLM-$version.apk\` herunterladen und öffnen."
if [ -f "$dmg" ]; then
  body+="
- macOS (Mac mit Apple-Chip, M1 oder neuer): \`ADEU-LLM-$version-macOS.dmg\` öffnen und die App in den Ordner Programme ziehen. Zeigt macOS beim ersten Start eine Warnung, steht in der README dieses Repositorys, was zu tun ist."
fi
if [ -f "$linux" ]; then
  body+="
- Linux (64 Bit): \`ADEU-LLM-$version-Linux.tar.gz\` entpacken und \`ADEU LLM/bin/ADEU LLM\` starten (aktualisiert sich selbst) – oder \`ADEU-LLM-$version-Linux.deb\` mit \`sudo apt install ./ADEU-LLM-$version-Linux.deb\` installieren (Updates dann über die neue .deb)."
fi
if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
  gh release upload "$tag" "${files[@]}" --repo "$repo" --clobber
  gh release edit "$tag" --repo "$repo" --notes "$body" --latest
else
  gh release create "$tag" "${files[@]}" --repo "$repo" --title "Lora $version" --notes "$body" --latest
fi
echo "Veröffentlicht: https://github.com/$repo/releases/tag/$tag"
