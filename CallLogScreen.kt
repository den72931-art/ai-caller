package com.aicaller.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicaller.app.data.AppDatabase
import com.aicaller.app.data.CallOutcome
import com.aicaller.app.data.CallRecord
import java.text.DateFormat
import java.util.Date

@Composable
fun CallLogScreen(
    onRequestPermissions: () -> Unit,
    onRequestDefaultRole: () -> Unit,
    hasAllPermissions: Boolean
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).callRecordDao() }
    val records by dao.observeAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AiCaller", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your AI assistant for incoming calls",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (!hasAllPermissions) {
            SetupCard(
                title = "Grant permissions",
                description = "AiCaller needs phone, contacts, microphone and notification access to screen and answer calls.",
                buttonText = "Grant permissions",
                onClick = onRequestPermissions
            )
            Spacer(Modifier.height(12.dp))
        }

        SetupCard(
            title = "Enable call handling",
            description = "Set AiCaller as your Caller ID & spam app (or default phone app) so it can screen and answer calls on your behalf.",
            buttonText = "Set up",
            onClick = onRequestDefaultRole
        )
        Spacer(Modifier.height(16.dp))

        Text("Recent calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (records.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Text("No calls yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.id }) { record -> CallRecordRow(record) }
            }
        }
    }
}

@Composable
private fun SetupCard(title: String, description: String, buttonText: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun CallRecordRow(record: CallRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(record.displayName ?: record.phoneNumber, fontWeight = FontWeight.Bold)
                Text(outcomeLabel(record.outcome), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                DateFormat.getDateTimeInstance().format(Date(record.startTimeMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            record.aiSummary?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun outcomeLabel(outcome: CallOutcome): String = when (outcome) {
    CallOutcome.ANSWERED_BY_AI -> "AI answered"
    CallOutcome.SCREENED_BLOCKED -> "Blocked"
    CallOutcome.SCREENED_SILENCED -> "Silenced"
    CallOutcome.MISSED -> "Missed"
    CallOutcome.HANDED_TO_USER -> "You answered"
    CallOutcome.FORWARDED -> "Forwarded"
}
