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
 *
 * Supports multiple address formats:
 * - Local IP: `192.168.1.100` or `192.168.1.100:8080`
 * - MAC address: `504F12345678` (resolved via Loxone Cloud DNS)
 * - URL: `https://dns.loxonecloud.com/504F12345678`
 */
class LoxoneAdapter(
    private val address: String,
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
                LoxoneEndpoint.fromUrl(LOX_DNS_URL + normalizedMac)
            }
            AddressType.URL -> LoxoneEndpoint.fromUrl(address)
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

    companion object {
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
