package com.ynara.hinduflipclock

import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object WeatherManager {

    data class WeatherData(
        val temp: String,
        val description: String,
        val location: String
    )

    private val client = OkHttpClient()
    private var lastFetch = 0L
    private val FETCH_INTERVAL_MS = 10 * 60 * 1000L  // 10 minutes

    fun fetch(city: String, apiKey: String, units: String, callback: (WeatherData?) -> Unit) {
        val now = System.currentTimeMillis()
        if (city.isBlank() || apiKey.isBlank()) {
            callback(null)
            return
        }
        if (now - lastFetch < FETCH_INTERVAL_MS) return  // use cached; UI updates only when fresh

        lastFetch = now
        val unit = if (units == "F") "imperial" else "metric"
        val symbol = if (units == "F") "°F" else "°C"
        val url = "https://api.openweathermap.org/data/2.5/weather" +
                "?q=${city.trim()}&appid=$apiKey&units=$unit"

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: run { callback(null); return }
                    val json = JSONObject(body)
                    if (json.optInt("cod", 200) != 200) { callback(null); return }
                    val temp = json.getJSONObject("main").getDouble("temp")
                    val desc = json.getJSONArray("weather")
                        .getJSONObject(0).getString("description")
                        .replaceFirstChar { it.uppercase() }
                    val name = json.getString("name")
                    callback(WeatherData("%.1f%s".format(temp, symbol), desc, name))
                } catch (e: Exception) {
                    callback(null)
                }
            }
        })
    }
}
