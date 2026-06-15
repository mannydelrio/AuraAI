/*
 * Aura — minimal owner so a ComposeView can live in a WindowManager overlay window.
 *
 * A view added directly via WindowManager has no Activity behind it, so Compose can't
 * find the Lifecycle / SavedStateRegistry / ViewModelStore it needs. This supplies them
 * and drives the lifecycle to RESUMED for as long as the overlay is shown.
 */
package com.aura.assistant.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val store = ViewModelStore()
  private val savedStateController = SavedStateRegistryController.create(this)

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val viewModelStore: ViewModelStore
    get() = store

  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateController.savedStateRegistry

  fun onCreate() {
    savedStateController.performRestore(null)
    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
  }

  fun onDestroy() {
    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    store.clear()
  }
}
