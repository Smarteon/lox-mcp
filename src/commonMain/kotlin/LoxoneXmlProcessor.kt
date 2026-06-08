package cz.smarteon.loxmcp

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

/**
 * Processes Loxone project XML: sanitizes known quirks and optionally strips
 * UI-only elements/attributes to reduce size for LLM consumption.
 */
internal object LoxoneXmlProcessor {

    // Known Loxone XML bugs — duplicate attributes on the same element
    private val DUPLICATE_SERIAL = Regex("""(<[^>]*)(Serial="[^"]*")([^>]*)Serial="[^"]*"([^>]*>)""")
    private val DUPLICATE_DTYPE  = Regex("""(<[^>]*)(DType="[^"]*")([^>]*)DType="[^"]*"([^>]*>)""")
    private val DUPLICATE_PHN    = Regex("""(<[^>]*)(Phn="\d*")([^>]*)Phn="\d*"([^>]*>)""")

    // Attribute values that contain literal newlines (invalid XML).
    // Use [^"<>]* to prevent the match from crossing tag boundaries —
    // otherwise the closing " of one attribute and the opening " of the next
    // element's attribute can both be consumed, injecting &#xA; into the prolog.
    private val NEWLINE_IN_ATTR  = Regex(""""[^"<>]*\r?\n[^"<>]*"""")

    /**
     * Applies regex-level fixes for known Loxone XML quirks before DOM parsing:
     *  - removes duplicate Serial / DType / Phn attributes
     *  - escapes literal newlines inside attribute values
     */
    fun sanitize(xml: String): String {
        var result = xml
        result = DUPLICATE_SERIAL.replace(result, "$1$2$3$4")
        result = DUPLICATE_DTYPE.replace(result, "$1$2$3$4")
        result = DUPLICATE_PHN.replace(result, "$1$2$3$4")
        result = NEWLINE_IN_ATTR.replace(result) { m ->
            m.value.replace("\r\n", "&#xD;&#xA;").replace("\n", "&#xA;")
        }
        return result
    }

    // Tags whose C elements of the given Type attribute should be removed entirely
    private val removeCTypes = setOf(
        "LoxCaption",
        "IconState", "IconCat", "IconPlace",
        "IconCaptionState", "IconCaptionCat", "IconCaptionPlace"
    )

    // Element tag names to remove entirely (non-C elements)
    private val removeElements = setOf(
        "Display", "LightscenesC", "LightsceneC", "SeqConf", "Preset", "Col", "RightGroup"
    )

    // Attribute names to strip from all elements
    private val stripAttrs = listOf("V", "WF", "Cl", "Px", "Py", "Px2", "Py2", "BkColor", "Logo")

    /**
     * Parses the XML via ksoup, removes all UI-only elements/attributes, and
     * serialises back to a string. Fully multiplatform (ksoup is KMP).
     */
    fun slim(xml: String): String {
        val doc = Ksoup.parseXml(xml.trimStart('\uFEFF'))
        doc.childNodes().filterIsInstance<Element>().forEach { slimElement(it) }
        return doc.outerHtml()
    }

    private fun slimElement(el: Element): Boolean {
        val type = el.attr("Type")

        if (el.tagName() == "C" && type in removeCTypes) return true
        if (el.tagName() in removeElements) return true

        stripAttrs.forEach { el.removeAttr(it) }

        val toRemove = mutableListOf<Element>()
        for (child in el.children()) {
            val remove = if (el.tagName() == "C" && type == "Page" && child.tagName() == "C") {
                val hasLogicRef = child.attr("U").isNotEmpty()
                if (hasLogicRef) slimElement(child)
                !hasLogicRef
            } else {
                slimElement(child)
            }

            if (remove) toRemove.add(child)
        }
        toRemove.forEach { it.remove() }

        return false
    }
}