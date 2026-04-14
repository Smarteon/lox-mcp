package cz.smarteon.loxmcp

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Processes Loxone project XML: sanitizes known quirks and optionally strips
 * UI-only elements/attributes to reduce size for LLM consumption.
 */
internal object LoxoneXmlProcessor {

    // <C Type="..."> values that are pure UI — remove element + all descendants
    private val REMOVE_C_TYPES = setOf(
        "LoxCaption",
        "IconState", "IconCat", "IconPlace",
        "IconCaptionState", "IconCaptionCat", "IconCaptionPlace"
    )

    // Non-<C> element names to remove entirely
    private val REMOVE_ELEMENTS = setOf(
        "Display", "LightscenesC", "LightsceneC", "SeqConf", "Preset", "Col", "RightGroup"
    )

    // Attributes to strip from every surviving element
    private val STRIP_ATTRS = listOf("V", "WF", "Cl", "Px", "Py", "Px2", "Py2", "BkColor", "Logo")

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

    /**
     * Parses the XML, removes all UI-only elements/attributes, and serialises
     * back to a string. Reduces file size by ≥70% for typical Loxone projects.
     */
    fun slim(xml: String): String {
        // Strip UTF-8 BOM (\uFEFF) — Loxone Miniserver includes it; Java's InputSource rejects it
        val cleanXml = xml.trimStart('\uFEFF')
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Harden against XXE: disable DOCTYPE declarations, external entities,
            // XInclude and all external resource access. The XML comes from a network
            // device and must never trigger outbound resolution.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(cleanXml)))

        slimElement(doc.documentElement)

        val writer = StringWriter()
        val transformerFactory = TransformerFactory.newInstance().apply {
            // Harden against SSRF/file reads during serialisation
            setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }
        transformerFactory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    /**
     * Recursively processes [el]. Returns `true` when the caller should remove
     * this element from its parent.
     */
    private fun slimElement(el: Element): Boolean {
        val type = el.getAttribute("Type") // "" when absent — never null in DOM

        // Remove entire subtree for UI-only <C> types
        if (el.tagName == "C" && type in REMOVE_C_TYPES) return true
        // Remove non-<C> UI elements
        if (el.tagName in REMOVE_ELEMENTS) return true

        // Strip cosmetic attributes from this element
        STRIP_ATTRS.forEach { el.removeAttribute(it) }

        // Recurse into children, collecting those that should be removed
        val toRemove = mutableListOf<Node>()
        val children = el.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childEl = child as Element

            // For a Page canvas, strip layout-only <C> children (no U = no logic reference).
            // Children with a U attribute are kept but still need to be slimmed.
            val remove = if (el.tagName == "C" && type == "Page" && childEl.tagName == "C") {
                val hasLogicRef = childEl.getAttribute("U").isNotEmpty()
                if (hasLogicRef) slimElement(childEl)
                !hasLogicRef
            } else {
                slimElement(childEl)
            }

            if (remove) toRemove.add(child)
        }
        toRemove.forEach { el.removeChild(it) }

        return false
    }
}
