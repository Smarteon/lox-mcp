package cz.smarteon.loxmcp.credentials

/**
 * Resolves the appropriate credential source based on command-line arguments.
 */
object CredentialResolver {

    /**
     * Determines the credential source from command-line arguments.
     *
     * @param args Command-line arguments
     * @return The appropriate credential source
     * @throws IllegalStateException if required arguments are missing for the selected source
     */
    fun fromArgs(args: Array<String>): CredentialSource {
        val parsed = parseArgs(args)

        return when {
            // Bitwarden takes priority if specified
            parsed.bitwardenSecret != null -> BitwardenCredentialSource(parsed.bitwardenSecret)
            // If any credential arg is provided, all must be provided
            parsed.hasAnyCredentialArg() -> ArgsCredentialSource(
                address = parsed.address ?: error("--address is required when using command-line credentials"),
                username = parsed.username ?: error("--username is required when using command-line credentials"),
                password = parsed.password ?: error("--password is required when using command-line credentials")
            )
            // Default to environment variables
            else -> EnvCredentialSource()
        }
    }

    private fun parseArgs(args: Array<String>): ParsedArgs {
        var address: String? = null
        var username: String? = null
        var password: String? = null
        var bitwardenSecret: String? = null

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--address", "--host" -> address = args.getOrNull(++i)
                "--username", "-u" -> username = args.getOrNull(++i)
                "--password", "-p" -> password = args.getOrNull(++i)
                "--bitwarden-secret", "--bws-secret" -> bitwardenSecret = args.getOrNull(++i)
            }
            i++
        }

        return ParsedArgs(address, username, password, bitwardenSecret)
    }

    private data class ParsedArgs(
        val address: String?,
        val username: String?,
        val password: String?,
        val bitwardenSecret: String?
    ) {
        fun hasAnyCredentialArg() = address != null || username != null || password != null
    }
}
