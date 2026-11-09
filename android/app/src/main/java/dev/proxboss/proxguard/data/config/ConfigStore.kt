package dev.proxboss.proxguard.data.config

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.proxboss.proxguard.data.alerts.AlertRules
import dev.proxboss.proxguard.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.configDataStore by preferencesDataStore(name = "proxguard_config")

class ConfigStore(private val context: Context) {

    val config: Flow<AppConfig> = context.configDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences.toConfig() }

    suspend fun current(): AppConfig = config.first()

    suspend fun save(config: AppConfig) {
        val normalized = config.copy(
            baseUrl = normalizeBaseUrl(config.baseUrl),
            tokenId = normalizeTokenId(config.tokenId),
            secret = config.secret.trim(),
            fingerprint = normalizeFingerprint(config.fingerprint),
            refreshSeconds = config.refreshSeconds.coerceIn(15, 300),
            rules = config.rules.sanitized(),
        )
        context.configDataStore.edit { preferences ->
            preferences[Keys.BASE_URL] = normalized.baseUrl
            preferences[Keys.TOKEN_ID] = normalized.tokenId
            preferences[Keys.SECRET] = normalized.secret
            preferences[Keys.ALLOW_SELF_SIGNED] = normalized.allowSelfSigned
            preferences[Keys.FINGERPRINT] = normalized.fingerprint.orEmpty()
            preferences[Keys.REFRESH_SECONDS] = normalized.refreshSeconds
            preferences[Keys.THEME_MODE] = normalized.themeMode.name
            preferences[Keys.ALERTS_ENABLED] = normalized.alertsEnabled
            preferences[Keys.RULE_CPU_ENABLED] = normalized.rules.cpuEnabled
            preferences[Keys.RULE_CPU_THRESHOLD] = normalized.rules.cpuThreshold
            preferences[Keys.RULE_MEM_ENABLED] = normalized.rules.memEnabled
            preferences[Keys.RULE_MEM_THRESHOLD] = normalized.rules.memThreshold
            preferences[Keys.RULE_DISK_ENABLED] = normalized.rules.diskEnabled
            preferences[Keys.RULE_DISK_THRESHOLD] = normalized.rules.diskThreshold
            preferences[Keys.RULE_GUEST_STOPPED] = normalized.rules.guestStopped
            preferences[Keys.RULE_HOST_UNREACHABLE] = normalized.rules.hostUnreachable
        }
    }

    suspend fun update(transform: (AppConfig) -> AppConfig) {
        save(transform(current()))
    }

    suspend fun setFingerprint(fingerprint: String?) {
        update { it.copy(fingerprint = normalizeFingerprint(fingerprint)) }
    }

    suspend fun clear() {
        context.configDataStore.edit { it.clear() }
    }

    private fun androidx.datastore.preferences.core.Preferences.toConfig(): AppConfig {
        val rules = AlertRules(
            cpuEnabled = this[Keys.RULE_CPU_ENABLED] ?: true,
            cpuThreshold = this[Keys.RULE_CPU_THRESHOLD] ?: 85,
            memEnabled = this[Keys.RULE_MEM_ENABLED] ?: true,
            memThreshold = this[Keys.RULE_MEM_THRESHOLD] ?: 90,
            diskEnabled = this[Keys.RULE_DISK_ENABLED] ?: true,
            diskThreshold = this[Keys.RULE_DISK_THRESHOLD] ?: 85,
            guestStopped = this[Keys.RULE_GUEST_STOPPED] ?: true,
            hostUnreachable = this[Keys.RULE_HOST_UNREACHABLE] ?: true,
        ).sanitized()
        return AppConfig(
            baseUrl = this[Keys.BASE_URL].orEmpty(),
            tokenId = this[Keys.TOKEN_ID].orEmpty(),
            secret = this[Keys.SECRET].orEmpty(),
            allowSelfSigned = this[Keys.ALLOW_SELF_SIGNED] ?: false,
            fingerprint = this[Keys.FINGERPRINT]?.takeIf { it.isNotBlank() },
            refreshSeconds = (this[Keys.REFRESH_SECONDS] ?: 30).coerceIn(15, 300),
            themeMode = ThemeMode.from(this[Keys.THEME_MODE]),
            alertsEnabled = this[Keys.ALERTS_ENABLED] ?: true,
            rules = rules,
        )
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN_ID = stringPreferencesKey("token_id")
        val SECRET = stringPreferencesKey("secret")
        val ALLOW_SELF_SIGNED = booleanPreferencesKey("allow_self_signed")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
        val REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val RULE_CPU_ENABLED = booleanPreferencesKey("rule_cpu_enabled")
        val RULE_CPU_THRESHOLD = intPreferencesKey("rule_cpu_threshold")
        val RULE_MEM_ENABLED = booleanPreferencesKey("rule_mem_enabled")
        val RULE_MEM_THRESHOLD = intPreferencesKey("rule_mem_threshold")
        val RULE_DISK_ENABLED = booleanPreferencesKey("rule_disk_enabled")
        val RULE_DISK_THRESHOLD = intPreferencesKey("rule_disk_threshold")
        val RULE_GUEST_STOPPED = booleanPreferencesKey("rule_guest_stopped")
        val RULE_HOST_UNREACHABLE = booleanPreferencesKey("rule_host_unreachable")
    }
}
