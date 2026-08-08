package com.audioninja.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audioninja.app.ui.theme.NeonRed

@Composable
fun AboutScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("About", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Audio Ninja", style = MaterialTheme.typography.headlineMedium, color = NeonRed)
            Text("Internal Audio Recorder", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Version 1.0.0\n\nCapture internal audio from any app where supported. " +
                    "High quality. No interruptions. Built for power users.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
