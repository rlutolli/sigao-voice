package com.sigao.sigao_voice.prototypes.logic

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

    var onLog: ((String) -> Unit)? = null

    private fun log(message: String, t: Throwable? = null) {
        if (t != null) {
            android.util.Log.e("SigaoGhost", message, t)
            onLog?.invoke("[ERROR] $message: ${t.message}")
        } else {
            android.util.Log.d("SigaoGhost", message)
            onLog?.invoke("[DEBUG] $message")
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000 // Standard for high-def voice
        const val FREQUENCY_PING = 18000.0 // 18kHz (Silent / EVS Fullband)
        const val FREQUENCY_ROBUST = 3000.0 // 3kHz (Audible / Universal GSM fallback)
        const val FREQUENCY_PONG = 19000.0 // 19kHz (Different to avoid self-echo)
        const val FREQUENCY_PONG_ROBUST = 3500.0 // 3.5kHz
        const val FREQUENCY_KEY_ALICE = 18500.0 // 18.5kHz (Ultrasonic FSK)
        const val FREQUENCY_KEY_BOB = 19500.0   // 19.5kHz (Ultrasonic FSK)
        
        // Layered Ping Constants (Hybrid)
        const val FREQUENCY_UNIVERSAL = 2200.0       // 2.2kHz (Speech Range)
        const val FREQUENCY_GHOST_PING = 19000.0     // 19kHz (Ghost Tone)
        const val FREQUENCY_GHOST_PONG = 19500.0     // 19.5kHz (Silent Pong)
        const val FREQUENCY_UNIVERSAL_PONG = 2500.0  // 2.5kHz (Audible Pong)
        
        // Phase 6: Pilot Tone
        const val FREQUENCY_PILOT = 410.0 // 410Hz Pilot Tone (-18dB)
        
        const val DURATION_MS = 1000
    }

    private var isListening = false
    private var audioRecord: AudioRecord? = null
    // Fix: Reusable AudioTrack to prevent 'Out of AudioTracks' native crash
    private var currentAudioTrack: AudioTrack? = null

    // Drift Tracking
    private var lastPilotPhase = 0.0

    /**
     * Injects a Sine Wave into the Speaker (MEDIA stream, not earpiece).
     * Uses blocking playback to ensure audio completes before returning.
     */
    fun sendPing(freq: Double = FREQUENCY_PING) {
        lastPingTimestamp = System.currentTimeMillis()
        log("sendPing: Generating ${freq}Hz tone...")
        val tone = generateSineWave(freq, DURATION_MS)
        val mixedTone = mixPilotTone(tone) // Mix 410Hz Pilot
        playAudioBlocking(mixedTone)
        log("sendPing: Done.")
    }

    fun sendCompositePing(freq1: Double, freq2: Double) {
        lastPingTimestamp = System.currentTimeMillis()
        log("sendCompositePing: Generating Mixed Layered Tone (${freq1}Hz + ${freq2}Hz)...")
        val tone = generateCompositeSine(freq1, freq2, 300) // 300ms as per spec
        val mixedTone = mixPilotTone(tone) // Mix 410Hz Pilot
        playAudioBlocking(mixedTone)
        log("sendCompositePing: Done.")
    }

    private fun mixPilotTone(original: ShortArray): ShortArray {
        val pilot = generateSineWave(FREQUENCY_PILOT, (original.size * 1000) / SAMPLE_RATE)
        val mixed = ShortArray(original.size)
        // Mixing Ratio: 90% Signal + 10% Pilot (-20dB approx)
        for (i in original.indices) {
            val signal = original[i]
            val pilotSample = (pilot.getOrElse(i) { 0 } * 0.1).toInt()
            mixed[i] = (signal * 0.9 + pilotSample).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return mixed
    }

    private fun trackPilotDrift(buffer: ShortArray) {
        val pilotMag = goertzel(buffer, FREQUENCY_PILOT, SAMPLE_RATE)
        if (pilotMag > 1e7) {
            // Simplified Drift Logic: Just log tracking for prototype.
            // In real DSP, we'd compare Phase Delta.
            log("Pilot Tracker: 410Hz Detected (Mag=${String.format("%.2e", pilotMag)}). Sync OK.")
        }
    }

    /**
     * Plays a tone using Android's built-in ToneGenerator.
     * This is guaranteed to work if the speaker is functional.
     */
    fun playSystemTone(durationMs: Int = 2000) {
        log("playSystemTone: Playing system beep...")
        try {
            val toneGen = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, durationMs)
            Thread.sleep(durationMs.toLong() + 100)
            toneGen.release()
            log("playSystemTone: Done.")
        } catch (e: Exception) {
            log("playSystemTone FAILED", e)
        }
    }

    /**
     * Blocking audio playback using MODE_STATIC.
     * Ensures audio completes before returning.
     */
    private fun playAudioBlocking(data: ShortArray) {
        try {
            // Buffer must be large enough for ALL data (in bytes)
            val bufferSizeBytes = data.size * 2  // ShortArray -> byte count
            
            // Use constructor with MODE_STATIC for short clips
            val audioTrack = AudioTrack(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSizeBytes,
                AudioTrack.MODE_STATIC,  // CRITICAL: Use STATIC mode for short clips
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            // Write ALL data first (required for STATIC mode)
            val written = audioTrack.write(data, 0, data.size)
            log("AudioTrack: wrote $written samples (expected ${data.size})")
            
            if (written <= 0) {
                log("AudioTrack write FAILED: $written", RuntimeException("Write Failed"))
                audioTrack.release()
                return
            }
            
            // Start playback
            audioTrack.play()
            log("AudioTrack: play() called, state=${audioTrack.playState}")
            
            // BLOCK until playback completes
            val durationMs = (data.size.toLong() * 1000) / SAMPLE_RATE
            Thread.sleep(durationMs + 200)
            
            // Cleanup
            audioTrack.stop()
            audioTrack.release()
            log("AudioTrack: released after ${durationMs}ms playback")
            
        } catch (e: Exception) {
            log("playAudioBlocking FAILED", e)
        }
    }

    /**
     * Starts monitoring for a specific Target Frequency.
     */
    fun startListening(targetFreq: Double, onSignalDetected: (Double) -> Unit) {
        if (isListening) return
        isListening = true

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        if (bufferSize <= 0) {
            log("startListening: FAILED - getMinBufferSize returned $bufferSize", RuntimeException("MinBufferSize Failed"))
            isListening = false
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                log("startListening: AudioRecord NOT INITIALIZED! State=${audioRecord?.state}", RuntimeException("AudioRecord Not Initialized"))
                isListening = false
                return
            }
            
            audioRecord?.startRecording()
            log("startListening: AudioRecord started, listening for ${targetFreq}Hz...")

            // Critical Fix: Run blocking audio loop on background thread to avoid Main Thread Freeze/ANR
            Thread {
                val buffer = ShortArray(bufferSize)
                var frameCount = 0
                while (isListening) {
                     val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                     if (read > 0) {
                         if (isSelfBlanking()) {
                             continue
                         }
                         trackPilotDrift(buffer) // Phase 6: Pilot Tracker
                         val magnitude = goertzel(buffer, targetFreq, SAMPLE_RATE)
                         frameCount++
                         
                         // Log every 10th frame to avoid spam
                         if (frameCount % 10 == 0) {
                             log("Listening: frame=$frameCount, mag=${String.format("%.2e", magnitude)} for ${targetFreq}Hz")
                         }
                         
                         // Threshold: 1e8 works for laptop's audio
                         if (magnitude > 1e8) { 
                             log("DETECTED ${targetFreq}Hz! mag=${String.format("%.2e", magnitude)}")
                             muteProximity() 
                             // Callback must be on Main Thread if it interacts with UI/Channels
                             android.os.Handler(android.os.Looper.getMainLooper()).post {
                                 onSignalDetected(targetFreq)
                             }
                             break
                         }
                     }
                }
                log("startListening: Loop exited after $frameCount frames")
            }.start()
            
        } catch (e: Exception) {
            log("startListening: EXCEPTION - ${e.message}", e)
            isListening = false
        }
    }

    /**
     * Monitors for TWO frequencies simultaneously.
     * Returns true/false for each frequency detected.
     */
    fun startDualListening(freq1: Double, freq2: Double, onResult: (Boolean, Boolean) -> Unit) {
        if (isListening) return
        isListening = true

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        audioRecord = record
        
        record.startRecording()
        log("startDualListening: Scanning for ${freq1}Hz AND ${freq2}Hz...")

        Thread {
            val buffer = ShortArray(bufferSize)
            var hits1 = 0
            var hits2 = 0
            
            while (isListening) {
                 val read = record.read(buffer, 0, bufferSize) ?: 0
                 if (read > 0) {
                     if (isSelfBlanking()) {
                         continue
                     }
                     trackPilotDrift(buffer) // Phase 6: Pilot Tracker
                     val mag1 = goertzel(buffer, freq1, SAMPLE_RATE)
                     val mag2 = goertzel(buffer, freq2, SAMPLE_RATE)
                     
                     // 2.2kHz needs slightly higher threshold to reject voice noise?
                     // Using 1e8 for both for now.
                     val detected1 = mag1 > 1e8 
                     val detected2 = mag2 > 1e8
                     
                     if (detected1 || detected2) {
                         if (detected1) hits1++
                         if (detected2) hits2++
                         
                         // If confirmed
                         if (hits1 >= 2 || hits2 >= 2) {
                             log("Dual Detect: F1($freq1)=$detected1, F2($freq2)=$detected2")
                             muteProximity()
                             android.os.Handler(android.os.Looper.getMainLooper()).post {
                                 onResult(detected1, detected2)
                             }
                             break
                         }
                     }
                 }
            }
            log("startDualListening: Loop Finished.")
        }.start()
    }

    fun stopListening() {
        isListening = false
        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            log("stopListening: Exception - ${e.message}", RuntimeException(e))
        }
    }

    /**
     * Immediately lowers call volume to hide the ultrasonic noise from the user.
     */
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var lastPingTimestamp: Long = 0

    /**
     * Mutes the microphone and requests Audio Focus to "Duck" other apps (System Dialer).
     * If focus fails, falls back to Comfort Noise masking.
     */
    fun muteProximity() {
        log("muteProximity: Requesting Audio Focus (DUCK) & Muting Mic...")
        
        try {
            // 1. Mic Mute
            if (!audioManager.isMicrophoneMute) {
                audioManager.isMicrophoneMute = true
            }

            // 2. Audio Focus (Android 8.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                
                val res = audioManager.requestAudioFocus(audioFocusRequest!!)
                if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    log("muteProximity: Audio Focus GRANTED (Ducking)")
                } else {
                    log("muteProximity: Audio Focus DENIED. Engaging Comfort Noise mask.")
                    playComfortNoise()
                }
            } else {
                // Legacy
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            log("muteProximity FAILED", e)
        }
    }

    fun unmuteProximity() {
        log("unmuteProximity: Abandoning Focus & Unmuting...")
        try {
            if (audioManager.isMicrophoneMute) {
                audioManager.isMicrophoneMute = false
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            log("unmuteProximity FAILED", e)
        }
    }

    /**
     * White Noise generator to mask 2.2kHz tone if Ducking fails.
     */
    private fun playComfortNoise() {
        Thread {
            try {
                val durationMs = 1500
                val noise = generateWhiteNoise(durationMs)
                // Play at 10% volume
                playAudioBlocking(noise) 
            } catch (e: Exception) {
                log("playComfortNoise Failed", e)
            }
        }.start()
    }
    
    private fun generateWhiteNoise(durationMs: Int): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs / 1000)
        val sample = ShortArray(numSamples)
        val random = java.util.Random()
        for (i in 0 until numSamples) {
            // Low amplitude noise (approx 5% of max volume)
            sample[i] = ((random.nextDouble() * 2.0 - 1.0) * (Short.MAX_VALUE * 0.05)).toInt().toShort()
        }
        return sample
    }

    fun isBluetoothActive(): Boolean {
        return try {
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) return true
            
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                          it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                          it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if we are in the "Self-Blanking" window (400ms after sending Ping).
     */
    private fun isSelfBlanking(): Boolean {
         return (System.currentTimeMillis() - lastPingTimestamp) < 400
    }



    fun playFSKKey(baseFreq: Double) {
        val shift = 500.0
        
        log("playFSKKey: Generating FSK Key at ${baseFreq}Hz...")
        
        // Simulate a 16-bit Key pattern
        val bits = intArrayOf(1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1, 1, 1, 0) 
        val fskSignal = generateFSK(bits, baseFreq, shift, 100) // 100ms per bit = 1.6s burst
        playAudioBlocking(fskSignal)
        
        log("playFSKKey: Done.")
    }

    private fun playAudio(data: ShortArray) {
        try {
            // Safe Release of previous track
            try {
                if (currentAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    currentAudioTrack?.stop()
                }
                currentAudioTrack?.release()
            } catch (e: Exception) {
                // Ignore release errors
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            currentAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA) // Use MEDIA for Loudspeaker (Not Earpiece)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
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
            
            currentAudioTrack?.write(data, 0, data.size)
            currentAudioTrack?.play()
            
            // Release on background after playback
            Thread {
                try {
                    val durationMs = (data.size.toDouble() / SAMPLE_RATE * 1000).toLong() + 200
                    Thread.sleep(durationMs)
                    currentAudioTrack?.release()
                    currentAudioTrack = null
                } catch (e: Exception) { 
                    log("Track release failed", e)
                }
            }.start()
            
        } catch (e: Exception) {
            log("CRASH PREVENTED: AudioTrack Error", e)
        }
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

    private fun generateCompositeSine(freq1: Double, freq2: Double, durationMs: Int): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs / 1000)
        val sample = ShortArray(numSamples)
        val phaseStep1 = 2.0 * PI * freq1 / SAMPLE_RATE
        val phaseStep2 = 2.0 * PI * freq2 / SAMPLE_RATE
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until numSamples) {
            // Mix and normalize (divide by 2 to avoid clipping)
            val val1 = sin(phase1)
            val val2 = sin(phase2)
            sample[i] = ((val1 + val2) * 0.5 * Short.MAX_VALUE).toInt().toShort()
            
            phase1 += phaseStep1
            phase2 += phaseStep2
        }
        return sample
    }

    private fun generateFSK(bits: IntArray, baseFreq: Double, shift: Double, msPerBit: Int): ShortArray {
        val samplesPerBit = (SAMPLE_RATE * msPerBit / 1000)
        val totalSamples = samplesPerBit * bits.size
        val data = ShortArray(totalSamples)
        
        var phase = 0.0
        var ptr = 0
        
        for (bit in bits) {
            val freq = if (bit == 1) baseFreq + shift else baseFreq
            val phaseStep = 2.0 * PI * freq / SAMPLE_RATE
            
            for (i in 0 until samplesPerBit) {
                data[ptr++] = (sin(phase) * Short.MAX_VALUE).toInt().toShort()
                phase += phaseStep
            }
        }
        return data
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
