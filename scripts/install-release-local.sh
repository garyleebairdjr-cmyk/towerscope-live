#!/usr/bin/env bash
# Builds the open-source edition's release APK, signed with your own key, and installs it over the
# existing app (io.towerscope.android), keeping its data:
#
#   scripts/install-release-local.sh [device-serial]
#
# The signing details come from, in order: TOWERSCOPE_* environment variables, ./keystore.properties,
# or ~/.android/towerscope-live-release.properties. The passwords are read from those files by Gradle
# and never printed.
#
# It refuses to install unless the APK is signed with the same certificate as the published releases, so a
# wrong password (which silently falls back to the debug key) can never force an uninstall and wipe settings.
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ -z "${TOWERSCOPE_KEYSTORE_PASSWORD:-}" && ! -f keystore.properties ]]; then
  : "${TOWERSCOPE_KEYSTORE_PROPERTIES:=$HOME/.android/towerscope-live-release.properties}"
  [[ -f "$TOWERSCOPE_KEYSTORE_PROPERTIES" ]] || {
    echo "No signing key configured. See keystore.properties.example." >&2; exit 1; }
  export TOWERSCOPE_KEYSTORE_PROPERTIES
  unset TOWERSCOPE_KEYSTORE   # let the properties file name the keystore
fi

EXPECTED_CERT="bfe89f4055857ffd9b8bd376"   # first 24 hex digits of the release certificate's SHA-256
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
APKSIGNER=$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)
ADB=(adb); [[ -n "${1:-}" ]] && ADB=(adb -s "$1")

# Number the build one above what's installed, so Android accepts it as an update instead of a downgrade.
if [[ -z "${VERSION_CODE:-}" ]]; then
  INSTALLED=$("${ADB[@]}" shell dumpsys package io.towerscope.android 2>/dev/null | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
  export VERSION_CODE=$(( ${INSTALLED:-0} + 1 ))
  export VERSION_NAME="0.9.${VERSION_CODE}-local"
fi

./gradlew -q :androidApp:assembleFossRelease
APK=androidApp/build/outputs/apk/foss/release/androidApp-foss-release.apk

CERT=$("$APKSIGNER" verify --print-certs "$APK" | awk '/SHA-256 digest/ {print $NF; exit}')
if [[ "${CERT:0:24}" != "$EXPECTED_CERT" ]]; then
  echo "Not installing: the APK is signed with ${CERT:0:24}…, not the release key $EXPECTED_CERT…" >&2
  echo "Check the passwords and alias. Installing it would require uninstalling and would erase saved settings." >&2
  exit 2
fi

"${ADB[@]}" install -r "$APK"
echo "Installed with the release key; the app's data (settings, API key, history) was kept."
