package com.ynara.hinduflipclock

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.ynara.hinduflipclock.databinding.ActivitySettingsBinding
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    // 20 preset colors arranged in rows of 4
    private val presetColors = listOf(
        "#FFAA00", "#FFD700", "#FF8C00", "#FFA500",  // Ambers & golds
        "#FF6B6B", "#FF4444", "#E91E63", "#FF69B4",  // Reds & pinks
        "#87CEEB", "#4FC3F7", "#2196F3", "#00BCD4",  // Blues
        "#66BB6A", "#4CAF50", "#00E676", "#8BC34A",  // Greens
        "#CE93D8", "#9C27B0", "#FFFFFF", "#CCCCCC"   // Purples & whites
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // ── Chime spinner ──
        val chimeNames = ChimeManager.ChimeType.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, chimeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerChime.adapter = adapter

        // ── Load saved values ──
        binding.etCity.setText(prefs.getString("city", ""))
        binding.etApiKey.setText(prefs.getString("api_key", ""))
        binding.cbChimes.isChecked = prefs.getBoolean("chimes_enabled", true)

        val savedChime = prefs.getString("selected_chime", ChimeManager.ChimeType.SOFT_BELL.name)
        val chimeIndex = ChimeManager.ChimeType.values().indexOfFirst { it.name == savedChime }.coerceAtLeast(0)
        binding.spinnerChime.setSelection(chimeIndex)

        val savedUnits = prefs.getString("units", "C")
        if (savedUnits == "F") binding.rbFahrenheit.isChecked = true else binding.rbCelsius.isChecked = true

        val initClock    = prefs.getString("color_clock",    "#FFAA00")!!
        val initPanchang = prefs.getString("color_panchang", "#FFD700")!!
        val initDate     = prefs.getString("color_date",     "#CCCCCC")!!
        val initWeather  = prefs.getString("color_weather",  "#87CEEB")!!

        binding.etColorClock.setText(initClock)
        binding.etColorPanchang.setText(initPanchang)
        binding.etColorDate.setText(initDate)
        binding.etColorWeather.setText(initWeather)

        // ── Wire up swatches ──
        updateSwatch(binding.vSwatchClock,    initClock)
        updateSwatch(binding.vSwatchPanchang, initPanchang)
        updateSwatch(binding.vSwatchDate,     initDate)
        updateSwatch(binding.vSwatchWeather,  initWeather)

        addSwatchWatcher(binding.etColorClock,    binding.vSwatchClock)
        addSwatchWatcher(binding.etColorPanchang, binding.vSwatchPanchang)
        addSwatchWatcher(binding.etColorDate,     binding.vSwatchDate)
        addSwatchWatcher(binding.etColorWeather,  binding.vSwatchWeather)

        binding.vSwatchClock.setOnClickListener {
            showColorPicker(binding.etColorClock.text.toString()) { hex ->
                binding.etColorClock.setText(hex)
            }
        }
        binding.vSwatchPanchang.setOnClickListener {
            showColorPicker(binding.etColorPanchang.text.toString()) { hex ->
                binding.etColorPanchang.setText(hex)
            }
        }
        binding.vSwatchDate.setOnClickListener {
            showColorPicker(binding.etColorDate.text.toString()) { hex ->
                binding.etColorDate.setText(hex)
            }
        }
        binding.vSwatchWeather.setOnClickListener {
            showColorPicker(binding.etColorWeather.text.toString()) { hex ->
                binding.etColorWeather.setText(hex)
            }
        }

        // ── Preview chime button ──
        binding.btnPreviewChime.setOnClickListener {
            val selectedChime = ChimeManager.ChimeType.values()[binding.spinnerChime.selectedItemPosition].name
            prefs.edit().putString("selected_chime", selectedChime).apply()
            // Use minute=15 for preview (plays one short phrase for Westminster)
            ChimeManager.chime(this, 15, preview = true)
        }

        // ── Quiet hours ──
        val hourLabels = (0..23).map { h ->
            val amPm = if (h < 12) "AM" else "PM"
            val display = when (h) {
                0    -> "12 AM"
                12   -> "12 PM"
                in 1..11   -> "$h AM"
                else -> "${h - 12} PM"
            }
            display
        }
        val hourAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, hourLabels)
        hourAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val hourAdapterEnd = ArrayAdapter(this, android.R.layout.simple_spinner_item, hourLabels)
        hourAdapterEnd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerQuietStart.adapter = hourAdapter
        binding.spinnerQuietEnd.adapter   = hourAdapterEnd

        val quietEnabled   = prefs.getBoolean("quiet_hours_enabled", true)
        val quietStartHour = prefs.getInt("quiet_start_hour", 22)
        val quietEndHour   = prefs.getInt("quiet_end_hour",   6)

        binding.swQuietHours.isChecked = quietEnabled
        binding.spinnerQuietStart.setSelection(quietStartHour)
        binding.spinnerQuietEnd.setSelection(quietEndHour)
        binding.layoutQuietTimes.visibility = if (quietEnabled) View.VISIBLE else View.GONE

        binding.swQuietHours.setOnCheckedChangeListener { _, checked ->
            binding.layoutQuietTimes.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // ── Save ──
        binding.btnSave.setOnClickListener {
            val city    = binding.etCity.text?.toString()?.trim() ?: ""
            val apiKey  = binding.etApiKey.text?.toString()?.trim() ?: ""
            val chimes  = binding.cbChimes.isChecked
            val units   = if (binding.rbFahrenheit.isChecked) "F" else "C"
            val selectedChime = ChimeManager.ChimeType.values()[binding.spinnerChime.selectedItemPosition].name

            val quietHoursOn  = binding.swQuietHours.isChecked
            val quietStart    = binding.spinnerQuietStart.selectedItemPosition
            val quietEnd      = binding.spinnerQuietEnd.selectedItemPosition

            val cClock    = binding.etColorClock.text?.toString()?.trim() ?: "#FFAA00"
            val cPanchang = binding.etColorPanchang.text?.toString()?.trim() ?: "#FFD700"
            val cDate     = binding.etColorDate.text?.toString()?.trim() ?: "#CCCCCC"
            val cWeather  = binding.etColorWeather.text?.toString()?.trim() ?: "#87CEEB"

            if (!isValidHex(cClock) || !isValidHex(cPanchang) || !isValidHex(cDate) || !isValidHex(cWeather)) {
                Toast.makeText(this, "Invalid color — use format #RRGGBB", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("city", city)
                .putString("api_key", apiKey)
                .putBoolean("chimes_enabled", chimes)
                .putString("selected_chime", selectedChime)
                .putString("units", units)
                .putBoolean("quiet_hours_enabled", quietHoursOn)
                .putInt("quiet_start_hour", quietStart)
                .putInt("quiet_end_hour", quietEnd)
                .putString("color_clock", cClock)
                .putString("color_panchang", cPanchang)
                .putString("color_date", cDate)
                .putString("color_weather", cWeather)
                .apply()

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ── Color swatch helpers ──

    private fun updateSwatch(swatch: View, hex: String) {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setStroke(3, Color.parseColor("#555555"))
        try {
            drawable.setColor(Color.parseColor(hex))
        } catch (e: Exception) {
            drawable.setColor(Color.DKGRAY)
        }
        swatch.background = drawable
    }

    private fun addSwatchWatcher(editText: EditText, swatch: View) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hex = s?.toString() ?: return
                if (hex.length == 7 && hex.startsWith("#")) {
                    updateSwatch(swatch, hex)
                }
            }
        })
    }

    private fun showColorPicker(currentHex: String, onPicked: (String) -> Unit) {
        val dp = resources.displayMetrics.density
        val swatchSize = (48 * dp).toInt()
        val swatchMargin = (8 * dp).toInt()
        val padding = (16 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        // Declare dialog before building rows so click listeners can close over it
        var dialog: AlertDialog? = null

        presetColors.chunked(4).forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = swatchMargin }
            }

            row.forEach { hex ->
                val isSelected = hex.equals(currentHex, ignoreCase = true)
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    if (isSelected) setStroke((3 * dp).toInt(), Color.WHITE)
                    else setStroke((1 * dp).toInt(), Color.parseColor("#444444"))
                }
                val swatchView = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                        marginEnd = swatchMargin
                    }
                    background = drawable
                    setOnClickListener {
                        onPicked(hex)
                        dialog?.dismiss()
                    }
                }
                rowLayout.addView(swatchView)
            }
            container.addView(rowLayout)
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Pick a Color")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun isValidHex(color: String): Boolean {
        return try { Color.parseColor(color); true } catch (e: Exception) { false }
    }
}
