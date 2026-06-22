package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object AudioSynth {
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playTone(
        frequency: Float,
        durationMs: Int,
        type: String = "sine",
        frequencyEnd: Float? = null,
        volume: Float = 0.2f
    ) {
        scope.launch {
            try {
                val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
                if (numSamples <= 0) return@launch
                val samples = FloatArray(numSamples)
                
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val currentFreq = if (frequencyEnd != null) {
                        val progress = i.toFloat() / numSamples
                        frequency + (frequencyEnd - frequency) * progress
                    } else {
                        frequency
                    }
                    
                    val angle = 2.0 * Math.PI * currentFreq * t
                    val rawSample = when (type) {
                        "sine" -> sin(angle).toFloat()
                        "triangle" -> {
                            val x = (t * currentFreq) % 1f
                            2.0f * kotlin.math.abs(2.0f * x - 1.0f) - 1.0f
                        }
                        "square" -> {
                            val x = sin(angle)
                            if (x >= 0) 1.0f else -1.0f
                        }
                        "sawtooth" -> {
                            val x = (t * currentFreq) % 1f
                            2f * x - 1f
                        }
                        else -> sin(angle).toFloat()
                    }
                    // Exponential decay volume envelope
                    val envelope = 1f - i.toFloat() / numSamples
                    samples[i] = rawSample * volume * envelope
                }

                // Convert to 16-bit PCM buffer
                val pcmBuffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    pcmBuffer[i] = (samples[i] * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    pcmBuffer.size * 2,
                    AudioTrack.MODE_STATIC
                )
                audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
                audioTrack.play()
                
                // Wait for tone duration to release resource
                kotlinx.coroutines.delay(durationMs.toLong() + 80)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playIntake() {
        playTone(600f, 150, "sine", 1400f, 0.4f)
        playTone(1200f, 100, "triangle", 2800f, 0.15f)
    }

    fun playCrash() {
        playTone(150f, 600, "sawtooth", 20f, 0.5f)
    }

    fun playTeleport() {
        playTone(200f, 180, "sine", 1600f, 0.4f)
    }

    fun playWin() {
        val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)
        scope.launch {
            notes.forEachIndexed { i, freq ->
                kotlinx.coroutines.delay(65L)
                playTone(freq, 200, "triangle", volume = 0.25f)
            }
        }
    }
}
