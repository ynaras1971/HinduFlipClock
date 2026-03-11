package com.ynara.hinduflipclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.ynara.hinduflipclock.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            val now = System.currentTimeMillis()
            val delay = 1000L - (now % 1000L)
            handler.postDelayed(this, delay)
        }
    }

    private var lastWeatherFetch = 0L
    private val WEATHER_INTERVAL = 10 * 60 * 1000L
    private var lastChimeMinute = -1
    private var cachedWeather: WeatherManager.WeatherData? = null

    private val dateFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                binding.tvBattery.text = "$batteryPct%"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        handler.post(tickRunnable)
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(batteryReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
    }

    private fun tick() {
        val now = Calendar.getInstance()
        val h   = now.get(Calendar.HOUR_OF_DAY)
        val m   = now.get(Calendar.MINUTE)
        val s   = now.get(Calendar.SECOND)

        binding.flipClock.setTime(h, m, s)

        val englishDate = dateFmt.format(now.time).uppercase()

        if (s == 0 && m != lastChimeMinute && (m % 15 == 0)) {
            lastChimeMinute = m
            if (prefs.getBoolean("chimes_enabled", true)) {
                ChimeManager.chime(this, m)
            }
        }

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastWeatherFetch > WEATHER_INTERVAL) {
            lastWeatherFetch = nowMs
            fetchWeather()
        }

        if (s == 0 || s == 1 || binding.tvPanchangPrimary.text.isNullOrBlank()) {
            updatePanchang(now, englishDate)
        }
    }

    private fun fetchWeather() {
        val city   = prefs.getString("city", "") ?: ""
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (city.isBlank() || apiKey.isBlank()) return

        WeatherManager.fetch(city, apiKey, "C") { data ->
            runOnUiThread {
                if (data != null) {
                    cachedWeather = data
                    binding.tvTemp.text = data.temp
                    binding.tvWeatherDesc.text = data.description
                }
            }
        }
    }

    private fun updatePanchang(cal: Calendar, englishDate: String) {
        val p = HinduCalendar.calculate(cal)

        binding.tvDate.text = englishDate
        binding.tvSecondaryInfo.text = p.samvatsara
        binding.tvPanchangPrimary.text = "${p.masa} • ${p.tithi}"
        binding.tvFestivals.text = p.festivals.joinToString(" • ")
        binding.tvFestivals.visibility = if (p.festivals.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun applyColors() {
        val cClock = parseColor(prefs.getString("color_clock", "#FFAA00"), Color.parseColor("#FFAA00"))
        val cPanchang = parseColor(prefs.getString("color_panchang", "#FFD700"), Color.parseColor("#FFD700"))
        val cDate = parseColor(prefs.getString("color_date", "#CCCCCC"), Color.parseColor("#CCCCCC"))
        val cWeather = parseColor(prefs.getString("color_weather", "#87CEEB"), Color.parseColor("#87CEEB"))

        binding.flipClock.setDigitColor(cClock)
        binding.tvPanchangPrimary.setTextColor(cPanchang)
        binding.tvSecondaryInfo.setTextColor(cDate)
        binding.tvDate.setTextColor(cDate)
        binding.tvTemp.setTextColor(cWeather)
        binding.tvWeatherDesc.setTextColor(cWeather)
        binding.btnSettings.drawable?.setTint(cClock)
        binding.tvBattery.setTextColor(cDate)
    }

    private fun parseColor(hex: String?, default: Int): Int {
        return try { Color.parseColor(hex) } catch (e: Exception) { default }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        applyColors()
        lastWeatherFetch = 0L
        updatePanchang(Calendar.getInstance(), dateFmt.format(Date()).uppercase())
    }
}
