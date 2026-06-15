/*
 * Aura — registers as the device's VoiceInteractionService (the "assistant" role).
 *
 * The point isn't to use the system voice UI — it's that the OS exempts the *assistant's*
 * UID from Android 10's background-microphone silencing. Once Aura is the assistant, our
 * VoiceService can keep hearing the mic even while another app is in the foreground.
 */
package com.aura.assistant.assist

import android.service.voice.VoiceInteractionService

class AuraInteractionService : VoiceInteractionService()
