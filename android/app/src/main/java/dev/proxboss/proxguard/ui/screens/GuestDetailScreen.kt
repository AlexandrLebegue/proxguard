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
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.proxguard.data.model.GuestDetail
import dev.proxboss.proxguard.data.model.GuestType
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.ErrorPane
import dev.proxboss.proxguard.ui.components.InfoTile
import dev.proxboss.proxguard.ui.components.KeyValueRow
import dev.proxboss.proxguard.ui.components.LoadingPane
import dev.proxboss.proxguard.ui.components.MetricBar
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.ui.components.PgDivider
import dev.proxboss.proxguard.ui.components.SectionHeader
import dev.proxboss.proxguard.ui.components.Sparkline
import dev.proxboss.proxguard.ui.components.StatusPill
import dev.proxboss.proxguard.ui.components.TaskRow
import dev.proxboss.proxguard.ui.components.usageColor
import dev.proxboss.proxguard.util.formatBytes
import dev.proxboss.proxguard.util.formatDuration
import dev.proxboss.proxguard.util.formatPercentCompact
import dev.proxboss.proxguard.util.formatPercentValue

@Composable
fun GuestDetailScreen(
    viewModel: MainViewModel,
    node: String,
    type: GuestType,
    vmid: Int,
    onBack: () -> Unit,
) {
    val state by viewModel.guestDetail.collectAsStateWithLifecycle()
    LaunchedEffect(node, type, vmid) {
        viewModel.loadGuestDetail(node, type, vmid)
    }

    val detail = state.data?.takeIf { it.guest.vmid == vmid && it.guest.type == type }

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
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail?.guest?.displayName ?: "${type.iconLabel} $vmid",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${type.label} $vmid · nœud $node",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { viewModel.loadGuestDetail(node, type, vmid, force = true) },
                    modifier = Modifier.size(48.dp),
                ) {
                    if (state.refreshing || state.loading) {
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

        if (detail == null) {
            item {
                if (state.loading || state.error == null) {
                    LoadingPane("Chargement de l'invité…")
                } else {
                    ErrorPane(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.loadGuestDetail(node, type, vmid, force = true) },
                    )
                }
            }
        } else {
            if (detail.warnings.isNotEmpty() || state.error != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = state.error ?: detail.warnings.joinToString(" "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { OverviewCard(detail) }
            if (detail.samples.size >= 2) {
                item { GuestTrendCard(detail) }
            }
            item { SummaryCard(detail) }
            if (detail.disks.isNotEmpty()) {
                item { DisksCard(detail) }
            }
            if (detail.networks.isNotEmpty()) {
                item { NetworksCard(detail) }
            }
            item { TasksCard(detail) }
        }
    }
}

@Composable
private fun OverviewCard(detail: GuestDetail) {
    val guest = detail.guest
    val statusColor = when {
        guest.running -> MaterialTheme.colorScheme.secondary
        guest.status == "paused" || guest.status == "suspended" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    PgCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (guest.type == GuestType.QEMU) Icons.Rounded.Computer else Icons.Rounded.Dns,
                contentDescription = null,
                tint = if (guest.type == GuestType.QEMU) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = guest.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${guest.type.label} ${guest.vmid} · ${guest.node}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = if (guest.template) "Modèle" else guest.statusLabel,
                color = if (guest.template) MaterialTheme.colorScheme.primary else statusColor,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(
                label = "Uptime",
                value = if (guest.running) formatDuration(guest.uptime) else "—",
                icon = Icons.Rounded.Timer,
                modifier = Modifier.weight(1f),
            )
            InfoTile(
                label = "vCPU",
                value = guest.maxCpu.toString(),
                modifier = Modifier.weight(0.7f),
            )
            InfoTile(
                label = "Nœud",
                value = guest.node,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        PgDivider()
        Spacer(Modifier.height(14.dp))
        MetricBar(
            label = "CPU",
            valueText = formatPercentValue(guest.cpuPercent),
            fraction = (guest.cpuPercent / 100.0).toFloat(),
            color = usageColor(guest.cpuPercent, 75.0, 90.0),
        )
        Spacer(Modifier.height(12.dp))
        MetricBar(
            label = "Mémoire · ${formatBytes(guest.mem)} / ${formatBytes(guest.maxMem)}",
            valueText = formatPercentCompact(guest.memPercent),
            fraction = (guest.memPercent / 100.0).toFloat(),
            color = usageColor(guest.memPercent, 80.0, 92.0),
        )
        Spacer(Modifier.height(12.dp))
        MetricBar(
            label = "Disque · ${formatBytes(guest.disk)} / ${formatBytes(guest.maxDisk)}",
            valueText = formatPercentCompact(guest.diskPercent),
            fraction = (guest.diskPercent / 100.0).toFloat(),
            color = usageColor(guest.diskPercent, 80.0, 92.0),
        )
    }
}

@Composable
private fun GuestTrendCard(detail: GuestDetail) {
    PgCard(contentPadding = PaddingValues(18.dp)) {
        SectionHeader("Activité sur 1 h")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CPU",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatPercentValue(detail.guest.cpuPercent),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Sparkline(
            values = detail.samples.map { it.cpuPercent.toFloat() },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Mémoire",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatPercentValue(detail.guest.memPercent),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Sparkline(
            values = detail.samples.map { it.memPercent.toFloat() },
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
        )
    }
}

@Composable
private fun SummaryCard(detail: GuestDetail) {
    PgCard(contentPadding = PaddingValues(18.dp)) {
        SectionHeader("Configuration")
        Spacer(Modifier.height(8.dp))
        if (detail.summary.isEmpty()) {
            Text(
                text = "Configuration indisponible pour cet invité.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            detail.summary.forEach { field -> KeyValueRow(field.label, field.value) }
        }
    }
}

@Composable
private fun DisksCard(detail: GuestDetail) {
    PgCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            SectionHeader("Disques")
        }
        Spacer(Modifier.height(8.dp))
        detail.disks.forEach { disk ->
            KeyValueRow(disk.label, disk.value)
        }
    }
}

@Composable
private fun NetworksCard(detail: GuestDetail) {
    PgCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Lan,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            SectionHeader("Réseau")
        }
        Spacer(Modifier.height(8.dp))
        detail.networks.forEach { network ->
            KeyValueRow(network.label, network.value)
        }
    }
}

@Composable
private fun TasksCard(detail: GuestDetail) {
    PgCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        SectionHeader("Tâches récentes")
        Spacer(Modifier.height(6.dp))
        if (detail.tasks.isEmpty()) {
            Text(
                text = "Aucune tâche récente pour cet invité.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            detail.tasks.take(8).forEach { task -> TaskRow(task) }
        }
    }
}
