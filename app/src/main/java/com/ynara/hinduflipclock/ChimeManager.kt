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
        CRYSTAL_TING("Crystal Ting")
    }

    fun chime(context: Context, minute: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val selectedChimeStr = prefs.getString("selected_chime", ChimeType.SOFT_BELL.name)
        val chimeType = try { ChimeType.valueOf(selectedChimeStr!!) } catch (e: Exception) { ChimeType.SOFT_BELL }

        thread(isDaemon = true) {
            when (chimeType) {
                ChimeType.SOFT_BELL -> playTone(660.0, 1.2, 2.5, 0.7, getCount(minute))
                ChimeType.DEEP_GONG -> playTone(220.0, 2.0, 1.5, 0.9, getCount(minute))
                ChimeType.QUICK_BEEP -> playTone(1200.0, 0.3, 8.0, 0.5, getCount(minute))
                ChimeType.CRYSTAL_TING -> playTone(1800.0, 0.8, 4.0, 0.4, getCount(minute))
            }
        }
    }

    private fun getCount(minute: Int): Int {
        return when (minute) {
            0 -> 1
            15 -> 1
            30 -> 2
            45 -> 3
            else -> 1
        }
    }

    private fun playTone(freq: Double, durationSec: Double, decay: Double, volume: Double, count: Int) {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationSec).toInt()
        val pauseSamples = sampleRate / 4

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
