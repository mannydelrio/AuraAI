#!/usr/bin/env bash
# Aura — one-time setup for Meta Portal (macOS / Linux).
#
# Prerequisites:
#   1. Enable ADB on the Portal: Settings > Debug > "ADB Enabled".
#   2. Connect the Portal with a USB-C data cable, tap "Allow" on the device.
#   3. Install Android platform-tools (adb): https://developer.android.com/tools/releases/platform-tools
#
# Run from the folder containing the Aura APK:
#   bash setup.sh
#
# Afterward, open Aura on the Portal -> Settings and enter your AI provider API key
# (and optionally Home Assistant URL + token, and a default location).
set -e

PKG="com.aura.assistant"
ASSISTANT="$PKG/.assist.AuraInteractionService"
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Aura setup ==="

command -v adb >/dev/null 2>&1 || { echo "adb not found. Install Android platform-tools and re-run."; exit 1; }

APK="$(ls -t "$DIR"/*.apk 2>/dev/null | head -n 1 || true)"
[ -z "$APK" ] && APK="$(ls -t "$DIR"/app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1 || true)"
if [ -z "$APK" ]; then echo "No APK found. Put app-debug.apk next to this script."; exit 1; fi
echo "APK: $APK"

echo "Waiting for the Portal over ADB..."
adb wait-for-device

echo "Installing Aura..."
adb install -r "$APK"

echo "Granting permissions..."
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO

echo "Setting Aura as the device assistant..."
adb shell settings put secure voice_interaction_service "$ASSISTANT"
adb shell settings put secure assistant "$ASSISTANT"

echo "Keeping the screen on (disabling screensaver)..."
adb shell settings put secure screensaver_enabled 0
adb shell settings put secure screensaver_activate_on_sleep 0
adb shell settings put secure screensaver_activate_on_dock 0
adb shell settings put system screen_off_timeout 2147483647

adb shell am start -n "$PKG/.MainActivity" >/dev/null

echo ""
echo "Done! Open Aura on the Portal -> Settings, add your AI provider key (OpenAI recommended for"
echo "voice), optionally a Default location and your Home Assistant URL + token, then tap"
echo "'Start Aura' and 'Start listening' (and make sure the Portal mic is unmuted)."
