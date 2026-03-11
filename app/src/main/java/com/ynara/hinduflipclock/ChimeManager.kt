package com.ynara.hinduflipclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.preference.PreferenceManager
import kotlin.concurrent.thread
import kotlin.math.*

object ChimeManager {

    enum class ChimeType(val displayName: String) {
        SOFT_BELL("Soft Bell"),
        DEEP_GONG("Deep Gong"),
        QUICK_BEEP("Quick Beep"),
        CRYSTAL_TING("Crystal Ting"),
        WESTMINSTER("Westminster")
    }

    // Westminster note frequencies (Hz)
    private const val G4 = 392.0
    private const val C5 = 523.0
    private const val D5 = 587.0
    private const val E5 = 659.0

    // Westminster 4-note phrases
    private val PHRASE_A = listOf(E5, C5, D5, G4)
    private val PHRASE_B = listOf(G4, D5, E5, C5)
    private val PHRASE_C = listOf(E5, D5, C5, G4)
    private val PHRASE_D = listOf(G4, C5, D5, E5)

    // Full note sequence per quarter
    private fun westminsterSequence(minute: Int): List<Double> = when (minute) {
        15   -> PHRASE_A
        30   -> PHRASE_B + PHRASE_A
        45   -> PHRASE_A + PHRASE_B + PHRASE_C
        0    -> PHRASE_B + PHRASE_A + PHRASE_D + PHRASE_C
        else -> emptyList()
    }

    /**
     * Called by MainActivity on every chime tick.
     * Skips silently during quiet hours unless [preview] is true.
     */
    fun chime(context: Context, minute: Int, preview: Boolean = false) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (!preview && prefs.getBoolean("quiet_hours_enabled", true)) {
            val now = java.util.Calendar.getInstance()
            val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
            val quietStart = prefs.getInt("quiet_start_hour", 22)
            val quietEnd   = prefs.getInt("quiet_end_hour",   6)
            if (isQuietTime(hour, quietStart, quietEnd)) return
        }

        val selectedChimeStr = prefs.getString("selected_chime", ChimeType.SOFT_BELL.name)
        val chimeType = try {
            ChimeType.valueOf(selectedChimeStr!!)
        } catch (e: Exception) {
            ChimeType.SOFT_BELL
        }

        thread(isDaemon = true) {
            when (chimeType) {
                ChimeType.SOFT_BELL    -> playTone(660.0,  1.2, 2.5, 0.7, getCount(minute))
                ChimeType.DEEP_GONG   -> playTone(220.0,  2.0, 1.5, 0.9, getCount(minute))
                ChimeType.QUICK_BEEP  -> playTone(1200.0, 0.3, 8.0, 0.5, getCount(minute))
                ChimeType.CRYSTAL_TING-> playTone(1800.0, 0.8, 4.0, 0.4, getCount(minute))
                ChimeType.WESTMINSTER -> {
                    val hour = java.util.Calendar.getInstance()
                        .get(java.util.Calendar.HOUR_OF_DAY)
                    playWestminster(minute, hour)
                }
            }
        }
    }

    private fun isQuietTime(hour: Int, startHour: Int, endHour: Int): Boolean {
        return if (startHour >= endHour) {
            // Spans midnight e.g. 22:00 → 06:00
            hour >= startHour || hour < endHour
        } else {
            hour >= startHour && hour < endHour
        }
    }

    private fun getCount(minute: Int): Int = when (minute) {
        0  -> 1
        15 -> 1
        30 -> 2
        45 -> 3
        else -> 1
    }

    // ── Westminster chime ─────────────────────────────────────────────────────

    private fun playWestminster(minute: Int, hour: Int) {
        val sequence = westminsterSequence(minute)
        if (sequence.isEmpty()) return

        val sampleRate   = 44100
        val noteSamples  = (sampleRate * 0.45).toInt()   // 450ms per note
        val gapSamples   = (sampleRate * 0.08).toInt()   // 80ms gap between notes
        val phraseSamples= (sampleRate * 0.28).toInt()   // 280ms pause between phrases
        val silence1s    = sampleRate                    // 1-second pause before strikes

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()

        // Play chime notes
        sequence.forEachIndexed { i, freq ->
            val note = ShortArray(noteSamples) { idx ->
                val t = idx.toDouble() / sampleRate
                val env = exp(-t * 3.0)
                (sin(2 * PI * freq * t) * env * Short.MAX_VALUE * 0.65).toInt().toShort()
            }
            track.write(note, 0, note.size)
            track.write(ShortArray(gapSamples), 0, gapSamples)

            // Extra pause after each complete 4-note phrase (but not at the very end)
            if ((i + 1) % 4 == 0 && i < sequence.size - 1) {
                track.write(ShortArray(phraseSamples), 0, phraseSamples)
            }
        }

        // Hour strikes at :00
        if (minute == 0) {
            val strikeHour = when {
                hour == 0  -> 12
                hour > 12  -> hour - 12
                else       -> hour
            }
            track.write(ShortArray(silence1s), 0, silence1s)

            val strikeDuration = (sampleRate * 0.7).toInt()
            val strikePause    = (sampleRate * 0.4).toInt()
            repeat(strikeHour) { i ->
                val strike = ShortArray(strikeDuration) { idx ->
                    val t = idx.toDouble() / sampleRate
                    val env = exp(-t * 2.0)
                    (sin(2 * PI * G4 * t) * env * Short.MAX_VALUE * 0.8).toInt().toShort()
                }
                track.write(strike, 0, strike.size)
                if (i < strikeHour - 1) {
                    track.write(ShortArray(strikePause), 0, strikePause)
                }
            }
        }

        track.stop()
        track.release()
    }

    // ── Simple tone (used by all non-Westminster chime types) ─────────────────

    private fun playTone(freq: Double, durationSec: Double, decay: Double, volume: Double, count: Int) {
        val sampleRate  = 44100
        val numSamples  = (sampleRate * durationSec).toInt()
        val pauseSamples= sampleRate / 4

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()

        repeat(count) {
            val samples = ShortArray(numSamples) { i ->
                val t = i.toDouble() / sampleRate
                val env = exp(-t * decay)
                val wave = (sin(2 * PI * freq * t) * 0.7
                        + sin(2 * PI * freq * 2 * t) * 0.2
                        + sin(2 * PI * freq * 3 * t) * 0.1)
                (wave * env * Short.MAX_VALUE * volume).toInt().toShort()
            }
            track.write(samples, 0, samples.size)

            if (it < count - 1) {
                val silence = ShortArray(pauseSamples) { 0 }
                track.write(silence, 0, silence.size)
            }
        }

        track.stop()
        track.release()
    }
}
