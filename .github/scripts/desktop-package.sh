#!/usr/bin/env bash
# Packs the macOS or Linux app (AP-31) into the release files, after
#   macOS: ./gradlew -p desktop createDistributable packageDmg   (on a Mac, Apple Silicon)
#   Linux: ./gradlew -p desktop createDistributable packageDeb   (needs dpkg-deb and fakeroot)
#
#   dist/ADEU-LLM-<version>-macOS.dmg      first installation: open, drag Lora into "Programme"
#   dist/ADEU-LLM-<version>-macOS.tar.gz   "ADEU LLM.app/" at the top – the automatic update of the macOS app
#   dist/ADEU-LLM-<version>-Linux.tar.gz   "ADEU LLM/" (bin/ADEU LLM, lib/app, lib/runtime) – start without
#                                          installing; the automatic update of the Linux app
#   dist/ADEU-LLM-<version>-Linux.deb      package "adeu-llm" (/opt/adeu-llm) for "sudo apt install ./…deb"
#
# The tar.gz keep symlinks and exec bits (the Java runtime needs both). On macOS without AppleDouble files ("._*") and
# extended attributes, which would break the code signature of the bundle after unpacking.
#
# Usage: .github/scripts/desktop-package.sh <macos|linux> <version, e.g. 1.0.45> [dist folder, default dist]
set -euo pipefail
platform="${1:?macos oder linux}"
version="${2:?Versionsnummer, z.B. 1.0.45}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
dist="${3:-$root/dist}"
mkdir -p "$dist"
dist="$(cd "$dist" && pwd)"
binaries="$root/desktop/build/compose/binaries/main"

# The one file of a jpackage output folder (its name carries the version of the build, e.g. "ADEU LLM-1.0.45.dmg").
single() {
  local found=("$1"/*."$2")
  [ -f "${found[0]}" ] || { echo "Keine .$2-Datei in $1 – erst ./gradlew -p desktop … bauen." >&2; exit 1; }
  [ "${#found[@]}" -eq 1 ] || { echo "Mehrere .$2-Dateien in $1: ${found[*]}" >&2; exit 1; }
  printf '%s\n' "${found[0]}"
}

case "$platform" in
  macos)
    app="$binaries/app/ADEU LLM.app"
    [ -x "$app/Contents/MacOS/ADEU LLM" ] || { echo "Fehlt: $app" >&2; exit 1; }
    dmg="$dist/ADEU-LLM-$version-macOS.dmg"
    tgz="$dist/ADEU-LLM-$version-macOS.tar.gz"
    cp "$(single "$binaries/dmg" dmg)" "$dmg"
    rm -f "$tgz"
    COPYFILE_DISABLE=1 tar -C "$binaries/app" --no-mac-metadata --no-xattrs -czf "$tgz" "ADEU LLM.app"
    hdiutil verify "$dmg" >/dev/null
    launcher="ADEU LLM.app/Contents/MacOS/ADEU LLM"
    ;;
  linux)
    app="$binaries/app/ADEU LLM"
    [ -x "$app/bin/ADEU LLM" ] || { echo "Fehlt: $app/bin/ADEU LLM" >&2; exit 1; }
    deb="$dist/ADEU-LLM-$version-Linux.deb"
    tgz="$dist/ADEU-LLM-$version-Linux.tar.gz"
    cp "$(single "$binaries/deb" deb)" "$deb"
    rm -f "$tgz"
    tar -C "$binaries/app" --owner=0 --group=0 --numeric-owner -czf "$tgz" "ADEU LLM"
    # The package installs /opt/adeu-llm/bin/ADEU LLM and carries the version of this build.
    [ "$(dpkg-deb -f "$deb" Package)" = "adeu-llm" ] || { echo "$deb: Paketname ist nicht adeu-llm" >&2; exit 1; }
    [ "$(dpkg-deb -f "$deb" Version)" = "$version" ] || echo "Warnung: $deb hat Version $(dpkg-deb -f "$deb" Version), erwartet $version" >&2
    grep -q "/opt/adeu-llm/bin/ADEU LLM$" <<<"$(dpkg-deb -c "$deb")" || { echo "$deb: Starter fehlt" >&2; exit 1; }
    launcher="ADEU LLM/bin/ADEU LLM"
    ;;
  *)
    echo "Plattform: macos oder linux" >&2
    exit 1
    ;;
esac

# The launcher must arrive executable at the top of the archive, and the runtime's symlinks must stay symlinks.
listing="$(tar -tvzf "$tgz")"
grep -q -- "^-rwx.* $launcher\$" <<<"$listing" || { echo "$tgz: \"$launcher\" fehlt oder ist nicht ausführbar" >&2; exit 1; }
if grep -q -- "^-.* \._" <<<"$listing" || grep -q '/\._' <<<"$listing"; then echo "$tgz: enthält AppleDouble-Dateien (._*)" >&2; exit 1; fi
echo "$(grep -c '' <<<"$listing") Einträge, davon $(grep -c '^l' <<<"$listing" || true) symbolische Links"
ls -l "$dist"/ADEU-LLM-"$version"-*
