package cz.smarteon.loxmcp

import cz.smarteon.loxkt.LoxoneAuth
import cz.smarteon.loxkt.LoxoneClient
import cz.smarteon.loxkt.LoxoneCommands
import cz.smarteon.loxkt.LoxoneEndpoint
import cz.smarteon.loxkt.app.LoxoneApp
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
    private var cachedApp: LoxoneApp? = null

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
     * Get the LoxoneApp structure file.
     * This contains all rooms, controls, and categories.
     * Results are cached after first retrieval.
     */
    suspend fun getApp(): LoxoneApp {
        cachedApp?.let { return it }

        logger.info { "Fetching LoxoneApp structure from Miniserver" }
        val app = getClient().call(LoxoneCommands.App.get())
        cachedApp = app

        logger.info { "LoxoneApp cached: lastModified=${app.lastModified}" }
        return app
    }


    /**
     * Execute a raw command string on the Loxone Miniserver.
     */
    suspend fun callRaw(command: String): String {
        return getClient().callRaw(command)
    }

    /**
     * Send a command to control a device by UUID.
     */
    suspend fun sendCommand(uuid: String, command: String): String {
        logger.debug { "Sending command '$command' to device $uuid" }
        return getClient().callRaw("jdev/sps/io/$uuid/$command")
    }

    /**
     * Close the client connection.
     */
    suspend fun close() {
        client?.close()
        client = null
        cachedApp = null
        logger.info { "Disconnected from Loxone Miniserver" }
    }
}
