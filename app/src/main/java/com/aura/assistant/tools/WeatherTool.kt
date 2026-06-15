/*
 * Aura — current weather + today's forecast via Open-Meteo (free, no API key).
 */
package com.aura.assistant.tools

import java.io.IOException
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WeatherTool(
    private val client: OkHttpClient,
    private val defaultLocation: String = "",
) : Tool {
  override val name = "get_weather"
  override val description =
      "Get the current weather and today's high/low for a city or place name. Use for any weather " +
          "question. If no location is given, the user's default location is used."

  override fun parameters(): JSONObject =
      JSONObject(
          """{"type":"object","properties":{"location":{"type":"string","description":"City or place, e.g. 'Miami' or 'Paris, France'. Optional — omit to use the user's default location."}}}""")

  override suspend fun execute(args: JSONObject): String =
      withContext(Dispatchers.IO) {
        val loc = args.optString("location").trim().ifBlank { defaultLocation.trim() }
        if (loc.isEmpty()) return@withContext "Please tell me which location, or set a default location in Aura's settings."

        val geo =
            JSONObject(
                httpGet(
                    "https://geocoding-api.open-meteo.com/v1/search?name=${enc(loc)}&count=1&language=en&format=json"))
        val results = geo.optJSONArray("results")
        if (results == null || results.length() == 0) return@withContext "I couldn't find a place called \"$loc\"."
        val place = results.getJSONObject(0)
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val cityName = place.optString("name", loc)
        val region = place.optString("admin1", "")
        val country = place.optString("country", "")

        val fc =
            JSONObject(
                httpGet(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                        "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                        "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto"))
        val cur = fc.getJSONObject("current")
        val temp = cur.getDouble("temperature_2m").roundToInt()
        val feels = cur.optDouble("apparent_temperature", temp.toDouble()).roundToInt()
        val humidity = cur.optInt("relative_humidity_2m")
        val wind = cur.optDouble("wind_speed_10m", 0.0).roundToInt()
        val desc = describe(cur.optInt("weather_code"))
        val daily = fc.getJSONObject("daily")
        val hi = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
        val lo = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()

        val where = listOf(cityName, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
        "Weather in $where: $desc, $temp°F (feels like $feels°F), humidity $humidity%, wind $wind mph. " +
            "Today's high $hi°F, low $lo°F."
      }

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

  private fun httpGet(url: String): String {
    client.newCall(Request.Builder().url(url).build()).execute().use {
      if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
      return it.body!!.string()
    }
  }

  /** WMO weather interpretation codes → short text. */
  private fun describe(code: Int): String =
      when (code) {
        0 -> "clear sky"
        1 -> "mainly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "foggy"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61, 63, 65 -> "rain"
        66, 67 -> "freezing rain"
        71, 73, 75 -> "snow"
        77 -> "snow grains"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown conditions"
      }
}
