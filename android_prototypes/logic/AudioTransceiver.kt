package com.sigao.prototypes.logic

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Handles the "Physical Layer" of the Acoustic Handshake.
 * Sends and Receives Ultrasonic (18kHz) signals via the voice channel.
 */
class AudioTransceiver(private val audioManager: AudioManager) {

    companion object {
        const val SAMPLE_RATE = 48000 // Standard for high-def voice
        const val FREQUENCY_PING = 18000.0 // 18kHz
        const val FREQUENCY_PONG = 19000.0 // 19kHz (Different to avoid self-echo)
        const val DURATION_MS = 500
    }

    private var isListening = false
    private var audioRecord: AudioRecord? = null

    /**
     * Injects an 18kHz Sine Wave into the Uplink (Microphone path).
     * Note: In a real app, this requires TelecomManager or an AudioInjection service.
     */
    fun sendPing() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        val tone = generateSineWave(FREQUENCY_PING, DURATION_MS)
        audioTrack.write(tone, 0, tone.size)
        audioTrack.play()
        
        // Cleanup after playing (async in real impl)
        // audioTrack.release()
    }

    /**
     * Starts monitoring the Downlink (Incoming Audio) for 18kHz/19kHz tones.
     */
    fun startListening(onSignalDetected: (Double) -> Unit) {
        if (isListening) return
        isListening = true

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Capture call downstream
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        // Analysis Loop (runs in separate thread in prod)
        // For prototype, we show the Goertzel logic stub
        val buffer = ShortArray(bufferSize)
        while (isListening) {
             val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
             if (read > 0) {
                 val magnitude = goertzel(buffer, FREQUENCY_PING, SAMPLE_RATE)
                 if (magnitude > 5000) { // Arbitrary threshold
                     duckAudio() // Silence it!
                     onSignalDetected(FREQUENCY_PING)
                     break // One-shot detect
                 }
             }
        }
    }

    fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
    }

    /**
     * Immediately lowers call volume to hide the ultrasonic noise from the user.
     */
    private fun duckAudio() {
        // audioManager.adjustVolume(AudioManager.ADJUST_LOWER, ...)
    }

    // --- DSP Helpers ---

    private fun generateSineWave(freq: Double, durationMs: Int): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs / 1000)
        val sample = ShortArray(numSamples)
        val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
        var phase = 0.0

        for (i in 0 until numSamples) {
            sample[i] = (sin(phase) * Short.MAX_VALUE).toInt().toShort()
            phase += phaseStep
        }
        return sample
    }

    /**
     * Goertzel Algorithm for efficient single-frequency detection.
     */
    private fun goertzel(samples: ShortArray, targetFreq: Double, sampleRate: Int): Double {
        val k = (0.5 + (samples.size * targetFreq) / sampleRate).toInt()
        val omega = (2.0 * PI * k) / samples.size
        val cosine = cos(omega)
        val coeff = 2.0 * cosine

        var q1 = 0.0
        var q2 = 0.0
        
        for (sample in samples) {
            val q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }
        
        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }
}
