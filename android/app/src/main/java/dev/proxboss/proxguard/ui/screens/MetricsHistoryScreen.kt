package dev.proxboss.proxguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.proxguard.data.history.MetricSample
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.ConfirmDialog
import dev.proxboss.proxguard.ui.components.EmptyState
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.ui.components.Sparkline
import dev.proxboss.proxguard.util.formatBytesPerSecond
import dev.proxboss.proxguard.util.formatLoad
import dev.proxboss.proxguard.util.formatPercentCompact
import dev.proxboss.proxguard.util.formatTime

@Composable
fun MetricsHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val samples by viewModel.metrics.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.refreshMetrics()
        viewModel.refreshDashboard(silent = true)
    }

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
                        text = "Historique local",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "24 dernières heures · ${samples.size} mesure${if (samples.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (samples.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Effacer les courbes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (samples.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Insights,
                    title = "Pas encore de mesures",
                    message = "Les courbes se construisent à chaque rafraîchissement du tableau de bord " +
                        "et toutes les 15 min en arrière-plan. Reviens dans quelques minutes.",
                )
            }
        } else {
            item {
                PgCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Fenêtre glissante",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${formatTime(samples.first().time)} → ${formatTime(samples.last().time)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Les échantillons de plus de 24 h sont purgés automatiquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                PercentChartCard(
                    title = "CPU",
                    icon = Icons.Rounded.Speed,
                    values = samples.map { it.cpu.toFloat() },
                    current = samples.last().cpu,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                PercentChartCard(
                    title = "Mémoire",
                    icon = Icons.Rounded.Memory,
                    values = samples.map { it.mem.toFloat() },
                    current = samples.last().mem,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                PercentChartCard(
                    title = "Swap",
                    icon = Icons.Rounded.SwapVert,
                    values = samples.map { it.swap.toFloat() },
                    current = samples.last().swap,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                PercentChartCard(
                    title = "Disque racine",
                    icon = Icons.Rounded.Storage,
                    values = samples.map { it.disk.toFloat() },
                    current = samples.last().disk,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            item {
                ChartCard(
                    title = "Charge système",
                    icon = Icons.Rounded.Timer,
                    values = samples.map { it.load.toFloat() },
                    currentText = formatLoad(samples.last().load),
                    formatValue = { formatLoad(it.toDouble()) },
                    color = MaterialTheme.colorScheme.primary,
                    times = samples,
                )
            }
            item {
                PgCard(contentPadding = PaddingValues(18.dp)) {
                    ChartHeader(
                        title = "Réseau",
                        icon = Icons.Rounded.Wifi,
                        currentText = "↓ ${formatBytesPerSecond(samples.last().netIn.toLong())}",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Entrant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Sparkline(
                        values = samples.map { it.netIn.toFloat() },
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Sortant · ${formatBytesPerSecond(samples.last().netOut.toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Sparkline(
                        values = samples.map { it.netOut.toFloat() },
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    ChartFooter(samples)
                }
            }
            item {
                Text(
                    text = "Les mesures sont stockées dans un fichier JSON local, sans base de données.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "Effacer les courbes ?",
            message = "Les mesures des dernières 24 h seront supprimées définitivement.",
            confirmLabel = "Effacer",
            destructive = true,
            onConfirm = { viewModel.clearMetrics() },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun PercentChartCard(
    title: String,
    icon: ImageVector,
    values: List<Float>,
    current: Double,
    color: Color,
) {
    PgCard(contentPadding = PaddingValues(18.dp)) {
        ChartHeader(
            title = title,
            icon = icon,
            currentText = formatPercentCompact(current),
            currentColor = color,
        )
        Spacer(Modifier.height(10.dp))
        Sparkline(
            values = values,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Text(
                text = "min ${formatPercentCompact(values.min().toDouble())} · " +
                    "moy ${formatPercentCompact(values.average())} · " +
                    "max ${formatPercentCompact(values.max().toDouble())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    icon: ImageVector,
    values: List<Float>,
    currentText: String,
    formatValue: (Float) -> String,
    color: Color,
    times: List<MetricSample>,
) {
    PgCard(contentPadding = PaddingValues(18.dp)) {
        ChartHeader(title = title, icon = icon, currentText = currentText, currentColor = color)
        Spacer(Modifier.height(10.dp))
        Sparkline(
            values = values,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "min ${formatValue(values.min())} · moy ${formatValue(values.average().toFloat())} · " +
                "max ${formatValue(values.max())}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChartFooter(times)
    }
}

@Composable
private fun ChartHeader(
    title: String,
    icon: ImageVector,
    currentText: String,
    currentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = currentText,
            style = MaterialTheme.typography.titleSmall,
            color = currentColor,
        )
    }
}

@Composable
private fun ChartFooter(times: List<MetricSample>) {
    if (times.size < 2) return
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = formatTime(times.first().time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatTime(times.last().time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
