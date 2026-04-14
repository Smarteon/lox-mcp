package cz.smarteon.loxmcp

import cz.smarteon.loxkt.LoxoneAuth
import cz.smarteon.loxkt.LoxoneClient
import cz.smarteon.loxkt.LoxoneCommands
import cz.smarteon.loxkt.LoxoneEndpoint
import cz.smarteon.loxkt.app.LoxoneApp
import cz.smarteon.loxkt.ktor.KtorHttpLoxoneClient
import cz.smarteon.loxkt.LoxoneCredentials
import cz.smarteon.loxkt.LoxoneProfile
import cz.smarteon.loxkt.state.LoxoneState
import cz.smarteon.loxkt.LoxoneTokenAuthenticator
import cz.smarteon.loxkt.app.Control
import cz.smarteon.loxkt.callForMsg
import cz.smarteon.loxkt.ktor.KtorWebsocketLoxoneClient
import cz.smarteon.loxkt.state.collectFrom
import cz.smarteon.loxkt.app.getAllValues
import cz.smarteon.loxkt.message.ApiInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Adapter that wraps the Loxone HTTP client and provides high-level operations
 * for the MCP server to use.
 *
 * Supports multiple address formats:
 * - Local IP: `192.168.1.100` or `192.168.1.100:8080`
 * - MAC address: `504F12345678` (resolved via Loxone Cloud DNS)
 * - URL: `https://dns.loxonecloud.com/504F12345678`
 *
 * @param scope CoroutineScope for managing WebSocket state updates lifecycle
 */
