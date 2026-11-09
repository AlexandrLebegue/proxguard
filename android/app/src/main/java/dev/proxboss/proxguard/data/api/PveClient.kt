package dev.proxboss.proxguard.data.api

import dev.proxboss.proxguard.data.config.AppConfig
import dev.proxboss.proxguard.data.model.ConnectionTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

class PveClient(
    private val config: AppConfig,
    private val client: OkHttpClient = buildHttpClient(config),
) {

    fun get(path: String): JSONObject = execute(path).json

    fun testConnection(): ConnectionTest {
        val nodesResponse = execute("/nodes")
        val nodes = PveParsers.parseNodes(nodesResponse.json)
        if (nodes.isEmpty()) throw NoNodeException()
        val node = nodes.firstOrNull { it.online } ?: nodes.first()
        val statusResponse = execute("/nodes/${node.node}/status")
        val status = PveParsers.parseNodeStatus(statusResponse.json)
        return ConnectionTest(
            pveVersion = status.pveVersion.ifBlank { "Proxmox VE" },
            nodeName = node.node,
            nodeCount = nodes.size,
            fingerprint = statusResponse.fingerprint ?: nodesResponse.fingerprint,
        )
    }

    private fun execute(path: String): ApiResponse {
        val base = config.apiBaseUrl
        require(base.isNotBlank()) { "URL du serveur manquante" }
        val request = Request.Builder()
            .url(base + path)
            .header("Authorization", "PVEAPIToken=${config.tokenId}=${config.secret}")
            .header("Accept", "application/json")
            .header("User-Agent", "ProxGuard/1.0 (Android)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException.fromHttp(response.code, body)
            }
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw ApiException(response.code, "Réponse illisible du serveur Proxmox.")
            }
            val fingerprint = runCatching {
                (response.handshake?.peerCertificates?.firstOrNull() as? X509Certificate)?.let(::certificateFingerprint)
            }.getOrNull()
            return ApiResponse(json, fingerprint)
        }
    }

    private data class ApiResponse(val json: JSONObject, val fingerprint: String?)

    companion object {
        fun buildHttpClient(config: AppConfig): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(35, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)

            val host = config.host
            if (config.allowSelfSigned && !host.isNullOrBlank()) {
                val trustManager = SelfSignedHostTrustManager(host, config.fingerprint)
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
                builder.hostnameVerifier { hostname, _ -> hostname.equals(host, ignoreCase = true) }
            }
            return builder.build()
        }

        fun certificateFingerprint(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString(":") { byte -> "%02X".format(byte) }
    }
}

private class SelfSignedHostTrustManager(
    private val expectedHost: String,
    private val expectedFingerprint: String?,
) : X509ExtendedTrustManager() {

    private val delegate: X509TrustManager = run {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        validate(chain, null, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost
        validate(chain, host, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        validate(chain, engine?.peerHost, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    private fun validate(chain: Array<out X509Certificate>?, host: String?, authType: String?) {
        if (host != null && !host.equals(expectedHost, ignoreCase = true)) {
            throw CertificateException("Certificat présenté pour un hôte non autorisé ($host).")
        }
        val certificates = chain ?: throw CertificateException("Chaîne de certificats absente.")
        if (certificates.isEmpty()) throw CertificateException("Chaîne de certificats vide.")
        try {
            delegate.checkServerTrusted(certificates, authType ?: "RSA")
            return
        } catch (_: Exception) {
            // Certificat auto-signé : accepté uniquement pour cet hôte, avec épinglage si connu.
        }
        val fingerprint = PveClient.certificateFingerprint(certificates[0])
        if (expectedFingerprint != null && !expectedFingerprint.equals(fingerprint, ignoreCase = true)) {
            throw CertificateException(
                "L'empreinte du certificat a changé. Attendu : $expectedFingerprint. Reçu : $fingerprint."
            )
        }
    }
}
