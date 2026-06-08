package cz.smarteon.loxmcp.credentials

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class EnvCredentialSourceTest : ShouldSpec({

    context("get") {
        // Note: This test is environment-dependent. It only runs when LOXONE_HOST is not set.
        // For comprehensive testing, consider using a library like system-lambda to mock env vars.
        should("throw when environment variables are not set") {
            val source = EnvCredentialSource()

            if (System.getenv("LOXONE_HOST") == null) {
                shouldThrow<IllegalStateException> {
                    source.get()
                }.message shouldContain "LOXONE_HOST"
            }
        }
    }

    context("name") {
        should("return environment") {
            EnvCredentialSource().name shouldBe "environment"
        }
    }
})

class ArgsCredentialSourceTest : ShouldSpec({

    context("get") {
        should("return credentials") {
            val source = ArgsCredentialSource("192.168.1.1", "admin", "secret")

            val credentials = source.get()

            credentials.address shouldBe "192.168.1.1"
            credentials.username shouldBe "admin"
            credentials.password shouldBe "secret"
        }
    }

    context("name") {
        should("return command-line") {
            ArgsCredentialSource("a", "b", "c").name shouldBe "command-line"
        }
    }
})

class BitwardenCredentialSourceTest : ShouldSpec({

    context("get") {
        should("throw NotImplementedError") {
            val source = BitwardenCredentialSource("secret-123")

            shouldThrow<NotImplementedError> {
                source.get()
            }
        }
    }

    context("name") {
        should("return bitwarden") {
            BitwardenCredentialSource("x").name shouldBe "bitwarden"
        }
    }
})

class CredentialResolverTest : ShouldSpec({

    context("fromArgs") {
        should("return EnvCredentialSource when no args") {
            val source = CredentialResolver.fromArgs(arrayOf("--stdio"))

            source.shouldBeInstanceOf<EnvCredentialSource>()
        }

        should("return ArgsCredentialSource when all credential args provided") {
            val args = arrayOf("--address", "192.168.1.1", "--username", "admin", "--password", "secret")

            val source = CredentialResolver.fromArgs(args)

            source.shouldBeInstanceOf<ArgsCredentialSource>()
            val creds = source.get()
            creds.address shouldBe "192.168.1.1"
            creds.username shouldBe "admin"
            creds.password shouldBe "secret"
        }

        should("return ArgsCredentialSource with short args") {
            val args = arrayOf("--host", "10.0.0.1", "-u", "user", "-p", "pass")

            val source = CredentialResolver.fromArgs(args)

            source.shouldBeInstanceOf<ArgsCredentialSource>()
            val creds = source.get()
            creds.address shouldBe "10.0.0.1"
            creds.username shouldBe "user"
            creds.password shouldBe "pass"
        }

        should("return BitwardenCredentialSource when bitwarden-secret provided") {
            val args = arrayOf("--bitwarden-secret", "my-secret-id")

            val source = CredentialResolver.fromArgs(args)

            source.shouldBeInstanceOf<BitwardenCredentialSource>()
        }

        should("return BitwardenCredentialSource with bws-secret alias") {
            val args = arrayOf("--bws-secret", "secret-123")

            val source = CredentialResolver.fromArgs(args)

            source.shouldBeInstanceOf<BitwardenCredentialSource>()
        }

        should("prefer bitwarden over args") {
            val args = arrayOf(
                "--address", "host", "--username", "user", "--password", "pass",
                "--bitwarden-secret", "secret-id"
            )

            val source = CredentialResolver.fromArgs(args)

            source.shouldBeInstanceOf<BitwardenCredentialSource>()
        }

        should("throw when only some credential args provided") {
            val args = arrayOf("--address", "host", "--username", "user")

            shouldThrow<IllegalStateException> {
                CredentialResolver.fromArgs(args)
            }.message shouldContain "--password is required"
        }

        should("throw when address missing but others provided") {
            val args = arrayOf("--username", "user", "--password", "pass")

            shouldThrow<IllegalStateException> {
                CredentialResolver.fromArgs(args)
            }.message shouldContain "--address is required"
        }

        should("handle mixed args with mode") {
            val args = arrayOf("--stdio", "--address", "host", "-u", "user", "-p", "pass", "--sse")

            val source = CredentialResolver.fromArgs(args)

            source.shouldBeInstanceOf<ArgsCredentialSource>()
        }
    }
})
