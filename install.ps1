# AuraAI - seamless one-step installer for Meta Portal (Windows).
#
# Does everything automatically: downloads adb if you don't have it, downloads the latest
# AuraAI app, installs it, grants permissions, sets AuraAI as the assistant, and disables the
# screensaver - across all Portal generations.
#
# You only do two things on the Portal:
#   1) Settings > Debug > "ADB Enabled"
#   2) Plug it into this PC with a USB-C cable and tap "Allow"
#
# Then run this (or just double-click Install-AuraAI.bat).

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$pkg = 'com.aura.assistant'
$assistant = "$pkg/.assist.AuraInteractionService"
$work = Join-Path $env:TEMP 'auraai-install'
New-Item -ItemType Directory -Force -Path $work | Out-Null

function Section($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }

# --- 1. adb: use an existing one, or download platform-tools automatically ---
Section 'Setting up adb'
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) {
  Write-Host 'Downloading Android platform-tools (one-time, ~10 MB)...'
  $zip = Join-Path $work 'platform-tools.zip'
  Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
  Expand-Archive -Path $zip -DestinationPath $work -Force
  $adb = Join-Path $work 'platform-tools\adb.exe'
}
Write-Host "adb: $adb"

# --- 2. AuraAI app: use a local APK if present next to this script, else download latest ---
Section 'Getting the AuraAI app'
$apk = $null
if ($PSScriptRoot) {
  $apk = Get-ChildItem -Path $PSScriptRoot -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $apk) {
  Write-Host 'Downloading the latest AuraAI release...'
  $rel = Invoke-RestMethod 'https://api.github.com/repos/mannydelrio/AuraAI/releases/latest' -Headers @{ 'User-Agent' = 'auraai-installer' }
  $apkUrl = ($rel.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1).browser_download_url
  if (-not $apkUrl) { throw 'Could not find an APK in the latest release.' }
  $apk = Join-Path $work 'auraai.apk'
  Invoke-WebRequest $apkUrl -OutFile $apk
}
Write-Host "app: $apk"

# --- 3. wait for the Portal ---
Section 'Connecting to your Portal'
Write-Host 'Make sure ADB is enabled (Settings > Debug) and you tapped "Allow". Waiting...'
& $adb wait-for-device

# --- 4. install + configure everything ---
Section 'Installing AuraAI'
& $adb install -r "$apk"

Section 'Granting permissions and setting up'
& $adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow
& $adb shell pm grant $pkg android.permission.RECORD_AUDIO
& $adb shell settings put secure voice_interaction_service $assistant
& $adb shell settings put secure assistant $assistant
& $adb shell settings put secure screensaver_enabled 0
& $adb shell settings put secure screensaver_activate_on_sleep 0
& $adb shell settings put secure screensaver_activate_on_dock 0
& $adb shell settings delete secure screensaver_components
& $adb shell settings delete secure screensaver_default_component
& $adb shell settings put system screen_off_timeout 2147483647
& $adb shell settings put global stay_on_while_plugged_in 7
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null

Write-Host "`nAll set!" -ForegroundColor Green
Write-Host "On the Portal, open AuraAI -> Settings and:" -ForegroundColor Green
Write-Host "  - choose OpenAI and paste your API key (platform.openai.com)" -ForegroundColor Green
Write-Host "  - (optional) set a default location, and your Home Assistant URL + token" -ForegroundColor Green
Write-Host "Then tap 'Start AuraAI' and 'Start listening'. Make sure the Portal mic is unmuted." -ForegroundColor Green
