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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.model.ThemeMode
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.AppLogo
import dev.proxboss.proxguard.ui.components.ConfirmDialog
import dev.proxboss.proxguard.ui.components.KeyValueRow
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.ui.components.PgDivider
import dev.proxboss.proxguard.ui.components.ScreenHeader
import dev.proxboss.proxguard.ui.components.SectionHeader
import dev.proxboss.proxguard.ui.components.StatusPill
import dev.proxboss.proxguard.util.pveVersionLabel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    config: AppConfig,
    onEditConnection: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val testState by viewModel.test.collectAsStateWithLifecycle()
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showClearLocal by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.clearTest()
        viewModel.refreshIncidents()
        viewModel.refreshMetrics()
    }

    LaunchedEffect(testState.result?.fingerprint) {
        val fingerprint = testState.result?.fingerprint
        if (fingerprint != null && config.allowSelfSigned && config.fingerprint == null) {
            viewModel.saveConfig(config.copy(fingerprint = fingerprint)) { }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Réglages",
                subtitle = "Connexion, alertes et préférences",
            )
        }

        item {
            PgCard(contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Lan,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Serveur Proxmox", style = MaterialTheme.typography.titleMedium)
                        Text(
                            config.normalizedBaseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusPill(
                        text = when {
                            !config.allowSelfSigned -> "TLS vérifié"
                            config.fingerprint != null -> "Épinglé"
                            else -> "Auto-signé"
                        },
                        color = if (config.allowSelfSigned) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                PgDivider()
                Spacer(Modifier.height(6.dp))
                KeyValueRow("Jeton", config.tokenId)
                KeyValueRow("Secret", "••••••••••••")
                KeyValueRow(
                    "Certificat",
                    if (config.allowSelfSigned) "Auto-signé accepté pour cet hôte" else "Chaîne TLS du système",
                )
                if (config.fingerprint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Empreinte SHA-256 épinglée",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = config.fingerprint,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButtonCompact(
                            icon = Icons.Rounded.ContentCopy,
                            contentDescription = "Copier l'empreinte",
                            onClick = {
                                clipboard.setText(AnnotatedString(config.fingerprint.orEmpty()))
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.testConnection(config) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = !testState.loading,
                    ) {
                        if (testState.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Tester")
                        }
                    }
                    Button(
                        onClick = onEditConnection,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Modifier")
                    }
                }
                testState.result?.let { result ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Connexion réussie · ${pveVersionLabel(result.pveVersion)} · nœud ${result.nodeName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                testState.error?.let { error ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            PgCard(contentPadding = PaddingValues(18.dp)) {
                SettingHeader(
                    icon = Icons.Rounded.Sync,
                    title = "Rafraîchissement",
                    subtitle = "Intervalle du tableau de bord et des invités",
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15 to "15 s", 30 to "30 s", 60 to "1 min", 300 to "5 min").forEach { (seconds, label) ->
                        FilterChip(
                            selected = config.refreshSeconds == seconds,
                            onClick = { viewModel.setRefreshSeconds(seconds) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Le rafraîchissement s'arrête quand l'application est en arrière-plan. " +
                        "Les alertes restent vérifiées toutes les 15 min.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PgCard(contentPadding = PaddingValues(18.dp)) {
                SettingHeader(
                    icon = Icons.Rounded.Palette,
                    title = "Apparence",
                    subtitle = "Thème sombre par défaut",
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = config.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        item {
            PgCard(contentPadding = PaddingValues(16.dp), onClick = onOpenAlerts) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alertes et seuils", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "CPU, RAM, disque, invités et hôte injoignable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(
                        text = if (config.alertsEnabled) "Activées" else "Désactivées",
                        color = if (config.alertsEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            PgCard(contentPadding = PaddingValues(18.dp)) {
                SettingHeader(
                    icon = Icons.Rounded.Delete,
                    title = "Données locales",
                    subtitle = "Courbes 24 h et incidents",
                )
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Mesures conservées", metrics.size.toString())
                KeyValueRow("Incidents conservés", incidents.size.toString())
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearLocal = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Effacer courbes et incidents")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showReset = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Réinitialiser ProxGuard",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            PgCard(contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ProxGuard", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Version 1.0.0 · par Proxboss",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Supervision de ton hôte Proxmox VE, alertes avant la panne et historique " +
                        "local. Aucune donnée d'infrastructure ne quitte ton téléphone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniTag("API Proxmox VE")
                    MiniTag("WorkManager")
                    MiniTag("Stockage local")
                }
            }
        }

        item {
            Text(
                text = "Jeton d'API uniquement · secret jamais affiché ni journalisé",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }
    }

    if (showClearLocal) {
        ConfirmDialog(
            title = "Effacer les données locales ?",
            message = "Les courbes des dernières 24 h et l'historique des incidents seront supprimés. " +
                "La configuration du serveur est conservée.",
            confirmLabel = "Effacer",
            destructive = true,
            onConfirm = { viewModel.clearLocalData() },
            onDismiss = { showClearLocal = false },
        )
    }

    if (showReset) {
        ConfirmDialog(
            title = "Réinitialiser ProxGuard ?",
            message = "La configuration du serveur, les courbes et les incidents seront supprimés. " +
                "Cette action est irréversible.",
            confirmLabel = "Réinitialiser",
            destructive = true,
            onConfirm = { viewModel.resetApplication { } },
            onDismiss = { showReset = false },
        )
    }
}

@Composable
private fun SettingHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconButtonCompact(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(6.dp)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MiniTag(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
