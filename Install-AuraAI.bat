@echo off
title AuraAI Installer
echo ============================================
echo   AuraAI - Meta Portal installer
echo ============================================
echo.
echo Before continuing, on your Portal:
echo   1) Settings ^> Debug ^> "ADB Enabled"
echo   2) Plug it into this PC with a USB-C cable
echo   3) Tap "Allow" on the Portal screen
echo.
pause
echo.
echo Running installer (this downloads adb + the app automatically)...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; iwr 'https://raw.githubusercontent.com/mannydelrio/AuraAI/main/install.ps1' -UseBasicParsing | iex"
echo.
pause
