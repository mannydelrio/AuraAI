# Aura — one-time setup for Meta Portal (Windows).
#
# Prerequisites:
#   1. Enable ADB on the Portal: Settings > Debug > "ADB Enabled".
#   2. Connect the Portal to this PC with a USB-C data cable, tap "Allow" on the device.
#   3. Install Android platform-tools (adb) and/or have it on PATH:
#      https://developer.android.com/tools/releases/platform-tools
#
# Then run this script from the folder that contains the Aura APK:
#   powershell -ExecutionPolicy Bypass -File setup.ps1
#
# After it finishes, open Aura on the Portal -> Settings, and enter your AI provider API key
# (and optionally your Home Assistant URL + token, and a default location).

$ErrorActionPreference = "Stop"
$pkg = "com.aura.assistant"
$assistant = "$pkg/.assist.AuraInteractionService"

Write-Host "=== Aura setup ===" -ForegroundColor Cyan

# --- locate adb ---
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) {
  Write-Host "adb not found. Install Android platform-tools and re-run." -ForegroundColor Red
  exit 1
}

# --- locate the APK (next to this script, or the gradle build output) ---
$apk = Get-ChildItem -Path $PSScriptRoot -Filter "*.apk" -Recurse -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) {
  Write-Host "No APK found next to this script. Put app-debug.apk here and re-run." -ForegroundColor Red
  exit 1
}
Write-Host "APK: $($apk.FullName)"

# --- wait for the Portal ---
Write-Host "Waiting for the Portal over ADB (make sure it's connected and you tapped Allow)..."
& $adb wait-for-device

# --- install ---
Write-Host "Installing Aura..." -ForegroundColor Cyan
& $adb install -r "$($apk.FullName)"

# --- permissions ---
Write-Host "Granting permissions..." -ForegroundColor Cyan
& $adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow     # display over other apps
& $adb shell pm grant $pkg android.permission.RECORD_AUDIO # microphone

# --- assistant role (lets the mic work while other apps are open) ---
Write-Host "Setting Aura as the device assistant..." -ForegroundColor Cyan
& $adb shell settings put secure voice_interaction_service $assistant
& $adb shell settings put secure assistant $assistant

# --- keep the screen on, no Superframe screensaver (all Portal generations) ---
# A system screensaver ("dream") draws ABOVE app overlays, so it can't be covered — it must
# be disabled, not drawn over. We turn it off, unregister its component, and stop the screen
# from ever idling into it.
Write-Host "Keeping the screen on (disabling screensaver)..." -ForegroundColor Cyan
& $adb shell settings put secure screensaver_enabled 0
& $adb shell settings put secure screensaver_activate_on_sleep 0
& $adb shell settings put secure screensaver_activate_on_dock 0
& $adb shell settings delete secure screensaver_components
& $adb shell settings delete secure screensaver_default_component
& $adb shell settings put system screen_off_timeout 2147483647
& $adb shell settings put global stay_on_while_plugged_in 7

# --- launch ---
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null

Write-Host ""
Write-Host "Done! Aura is installed and running." -ForegroundColor Green
Write-Host "On the Portal: open Aura -> Settings, then:" -ForegroundColor Green
Write-Host "  - choose your AI provider and paste your API key (OpenAI recommended for voice)" -ForegroundColor Green
Write-Host "  - (optional) set a Default location for weather" -ForegroundColor Green
Write-Host "  - (optional) add your Home Assistant URL + long-lived token for smart-home control" -ForegroundColor Green
Write-Host "Then tap 'Start Aura' and 'Start listening', and make sure the Portal mic is unmuted." -ForegroundColor Green
