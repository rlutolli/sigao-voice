package com.sigao.sigao_voice

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel

import com.sigao.sigao_voice.prototypes.ui.StealthMode

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.sigao.voice/role"
    private val REQUEST_ROLE_CODE = 1

    private var methodChannel: MethodChannel? = null
    private var secureLinkChannel: MethodChannel? = null
    private var lastSecureLinkConfig: Map<String, Any?>? = null
    
    // Log Streaming
    private val LOG_CHANNEL_NAME = "com.sigao.voice/logs"
    private var logSink: EventChannel.EventSink? = null
    private val pendingLogs = java.util.ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Activate "Screen Shield" (Prevent Screenshots / Recents Preview)
        StealthMode.applyScreenShield(this)
        
        // Show over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private var audioTransceiver: com.sigao.sigao_voice.prototypes.logic.AudioTransceiver? = null

    private fun ensureTransceiverReady(): com.sigao.sigao_voice.prototypes.logic.AudioTransceiver {
        if (audioTransceiver == null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioTransceiver = com.sigao.sigao_voice.prototypes.logic.AudioTransceiver(audioManager)
            audioTransceiver!!.onLog = { msg ->
                runOnUiThread {
                    logSink?.success(msg) ?: pendingLogs.add(msg)
                }
            }
        }
        return audioTransceiver!!
    }

    private fun secureLinkConfigFrom(intent: Intent?): Map<String, Any?>? {
        val role = intent?.getStringExtra("slrole") ?: return null
        return mapOf(
            "role" to role,
            "host" to (intent.getStringExtra("slhost") ?: "127.0.0.1"),
            "port" to intent.getIntExtra("slport", 7100),
            "frames" to intent.getIntExtra("slframes", 5)
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() current so getConfig() reflects the latest launch.
        setIntent(intent)

        // If launched/redelivered with secure-link extras, (re)run the test
        // screen even on a warm start (singleTop -> onNewIntent).
        val cfg = secureLinkConfigFrom(intent)
        if (cfg != null) {
            runOnUiThread { secureLinkChannel?.invokeMethod("runConfig", cfg) }
        }
        
        val transceiver = ensureTransceiverReady()
        
        when (intent.action) {
            "com.sigao.voice.PLAY_SYSTEM_TONE" -> {
                // Use Android's built-in ToneGenerator (guaranteed to work)
                android.util.Log.d("SigaoGhost", "PLAY_SYSTEM_TONE: Using ToneGenerator...")
                transceiver.playSystemTone(2000)
                android.util.Log.d("SigaoGhost", "PLAY_SYSTEM_TONE: Done!")
            }
            "com.sigao.voice.PLAY_TONE" -> {
                // Test our AudioTrack implementation
                android.util.Log.d("SigaoGhost", "PLAY_TONE: Playing 3kHz for 3 seconds...")
                // Play 3 times for 1 second each to make it obvious
                transceiver.sendPing(3000.0)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    transceiver.sendPing(3000.0)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        transceiver.sendPing(3000.0)
                        android.util.Log.d("SigaoGhost", "PLAY_TONE: Done!")
                    }, 1200)
                }, 1200)
            }
            "com.sigao.voice.START_CALL" -> {
                android.util.Log.d("SigaoGhost", "Received ADB Intent: START_CALL (Alice Mode)")
                
                transceiver.stopListening()
                
                // FULL Alice Handshake Sequence:
                // 1. Send Ping (3kHz)
                android.util.Log.d("SigaoGhost", "[Alice Step 1] Sending 3kHz Ping...")
                transceiver.sendPing(com.sigao.sigao_voice.prototypes.logic.AudioTransceiver.FREQUENCY_ROBUST)
                
                // 2. Listen for Pong (3.5kHz)
                android.util.Log.d("SigaoGhost", "[Alice Step 2] Listening for 3.5kHz Pong...")
                transceiver.startListening(com.sigao.sigao_voice.prototypes.logic.AudioTransceiver.FREQUENCY_PONG_ROBUST) { _ ->
                    android.util.Log.d("SigaoGhost", "[Alice Step 2] DETECTED 3.5kHz PONG!")
                    transceiver.stopListening()
                    
                    // 3. Send Alice Key (4kHz FSK) - RUN ON BACKGROUND THREAD to avoid ANR
                    Thread {
                        try {
                            Thread.sleep(500) // Small delay after detecting Pong
                            android.util.Log.d("SigaoGhost", "[Alice Step 3] About to call playFSKKey(4000.0)...")
                            transceiver.playFSKKey(4000.0) // isAlice=true
                            android.util.Log.d("SigaoGhost", "[Alice Step 3] playFSKKey(4000.0) completed!")
                            
                            // 4. Listen for Bob Key (5kHz) - wait for our FSK to finish first
                            Thread.sleep(2000)
                            android.util.Log.d("SigaoGhost", "[Alice Step 4] Listening for Bob Key (5kHz)...")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                transceiver.startListening(5000.0) { _ ->
                                    android.util.Log.d("SigaoGhost", "[Alice Step 4] DETECTED 5kHz BOB KEY! HANDSHAKE COMPLETE!")
                                    transceiver.stopListening()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SigaoGhost", "[Alice Step 3-4] EXCEPTION: ${e.message}", e)
                        }
                    }.start()
                }
            }
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // 0. Log Streaming Logic
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, LOG_CHANNEL_NAME).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    logSink = events
                    // Flush pending logs
                    for (log in pendingLogs) {
                        events?.success(log)
                    }
                    pendingLogs.clear()
                }
                override fun onCancel(arguments: Any?) {
                    logSink = null
                }
            }
        )

        // 1. Role Logic
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "requestRole") {
                requestRole()
                result.success(true)
            } else {
                result.notImplemented()
            }
        }
        
        // 2. KeyStore Logic
        val paramKeyStore = KeyStoreService(this)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.sigao.voice/keystore").setMethodCallHandler { call, result ->
            when (call.method) {
                "generate" -> {
                    val success = paramKeyStore.generateIdentityKey()
                    result.success(success)
                }
                "getPublicKey" -> {
                    result.success(paramKeyStore.getPublicKey())
                }
                "sign" -> {
                    val data = call.argument<ByteArray>("data")
                    if (data != null) {
                        result.success(paramKeyStore.signData(data))
                    } else {
                        result.error("INVALID_ARGS", "Data is null", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
        // 2b. Secure Link test config (read launch-intent extras for headless driving)
        secureLinkChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.sigao.voice/securelink")
        secureLinkChannel!!.setMethodCallHandler { call, result ->
            when (call.method) {
                "getConfig" -> result.success(secureLinkConfigFrom(intent))
                else -> result.notImplemented()
            }
        }

        // 3. Ghost Handshake (Fixed Threading)        // Ensure the transceiver is ready (lazy init)
        val transceiver = ensureTransceiverReady()
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.sigao.voice/handshake").setMethodCallHandler { call, result ->
             when (call.method) {
                 "startListener" -> { // Bob Mode
                     android.util.Log.d("SigaoGhost", "Starting Listener (Bob)...")
                     // 1. Listen for 3kHz Ping
                     transceiver.startListening(com.sigao.sigao_voice.prototypes.logic.AudioTransceiver.FREQUENCY_ROBUST) { freq ->
                         // Callback is on Main Thread
                         android.util.Log.d("SigaoGhost", "DETECTED 3kHz PING! Sending Pong...")
                         transceiver.stopListening()
                         
                         Thread {
                             Thread.sleep(500) // Debounce
                             // 2. Send 3.5kHz Pong (Blocking)
                             transceiver.sendPing(com.sigao.sigao_voice.prototypes.logic.AudioTransceiver.FREQUENCY_PONG_ROBUST)
                             android.util.Log.d("SigaoGhost", "Pong Sent. Listering for Alice Key...")
                             
                             android.os.Handler(android.os.Looper.getMainLooper()).post {
                                 // 3. Listen for Alice Key (4kHz)
                                 transceiver.startListening(4000.0) { _ ->
                                     android.util.Log.d("SigaoGhost", "DETECTED ALICE KEY! Sending Bob Key...")
                                     transceiver.stopListening()
                                     
                                     Thread {
                                         Thread.sleep(500)
                                         // 4. Send Bob Key (5kHz) (Blocking)
                                         transceiver.playFSKKey(5000.0) 
                                     }.start()
                                 }
                             }
                         }.start()
                     }
                     result.success(true)
                 }
                 "startCall" -> { // Alice Mode
                     android.util.Log.d("SigaoGhost", "Starting Call (Alice)...")
                     Thread {
                         // 1. Send 3kHz Ping (Blocking)
                         transceiver.sendPing(com.sigao.sigao_voice.prototypes.logic.AudioTransceiver.FREQUENCY_ROBUST)
                         
                         android.os.Handler(android.os.Looper.getMainLooper()).post {
                             // 2. Listen for 3.5kHz Pong
                             transceiver.startListening(com.sigao.sigao_voice.prototypes.logic.AudioTransceiver.FREQUENCY_PONG_ROBUST) { _ ->
                                 android.util.Log.d("SigaoGhost", "DETECTED PONG! Sending Alice Key...")
                                 transceiver.stopListening()
                                 
                                 Thread {
                                     Thread.sleep(500)
                                     // 3. Send Alice Key (4kHz) (Blocking)
                                     transceiver.playFSKKey(4000.0)
                                     
                                     Thread.sleep(1500) // Wait for playback
                                     android.util.Log.d("SigaoGhost", "Key Sent. Listening for Bob Key...")
                                     
                                     android.os.Handler(android.os.Looper.getMainLooper()).post {
                                         // 4. Listen for Bob Key (5kHz)
                                         transceiver.startListening(5000.0) { _ ->
                                             android.util.Log.d("SigaoGhost", "DETECTED BOB KEY! HANDSHAKE COMPLETE.")
                                             transceiver.stopListening()
                                         }
                                     }
                                 }.start()
                             }
                         }
                     }.start()
                     result.success(true)
                 }
                 "stopListener" -> {
                     transceiver.stopListening()
                     result.success(true)
                 }
                 else -> result.notImplemented()
             }
        }
    }

    private fun requestRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    // Already held
                    return
                }
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivityForResult(intent, REQUEST_ROLE_CODE)
            }
        }
    }
}
