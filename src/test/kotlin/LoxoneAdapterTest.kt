package cz.smarteon.loxmcp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class LoxoneAdapterTest : ShouldSpec({

    context("resolveAddressType") {
        val adapter = LoxoneAdapter("127.0.0.1", "user", "password")

        should("resolve local IP address") {
            val result = adapter.resolveAddressType("192.168.0.1")
            result shouldBe AddressType.LOCAL
        }

        should("resolve local IP address with port") {
            val result = adapter.resolveAddressType("192.168.0.1:8080")
            result shouldBe AddressType.LOCAL
        }

        should("resolve MAC address without colons") {
            val result = adapter.resolveAddressType("504F12345678")
            result shouldBe AddressType.MAC
        }

        should("resolve MAC address with colons") {
            val result = adapter.resolveAddressType("50:4F:12:34:56:78")
            result shouldBe AddressType.MAC
        }

        should("resolve URL address") {
            val result = adapter.resolveAddressType("example.com")
            result shouldBe AddressType.URL
        }

        should("resolve full URL with protocol") {
            val result = adapter.resolveAddressType("https://my.loxone.server")
            result shouldBe AddressType.URL
        }

        should("throw exception for invalid address") {
            shouldThrow<IllegalArgumentException> {
                adapter.resolveAddressType("invalid_address")
            }
        }

        should("throw exception for empty address") {
            shouldThrow<IllegalArgumentException> {
                adapter.resolveAddressType("")
            }
        }

        should("resolve edge case IP 0.0.0.0") {
            val result = adapter.resolveAddressType("0.0.0.0")
            result shouldBe AddressType.LOCAL
        }

        should("resolve edge case IP 255.255.255.255") {
            val result = adapter.resolveAddressType("255.255.255.255")
            result shouldBe AddressType.LOCAL
        }

        should("resolve localhost") {
            val result = adapter.resolveAddressType("localhost")
            result shouldBe AddressType.URL
        }
    }
})
