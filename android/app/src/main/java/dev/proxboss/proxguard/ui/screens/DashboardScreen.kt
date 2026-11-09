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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.model.DashboardData
import dev.proxboss.proxguard.data.model.StorageInfo
import dev.proxboss.proxguard.data.model.TaskInfo
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.AppLogo
import dev.proxboss.proxguard.ui.components.GaugeRing
import dev.proxboss.proxguard.ui.components.HealthBanner
import dev.proxboss.proxguard.ui.components.InfoTile
import dev.proxboss.proxguard.ui.components.LoadingPane
import dev.proxboss.proxguard.ui.components.MetricBar
import dev.proxboss.proxguard.ui.components.ErrorPane
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.ui.components.PgDivider
import dev.proxboss.proxguard.ui.components.SectionHeader
import dev.proxboss.proxguard.ui.components.Sparkline
import dev.proxboss.proxguard.ui.components.StatusPill
import dev.proxboss.proxguard.ui.components.TaskRow
import dev.proxboss.proxguard.ui.components.evaluateHealth
import dev.proxboss.proxguard.util.formatPercentCompact
import dev.proxboss.proxguard.ui.components.usageColor
import dev.proxboss.proxguard.util.formatBytes
import dev.proxboss.proxguard.util.formatDuration
import dev.proxboss.proxguard.util.formatLoad
import dev.proxboss.proxguard.util.formatPercentValue
import dev.proxboss.proxguard.util.formatTime
import dev.proxboss.proxguard.util.pveVersionLabel
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    config: AppConfig,
    onOpenGuests: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenMetrics: () -> Unit,
) {
    val state by viewModel.dashboard.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val refreshSeconds = config.refreshSeconds

    LaunchedEffect(refreshSeconds) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(refreshSeconds * 1000L)
                viewModel.refreshDashboard(silent = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLogo(42.dp)
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PROXGUARD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.data?.let { "Hôte ${it.nodeName}" } ?: "Tableau de bord",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(
                    onClick = { viewModel.refreshDashboard() },
                    modifier = Modifier.size(48.dp),
                ) {
                    if (state.refreshing || (state.loading && state.data != null)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser")
                    }
                }
            }
        }

        val data = state.data
        if (data == null) {
            item {
                if (state.loading || state.error == null) {
                    LoadingPane("Connexion à l'hôte Proxmox…")
                } else {
                    ErrorPane(message = state.error.orEmpty(), onRetry = { viewModel.refreshDashboard() })
                }
            }
        } else {
            item {
                HealthBanner(
                    status = evaluateHealth(data, config.rules),
                    onClick = onOpenAlerts,
                )
            }
            if (state.error != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = "Actualisation impossible : ${state.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { viewModel.refreshDashboard() }) {
                                Text("Réessayer")
                            }
                        }
                    }
                }
            }
            item { HostCard(data) }
            item { TrendsCard(data) }
            item { SummaryRow(data, onOpenGuests) }
            item { LocalHistoryCard(metrics, onOpenMetrics) }
            if (data.storages.any { it.total > 0 }) {
                item { StorageCard(data.storages) }
            }
            if (data.tasks.isNotEmpty()) {
                item { TasksCard(data.tasks) }
            }
            item {
                Text(
                    text = "Dernière synchronisation à ${formatTime(data.fetchedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HostCard(data: DashboardData) {
    val status = data.nodeStatus
    val nodeInfo = data.nodes.firstOrNull { it.node == data.nodeName }
    val cpuColor = usageColor(status.cpuPercent, 75.0, 90.0)
    val memColor = usageColor(status.memPercent, 80.0, 92.0)
    val diskColor = usageColor(status.diskPercent, 80.0, 92.0)
    PgCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HÔTE PROXMOX",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = data.nodeName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${pveVersionLabel(status.pveVersion)} · ${status.cpuModel.ifBlank { "Proxmox VE" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill("En ligne", MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GaugeRing(
                percent = status.cpuPercent,
                label = "CPU",
                valueText = formatPercentCompact(status.cpuPercent),
                color = cpuColor,
                modifier = Modifier.weight(1f),
            )
            GaugeRing(
                percent = status.memPercent,
                label = "RAM",
                valueText = formatPercentCompact(status.memPercent),
                color = memColor,
                modifier = Modifier.weight(1f),
            )
            GaugeRing(
                percent = status.diskPercent,
                label = "Disque",
                valueText = formatPercentCompact(status.diskPercent),
                color = diskColor,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        PgDivider()
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(
                label = "Charge 1/5/15",
                value = "${formatLoad(status.load1)} · ${formatLoad(status.load5)} · ${formatLoad(status.load15)}",
                icon = Icons.Rounded.Speed,
                modifier = Modifier.weight(1.4f),
            )
            InfoTile(
                label = "Uptime",
                value = formatDuration(status.uptime),
                icon = Icons.Rounded.Timer,
                modifier = Modifier.weight(1f),
            )
            InfoTile(
                label = "vCPU",
                value = (nodeInfo?.maxCpu ?: status.cores).toString(),
                icon = Icons.Rounded.Bolt,
                modifier = Modifier.weight(0.7f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "RAM ${formatBytes(status.memUsed)} / ${formatBytes(status.memTotal)}" +
                " · swap ${formatBytes(status.swapUsed)}" +
                if (status.kernel.isNotBlank()) " · ${status.kernel.substringBefore(" #")}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrendsCard(data: DashboardData) {
    val status = data.nodeStatus
    val samples = data.samples
    PgCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Activité sur 1 h",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Courbes RRD moyennes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill("1 h", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        if (samples.size < 2) {
            Text(
                text = "Courbes indisponibles pour le moment. Elles réapparaîtront au prochain rafraîchissement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@PgCard
        }
        SparkRow(
            label = "CPU",
            value = formatPercentValue(status.cpuPercent),
            values = samples.map { it.cpuPercent.toFloat() },
            color = usageColor(status.cpuPercent, 75.0, 90.0),
        )
        Spacer(Modifier.height(13.dp))
        SparkRow(
            label = "Mémoire",
            value = formatPercentValue(status.memPercent),
            values = samples.map { it.memPercent.toFloat() },
            color = usageColor(status.memPercent, 80.0, 92.0),
        )
        Spacer(Modifier.height(13.dp))
        SparkRow(
            label = "Disque racine",
            value = formatPercentValue(status.diskPercent),
            values = samples.map { it.diskPercent.toFloat() },
            color = usageColor(status.diskPercent, 80.0, 92.0),
        )
    }
}

@Composable
private fun SparkRow(
    label: String,
    value: String,
    values: List<Float>,
    color: androidx.compose.ui.graphics.Color,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(5.dp))
        Sparkline(
            values = values,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        )
    }
}

