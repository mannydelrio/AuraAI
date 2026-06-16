# Aura — Setup Guide

Aura turns a **Meta Portal** into an always-on, voice-driven AI assistant that floats over every
app — weather, timers/alarms/reminders, web lookups, and **Home Assistant smart-home control**.

Because Portal is a locked-down device with no normal Settings screens, a few things have to be set
up once from a computer over USB (a small script does it all). After that, everything you personalize
— your AI key, Home Assistant, location — is done right on the Portal in **Aura → Settings**.

---

## What you need
- A **Meta Portal** (touch model) and its **USB-C data cable**
- A **Windows or Mac computer** (the installer downloads `adb` for you — nothing to pre-install)
- An **OpenAI API key** — get one at https://platform.openai.com/api-keys (add ~$5 of credit;
  this is separate from ChatGPT Plus/Pro). It powers both the brain and the voice.

---

## Step 1 — Enable ADB on the Portal
On the Portal: **Settings → Debug → "ADB Enabled"** (enter your PIN if asked).
Plug the Portal into your computer with the USB-C cable, and **tap "Allow"** on the Portal screen.

> Portal Go: pop off the rubber cover to reach the USB-C port.

## Step 2 — Run the installer (it downloads everything for you)
From the [latest release](../../releases/latest):

- **Windows:** download **`Install-AuraAI.bat`** and **double-click it**.
- **Mac:** download **`install.command`**, then double-click it (if macOS blocks it the first
  time, right-click it → **Open** → **Open**).

That's it — the installer auto-downloads `adb` and the AuraAI app, installs it, and configures
everything: display-over-apps, microphone, the **assistant role** (so voice works over other apps),
and turns off the **screensaver** so the assistant stays on screen (all Portal generations).

> Prefer to do it manually? `setup.ps1` / `setup.sh` are also on the release if you already have
> `adb` and the APK locally.

## Step 3 — Personalize in the app
On the Portal, open **Aura → Settings**:
- **Provider:** choose **OpenAI**, paste your API key, leave model as `gpt-4o-mini`.
- **Default location** (optional): e.g. `Austin, TX` — used for weather when you don't name a place.
- **Smart home (Home Assistant)** (optional): your HA **URL** (e.g. `http://192.168.1.50:8123`) and a
  **long-lived access token** (HA → your profile → Security → Long-lived access tokens → Create).
- **Activation:** "Wake word" (say "Hey Aura …") or "Continuous".

Tap **Save**, go back, tap **Start Aura** and **Start listening**. **Unmute the Portal mic** if it's muted.

---

## Using Aura
Say **"Hey Aura, …"** (or tap the bubble and type):
- "what's the weather?" · "what time is it?"
- "set a timer for 10 minutes" · "remind me to take out the trash in 1 hour"
- "turn off the living room lights" · "set the thermostat to 72" · "is the front door locked?"
- anything conversational

To silence a ringing alarm: tap **Stop** on the notification, or say **"Hey Aura, stop."**

---

## Troubleshooting
- **Voice does nothing while another app is open:** Aura must be the device **assistant**. Re-run the
  setup script, or run:
  `adb shell settings put secure voice_interaction_service com.aura.assistant/.assist.AuraInteractionService`
- **Voice does nothing at all:** the Portal **mic is muted** (mic-slash icon up top) — unmute it.
- **The bubble disappears at the launcher / when the screensaver kicks in** (more common on
  **Gen 1** Portals): the app self-heals (it re-attaches the bubble automatically), but the
  **Superframe screensaver renders above all overlays**, so it must be *disabled*, not covered.
  Re-run the setup script — it turns the screensaver off and keeps the screen on. Also enable
  "Start automatically after reboot" in Settings.
- **Smart-home commands fail:** check the HA URL/token in Settings, and that the Portal is on the same
  Wi-Fi as Home Assistant.
- **Screen turns off / shows photos again:** a Portal update may have reset the settings — re-run the
  setup script.

---

## Privacy notes
- Your API key and Home Assistant token are stored only in the app's local settings on the Portal.
- With voice on, the mic is always listening for the wake word; speech is only sent to your chosen
  cloud provider when an utterance is detected (or every utterance in Continuous mode).
- A long-lived Home Assistant token can control your whole home — treat it like a password.
