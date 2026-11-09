package dev.proxboss.proxguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.model.Guest
import dev.proxboss.proxguard.data.model.GuestType
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.EmptyState
import dev.proxboss.proxguard.ui.components.ErrorPane
import dev.proxboss.proxguard.ui.components.LoadingPane
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.ui.components.ScreenHeader
import dev.proxboss.proxguard.ui.components.StatusPill
import dev.proxboss.proxguard.util.formatBytes
import dev.proxboss.proxguard.util.formatDuration
import dev.proxboss.proxguard.util.formatPercentCompact
import kotlinx.coroutines.delay

private const val FILTER_ALL = "all"
private const val FILTER_QEMU = "qemu"
private const val FILTER_LXC = "lxc"
private const val STATUS_RUNNING = "running"
private const val STATUS_STOPPED = "stopped"

private val sortOptions = linkedMapOf(
    "status" to "Statut",
    "name" to "Nom",
    "cpu" to "CPU",
    "ram" to "RAM",
    "vmid" to "VMID",
)

@Composable
fun GuestsScreen(
    viewModel: MainViewModel,
    config: AppConfig,
    onOpenGuest: (Guest) -> Unit,
) {
    val state by viewModel.guests.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val refreshSeconds = config.refreshSeconds

    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var statusFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var sortKey by rememberSaveable { mutableStateOf("status") }
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refreshSeconds) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(refreshSeconds * 1000L)
                viewModel.refreshGuests(silent = true)
            }
        }
    }

    val allGuests = state.data?.guests.orEmpty()
    val filtered = remember(allGuests, query, typeFilter, statusFilter, sortKey) {
        val text = query.trim().lowercase()
        val base = allGuests.filter { guest ->
            val typeOk = when (typeFilter) {
                FILTER_QEMU -> guest.type == GuestType.QEMU
                FILTER_LXC -> guest.type == GuestType.LXC
                else -> true
            }
            val statusOk = when (statusFilter) {
                STATUS_RUNNING -> guest.running
                STATUS_STOPPED -> !guest.running
                else -> true
            }
            val queryOk = text.isBlank() ||
                guest.displayName.lowercase().contains(text) ||
                guest.vmid.toString().contains(text) ||
                guest.node.lowercase().contains(text) ||
                guest.tags.any { it.lowercase().contains(text) }
            typeOk && statusOk && queryOk
        }
        when (sortKey) {
            "name" -> base.sortedBy { it.displayName.lowercase() }
            "cpu" -> base.sortedByDescending { it.cpuPercent }
            "ram" -> base.sortedByDescending { it.memPercent }
            "vmid" -> base.sortedBy { it.vmid }
            else -> base.sortedWith(
                compareByDescending<Guest> { it.running }.thenBy { it.displayName.lowercase() }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Invités",
                subtitle = if (state.data != null) {
                    "${allGuests.count { it.running }} actifs · " +
                        "${allGuests.count { !it.running && !it.template }} arrêtés · " +
                        "${allGuests.size} au total"
                } else {
                    "VMs et conteneurs de l'hôte"
                },
                trailing = {
                    IconButton(
                        onClick = { viewModel.refreshGuests() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser")
                        }
                    }
                },
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Rechercher un nom, un VMID, un tag…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Effacer la recherche")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = typeFilter == FILTER_ALL,
                        onClick = { typeFilter = FILTER_ALL },
                        label = { Text("Tous") },
                    )
                    FilterChip(
                        selected = typeFilter == FILTER_QEMU,
                        onClick = { typeFilter = FILTER_QEMU },
                        label = { Text("VM") },
                        leadingIcon = { Icon(Icons.Rounded.Computer, null, Modifier.size(16.dp)) },
                    )
                    FilterChip(
                        selected = typeFilter == FILTER_LXC,
                        onClick = { typeFilter = FILTER_LXC },
                        label = { Text("Conteneurs") },
                        leadingIcon = { Icon(Icons.Rounded.Dns, null, Modifier.size(16.dp)) },
                    )
                    FilterChip(
                        selected = statusFilter == STATUS_RUNNING,
                        onClick = {
                            statusFilter = if (statusFilter == STATUS_RUNNING) FILTER_ALL else STATUS_RUNNING
                        },
                        label = { Text("Actifs") },
                    )
                    FilterChip(
                        selected = statusFilter == STATUS_STOPPED,
                        onClick = {
                            statusFilter = if (statusFilter == STATUS_STOPPED) FILTER_ALL else STATUS_STOPPED
                        },
                        label = { Text("Arrêtés") },
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${filtered.size} invité${if (filtered.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            Icons.Rounded.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(sortOptions[sortKey] ?: "Trier")
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        sortOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text("Trier par $label") },
                                onClick = {
                                    sortKey = key
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (state.data == null) {
            item {
                if (state.loading || state.error == null) {
                    LoadingPane("Chargement des invités…")
                } else {
                    ErrorPane(message = state.error.orEmpty(), onRetry = { viewModel.refreshGuests() })
                }
            }
        } else {
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
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Aucun invité trouvé",
                        message = if (allGuests.isEmpty()) {
                            "L'hôte ne renvoie aucun invité pour le moment."
                        } else {
                            "Ajuste ta recherche ou retire un filtre pour voir les invités."
                        },
                        actionLabel = if (query.isNotBlank() || typeFilter != FILTER_ALL || statusFilter != FILTER_ALL) {
                            "Réinitialiser"
                        } else null,
                        onAction = {
                            query = ""
                            typeFilter = FILTER_ALL
                            statusFilter = FILTER_ALL
                        },
                    )
                }
            } else {
                items(filtered, key = { "${it.type.apiValue}-${it.vmid}" }) { guest ->
                    GuestCard(guest = guest, onClick = { onOpenGuest(guest) })
                }
            }
        }
    }
}

@Composable
private fun GuestCard(guest: Guest, onClick: () -> Unit) {
    val statusColor = when {
        guest.running -> MaterialTheme.colorScheme.secondary
        guest.status == "paused" || guest.status == "suspended" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val typeColor = if (guest.type == GuestType.QEMU) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    PgCard(
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (guest.type == GuestType.QEMU) Icons.Rounded.Computer else Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = guest.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${guest.type.iconLabel} ${guest.vmid} · ${guest.node}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatusPill(
                text = if (guest.template) "Modèle" else guest.statusLabel,
                color = if (guest.template) MaterialTheme.colorScheme.primary else statusColor,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GuestStat(
                label = "CPU",
                value = formatPercentCompact(guest.cpuPercent),
                modifier = Modifier.weight(1f),
            )
            GuestStat(
                label = "RAM",
                value = formatPercentCompact(guest.memPercent),
                detail = "${formatBytes(guest.mem)} / ${formatBytes(guest.maxMem)}",
                modifier = Modifier.weight(1.5f),
            )
            GuestStat(
                label = "Disque",
                value = formatPercentCompact(guest.diskPercent),
                detail = formatBytes(guest.maxDisk),
                modifier = Modifier.weight(1.2f),
            )
        }
        if (guest.running && guest.uptime > 0) {
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "En ligne depuis ${formatDuration(guest.uptime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (guest.tags.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = guest.tags.take(3).joinToString("  ") { "#$it" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GuestStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
