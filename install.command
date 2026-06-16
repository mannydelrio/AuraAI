#!/usr/bin/env bash
# AuraAI — seamless one-step installer for Meta Portal (macOS / Linux).
# Double-click on macOS, or run: bash install.command
#
# Auto-downloads adb + the latest AuraAI app, installs, and configures everything.
# On the Portal first: Settings > Debug > "ADB Enabled", connect USB-C, tap Allow.
set -e

PKG="com.aura.assistant"
ASSIST="$PKG/.assist.AuraInteractionService"
WORK="$(mktemp -d)"
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== AuraAI installer ==="

# --- adb: use existing, or download platform-tools ---
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
else
  echo "Downloading Android platform-tools (one-time)..."
  case "$(uname)" in
    Darwin) URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip" ;;
    *)      URL="https://dl.google.com/android/repository/platform-tools-latest-linux.zip" ;;
  esac
  curl -L "$URL" -o "$WORK/pt.zip"
  unzip -q "$WORK/pt.zip" -d "$WORK"
  ADB="$WORK/platform-tools/adb"
fi

# --- app: local APK next to this file, else download latest release ---
APK="$(ls -t "$DIR"/*.apk 2>/dev/null | head -n1 || true)"
if [ -z "$APK" ]; then
  echo "Downloading the latest AuraAI release..."
  APKURL="$(curl -s https://api.github.com/repos/mannydelrio/AuraAI/releases/latest | grep -o 'https://[^\"]*\.apk' | head -n1)"
  [ -z "$APKURL" ] && { echo "Could not find an APK in the latest release."; exit 1; }
  APK="$WORK/auraai.apk"
  curl -L "$APKURL" -o "$APK"
fi

echo "Waiting for your Portal (enable ADB, connect USB, tap Allow)..."
"$ADB" wait-for-device

echo "Installing AuraAI..."
"$ADB" install -r "$APK"

echo "Granting permissions and configuring..."
"$ADB" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
"$ADB" shell pm grant "$PKG" android.permission.RECORD_AUDIO
"$ADB" shell settings put secure voice_interaction_service "$ASSIST"
"$ADB" shell settings put secure assistant "$ASSIST"
"$ADB" shell settings put secure screensaver_enabled 0
"$ADB" shell settings put secure screensaver_activate_on_sleep 0
"$ADB" shell settings put secure screensaver_activate_on_dock 0
"$ADB" shell settings delete secure screensaver_components
"$ADB" shell settings delete secure screensaver_default_component
"$ADB" shell settings put system screen_off_timeout 2147483647
"$ADB" shell settings put global stay_on_while_plugged_in 7
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null

echo ""
echo "Done! Open AuraAI on the Portal -> Settings, add your OpenAI API key"
echo "(and optionally a default location + Home Assistant URL/token). Then Start AuraAI + Start listening."
