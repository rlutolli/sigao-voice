package com.sigao.sigao_voice.prototypes.logic

import android.util.Log

/**
 * State Machine for the SIGAO Zero-Touch Handshake.
 * Defines the strict sequence of events for a secure connection.
 */
class HandshakeState {

    enum class State {
        IDLE,             // Call not started or effectively ended
        STABILIZING,      // Call Answered, waiting 500ms
        SIGNAL_CHECK,     // Measuring noise floor (100ms)
        LISTENING_FOR_PING, // Bob: Waiting for Alice
        SENDING_PING,     // Alice: sending initial ping
        SENDING_PONG,     // Bob: replying
        MUTED_EXCHANGE,   // Audio Muted, exchanging keys (FSK)
        SECURE,           // Handshake success, line unmutes
        FAILED            // Timeout or error
    }

    private var currentState = State.IDLE
    private val TAG = "SigaoHandshakeState"

    @Synchronized
    fun transitionTo(newState: State) {
        if (!isValidTransition(currentState, newState)) {
            Log.w(TAG, "Invalid Transition: $currentState -> $newState")
            return
        }
        
        Log.i(TAG, "State Transition: $currentState -> $newState")
        currentState = newState
    }

    @Synchronized
    fun getCurrentState(): State {
        return currentState
    }

    fun reset() {
        currentState = State.IDLE
    }

    private fun isValidTransition(from: State, to: State): Boolean {
        // Simplified validation logic
        if (to == State.IDLE || to == State.FAILED) return true
        
        return when (from) {
            State.IDLE -> to == State.STABILIZING
            State.STABILIZING -> to == State.SIGNAL_CHECK
            State.SIGNAL_CHECK -> to == State.SENDING_PING || to == State.LISTENING_FOR_PING
            State.SENDING_PING -> to == State.MUTED_EXCHANGE // Alice: Ping sent -> wait for pong (handled in logic as listening part of exchange?)
            // Actually Alice sends Ping, then Enters Listening Mode for Pong. 
            // Let's refine: Alice: Sending Ping -> Listening for Pong (which might not be a state strictly but part of logic)
            // Let's keep it simple for now.
            State.LISTENING_FOR_PING -> to == State.SENDING_PONG
            State.SENDING_PONG -> to == State.MUTED_EXCHANGE
            State.MUTED_EXCHANGE -> to == State.SECURE
            else -> true // Allow loose transitions for prototype
        }
    }
}
