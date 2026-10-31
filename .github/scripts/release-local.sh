#!/usr/bin/env bash
# Builds a release without GitHub Actions (when the Actions minutes are used up):
#   dist/ADEU-LLM-<version>.apk          – signed with the release key (keystore.properties), installs as update
#   dist/ADEU-LLM-<version>-Windows.zip  – portable Windows version
#
# The Windows version is assembled on any OS: the app's jars (with the Windows libraries) are built here, the
# Windows launcher ("ADEU LLM.exe"), the Java runtime and the Skia DLL are taken from the Windows ZIP of an
# earlier release. That works as long as Compose, Skia and the Java modules stay the same (desktop/build.gradle.kts);
# after updating them, build the Windows version once on Windows (Actions › Build & Release › Run workflow).
#
# Usage: .github/scripts/release-local.sh <version-code> <template-windows.zip>
#   e.g. .github/scripts/release-local.sh 26 ADEU-LLM-1.0.25-Windows.zip
# With PUBLISH=1 (and UPDATE_NOTES="Was ist neu") the result goes straight to the public update repository, from
# where the apps update themselves (.github/scripts/publish-update.sh, needs "gh auth login").
# Needs JDK 17+, the Android SDK with NDK (local.properties with sdk.dir, or ANDROID_HOME), git and CMake (for the
# local language models, see app/llama.gradle.kts) and internet access for the speech libraries.
set -euo pipefail
code="${1:?Versionsnummer, z.B. 26}"
template="${2:?Windows-ZIP einer früheren Version}"
export VERSION_CODE="$code" VERSION_NAME="1.0.$code"
root="$(cd "$(dirname "$0")/../.." && pwd)"
dist="$root/dist"
mkdir -p "$dist"

# The release key is not in the repository (see keystore.properties.example and README › Signaturschlüssel).
if [ ! -f "$root/keystore.properties" ] && [ -z "${KEYSTORE_PATH:-}" ]; then
  echo "Kein Signaturschlüssel: keystore.properties anlegen (Vorlage keystore.properties.example) oder KEYSTORE_PATH usw. setzen." >&2
  exit 1
fi

echo "== Android $VERSION_NAME"
(cd "$root" && ./gradlew --no-daemon -q assembleRelease)
cp "$root/app/build/outputs/apk/release/app-release.apk" "$dist/ADEU-LLM-$VERSION_NAME.apk"

echo "== Windows $VERSION_NAME (Jars mit Windows-Bibliotheken)"
(cd "$root" && ./gradlew -p desktop --no-daemon -q -PwindowsJars portableJars portableResources)
jars="$root/desktop/build/portable/app"
# Speech recognition: native libraries and ffmpeg for Windows (resources/native, resources/tools).
resources="$root/desktop/build/portable/resources"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -q "$template" -d "$work"
app="$work/ADEU LLM/app"
[ -f "$work/ADEU LLM/ADEU LLM.exe" ] && [ -d "$work/ADEU LLM/runtime" ] || { echo "Vorlage ist keine Windows-ZIP von ADEU LLM" >&2; exit 1; }
# Old jars out, new ones in; DLL, ICU data and resources stay.
rm -f "$app"/*.jar
cp "$jars"/*.jar "$app/"
mkdir -p "$app/resources"
cp -r "$resources"/. "$app/resources/"
main="$(cd "$jars" && ls ADEU-LLM-Desktop*.jar | head -1)"
{
  echo "[Application]"
  echo "app.classpath=\$APPDIR\\$main"
  echo "app.mainclass=io.github.thepytales.llmhub.MainKt"
  (cd "$jars" && ls *.jar | grep -v "^$main\$" | sort | sed 's/^/app.classpath=$APPDIR\\/')
  echo
  echo "[JavaOptions]"
  echo "java-options=-Djpackage.app-version=$VERSION_NAME"
  echo "java-options=-Dcompose.application.resources.dir=\$APPDIR\\resources"
  echo "java-options=-Dcompose.application.configure.swing.globals=true"
  echo "java-options=-Dfile.encoding=UTF-8"
  echo "java-options=-Dsun.stdout.encoding=UTF-8"
  echo "java-options=-Xss4m"
  # Memory (AP-04, same list as MEMORY_JVM_ARGS in desktop/build.gradle.kts): fixed limit, free heap back to Windows.
  for opt in -Xms64m -Xmx1536m -XX:+UseG1GC -XX:G1PeriodicGCInterval=60000 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 \
    -XX:+UseStringDeduplication -XX:SoftRefLRUPolicyMSPerMB=200; do
    echo "java-options=$opt"
  done
  # Browser beside the chat (AP-14, same list as BROWSER_JVM_ARGS in desktop/build.gradle.kts).
  for opt in --add-exports=java.desktop/sun.awt=ALL-UNNAMED --add-exports=java.desktop/sun.java2d=ALL-UNNAMED \
    --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens=java.desktop/java.awt.peer=ALL-UNNAMED; do
    echo "java-options=$opt"
  done
  echo "java-options=-Dskiko.library.path=\$APPDIR"
} | sed 's/$/\r/' > "$app/ADEU LLM.cfg"
# The Skia library inside the jar must match the DLL next to it.
skiko_new="$(cd "$jars" && ls skiko-awt-runtime-windows-x64-*.jar)"
unzip -p "$jars/$skiko_new" skiko-windows-x64.dll.sha256 | cmp -s - "$app/skiko-windows-x64.dll.sha256" \
  || { echo "Skia-Version hat sich geändert – Windows-Version einmal auf Windows bauen." >&2; exit 1; }
rm -f "$dist/ADEU-LLM-$VERSION_NAME-Windows.zip"
(cd "$work" && zip -q -r -9 "$dist/ADEU-LLM-$VERSION_NAME-Windows.zip" "ADEU LLM")
ls -l "$dist"

if [ "${PUBLISH:-}" = "1" ]; then
  "$root/.github/scripts/publish-update.sh" "$VERSION_NAME" "$dist"
else
  echo
  echo "Als automatisches Update veröffentlichen (Apps fragen dann beim Start):"
  echo "  .github/scripts/publish-update.sh $VERSION_NAME dist \"Was ist neu …\""
fi
