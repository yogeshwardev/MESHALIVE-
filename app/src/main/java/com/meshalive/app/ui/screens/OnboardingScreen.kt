package com.meshalive.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshalive.app.R
import com.meshalive.app.data.Settings
import com.meshalive.app.ui.translate
import com.meshalive.app.ui.theme.HeroGradient
import com.meshalive.app.ui.theme.MeBlue
import com.meshalive.app.ui.theme.Navy
import com.meshalive.app.ui.theme.SafeGreen
import com.meshalive.app.ui.theme.SosRed

private enum class Role { CITIZEN, CARRIER, GATEWAY }

/** First-run flow: what MeshAlive is, language, household, and this phone's role. */
@Composable
fun OnboardingScreen(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    AnimatedContent(
        targetState = step,
        transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
        label = "onboarding",
    ) { s ->
        when (s) {
            0 -> Welcome { step = 1 }
            else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Dots(s)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (s) {
                            1 -> LanguageStep(settings, update)
                            2 -> HouseholdStep(settings, update)
                            else -> RoleStep(settings, update)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { step = s - 1 }) { Text(t(settings, "Back")) }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { if (s < 3) step = s + 1 else update { it.copy(onboarded = true) } },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                        ) { Text(if (s < 3) t(settings, "Next") else t(settings, "Start MeshAlive"), fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

private fun t(s: Settings, en: String) = translate(s.lang, en)

@Composable
private fun Welcome(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(HeroGradient)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(80.dp).background(Color.White, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(100.dp))
                }
                Text("MeshAlive", color = Color.White, style = MaterialTheme.typography.displaySmall)
                Text(
                    "Help reaches you even when the network doesn't.",
                    color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleLarge,
                )
                Feature(Icons.Filled.Hub, "Phones relay your SOS to each other over Bluetooth and Wi-Fi — no towers needed.")
                Feature(Icons.Filled.DirectionsBoat, "Rescue boats and NDRF teams carry messages out of areas with no signal.")
                Feature(Icons.Filled.SatelliteAlt, "The district control room sees you on a live map — and can reply.")
                Feature(Icons.Filled.VerifiedUser, "Free, no account, works in Tamil and English.")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SosRed),
            ) { Text("Get started", style = MaterialTheme.typography.titleMedium) }
            Text(
                "Takes 30 seconds. Set it up now, before you need it.",
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Dots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..3) Box(
            Modifier.height(6.dp).width(if (i == step) 28.dp else 14.dp)
                .background(if (i <= step) SosRed else MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}

@Composable
private fun StepTitle(icon: ImageVector, title: String, sub: String) {
    Icon(icon, null, tint = SosRed, modifier = Modifier.size(36.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LanguageStep(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    StepTitle(Icons.Filled.Translate, "Choose your language", "மொழியைத் தேர்ந்தெடுக்கவும்")
    BigChoice("English", "SOS screens in English", s.lang != "ta", Navy) { update { it.copy(lang = "en") } }
    BigChoice("தமிழ்", "SOS திரைகள் தமிழில்", s.lang == "ta", Navy) { update { it.copy(lang = "ta") } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HouseholdStep(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    var name by rememberSaveable { mutableStateOf(if (s.displayName.startsWith("Phone-")) "" else s.displayName) }
    StepTitle(Icons.Filled.Person, t(s, "About your household"), t(s, "Rescuers see this with every SOS, so they bring the right help."))
    OutlinedTextField(
        name, { name = it; update { st -> st.copy(displayName = it.ifBlank { st.displayName }) } },
        label = { Text(t(s, "Your name")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t(s, "People with you"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { update { it.copy(people = (it.people - 1).coerceAtLeast(1)) } },
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp)) { Text("−", fontSize = 22.sp) }
        Text("${s.people}", modifier = Modifier.width(52.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { update { it.copy(people = (it.people + 1).coerceAtMost(99)) } },
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp)) { Text("+", fontSize = 22.sp) }
    }
    Text(t(s, "Anyone who needs extra help?"), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(t(s, "Elderly"), s.elderly) { on -> update { it.copy(elderly = on) } }
        Chip(t(s, "Children"), s.children) { on -> update { it.copy(children = on) } }
        Chip(t(s, "Disabled"), s.disabled) { on -> update { it.copy(disabled = on) } }
    }
    Text(t(s, "You can change this any time on the Home screen."), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RoleStep(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val role = when {
        s.carrierMode -> Role.CARRIER
        s.gatewayMode -> Role.GATEWAY
        else -> Role.CITIZEN
    }
    StepTitle(Icons.Filled.CellTower, t(s, "How will this phone help?"), t(s, "Every phone relays SOS for others automatically."))
    BigChoice(t(s, "Citizen"), t(s, "Send SOS for my family and relay for neighbours"), role == Role.CITIZEN, SosRed) {
        update { it.copy(carrierMode = false, gatewayMode = false) }
    }
    BigChoice(t(s, "Rescue carrier"), t(s, "Boat, NDRF/SDRF team or ambulance: carry SOS out of dead zones"), role == Role.CARRIER, MeBlue) {
        update { it.copy(carrierMode = true, gatewayMode = false) }
    }
    BigChoice(t(s, "Control-room gateway"), t(s, "Has internet: uploads every SOS to the district dashboard"), role == Role.GATEWAY, SafeGreen) {
        update { it.copy(carrierMode = false, gatewayMode = true) }
    }
}

@Composable
private fun BigChoice(title: String, sub: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.size(24.dp).border(2.dp, if (selected) accent else MaterialTheme.colorScheme.outline, CircleShape).padding(5.dp)
                .background(if (selected) accent else Color.Transparent, CircleShape)
        )
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = on, onClick = { onChange(!on) }, label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SosRed, selectedLabelColor = Color.White),
        shape = RoundedCornerShape(12.dp),
    )
}
