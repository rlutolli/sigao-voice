package com.sigao.sigao_voice.prototypes.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log

/**
 * Intercepts incoming SMS messages to detect SIGAO protocol methods.
 * Handles:
 * 1. Silent Handshakes (??SIGAO_v1??)
 * 2. Group Invites (??SIGAO_GRP_JOIN??)
 * 3. Multipart Reassembly (??SIGAO_PARTx/y??)
 */

class SmsReceiver : BroadcastReceiver() {
    private val reassembler = MultipartSmsReassembler()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<Any>?
                val format = bundle.getString("format")
                
                pdus?.forEach { pdu ->
                    val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                    val sender = sms.originatingAddress ?: "UNKNOWN"
                    val body = sms.messageBody ?: ""

                    if (body.startsWith("??SIGAO_PART")) {
                        // Handle Reassembly
                        val completeBody = reassembler.processPart(sender, body)
                        if (completeBody != null) {
                            Log.d("SigaoSMS", "Reassembled Full Payload from $sender")
                            processSigaoMessage(context, sender, completeBody)
                        }
                    } else if (isSigaoProtocol(body)) {
                        Log.d("SigaoSMS", "Intercepted SIGAO Protocol Message from $sender")
                        processSigaoMessage(context, sender, body)
                    }
                }
            }
        }
    }

    private fun isSigaoProtocol(body: String): Boolean {
        return body.startsWith("??SIGAO_v1??") || 
               body.startsWith("??SIGAO_GRP_JOIN??")
    }

    private fun processSigaoMessage(context: Context, sender: String, body: String) {
        // Forwarding logic to MessagingOrchestrator
        Log.i("SigaoSMS", "Processing SIGAO payload: ${body.take(20)}...")
        // Instance retrieval would go here in prod
    }
}
