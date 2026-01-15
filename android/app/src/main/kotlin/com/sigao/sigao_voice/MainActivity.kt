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

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.sigao.voice/role"
    private val REQUEST_ROLE_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
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
