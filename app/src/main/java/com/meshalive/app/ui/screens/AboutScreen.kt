package com.meshalive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshalive.app.ui.components.SectionCard
import com.meshalive.app.ui.theme.GatewayGreen
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.SosRed

@Composable
fun AboutScreen() {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MeshAlive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = SosRed)
        Text(
            "India's offline disaster-response network. When cyclones and floods take down towers, an SOS still reaches the district control room, and help gets back to the person who asked for it.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("₹0", "extra hardware", Modifier.weight(1f))
            Stat("147×", "fewer radio sends than flooding", Modifier.weight(1f))
            Stat("2-way", "SOS out, help back in", Modifier.weight(1f))
        }

        SectionCard(title = "How it works") {
            Step(1, SosRed, "Ask for help", "One tap sends an SOS with GPS and a situation report: people, trapped, injured, elderly, children, water level. Works in Tamil.")
            Step(2, MeBlue, "Phones relay it", "Nearby phones pass it on over Bluetooth and Wi-Fi, choosing paths that avoid phones with low battery. The most urgent SOS always goes first.")
            Step(3, Color(0xFF6A1B9A), "Rescuers carry it", "Phones on rescue boats, NDRF teams and ambulances pick up SOS messages that have no route and carry them out of the dead zone.")
            Step(4, GatewayGreen, "Control room sees it", "The first phone with signal uploads everything it carries. The district dashboard shows every SOS live on a map, sorted by urgency.")
            Step(5, SosRed, "Help comes back", "The control room dispatches a team or broadcasts an evacuation alert; the message travels back through the mesh to phones that have no signal.")
        }

        SectionCard(title = "Why it is different") {
            Bullet("Bridgefy and Briar are chat apps. MeshAlive is a response system: triage, rescue carriers, a control-room dashboard and messages back to victims.")
            Bullet("Meshtastic and goTenna need a ₹2,500+ radio per person. MeshAlive runs on the phones people already own.")
            Bullet("Battery-aware routing keeps the weakest phones alive, so the network lasts longer when power is out for days.")
            Bullet("Control-room commands are signed, so nobody can inject a fake evacuation order.")
        }

        SectionCard(title = "Privacy and safety") {
            Bullet("Message text is encrypted on the phone (AES-GCM) before it leaves; relays forward it without reading it.")
            Bullet("Only the urgency score travels in clear, so relays can prioritise without seeing personal details.")
            Bullet("No account, no phone number, no server needed for the mesh itself.")
        }

        Text(
            "Built for the SevaFirst Innovation Challenge · Theme: Surakshit Bharat",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(big: String, small: String, modifier: Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(big, fontSize = 24.sp, fontWeight = FontWeight.Black, color = SosRed)
        Text(small, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Step(n: Int, color: Color, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ", fontWeight = FontWeight.Bold, color = SosRed)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
