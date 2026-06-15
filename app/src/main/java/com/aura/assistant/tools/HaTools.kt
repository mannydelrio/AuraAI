/*
 * Aura — Home Assistant smart-home tools: read device states and control devices.
 */
package com.aura.assistant.tools

import com.aura.assistant.ha.HomeAssistantClient
import org.json.JSONObject

private val USEFUL_DOMAINS =
    setOf(
        "light", "switch", "fan", "climate", "lock", "cover", "media_player", "scene", "sensor",
        "binary_sensor")

/** Read the smart home: list devices/entities and their current state. */
class HaGetStatesTool(private val ha: HomeAssistantClient) : Tool {
  override val name = "home_assistant_get_states"
  override val description =
      "List the user's smart-home devices and their current state from Home Assistant. " +
          "Optionally filter by domain (light, switch, fan, climate, lock, cover, media_player, " +
          "scene, sensor, binary_sensor). Use this to find an entity_id before controlling a " +
          "device, or to answer a state question (is the door locked, what's the temperature)."

  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"domain":{"type":"string","description":"Optional domain filter, e.g. light, climate, lock"}}}""")

  override suspend fun execute(args: JSONObject): String {
    val domain = args.optString("domain").trim()
    val states = ha.states()
    val sb = StringBuilder()
    for (i in 0 until states.length()) {
      val e = states.getJSONObject(i)
      val id = e.optString("entity_id")
      val dom = id.substringBefore('.')
      if (domain.isNotEmpty()) {
        if (dom != domain) continue
      } else if (dom !in USEFUL_DOMAINS) {
        continue
      }
      val name = e.optJSONObject("attributes")?.optString("friendly_name").orEmpty()
      val state = e.optString("state")
      sb.append(id)
      if (name.isNotBlank()) sb.append(" (").append(name).append(")")
      sb.append(": ").append(state).append('\n')
    }
    return if (sb.isEmpty()) "No matching devices found." else sb.toString().trim()
  }
}

/** Control a device by calling a Home Assistant service. */
class HaCallServiceTool(private val ha: HomeAssistantClient) : Tool {
  override val name = "home_assistant_call_service"
  override val description =
      "Control a Home Assistant device by calling a service. Examples: light.turn_on, " +
          "light.turn_off, switch.turn_on/turn_off, fan.turn_on/turn_off, lock.lock, lock.unlock, " +
          "climate.set_temperature (data {\"temperature\":72}), climate.set_hvac_mode " +
          "(data {\"hvac_mode\":\"cool\"}), scene.turn_on, media_player.turn_off / media_play_pause / " +
          "volume_set. Always pass entity_id; look it up with home_assistant_get_states first if unsure."

  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"domain":{"type":"string"},"service":{"type":"string"},"entity_id":{"type":"string"},"data":{"type":"object","description":"Optional extra parameters, e.g. {\"temperature\":72} or {\"brightness_pct\":50}"}},"required":["domain","service","entity_id"]}""")

  override suspend fun execute(args: JSONObject): String {
    val domain = args.optString("domain").trim()
    val service = args.optString("service").trim()
    val entityId = args.optString("entity_id").trim()
    if (domain.isEmpty() || service.isEmpty() || entityId.isEmpty()) {
      return "Need domain, service, and entity_id."
    }
    val payload = JSONObject().put("entity_id", entityId)
    args.optJSONObject("data")?.let { data ->
      val keys = data.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        payload.put(k, data.get(k))
      }
    }
    ha.callService(domain, service, payload)
    return "Done: called $domain.$service on $entityId."
  }
}