@Composable
private fun SummaryRow(data: DashboardData, onOpenGuests: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PgCard(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            onClick = onOpenGuests,
        ) {
            Icon(
                Icons.Rounded.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${data.runningGuests}/${data.guests.size}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "invités actifs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${data.vmCount} VM · ${data.containerCount} conteneurs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (data.stoppedGuests > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${data.stoppedGuests} arrêté${if (data.stoppedGuests > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        PgCard(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
        ) {
            Icon(
                Icons.Rounded.Update,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = data.updates.size.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "mises à jour",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (data.updates.isEmpty()) "Système à jour" else "paquets APT disponibles",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalHistoryCard(metrics: List<dev.proxboss.proxguard.data.history.MetricSample>, onOpen: () -> Unit) {
    PgCard(contentPadding = PaddingValues(16.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Historique local",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "24 h · ${metrics.size} mesure${if (metrics.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (metrics.size >= 2) {
            Sparkline(
                values = metrics.map { it.cpu.toFloat() },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            )
        } else {
            Text(
                text = "Les courbes se construisent à chaque rafraîchissement et toutes les 15 min en arrière-plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageCard(storages: List<StorageInfo>) {
    PgCard(contentPadding = PaddingValues(18.dp)) {
        SectionHeader("Stockage")
        Spacer(Modifier.height(12.dp))
        storages.filter { it.total > 0 }.take(4).forEachIndexed { index, storage ->
            if (index > 0) Spacer(Modifier.height(13.dp))
            MetricBar(
                label = "${storage.storage} · ${storage.type}",
                valueText = "${formatBytes(storage.used)} / ${formatBytes(storage.total)}",
                fraction = (storage.usedPercent / 100.0).toFloat(),
                color = usageColor(storage.usedPercent, 80.0, 92.0),
            )
        }
    }
}

@Composable
private fun TasksCard(tasks: List<TaskInfo>) {
    PgCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        SectionHeader("Dernières tâches")
        Spacer(Modifier.height(6.dp))
        tasks.take(4).forEach { TaskRow(it, showResource = true) }
    }
}
