package com.sigao.sigao_voice.prototypes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SecurityVaultScreen(viewModel: SecurityViewModel) {
    val config by viewModel.vaultConfig.collectAsState()
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Security Vault", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Ephemeral Toggle
        Text("Ephemeral Messaging", style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = config.isEphemeral,
            onCheckedChange = { viewModel.updateVaultConfig(it, config.ephemeralDurationHours) }
        )
        Text(
            text = if (config.isEphemeral) "Messages auto-delete after ${config.ephemeralDurationHours} hours" else "Messages are persistent",
            style = MaterialTheme.typography.bodySmall
        )
        
        if (config.isEphemeral) {
            Slider(
                value = config.ephemeralDurationHours.toFloat(),
                onValueChange = { viewModel.updateVaultConfig(true, it.toInt()) },
                valueRange = 1f..72f,
                steps = 71
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Mnemonic Reveal
        var isMnemonicVisible by remember { mutableStateOf(false) }
        Button(onClick = { isMnemonicVisible = !isMnemonicVisible }) {
            Icon(Icons.Default.Visibility, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isMnemonicVisible) "Hide Identity Key" else "Reveal Identity Key")
        }
        
        if (isMnemonicVisible) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "abandon ability able about above absent absorb abstract absurd abuse access accident",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Panic Config
        Text("Panic Trigger", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { /* Navigate to Gesture Config */ },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text("Configure 'Nuke' Gesture", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
