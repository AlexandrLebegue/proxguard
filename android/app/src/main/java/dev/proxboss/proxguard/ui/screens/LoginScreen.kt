package dev.proxboss.proxguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.config.normalizeBaseUrl
import dev.proxboss.proxguard.data.config.validateBaseUrl
import dev.proxboss.proxguard.data.config.validateTokenId
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.AppLogo
import dev.proxboss.proxguard.ui.components.PgCard
import dev.proxboss.proxguard.ui.components.PgDivider

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    isEditing: Boolean,
    onSaved: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val configState by viewModel.config.collectAsStateWithLifecycle()
    val currentConfig = configState ?: AppConfig()
    val testState by viewModel.test.collectAsStateWithLifecycle()

    var baseUrl by rememberSaveable { mutableStateOf(currentConfig.baseUrl) }
    var tokenId by rememberSaveable { mutableStateOf(currentConfig.tokenId) }
    var secret by rememberSaveable { mutableStateOf(currentConfig.secret) }
    var allowSelfSigned by rememberSaveable { mutableStateOf(currentConfig.allowSelfSigned) }
    var secretVisible by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val baseUrlError = validateBaseUrl(baseUrl)
    val tokenError = validateTokenId(tokenId)
    val secretError = if (secret.isBlank()) "Renseigne le secret du jeton." else null
    val savedFingerprint = currentConfig.fingerprint
    val hostUnchanged = normalizeBaseUrl(baseUrl) == currentConfig.normalizedBaseUrl

    fun candidateForTest(): AppConfig? {
        if (baseUrlError != null || tokenError != null || secretError != null) {
            attempted = true
            return null
        }
        return currentConfig.copy(
            baseUrl = normalizeBaseUrl(baseUrl),
            tokenId = tokenId.trim(),
            secret = secret.trim(),
            allowSelfSigned = allowSelfSigned,
            fingerprint = if (allowSelfSigned) savedFingerprint else null,
        )
    }

    fun candidateForSave(): AppConfig? {
        if (baseUrlError != null || tokenError != null || secretError != null) {
            attempted = true
            return null
        }
        val fingerprint = when {
            !allowSelfSigned -> null
            testState.result?.fingerprint != null -> testState.result?.fingerprint
            savedFingerprint != null && hostUnchanged -> savedFingerprint
            else -> null
        }
        return currentConfig.copy(
            baseUrl = normalizeBaseUrl(baseUrl),
            tokenId = tokenId.trim(),
            secret = secret.trim(),
            allowSelfSigned = allowSelfSigned,
            fingerprint = fingerprint,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (isEditing && onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    text = "Configuration du serveur",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppLogo(58.dp)
                Column {
                    Text(
                        text = "ProxGuard",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Proxmox VE, sous contrôle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = "Connecte ton serveur",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Un jeton d'API suffit pour superviser l'hôte, les invités et les alertes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }

        PgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL du serveur") },
                placeholder = { Text("https://pve.example.lan:8006") },
                leadingIcon = { Icon(Icons.Rounded.Lan, contentDescription = null) },
                singleLine = true,
                isError = attempted && baseUrlError != null,
                supportingText = {
                    Text(if (attempted && baseUrlError != null) baseUrlError else "Adresse de l'interface web Proxmox")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = tokenId,
                onValueChange = { tokenId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Identifiant du jeton") },
                placeholder = { Text("root@pam!mon-token") },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                singleLine = true,
                isError = attempted && tokenError != null,
                supportingText = {
                    Text(if (attempted && tokenError != null) tokenError else "Format : utilisateur@royaume!nom-du-jeton")
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Secret du jeton") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { secretVisible = !secretVisible }) {
                        Icon(
                            if (secretVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (secretVisible) "Masquer le secret" else "Afficher le secret",
                        )
                    }
                },
                visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                isError = attempted && secretError != null,
                supportingText = {
                    Text(if (attempted && secretError != null) secretError else "Stocké localement, jamais journalisé")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(16.dp))
            PgDivider()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accepter le certificat auto-signé",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "L'empreinte du certificat sera épinglée pour ce serveur uniquement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (allowSelfSigned && savedFingerprint != null && hostUnchanged) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Empreinte épinglée : ${savedFingerprint.take(29)}…",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = allowSelfSigned,
                    onCheckedChange = {
                        allowSelfSigned = it
                        viewModel.clearTest()
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = {
                    val candidate = candidateForTest() ?: return@OutlinedButton
                    viewModel.testConnection(candidate)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !testState.loading,
            ) {
                if (testState.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Test en cours…")
                } else {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tester la connexion")
                }
            }

            testState.result?.let { result ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Connexion réussie",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${result.pveVersion} · nœud ${result.nodeName}" +
                                if (result.nodeCount > 1) " (+${result.nodeCount - 1})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!result.fingerprint.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Empreinte SHA-256 du certificat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = result.fingerprint,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (allowSelfSigned) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = "Cette empreinte sera épinglée à l'enregistrement.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            testState.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.32f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val candidate = candidateForSave() ?: return@Button
                saving = true
                saveError = null
                viewModel.saveConfig(candidate) { result ->
                    saving = false
                    result.onSuccess { onSaved() }
                        .onFailure { saveError = "Impossible d'enregistrer la configuration." }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !saving,
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(if (isEditing) "Enregistrer les modifications" else "Enregistrer et continuer")
        }
        if (saveError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = saveError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!isEditing) {
            Spacer(Modifier.height(18.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Jeton d'API, pas de mot de passe",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Crée un jeton dédié dans Datacenter → Permissions → API Tokens. " +
                                "Tu peux le révoquer à tout moment, sans changer ton mot de passe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Proxboss · ProxGuard 1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
        }
        if (isEditing && onBack != null) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
