package dev.proxboss.proxguard.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.notify.NotificationHelper
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.ui.components.ScreenHeader
import dev.proxboss.proxguard.ui.components.SectionHeader
import dev.proxboss.proxguard.ui.components.StatusPill
import kotlin.math.roundToInt

@Composable
fun AlertsScreen(
    viewModel: MainViewModel,
    config: AppConfig,
    onOpenIncidents: () -> Unit,
) {
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(NotificationHelper.notificationsAllowed(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsEnabled = NotificationHelper.notificationsAllowed(context) }

    var cpuThreshold by remember(config.rules.cpuThreshold) {
        mutableFloatStateOf(config.rules.cpuThreshold.toFloat())
    }
    var memThreshold by remember(config.rules.memThreshold) {
        mutableFloatStateOf(config.rules.memThreshold.toFloat())
    }
    var diskThreshold by remember(config.rules.diskThreshold) {
        mutableFloatStateOf(config.rules.diskThreshold.toFloat())
    }

    LaunchedEffect(Unit) {
        viewModel.refreshIncidents()
        notificationsEnabled = NotificationHelper.notificationsAllowed(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationHelper.notificationsAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Alertes",
                subtitle = "Sois prévenu avant la panne",
            )
        }

        item {
            PgCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alertes actives", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Vérification toutes les 15 min en arrière-plan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = config.alertsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setAlertsEnabled(enabled)
                            notificationsEnabled = NotificationHelper.notificationsAllowed(context)
                            if (enabled && !notificationsEnabled) requestNotifications()
                        },
                    )
                }
                if (!notificationsEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Notifications désactivées",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    "Autorise les notifications pour recevoir les alertes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { requestNotifications() }) {
                                Text("Autoriser")
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Une alerte par règle et par franchissement. Aucune notification répétée " +
                            "tant que la situation ne revient pas à la normale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("Seuils de l'hôte") }

        item {
            ThresholdRuleCard(
                icon = Icons.Rounded.Speed,
                title = "CPU de l'hôte",
                description = "Alerte quand l'utilisation CPU dépasse le seuil.",
                enabled = config.rules.cpuEnabled,
                threshold = cpuThreshold,
                accent = MaterialTheme.colorScheme.primary,
                onEnabledChange = { enabled ->
                    viewModel.updateRules(config.rules.copy(cpuEnabled = enabled))
                },
                onThresholdChange = { cpuThreshold = it },
                onThresholdChangeFinished = {
                    viewModel.updateRules(config.rules.copy(cpuThreshold = cpuThreshold.roundToInt()))
                },
            )
        }

        item {
            ThresholdRuleCard(
                icon = Icons.Rounded.Memory,
                title = "Mémoire de l'hôte",
                description = "Alerte quand la RAM utilisée dépasse le seuil.",
                enabled = config.rules.memEnabled,
                threshold = memThreshold,
                accent = MaterialTheme.colorScheme.secondary,
                onEnabledChange = { enabled ->
                    viewModel.updateRules(config.rules.copy(memEnabled = enabled))
                },
                onThresholdChange = { memThreshold = it },
                onThresholdChangeFinished = {
                    viewModel.updateRules(config.rules.copy(memThreshold = memThreshold.roundToInt()))
                },
            )
        }

        item {
            ThresholdRuleCard(
                icon = Icons.Rounded.Storage,
                title = "Disque racine",
                description = "Alerte quand le disque de l'hôte approche de la saturation.",
                enabled = config.rules.diskEnabled,
                threshold = diskThreshold,
                accent = MaterialTheme.colorScheme.tertiary,
                onEnabledChange = { enabled ->
                    viewModel.updateRules(config.rules.copy(diskEnabled = enabled))
                },
                onThresholdChange = { diskThreshold = it },
                onThresholdChangeFinished = {
                    viewModel.updateRules(config.rules.copy(diskThreshold = diskThreshold.roundToInt()))
                },
            )
        }

        item { SectionHeader("Événements") }

        item {
            ToggleRuleCard(
                icon = Icons.Rounded.WarningAmber,
                title = "Invité arrêté",
                description = "Alerte dès qu'une VM ou un conteneur passe à l'arrêt.",
                checked = config.rules.guestStopped,
                onCheckedChange = { viewModel.updateRules(config.rules.copy(guestStopped = it)) },
            )
        }

        item {
            ToggleRuleCard(
                icon = Icons.Rounded.WifiOff,
                title = "Hôte injoignable",
                description = "Alerte quand l'API Proxmox ne répond plus.",
                checked = config.rules.hostUnreachable,
                onCheckedChange = { viewModel.updateRules(config.rules.copy(hostUnreachable = it)) },
            )
        }

        item {
            PgCard(contentPadding = PaddingValues(16.dp), onClick = onOpenIncidents) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Historique des incidents", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (incidents.isEmpty()) "Aucun incident déclenché"
                            else "${incidents.size} incident${if (incidents.size > 1) "s" else ""} conservé" +
                                if (incidents.size > 1) "s" else "",
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
            }
        }

        item {
            Text(
                text = "Android peut décaler légèrement les vérifications pour préserver la batterie. " +
                    "Les seuils et l'historique restent stockés uniquement sur cet appareil.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ThresholdRuleCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    threshold: Float,
    accent: Color,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onThresholdChangeFinished: () -> Unit,
) {
    PgCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Seuil d'alerte",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                text = "${threshold.roundToInt()} %",
                color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = threshold,
            onValueChange = onThresholdChange,
            valueRange = 50f..99f,
            steps = 48,
            onValueChangeFinished = onThresholdChangeFinished,
            enabled = enabled,
        )
        Row {
            Text(
                "50 %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "99 %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRuleCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    PgCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
