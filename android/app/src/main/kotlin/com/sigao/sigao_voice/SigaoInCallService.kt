package com.sigao.sigao_voice

import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import android.media.AudioDeviceInfo

class SigaoInCallService : InCallService() {

    private val TAG = "SigaoInCallService"

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: ${call.details.handle}")
        
        // Register callback to track state changes
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                Log.d(TAG, "onStateChanged: $state")
                
                if (state == Call.STATE_ACTIVE) {
                    performHandshakeInit()
                }
            }
        })

        // Notify Flutter UI (Launch Activity if needed)
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra("incoming_call", true)
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved")
    }

    private fun performHandshakeInit() {
        Log.d(TAG, "Attempting Handshake Initialization...")
        
        // 1. Lock Audio State (Request Focus / Mode)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // 2. Find Virtual Device (Bluetooth SCO Bridge)
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var targetDevice: AudioDeviceInfo? = null
        
        for (device in devices) {
            // In Phase 5/6 we will look for specific TYPE_BLUETOOTH_SCO or TYPE_BUS
            // For now, looking for Earpiece as fallback per plan
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                targetDevice = device
            }
        }

        // 3. Set Communication Device
        if (targetDevice != null) {
            val result = audioManager.setCommunicationDevice(targetDevice)
            Log.d(TAG, "setCommunicationDevice result: $result (Device: ${targetDevice.type})")
            
            // 4. Trigger Native DSP (Stub for now)
            // SigaoNative.startHandshake() 
        } else {
            Log.w(TAG, "No suitable audio device found for Secure Handshake")
        }
    }
}
