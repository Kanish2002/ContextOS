package dev.contextos.app.integrations

import org.junit.Assert.*
import org.junit.Test

class StructuredSearchTest {
    @Test fun naturalLanguageDateFiltersAreRecognized() {
        assertTrue(KnowledgeSearch.structuredQuery("What did I write last Tuesday?"))
        assertTrue(KnowledgeSearch.structuredQuery("Which projects haven't I completed?"))
        assertTrue(KnowledgeSearch.structuredQuery("Show pending career goals"))
    }

    @Test fun detectsInterruptedNegationButNotPositiveCompletion() {
        assertTrue(KnowledgeSearch.structuredQuery("Which projects haven't I finished?"))
        assertTrue(KnowledgeSearch.structuredQuery("Which projects haven’t I completed?"))
        assertFalse(KnowledgeSearch.structuredQuery("Show completed projects"))
        assertFalse(KnowledgeSearch.structuredQuery("What Java topics should I study?"))
    }

    @Test fun regularKnowledgeQuestionMayUseSemanticSearch() {
        assertFalse(KnowledgeSearch.structuredQuery("Explain Kafka partition ordering"))
    }
}
