package cz.smarteon.loxmcp.server

import cz.smarteon.loxkt.app.Category
import cz.smarteon.loxkt.app.LoxoneApp
import cz.smarteon.loxkt.app.Room
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findCategoryByName
import cz.smarteon.loxmcp.server.LoxoneQueryHelper.findRoomByName
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class LoxoneQueryHelperTest : ShouldSpec({

    context("findRoomByName") {
        should("find room by exact name") {
            val room = Room(uuid = "room1", name = "Living Room", image = "")
            val app = mockk<LoxoneApp> {
                every { rooms } returns mapOf("room1" to room)
            }

            val result = app.findRoomByName("Living Room")

            result.shouldNotBeNull()
            result.uuid shouldBe "room1"
        }

        should("find room by case-insensitive name") {
            val room = Room(uuid = "room1", name = "Living Room", image = "")
            val app = mockk<LoxoneApp> {
                every { rooms } returns mapOf("room1" to room)
            }

            val result = app.findRoomByName("living room")

            result.shouldNotBeNull()
            result.uuid shouldBe "room1"
        }

        should("return null when room not found") {
            val app = mockk<LoxoneApp> {
                every { rooms } returns emptyMap()
            }

            val result = app.findRoomByName("Nonexistent")

            result.shouldBeNull()
        }

        should("find first matching room when multiple rooms exist") {
            val room1 = Room(uuid = "room1", name = "Living Room", image = "")
            val room2 = Room(uuid = "room2", name = "Bedroom", image = "")
            val app = mockk<LoxoneApp> {
                every { rooms } returns mapOf("room1" to room1, "room2" to room2)
            }

            val result = app.findRoomByName("Bedroom")

            result.shouldNotBeNull()
            result.uuid shouldBe "room2"
        }
    }

    context("findCategoryByName") {
        should("find category by exact name") {
            val category = Category(uuid = "cat1", name = "Lights", type = "lighting", color = "")
            val app = mockk<LoxoneApp> {
                every { cats } returns mapOf("cat1" to category)
            }

            val result = app.findCategoryByName("Lights")

            result.shouldNotBeNull()
            result.uuid shouldBe "cat1"
        }

        should("find category by case-insensitive name") {
            val category = Category(uuid = "cat1", name = "Lights", type = "lighting", color = "")
            val app = mockk<LoxoneApp> {
                every { cats } returns mapOf("cat1" to category)
            }

            val result = app.findCategoryByName("LIGHTS")

            result.shouldNotBeNull()
            result.uuid shouldBe "cat1"
        }

        should("return null when category not found") {
            val app = mockk<LoxoneApp> {
                every { cats } returns emptyMap()
            }

            val result = app.findCategoryByName("Nonexistent")

            result.shouldBeNull()
        }

        should("find first matching category when multiple categories exist") {
            val category1 = Category(uuid = "cat1", name = "Lights", type = "lighting", color = "")
            val category2 = Category(uuid = "cat2", name = "Heating", type = "climate", color = "")
            val app = mockk<LoxoneApp> {
                every { cats } returns mapOf("cat1" to category1, "cat2" to category2)
            }

            val result = app.findCategoryByName("Heating")

            result.shouldNotBeNull()
            result.uuid shouldBe "cat2"
        }
    }
})
