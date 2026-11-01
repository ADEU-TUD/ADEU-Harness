#!/usr/bin/env bash
# Start test of the macOS/Linux app (AP-31), like windows-selftest.ps1: starts the app from the unpacked release file
# with --selftest, which opens every screen, creates documents, writes <folder>/selftest.txt and ends the app (exit
# code 1 if anything failed). Fails if there is no "ERGEBNIS: OK".
#
# Usage: .github/scripts/desktop-selftest.sh <launcher> [folder, default ./selftest]
#   macOS: "…/ADEU LLM.app/Contents/MacOS/ADEU LLM"
#   Linux: "…/ADEU LLM/bin/ADEU LLM" – on a server without screen inside xvfb-run, e.g.
#          xvfb-run -a -s "-screen 0 1600x1000x24" .github/scripts/desktop-selftest.sh "…/ADEU LLM/bin/ADEU LLM"
set -uo pipefail
launcher="${1:?Starter der App}"
dir="${2:-$PWD/selftest}"
limit="${SELFTEST_MINUTES:-8}"
[ -x "$launcher" ] || { echo "Nicht ausführbar: $launcher" >&2; exit 1; }
rm -rf "$dir"
mkdir -p "$dir"
dir="$(cd "$dir" && pwd)"
result="$dir/selftest.txt"
log="$dir/data/log.txt"

# No `timeout` on macOS: wait for the app, end it after the time limit.
"$launcher" --selftest "$dir" >"$dir/konsole.txt" 2>&1 &
pid=$!
deadline=$(( $(date +%s) + limit * 60 ))
while kill -0 "$pid" 2>/dev/null && [ "$(date +%s)" -lt "$deadline" ]; do sleep 3; done
if kill -0 "$pid" 2>/dev/null; then
  echo "Selbsttest nach $limit Minuten nicht beendet – App wird beendet."
  kill "$pid" 2>/dev/null
  sleep 5
  kill -9 "$pid" 2>/dev/null
fi
wait "$pid"
code=$?

[ -f "$log" ] && cp "$log" "$dir/log.txt"
show_logs() {
  echo "--- Konsole (Ende)"; tail -n 40 "$dir/konsole.txt" 2>/dev/null
  echo "--- log.txt (Ende)"; tail -n 80 "$log" 2>/dev/null
}
if [ ! -f "$result" ]; then
  show_logs
  echo "Der Selbsttest hat kein Ergebnis geschrieben (App nicht gestartet oder abgestürzt, Exit-Code $code)." >&2
  exit 1
fi
cat "$result"
if ! grep -qF "ERGEBNIS: OK" "$result"; then
  show_logs
  echo "Selbsttest fehlgeschlagen (Exit-Code $code)." >&2
  exit 1
fi
echo "Selbsttest bestanden (Exit-Code $code)."
