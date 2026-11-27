#!/usr/bin/env bash
# Builds a release APK signed with your own key and installs it over the existing app, keeping its data.
# Run it in your own terminal so the passwords never leave it:
#
#   read -rs -p "Keystore password: " TOWERSCOPE_KEYSTORE_PASSWORD; echo
#   read -rs -p "Key password (Enter if same): " TOWERSCOPE_KEY_PASSWORD; echo
#   export TOWERSCOPE_KEYSTORE_PASSWORD TOWERSCOPE_KEY_PASSWORD="${TOWERSCOPE_KEY_PASSWORD:-$TOWERSCOPE_KEYSTORE_PASSWORD}"
#   export TOWERSCOPE_KEY_ALIAS=<your key alias>
#   scripts/install-release-local.sh [device-serial]
#
# It refuses to install unless the APK is signed with the same certificate as the published releases, so a
# wrong password (which silently falls back to the debug key) can never force an uninstall and wipe settings.
set -euo pipefail

cd "$(dirname "$0")/.."
: "${TOWERSCOPE_KEYSTORE:=$HOME/.android/towerscope-live-release.jks}"
export TOWERSCOPE_KEYSTORE
for v in TOWERSCOPE_KEYSTORE_PASSWORD TOWERSCOPE_KEY_PASSWORD TOWERSCOPE_KEY_ALIAS; do
  [[ -n "${!v:-}" ]] || { echo "Set $v first (see the top of this script)." >&2; exit 1; }
done

EXPECTED_CERT="bfe89f4055857ffd9b8bd376"   # first 24 hex digits of the release certificate's SHA-256
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
APKSIGNER=$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)
ADB=(adb); [[ -n "${1:-}" ]] && ADB=(adb -s "$1")

./gradlew -q :androidApp:assembleRelease
APK=androidApp/build/outputs/apk/release/androidApp-release.apk

CERT=$("$APKSIGNER" verify --print-certs "$APK" | awk '/SHA-256 digest/ {print $NF; exit}')
if [[ "${CERT:0:24}" != "$EXPECTED_CERT" ]]; then
  echo "Not installing: the APK is signed with ${CERT:0:24}…, not the release key $EXPECTED_CERT…" >&2
  echo "Check the passwords and alias. Installing it would require uninstalling and would erase saved settings." >&2
  exit 2
fi

"${ADB[@]}" install -r "$APK"
echo "Installed with the release key; the app's data (settings, API key, history) was kept."
