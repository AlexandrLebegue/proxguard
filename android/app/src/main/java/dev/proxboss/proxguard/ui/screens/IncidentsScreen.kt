package dev.proxboss.proxguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.proxguard.data.alerts.AlertEvent
import dev.proxboss.proxguard.data.alerts.AlertSeverity
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.ConfirmDialog
import dev.proxboss.proxguard.ui.components.EmptyState
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.util.formatDate
import dev.proxboss.proxguard.util.formatRelative

@Composable
fun IncidentsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshIncidents() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Historique",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${incidents.size} incident${if (incidents.size > 1) "s" else ""} déclenché" +
                            if (incidents.size > 1) "s" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (incidents.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Effacer l'historique",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (incidents.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Aucun incident déclenché",
                    message = "Les alertes qui franchissent un seuil apparaîtront ici, " +
                        "avec leur horodatage et leur niveau de gravité.",
                )
            }
        } else {
            items(incidents, key = { "${it.key}-${it.timestamp}" }) { event ->
                IncidentCard(event)
            }
            item {
                Text(
                    text = "Les ${dev.proxboss.proxguard.data.alerts.AlertStore.MAX_HISTORY} derniers incidents " +
                        "sont conservés localement sur cet appareil.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Effacer l'historique ?",
            message = "Toutes les alertes déclenchées seront supprimées. Les règles et seuils sont conservés.",
            confirmLabel = "Effacer",
            destructive = true,
            onConfirm = { viewModel.clearIncidents() },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun IncidentCard(event: AlertEvent) {
    val color = when (event.severity) {
        AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        AlertSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    val icon = when (event.severity) {
        AlertSeverity.CRITICAL -> Icons.Rounded.ErrorOutline
        AlertSeverity.WARNING -> Icons.Rounded.WarningAmber
        AlertSeverity.INFO -> Icons.Rounded.Info
    }
    PgCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = event.severity.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "${formatDate(event.timestamp)} · ${formatRelative(event.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
