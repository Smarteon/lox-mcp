package cz.smarteon.loxmcp

import cz.smarteon.loxkt.LoxoneAuth
import cz.smarteon.loxkt.LoxoneClient
import cz.smarteon.loxkt.LoxoneEndpoint
import cz.smarteon.loxkt.ktor.KtorHttpLoxoneClient
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Adapter that wraps the Loxone HTTP client and provides high-level operations
 * for the MCP server to use.
 */
class LoxoneAdapter(
    private val host: String,
    private val username: String,
    private val password: String
) {
    private var client: LoxoneClient? = null

    /**
     * Lazily initializes and returns the HTTP client.
     */
    private fun getClient(): LoxoneClient = client
        ?: createHttpClient().also {
            client = it
            logger.info { "Connected to Loxone Miniserver at $host" }
        }

    private fun createHttpClient(): LoxoneClient {
        val endpoint = LoxoneEndpoint.fromUrl(host)
        val auth = LoxoneAuth.Basic(username, password)

        return KtorHttpLoxoneClient(endpoint, auth)
    }

    /**
     * Execute the API version command to test connectivity.
     */
    suspend fun getApiVersion(): String {
        return getClient().callRaw("jdev/cfg/api")
    }

    /**
     * Execute a raw command string on the Loxone Miniserver.
     */
    suspend fun callRaw(command: String): String {
        return getClient().callRaw(command)
    }

    /**
     * Close the client connection.
     */
    suspend fun close() {
        client?.close()
        client = null
        logger.info { "Disconnected from Loxone Miniserver" }
    }
}
