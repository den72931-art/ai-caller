package com.aicaller.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aicaller.app.data.AiCallerSettings
import com.aicaller.app.data.ScreeningMode
import com.aicaller.app.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.settingsFlow.collectAsState(initial = AiCallerSettings())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionLabel("Who's calling?")
        OutlinedTextField(
            value = settings.ownerName,
            onValueChange = { new -> scope.launch { repo.update { it.copy(ownerName = new) } } },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("Screening mode")
        ScreeningModeSelector(
            selected = settings.screeningMode,
            onSelect = { mode -> scope.launch { repo.update { it.copy(screeningMode = mode) } } }
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("Greeting")
        OutlinedTextField(
            value = settings.greeting,
            onValueChange = { new -> scope.launch { repo.update { it.copy(greeting = new) } } },
            label = { Text("What the AI says first (use {OWNER_NAME})") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("Block likely spam automatically")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = settings.autoBlockSpamLikely,
                onCheckedChange = { checked -> scope.launch { repo.update { it.copy(autoBlockSpamLikely = checked) } } }
            )
            Spacer(Modifier.width(8.dp))
            Text("Silently reject calls that fail caller-ID verification")
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Record calls")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = settings.recordCalls,
                onCheckedChange = { checked -> scope.launch { repo.update { it.copy(recordCalls = checked) } } }
            )
            Spacer(Modifier.width(8.dp))
            Text("Keep an audio recording in addition to the transcript")
        }
        Text(
            "Check your local laws before enabling this — many places require " +
                "telling or getting consent from the other party before recording a call.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("AI backend")
        OutlinedTextField(
            value = settings.llmEndpoint,
            onValueChange = { new -> scope.launch { repo.update { it.copy(llmEndpoint = new) } } },
            label = { Text("API endpoint") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = settings.llmModel,
            onValueChange = { new -> scope.launch { repo.update { it.copy(llmModel = new) } } },
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = settings.llmApiKey,
            onValueChange = { new -> scope.launch { repo.update { it.copy(llmApiKey = new) } } },
            label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "For production use, don't ship your API key inside the app — proxy requests " +
                "through your own server instead. See README.md.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ScreeningModeSelector(selected: ScreeningMode, onSelect: (ScreeningMode) -> Unit) {
    Column {
        ScreeningModeOption(
            ScreeningMode.OFF, selected, onSelect,
            "Off", "AiCaller doesn't touch your calls"
        )
        ScreeningModeOption(
            ScreeningMode.UNKNOWN_NUMBERS_ONLY, selected, onSelect,
            "Unknown numbers only", "AI answers numbers not in your contacts"
        )
        ScreeningModeOption(
            ScreeningMode.ALL_CALLS, selected, onSelect,
            "All calls", "AI answers every incoming call"
        )
    }
}

@Composable
private fun ScreeningModeOption(
    value: ScreeningMode,
    selected: ScreeningMode,
    onSelect: (ScreeningMode) -> Unit,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Column {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
