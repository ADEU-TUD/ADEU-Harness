#!/usr/bin/env bash
# Smoke test on the emulator: update over the previous release, start the app, open every screen.
# Fails on the first crash and prints its stack trace. Screenshots and logcat land in smoke/.
set -u
PKG=io.github.thepytales.llmhub
OUT=smoke
mkdir -p "$OUT"

crashed() {
  adb logcat -d > "$OUT/logcat.txt"
  grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"
}

check() {
  local name="$1"
  sleep "$2"
  adb exec-out screencap -p > "$OUT/$(echo "$name" | tr '/' '_').png" 2>/dev/null || true
  if crashed; then
    echo "::error::Absturz bei „$name“"
    grep -A 80 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -120
    exit 1
  fi
  if [ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then
    echo "::error::App läuft nach „$name“ nicht mehr"
    tail -200 "$OUT/logcat.txt"
    exit 1
  fi
  echo "OK: $name"
}

old=$(ls old/*.apk 2>/dev/null | head -1)
if [ -n "$old" ]; then
  echo "Installiere vorherige Version: $old"
  adb install "$old"
  # Without the grant the old version opens the notification dialog, which stays on top after the update
  # and swallows the start of the new version.
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  adb shell am start -W -n "$PKG/.MainActivity"
  sleep 8
  adb shell am force-stop "$PKG"
fi

new=$(ls new/*.apk | head -1)
echo "Installiere $new"
adb install -r "$new" || { echo "::error::Installation fehlgeschlagen"; exit 1; }
# The app asks for notifications on first start; the system dialog would cover the app during the test.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
# Close system dialogs that may still be open, so the start below reaches the app.
adb shell am force-stop com.google.android.permissioncontroller || true
adb shell input keyevent KEYCODE_HOME
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
check "start" 12

for route in drawer settings settings/api settings/github settings/chat settings/appearance settings/voice \
  settings/documents settings/memory settings/knowledge settings/backup environments environment/new templates tasks task/new usage compare models images chat; do
  adb shell am start -n "$PKG/.MainActivity" --es route "$route" > /dev/null
  check "$route" 5
done

# Designed PDFs are rendered by a WebView – in the app and while it is in the background.
pdf_test() {
  local mode="$1" wait="$2" line
  adb logcat -c
  adb shell am start -n "$PKG/.MainActivity" --es pdftest "$mode" > /dev/null
  if [ "$mode" = background ]; then sleep 2; adb shell input keyevent KEYCODE_HOME; fi
  for _ in $(seq "$wait"); do
    line=$(adb logcat -d -s PdfSelfTest:* | grep "PDF-SELFTEST" | tail -1)
    [ -n "$line" ] && break
    sleep 1
  done
  adb logcat -d > "$OUT/logcat-pdf-$mode.txt"
  echo "PDF ($mode): ${line:-keine Meldung}"
  case "$line" in
    *"OK:"*gestaltet*) ;;
    *"OK:"*) echo "::warning::PDF ($mode) nur mit dem einfachen Renderer erstellt: $line" ;;
    *) echo "::error::PDF ($mode) konnte nicht erstellt werden: ${line:-keine Meldung}"; exit 1 ;;
  esac
}
pdf_test foreground 90
pdf_test background 100
check "pdf" 1

adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
check "neustart" 8
echo "Alle Bildschirme ließen sich öffnen."
