package dev.contextos.app.integrations

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Bounded text extraction for explicitly selected Word DOCX files.
 * Does not execute macros, follow hyperlinks, or fetch external content.
 */
internal object WordDocumentParser {
    private const val MAX_DOCX_BYTES = 16 * 1024 * 1024
    private const val MAX_XML_BYTES = 8 * 1024 * 1024
    private const val MAX_TEXT_CHARS = 12 * KnowledgeSearch.MAX_TEXT

    fun extract(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_DOCX_BYTES) {
            "Word document exceeds the 16 MB import limit."
        }
        var doc: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            for (i in 0 until 256) {
                val entry = archive.nextEntry ?: break
                if (!entry.isDirectory && entry.name == "word/document.xml") {
                    doc = archive.readBoundedBytes(MAX_XML_BYTES)
                    archive.closeEntry()
                    break
                }
                archive.closeEntry()
            }
        }
        val xml = doc ?: error("Not a readable .docx Word document.")
        val raw = String(xml, Charsets.UTF_8)
        require(!raw.contains("<!DOCTYPE", ignoreCase = true) &&
            !raw.contains("<!ENTITY", ignoreCase = true)) {
            "Word XML contains unsupported external entities."
        }
        val out = StringBuilder()
        var inText = false
        fun append(value: String) {
            val remaining = MAX_TEXT_CHARS - out.length
            if (remaining > 0) out.append(value.take(remaining))
        }
        val reader = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newSAXParser().xmlReader
        listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities"
        ).forEach { reader.setFeature(it, false) }
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
        reader.contentHandler = object : DefaultHandler() {
            private fun tag(localName: String?, qualified: String?): String =
                localName?.takeIf { it.isNotBlank() } ?: qualified.orEmpty().substringAfter(':')
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (tag(localName, qName)) {
                    "t" -> inText = true
                    "p", "br" -> if (out.isNotEmpty() && out.last() != '\n') append("\n")
                    "tab" -> append("\t")
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (tag(localName, qName) == "t") inText = false
            }
            override fun characters(chars: CharArray, start: Int, length: Int) {
                if (inText) append(String(chars, start, length))
            }
        }
        reader.parse(InputSource(ByteArrayInputStream(xml)))
        return out.toString().trim().also {
            require(it.isNotEmpty()) { "Word document contains no readable text." }
        }
    }
}
