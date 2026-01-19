package com.sigao.sigao_voice.prototypes.messaging

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Async Queue for sending SMS messages.
 * Staggers transmissions by 150ms to prevent Android OS "SMS Rate Limiting"
 * or carrier blocking during Group Fan-out.
 */
class AsyncSmsQueue(private val context: Context) {

    data class SmsRequest(
        val phoneNumber: String,
        val messageBody: String?, // For Text SMS
        val dataPayload: ByteArray?, // For Binary SMS
        val port: Short = 0
    )

    private val queue = ConcurrentLinkedQueue<SmsRequest>()
    private val handler = Handler(Looper.getMainLooper())
    private var isSending = false
    private val sendIntervalMs = 150L

    private val smsManager: SmsManager by lazy {
        context.getSystemService(SmsManager::class.java)
    }

    /**
     * Enqueue a text message for sending.
     */
    fun enqueueSms(phoneNumber: String, message: String) {
        queue.add(SmsRequest(phoneNumber, message, null))
        processQueue()
    }

    /**
     * Enqueue a binary data message for sending.
     */
    fun enqueueDataSms(phoneNumber: String, data: ByteArray, port: Short) {
        queue.add(SmsRequest(phoneNumber, null, data, port))
        processQueue()
    }

    private fun processQueue() {
        if (isSending || queue.isEmpty()) return

        isSending = true
        sendNext()
    }

    private fun sendNext() {
        val request = queue.poll()
        if (request == null) {
            isSending = false
            return
        }

        try {
            if (request.messageBody != null) {
                // Text SMS
                Log.d("SigaoQueue", "Sending SMS to ${request.phoneNumber}")
                smsManager.sendTextMessage(request.phoneNumber, null, request.messageBody, null, null)
            } else if (request.dataPayload != null) {
                // Binary SMS
                Log.d("SigaoQueue", "Sending Data SMS to ${request.phoneNumber} Port ${request.port}")
                smsManager.sendDataMessage(
                    request.phoneNumber, null, request.port, request.dataPayload, null, null
                )
            }
        } catch (e: Exception) {
            Log.e("SigaoQueue", "Failed to send SMS to ${request.phoneNumber}", e)
        }

        // Schedule next send
        handler.postDelayed({
            sendNext()
        }, sendIntervalMs)
    }
}
