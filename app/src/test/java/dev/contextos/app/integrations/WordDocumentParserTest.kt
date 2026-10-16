package dev.contextos.app.integrations

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WordDocumentParserTest {
    private fun document(xml: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test fun extractsParagraphsAndEscapedText() {
        val word = document(
            """<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            """<w:body><w:p><w:r><w:t>Plan A &amp; B</w:t></w:r></w:p>""" +
            """<w:p><w:r><w:t>Kafka &lt; Redis</w:t></w:r></w:p></w:body></w:document>"""
        )
        assertEquals("Plan A & B\nKafka < Redis", WordDocumentParser.extract(word))
    }

    @Test fun malformedAndExternalEntityDocumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WordDocumentParser.extract(document("<doc>Not a Word document</doc>"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WordDocumentParser.extract(document(
                """<!DOCTYPE w:document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><w:document>&xxe;</w:document>"""
            ))
        }
    }
}
