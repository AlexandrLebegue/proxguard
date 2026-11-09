package dev.proxboss.proxguard.data.config

import dev.proxboss.proxguard.data.alerts.AlertRules
import dev.proxboss.proxguard.data.model.ThemeMode
import java.net.URI

data class AppConfig(
    val baseUrl: String = "",
    val tokenId: String = "",
    val secret: String = "",
    val allowSelfSigned: Boolean = false,
    val fingerprint: String? = null,
    val refreshSeconds: Int = 30,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val alertsEnabled: Boolean = true,
    val rules: AlertRules = AlertRules(),
) {
    val isConfigured: Boolean
        get() = normalizedBaseUrl.isNotBlank() && tokenId.isNotBlank() && secret.isNotBlank()

    val normalizedBaseUrl: String get() = normalizeBaseUrl(baseUrl)

    val apiBaseUrl: String get() = if (normalizedBaseUrl.isBlank()) "" else "$normalizedBaseUrl/api2/json"

    val host: String?
        get() = runCatching {
            val uri = URI(normalizedBaseUrl)
            uri.host ?: normalizedBaseUrl.substringAfter("://").substringBefore("/").substringBefore(":")
        }.getOrNull()

    override fun toString(): String =
        "AppConfig(baseUrl=$baseUrl, tokenId=$tokenId, secret=***, allowSelfSigned=$allowSelfSigned, " +
            "fingerprint=$fingerprint, refreshSeconds=$refreshSeconds, themeMode=$themeMode, " +
            "alertsEnabled=$alertsEnabled, rules=$rules)"
}

fun normalizeBaseUrl(input: String): String {
    var value = input.trim()
    if (value.isBlank()) return ""
    if (!value.startsWith("http://", ignoreCase = true) && !value.startsWith("https://", ignoreCase = true)) {
        value = "https://$value"
    }
    value = value.trim().trimEnd('/')
    if (value.endsWith("/api2/json", ignoreCase = true)) {
        value = value.dropLast("/api2/json".length).trimEnd('/')
    }
    return value
}

fun validateBaseUrl(input: String): String? {
    val normalized = normalizeBaseUrl(input)
    if (normalized.isBlank()) return "Renseigne l'URL du serveur Proxmox."
    val uri = runCatching { URI(normalized) }.getOrNull()
        ?: return "L'URL n'est pas valide."
    if (uri.scheme != "http" && uri.scheme != "https") return "L'URL doit commencer par http:// ou https://."
    if (uri.host.isNullOrBlank()) return "Le nom d'hôte de l'URL est introuvable."
    return null
}

fun normalizeTokenId(input: String): String = input.trim()

fun validateTokenId(input: String): String? {
    val token = input.trim()
    if (token.isBlank()) return "Renseigne l'identifiant du jeton."
    if (!token.contains('!')) return "L'identifiant doit ressembler à user@realm!tokenid."
    return null
}

fun normalizeFingerprint(input: String?): String? {
    if (input.isNullOrBlank()) return null
    val hex = input.replace(":", "").replace(" ", "").uppercase()
    if (hex.length != 64 || hex.any { it !in "0123456789ABCDEF" }) return input.trim().uppercase()
    return hex.chunked(2).joinToString(":")
}
