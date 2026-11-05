package com.meshalive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meshalive.app.data.MsgStatus
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.LowBatteryOrange
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.SosRed

/** The app's standard card: white on the paper background, soft shadow, generous radius. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F0B1F3A))
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun StatusChip(status: String) {
    val color = when (status) {
        MsgStatus.DELIVERED -> GatewayGreen
        MsgStatus.RELAYING, MsgStatus.CARRIED -> MeBlue
        MsgStatus.QUEUED -> LowBatteryOrange
        MsgStatus.FAILED -> SosRed
        else -> Color(0xFF546E7A)
    }
    Row(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(status, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}

/** A warning banner with an action, used for Bluetooth / location / permission problems. */
@Composable
fun WarningBanner(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val amber = Color(0xFFB45309)
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFFF7E6), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFFCD9A0), RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WarningAmber, null, tint = amber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7C4A03))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = amber, style = MaterialTheme.typography.labelLarge) }
    }
}
