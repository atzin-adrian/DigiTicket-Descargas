#!/usr/bin/env bash
set -euo pipefail
asset="$RUNNER_TEMP/signed-pilot/DigiTicket-${PILOT_TAG#v}.apk"
adb install "$asset"
adb shell am start -W -n mx.digiticket.app.beta/mx.digiticket.app.MainActivity
for attempt in {1..20}; do
  if adb shell uiautomator dump /sdcard/digiticket-screen.xml >/dev/null 2>&1 &&
     adb exec-out cat /sdcard/digiticket-screen.xml > "$RUNNER_TEMP/digiticket-screen.xml" &&
     grep -q 'Registrar venta' "$RUNNER_TEMP/digiticket-screen.xml" &&
     grep -q 'Registrar gasto' "$RUNNER_TEMP/digiticket-screen.xml"; then
    echo "Verified the signed pilot opens on its business dashboard."
    exit 0
  fi
  sleep 2
done
echo "The signed beta did not reach its dashboard." >&2
exit 1