class LoxoneAdapter(
    private val address: String,
    private val username: String,
    private val password: String,
    private val scope: CoroutineScope
) {
    private var client: LoxoneClient? = null
    private var cachedApp: LoxoneApp? = null
    private var cachedVersion: String? = null

    private val state = LoxoneState()
    private var wsClient: KtorWebsocketLoxoneClient? = null

    /**
     * Lazily initializes and returns the HTTP client.
     */
    private fun getClient(): LoxoneClient = client
        ?: createHttpClient().also {
            client = it
            logger.info { "Connected to Loxone Miniserver at $address" }
        }

    private fun createHttpClient(): LoxoneClient {
        val endpoint = resolveEndpoint(address)
        val auth = LoxoneAuth.Basic(username, password)

        logger.debug { "Creating client with endpoint: $endpoint (resolved from $address)" }
        return KtorHttpLoxoneClient(endpoint, auth)
    }

    /**
     * Resolves the address string to a [LoxoneEndpoint].
     */
    private fun resolveEndpoint(address: String): LoxoneEndpoint {
        return when (resolveAddressType(address)) {
            AddressType.LOCAL -> {
                if (address.contains(COLON)) {
                    val (ip, port) = address.split(COLON)
                    LoxoneEndpoint.local(ip, port.toInt())
                } else {
                    LoxoneEndpoint.local(address)
                }
            }
            AddressType.MAC -> {
                val normalizedMac = address.replace(COLON, "")
                val url = "$LOX_DNS_URL$normalizedMac"
                LoxoneEndpoint.fromUrl(url)
            }
            AddressType.URL -> {
                val urlWithProtocol = if (address.startsWith(HTTP_PREFIX) || address.startsWith(HTTPS_PREFIX)) {
                    address
                } else {
                    "$HTTPS_PREFIX$address"
                }
                LoxoneEndpoint.fromUrl(urlWithProtocol)
            }
        }
    }

    /**
     * Determines the type of address provided.
     *
     * @param address The address string to analyze
     * @return The [AddressType] of the address
     * @throws IllegalArgumentException if the address format is not recognized or is blank
     */
    fun resolveAddressType(address: String): AddressType {
        require(address.isNotBlank()) { "Address cannot be empty or blank" }

        if (address.matches(IP_REGEX)) {
            return AddressType.LOCAL
        }

        val cleanAddress = address.replace(COLON, "")
        if (cleanAddress.matches(MAC_REGEX)) {
            return AddressType.MAC
        }

        if (address.matches(URL_REGEX)) {
            return AddressType.URL
        }

        throw IllegalArgumentException("Invalid address format: $address")
    }

    /**
     * Execute the API version command to test connectivity.
     */
    suspend fun getApiVersion(): String {
        return getClient().callRaw("jdev/cfg/api")
    }

    /**
     * Get the Miniserver firmware version string (e.g., "16.0.2.30").
     * Results are cached after first retrieval.
     */
    suspend fun getMiniserverVersion(): String? {
        cachedVersion?.let { return it }

        return try {
            val apiInfo = getClient().callForMsg(ApiInfo.command)
            apiInfo.version.also {
                cachedVersion = it
                logger.info { "Miniserver firmware version: $it" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get Miniserver version" }
            null
        }
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
     * Fetch raw bytes from a Loxone Miniserver path.
     * Used for binary endpoints such as the project config file.
     */
    suspend fun fetchRawBytes(path: String): ByteArray {
        val normalizedPath = path.removePrefix("/")
        logger.debug { "Fetching raw bytes: $normalizedPath" }
        return getClient().callRawForData(normalizedPath)
    }

    /**
     * Send a raw command path to the Loxone Miniserver.
     * Strips leading slash if present to ensure proper formatting.
     *
     * Use this for generic commands like "/jdev/sps/io/{uuid}/{action}"
     */
    suspend fun sendRawCommand(commandPath: String): String {
        val normalizedPath = commandPath.removePrefix("/")
        logger.debug { "Sending raw command: $normalizedPath" }
        return getClient().callRaw(normalizedPath)
    }

    /**
     * Send a command to control a device by UUID.
     */
    suspend fun sendCommand(uuid: String, command: String): String {
        logger.debug { "Sending command '$command' to device $uuid" }
        return getClient().callRaw("jdev/sps/io/$uuid/$command")
    }

    /**
     * Initialize WebSocket event streaming for state value updates.
     * Call this once at startup to enable state reading functionality.
     */
    suspend fun enableStateUpdates() {
        logger.info { "Enabling state updates" }

        val endpoint = resolveEndpoint(address)
        val profile = LoxoneProfile(endpoint, LoxoneCredentials(username, password))
        val auth = LoxoneTokenAuthenticator(profile)

        wsClient = KtorWebsocketLoxoneClient(endpoint, auth).also {
            scope.launch {
                state.collectFrom(it.events)
            }
            it.callForMsg(LoxoneCommands.App.enableBinStatusUpdate())
        }
        logger.info { "State updates enabled" }
    }

    /**
     * Get the underlying LoxoneState for direct access to all state values.
     * Use this for bulk state queries.
     */
    fun getState(): LoxoneState = state

    /**
     * Get all current state values for a control by its UUID.
     * Looks up the control's states in the structure file and returns their current values.
     *
     * @param control The control to get states for
     * @return Map of state name -> typed state value (ValueState or TextState)
     */
    suspend fun getControlStates(control: Control): Map<String, Any> {
        return control.getAllValues(state)
    }

    /**
     * Close the client connections.
     */
    suspend fun close() {
        wsClient?.close()
        wsClient = null
        client?.close()
        client = null
        cachedApp = null
        cachedVersion = null
        logger.info { "Disconnected from Loxone Miniserver" }
    }

    companion object {
        private const val HTTP_PREFIX = "http://"
        private const val HTTPS_PREFIX = "https://"
        private const val COLON = ":"
        private val IP_REGEX = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}(:[0-9]{1,5})?")
        private val MAC_REGEX = Regex("504F[0-9A-Fa-f]{8}")
        private val URL_REGEX = Regex("^(https?://)?([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(:[0-9]{1,5})?(/.*)?$")
        private const val LOX_DNS_URL = "https://dns.loxonecloud.com/"
    }
}

/**
 * Type of address used to connect to Loxone Miniserver.
 */
enum class AddressType {
    /** Local IP address, optionally with port */
    LOCAL,
    /** Loxone MAC address (starts with 504F) */
    MAC,
    /** Full URL or domain name */
    URL
}
