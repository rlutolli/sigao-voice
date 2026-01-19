package com.sigao.sigao_voice.prototypes.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * In-Call HUD Overlay.
 * Designed to be shown via a dedicated WindowManager layer or Picture-in-Picture.
 * Features "Resilience Meter" (Waveform) and Encryption Status.
 */
@Composable
fun InCallSecurityOverlay(isSecure: Boolean, signalStability: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lock Animation
            LockIcon(isSecure)
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Resilience Meter (Waveform)
            ResilienceMeter(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                stability = signalStability
            )
        }
    }
}

@Composable
fun LockIcon(isSecure: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (isSecure) 1f else 0.3f,
        animationSpec = tween(500)
    )
    
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = "Encryption Status",
        tint = if (isSecure) Color.Green else Color.Yellow,
        modifier = Modifier
            .size(48.dp)
            .run { if (isSecure) this else this } // Simplified for prototype
    )
}

@Composable
fun ResilienceMeter(modifier: Modifier, stability: Float) { // 0.0 - 1.0 (1.0 = Perfect Stable)
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val amplitude = height / 3f * stability // More stable = larger reliable Amplitude? Or Jitter?
        // Let's say Stability 1.0 = Clean Sine Wave. Stability 0.2 = Jittery.
        // For visualization: 
        // 1.0 (Secure) = Steady Green Line with gentle pulse
        // 0.2 (Weak) = Erratic Red Line
        
        val color = if (stability > 0.8) Color.Green else if (stability > 0.4) Color.Yellow else Color.Red
        
        val points = mutableListOf<Offset>()
        for (x in 0..width.toInt() step 5) {
            val normalizedX = x / width
            // Simple Sine Wave for Prototype
            val y = height / 2 + sin(normalizedX * 10f + phase) * amplitude
            points.add(Offset(x.toFloat(), y))
        }

        for (i in 0 until points.size - 1) {
            drawLine(
                color = color,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 4.dp.toPx()
            )
        }
    }
}
