package cz.smarteon.loxmcp.credentials

import cz.smarteon.loxmcp.getEnv
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Loxone server credentials.
 */
data class LoxoneCredentials(
    val address: String,
    val username: String,
    val password: String
)

/**
 * Interface for providing Loxone credentials from various sources.
 */
interface CredentialSource {
    /**
     * Source identifier for logging.
     */
    val name: String

    /**
     * Retrieves credentials from this source.
     *
     * @throws IllegalStateException if credentials are not available or incomplete
     */
    fun get(): LoxoneCredentials
}

/**
 * Provides credentials from environment variables.
 *
 * Required variables:
 * - LOXONE_HOST: Miniserver address
 * - LOXONE_USER: Username
 * - LOXONE_PASS: Password
 */
class EnvCredentialSource : CredentialSource {
    override val name = "environment"

    override fun get(): LoxoneCredentials {
        logger.info { "Loading credentials from environment variables" }
        return LoxoneCredentials(
            address = getRequiredEnv(ENV_HOST),
            username = getRequiredEnv(ENV_USER),
            password = getRequiredEnv(ENV_PASS)
        )
    }

    private fun getRequiredEnv(name: String): String =
        getEnv(name) ?: error("Environment variable $name is required")

    companion object {
        const val ENV_HOST = "LOXONE_HOST"
        const val ENV_USER = "LOXONE_USER"
        const val ENV_PASS = "LOXONE_PASS"
    }
}

/**
 * Provides credentials from command-line arguments.
 */
class ArgsCredentialSource(
    private val address: String,
    private val username: String,
    private val password: String
) : CredentialSource {
    override val name = "command-line"

    override fun get(): LoxoneCredentials {
        logger.info { "Using credentials from command-line arguments" }
        return LoxoneCredentials(address, username, password)
    }
}

/**
 * Skeleton for Bitwarden Secrets Manager integration.
 *
 * @param secretId The Bitwarden secret ID containing Loxone credentials
 */
@Suppress("UnusedPrivateProperty")
class BitwardenCredentialSource(
    private val secretId: String
) : CredentialSource {
    override val name = "bitwarden"

    override fun get(): LoxoneCredentials {
        logger.info { "Loading credentials from Bitwarden Secrets Manager" }
        throw NotImplementedError(
            "Bitwarden integration not yet implemented. " +
                "Set LOXONE_HOST, LOXONE_USER, LOXONE_PASS environment variables instead."
        )
    }
}
