/*
 * Aura — holds the agent's tools, exposes them in OpenAI's "tools" format, and dispatches calls.
 */
package com.aura.assistant.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ToolRegistry(tools: List<Tool>) {

  private val byName: Map<String, Tool> = tools.associateBy { it.name }

  fun isEmpty(): Boolean = byName.isEmpty()

  /** The JSON array passed as "tools" to the chat/completions API. */
  fun openAiTools(): JSONArray {
    val arr = JSONArray()
    for (t in byName.values) {
      arr.put(
          JSONObject()
              .put("type", "function")
              .put(
                  "function",
                  JSONObject()
                      .put("name", t.name)
                      .put("description", t.description)
                      .put("parameters", t.parameters())))
    }
    return arr
  }

  suspend fun execute(name: String, args: JSONObject): String {
    val tool = byName[name] ?: return "Unknown tool: $name"
    return try {
      tool.execute(args)
    } catch (e: Exception) {
      Log.e("ToolRegistry", "tool $name failed", e)
      "The $name tool failed: ${e.message}"
    }
  }
}
