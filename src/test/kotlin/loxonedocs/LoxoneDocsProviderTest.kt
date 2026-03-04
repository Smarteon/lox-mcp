package cz.smarteon.loxmcp.loxonedocs

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LoxoneDocsProviderTest : ShouldSpec({

    context("DocsVersion") {
        should("parse X.Y format") {
            val version = DocsVersion.parse("16.0")
            version.shouldNotBeNull()
            version.major shouldBe 16
            version.minor shouldBe 0
        }

        should("parse X.Y.Z.W format extracting only X.Y") {
            val version = DocsVersion.parse("16.0.2.30")
            version.shouldNotBeNull()
            version.major shouldBe 16
            version.minor shouldBe 0
        }

        should("return null for invalid format") {
            DocsVersion.parse("invalid") shouldBe null
            DocsVersion.parse("16") shouldBe null
        }

        should("compare versions correctly") {
            val v14 = DocsVersion(14, 5)
            val v15 = DocsVersion(15, 0)
            val v16 = DocsVersion(16, 0)

            (v14 < v15) shouldBe true
            (v15 < v16) shouldBe true
            (v16 > v14) shouldBe true
        }

        should("format as X.Y string") {
            DocsVersion(16, 0).toString() shouldBe "16.0"
        }
    }

    context("version resolution") {
        should("use highest when no Miniserver version provided") {
            val version = LoxoneDocsProvider.resolveDocsVersion(null)
            version.shouldNotBeNull()
        }

        should("handle unparseable Miniserver version") {
            val version = LoxoneDocsProvider.resolveDocsVersion("invalid")
            version.shouldNotBeNull()
        }
    }
})
