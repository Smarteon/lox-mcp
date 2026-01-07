package cz.smarteon.loxmcp.loxonedocs

import kotlinx.serialization.Serializable

/**
 * Summary of a Loxone control for list views.
 */
@Serializable
data class LoxoneControlSummary(
    val name: String,
    val statesCount: Int,
    val commandsCount: Int,
    val detailsCount: Int
)

/**
 * Represents a Loxone control type from the official documentation.
 *
 * Examples: Hourcounter, InfoOnlyAnalog, Intelligent Room Controller v2, etc.
 */
@Serializable
data class LoxoneControl(
    val name: String,
    val coveredConfigItems: List<String> = emptyList(),
    val states: List<ControlField> = emptyList(),
    val commands: List<ControlCommand> = emptyList(),
    val details: List<ControlField> = emptyList()
)

/**
 * Represents a state or detail field in a control.
 */
@Serializable
data class ControlField(
    val name: String,
    val description: String,
    val enumValues: Map<Int, String>? = null
)

/**
 * Represents a command that can be sent to a control.
 */
@Serializable
data class ControlCommand(
    val name: String,
    val description: String,
    val effects: List<String> = emptyList()
)
