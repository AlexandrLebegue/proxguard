package dev.proxboss.proxguard.data.api

import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ApiException(val code: Int, override val message: String) : IOException(message) {
    companion object {
        fun fromHttp(code: Int, body: String): ApiException {
            val serverMessage = runCatching {
                JSONObject(body).optString("message").takeIf { it.isNotBlank() }
            }.getOrNull()?.take(180)
            val friendly = when (code) {
                401 -> "Authentification refusée (401). Vérifie l'identifiant de jeton et le secret."
                403 -> "Accès refusé (403). Le jeton n'a pas les permissions nécessaires."
                404 -> "Ressource introuvable (404) sur l'hôte Proxmox."
                in 500..599 -> {
                    val suffix = if (serverMessage != null) " : $serverMessage" else "."
                    "Erreur du serveur Proxmox ($code)$suffix"
                }
                else -> "Réponse inattendue du serveur ($code)."
            }
            return ApiException(code, friendly)
        }
    }
}

fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> message
    is UnknownHostException -> "Hôte introuvable. Vérifie l'URL et ta connexion réseau."
    is SocketTimeoutException -> "Le serveur ne répond pas. Vérifie l'URL et le réseau."
    is ConnectException -> "Connexion impossible à l'hôte Proxmox. Vérifie qu'il est allumé et joignable."
    is SSLException -> "Certificat TLS refusé. Active « Accepter le certificat auto-signé » ou vérifie l'empreinte."
    else -> "Erreur inattendue${message?.let { " : $it" } ?: "."}"
}

class NoNodeException : IOException("Aucun nœud Proxmox n'a été trouvé sur ce serveur.")
