package dev.proxboss.proxguard

import dev.proxboss.proxguard.data.api.ApiException
import dev.proxboss.proxguard.data.api.PveClient
import dev.proxboss.proxguard.data.config.AppConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PveClientTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Ressource absente : $name" }.readText()

    private fun clientFor(server: MockWebServer): PveClient {
        val config = AppConfig(
            baseUrl = server.url("/").toString().trimEnd('/'),
            tokenId = "root@pam!proxguard",
            secret = "secret-de-test",
        )
        return PveClient(config, PveClient.buildHttpClient(config))
    }

    @Test
    fun `appelle l API avec le jeton et parse la reponse`() {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(resource("nodes.json"))
            )
            server.start()
            val client = clientFor(server)

            val json = client.get("/nodes")

            assertEquals(1, json.getJSONArray("data").length())
            val request = server.takeRequest()
            assertEquals("/api2/json/nodes", request.path)
            assertEquals(
                "PVEAPIToken=root@pam!proxguard=secret-de-test",
                request.getHeader("Authorization"),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `une reponse 401 remonte une erreur explicite`() {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody(""))
            server.start()
            val client = clientFor(server)

            val error = try {
                client.get("/nodes")
                null
            } catch (exception: ApiException) {
                exception
            }

            assertTrue(error != null)
            assertEquals(401, error!!.code)
            assertTrue(error.message.contains("401"))
            assertTrue(error.message.contains("Authentification"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `une reponse 500 remonte le message du serveur`() {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody(resource("error500.json"))
            )
            server.start()
            val client = clientFor(server)

            val error = try {
                client.get("/nodes/pve/invalide")
                null
            } catch (exception: ApiException) {
                exception
            }

            assertTrue(error != null)
            assertEquals(500, error!!.code)
            assertTrue(error.message.contains("Erreur du serveur Proxmox (500)"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `le secret n apparait jamais dans la representation de la configuration`() {
        val config = AppConfig(
            baseUrl = "https://pve.example.lan:8006",
            tokenId = "root@pam!proxguard",
            secret = "mon-secret-tres-sensible",
        )
        assertFalse(config.toString().contains("mon-secret-tres-sensible"))
        assertTrue(config.toString().contains("secret=***"))
    }
}
