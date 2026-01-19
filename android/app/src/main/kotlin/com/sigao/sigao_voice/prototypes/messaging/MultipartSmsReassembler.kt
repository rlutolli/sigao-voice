package com.sigao.sigao_voice.prototypes.messaging

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Reassembles Multipart SMS messages that exceed standard limits.
 * Expected Format: ??SIGAO_PART[Current]/[Total]:[Payload]??
 * Example: ??SIGAO_PART1/3:Base64Data...
 */
class MultipartSmsReassembler {

    data class MessagePart(val index: Int, val total: Int, val payload: String)
    
    // Map<Sender, Map<TransactionId (Implicit/Time), List<Parts>>>
    // For prototype, we'll simplify: One multipart stream per sender at a time.
    private val buffer = ConcurrentHashMap<String, MutableMap<Int, String>>() // Sender -> Index -> Payload
    private val metadata = ConcurrentHashMap<String, Pair<Int, Long>>() // Sender -> (TotalExpected, Timestamp)

    /**
     * Tries to accept a message body.
     * Returns the COMPLETE payload if reassembly finished, or NULL if pending.
     */
    fun processPart(sender: String, body: String): String? {
        if (!body.startsWith("??SIGAO_PART")) return null

        // Parse Header: ??SIGAO_PART1/3:
        val regex = Regex("\\?\\?SIGAO_PART(\\d+)/(\\d+):")
        val match = regex.find(body) ?: return null
        
        val (currentStr, totalStr) = match.destructured
        val current = currentStr.toInt()
        val total = totalStr.toInt()
        
        val payload = body.substring(match.range.last + 1)
        
        Log.d("SigaoReassembler", "Received Part $current/$total from $sender")

        // Check if new stream started (simplified logic)
        val currentMeta = metadata[sender]
        if (currentMeta == null || System.currentTimeMillis() - currentMeta.second > 30_000L) {
            // New Stream
            buffer[sender] = mutableMapOf()
            metadata[sender] = Pair(total, System.currentTimeMillis())
        }

        // Store Part
        buffer[sender]?.put(current, payload)

        // Check Completion
        val parts = buffer[sender]!!
        if (parts.size == total) {
            Log.d("SigaoReassembler", "Reassembly COMPLETE for $sender")
            
            // Reconstruct
            val sb = StringBuilder()
            for (i in 1..total) {
                sb.append(parts[i])
            }
            
            // Cleanup
            buffer.remove(sender)
            metadata.remove(sender)
            
            return sb.toString()
        }

        return null
    }
}
