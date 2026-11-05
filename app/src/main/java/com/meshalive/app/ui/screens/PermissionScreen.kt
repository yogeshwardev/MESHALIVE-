package com.meshalive.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshalive.app.ui.PermissionInfo
import com.meshalive.app.ui.components.Chip
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.SosRed

@Composable
fun PermissionScreen(
    groups: List<PermissionInfo>,
    grantedCheck: (PermissionInfo) -> Boolean,
    askedOnce: Boolean,
    onRequest: () -> Unit,
    onContinueLimited: () -> Unit,
) {
    val context = LocalContext.current
    val requiredMissing = groups.any { it.required && !grantedCheck(it) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Welcome to MeshAlive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SosRed)
        Text(
            "When towers and internet are down, MeshAlive passes your SOS from phone to phone until it reaches someone online. " +
                "To do that it needs a few permissions:",
        )
        groups.forEach { g ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (grantedCheck(g)) Chip("Granted", GatewayGreen)
                    else Chip(if (g.required) "Required" else "Optional", if (g.required) SosRed else MaterialTheme.colorScheme.secondary)
                }
                Text(g.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Grant permissions") }
        if (askedOnce && requiredMissing) {
            Text(
                "Some required permissions were denied. If Android no longer shows the prompt, enable them in App settings.",
                color = SosRed, style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Open app settings") }
        }
        if (askedOnce) TextButton(onClick = onContinueLimited, modifier = Modifier.fillMaxWidth()) {
            Text(if (requiredMissing) "Continue without mesh (simulator only)" else "Continue")
        }
    }
}
