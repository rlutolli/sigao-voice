package com.sigao.sigao_voice.prototypes.logic

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.sigao.sigao_voice.prototypes.messaging.RcsTunnelHandler
import com.sigao.sigao_voice.prototypes.messaging.AsyncSmsQueue
import java.util.UUID

/**
 * Manages "Virtual Groups" (Client-Side) and Hybrid Fan-out.
 * Handles:
 * 1. Stealth Invites (??SIGAO_GRP_JOIN??)
 * 2. Hybrid Delivery (RCS Broadcast vs SMS Fan-out)
 */
class GroupOrchestrator(
    private val context: Context,
    private val rcsHandler: RcsTunnelHandler,
    private val smsQueue: AsyncSmsQueue
) {

    data class GroupMember(val phoneNumber: String, val hasRcs: Boolean)
    
    // In-memory Group Store (GroupId -> List<Member>)
    private val groups = mutableMapOf<String, List<GroupMember>>()

    /**
     * Alice creates a group -> Sends Stealth Invites.
     */
    fun createGroup(members: List<String>): String {
        val groupId = UUID.randomUUID().toString()
        
        // Check capabilities for all members (ASYNC in real app, mocked here)
        val groupMembers = members.map { phone ->
            // Mock: Ends with 5 or 0 = RCS
            val isRcs = phone.endsWith("5") || phone.endsWith("0")
            GroupMember(phone, isRcs)
        }
        
        groups[groupId] = groupMembers
        Log.d("SigaoGroup", "Created Group $groupId with ${members.size} members")

        // Send Stealth Invites
        groupMembers.forEach { member ->
            sendStealthInvite(member, groupId)
        }
        
        return groupId
    }

    private fun sendStealthInvite(member: GroupMember, groupId: String) {
        val invitePayload = "??SIGAO_GRP_JOIN:{" +
                "\"id\":\"$groupId\"," +
                "\"key\":\"MOCK_KEY_PKG\"" +
                "}??"
        
        if (member.hasRcs) {
            // RCS Tunnel Invite
            rcsHandler.sendTunnelMessage(member.phoneNumber, invitePayload.toByteArray())
        } else {
            // SMS Invite via Queue
            Log.d("SigaoGroup", "Queueing SMS Invite for ${member.phoneNumber}")
            smsQueue.enqueueSms(member.phoneNumber, invitePayload)
        }
    }

    /**
     * Broadcasts a message to the group.
     * Uses Hybrid Fan-out strategies.
     */
    fun broadcastMessage(groupId: String, messagePlaintext: String) {
        val members = groups[groupId] ?: return
        
        Log.d("SigaoGroup", "Broadcasting to Group $groupId: $messagePlaintext")

        // 1. Encrypt Payload (Mock)
        val encryptedPayload = "ENC($messagePlaintext)".toByteArray()

        // 2. Hybrid Split
        members.forEach { member ->
            if (member.hasRcs) {
                // RCS Path (MLS / TreeKEM would go here)
                rcsHandler.sendTunnelMessage(member.phoneNumber, encryptedPayload)
            } else {
                // SMS Path (Sender Key Fan-out)
                // Use Queue
                val base64Payload = android.util.Base64.encodeToString(encryptedPayload, android.util.Base64.NO_WRAP)
                smsQueue.enqueueSms(member.phoneNumber, "SIGAO_MSG:$base64Payload")
                 Log.d("SigaoGroup", "Queued SMS Payload for ${member.phoneNumber}")
            }
        }
    }
}
