/*
 * Aura — a single capability the agent can call (weather, timer, search, …).
 */
package com.aura.assistant.tools

import org.json.JSONObject

interface Tool {
  /** Function name exposed to the model (snake_case). */
  val name: String

  /** What it does — the model reads this to decide when to call it. */
  val description: String

  /** JSON Schema object describing the arguments (OpenAI "function.parameters"). */
  fun parameters(): JSONObject

  /** Run the tool and return a short, human-readable result for the model to use. */
  suspend fun execute(args: JSONObject): String
}
