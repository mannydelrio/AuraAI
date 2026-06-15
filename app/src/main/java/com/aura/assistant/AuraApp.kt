/*
 * Aura — Application entry point. Initializes the shared assistant session.
 */
package com.aura.assistant

import android.app.Application

class AuraApp : Application() {
  override fun onCreate() {
    super.onCreate()
    AssistantSession.init(this)
  }
}
