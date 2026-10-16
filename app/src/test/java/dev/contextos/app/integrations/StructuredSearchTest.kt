package dev.contextos.app.integrations

import org.junit.Assert.*
import org.junit.Test

class StructuredSearchTest {
    @Test fun naturalLanguageDateFiltersAreRecognized() {
        assertTrue(KnowledgeSearch.structuredQuery("What did I write last Tuesday?"))
        assertTrue(KnowledgeSearch.structuredQuery("Which projects haven't I completed?"))
        assertTrue(KnowledgeSearch.structuredQuery("Show pending career goals"))
    }

    @Test fun regularKnowledgeQuestionMayUseSemanticSearch() {
        assertFalse(KnowledgeSearch.structuredQuery("Explain Kafka partition ordering"))
    }
}
