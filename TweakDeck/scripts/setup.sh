#!/usr/bin/env bash
# Install TweakDeck and unlock full settings control. Run from the project folder.
# Usage: scripts/setup.sh [path-to-apk]
set -e
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG=com.tweakdeck.app

echo "Devices:"; adb devices
adb install -r "$APK"

echo "Granting WRITE_SECURE_SETTINGS..."
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS || {
  echo
  echo "Grant failed. On Realme UI open Settings > Additional settings > Developer options and turn on:"
  echo "  - USB debugging (Security settings)"
  echo "  - Disable permission monitoring"
  echo "then run this script again."
  exit 1
}
echo "Granting WRITE_SETTINGS..."
adb shell appops set $PKG WRITE_SETTINGS allow
echo "Done. Open TweakDeck on the phone."
