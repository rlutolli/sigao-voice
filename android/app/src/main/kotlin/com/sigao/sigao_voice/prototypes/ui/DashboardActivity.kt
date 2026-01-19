package com.sigao.sigao_voice.prototypes.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main Entry Point for the "Security Dashboard".
 * Hosted in a Compose-enabled Activity with FLAG_SECURE.
 */
class DashboardActivity : ComponentActivity() {

    private val viewModel: SecurityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Prohibit Screenshots (Privacy)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme() // Force Dark Mode for "Hacker" aesthetic
            ) {
                SecurityDashboard(viewModel)
            }
        }
    }
}

@Composable
fun SecurityDashboard(viewModel: SecurityViewModel) {
    val shieldState by viewModel.shieldState.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    Scaffold(
        topBar = { ShieldPulseHeader(shieldState, onPanicTrigger = { viewModel.triggerPanic() }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ContactTrustMap(contacts)
        }
    }
}

@Composable
fun ShieldPulseHeader(state: SecurityViewModel.PulseState, onPanicTrigger: () -> Unit) {
    // Animation Logic
    val pulseColor by animateColorAsState(
        targetValue = when (state) {
            SecurityViewModel.PulseState.SECURE -> Color(0xFF00FF00) // Hacker Green
            SecurityViewModel.PulseState.HANDSHAKE -> Color(0xFFFFD700) // Gold
            SecurityViewModel.PulseState.OFFLINE -> Color(0xFFFF0000) // Red
            SecurityViewModel.PulseState.PANIC -> Color.Red // Flashing Red handled by separate anim
        },
        animationSpec = tween(1000)
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .pointerInput(Unit) {
                // Stealth Panic Trigger: Triple Tap
                detectTapGestures(
                    onDoubleTap = { /* No-op */ },
                    onTap = { /* No-op */ },
                    onPress = { /* No-op */ },
                    onLongPress = { /* No-op */ }
                )
                // Note: detectTapGestures doesn't natively support triple tap easily in one modifier block 
                // without custom logic, simplifying to Double Tap for prototype or Long Press
                // Real implementation would use custom gesture detector.
            }
            .clickable { 
                // Simulating Triple Tap with a simple click for prototype ease
                // In prod: Logic for 3 taps in 500ms
                onPanicTrigger()
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Shield Status",
                tint = pulseColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = when (state) {
                    SecurityViewModel.PulseState.SECURE -> "SYSTEM SECURE"
                    SecurityViewModel.PulseState.HANDSHAKE -> "NEGOTIATING KEYS..."
                    SecurityViewModel.PulseState.OFFLINE -> "PROTECTION DISABLED"
                    SecurityViewModel.PulseState.PANIC -> "INITIATING WIPE..."
                },
                style = MaterialTheme.typography.titleLarge,
                color = pulseColor
            )
        }
    }
}

@Composable
fun ContactTrustMap(contacts: List<SecurityViewModel.ContactShield>) {
    LazyColumn {
        items(contacts) { contact ->
            ContactItem(contact)
        }
    }
}

@Composable
fun ContactItem(contact: SecurityViewModel.ContactShield) {
    val shieldLevel = contact.getShieldLevel()
    val shieldColor = if (shieldLevel >= 1.0f) Color.Green else if (shieldLevel > 0) Color.Yellow else Color.Gray

    ListItem(
        headlineContent = { Text(contact.name) },
        supportingContent = { Text(contact.phoneNumber) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (shieldLevel >= 1.0f) Icons.Default.Lock else Icons.Default.Warning,
                    contentDescription = null,
                    tint = shieldColor
                )
            }
        },
        trailingContent = {
            // Trust Score Indicator
            CircularProgressIndicator(
                progress = shieldLevel,
                color = shieldColor,
                modifier = Modifier.size(24.dp)
            )
        }
    )
}
