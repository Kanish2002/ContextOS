package dev.contextos.app.memory

import dev.contextos.app.model.ChatMessage
import dev.contextos.app.model.Role
import org.junit.Assert.*
import org.junit.Test

class MemorySummaryPolicyTest {
 @Test fun transcriptIsBoundedAndExplicit() {
   val conversation = (1L..20L).map { ChatMessage(it, Role.USER, "message-" + it + "x".repeat(1000)) }
   val text = MemorySummaryPolicy.transcript(conversation)
   assertTrue(text.length <= 2500)
   assertFalse(text.contains("message-1x"))
   assertTrue(text.contains("message-20"))
 }
 @Test fun proposalIsNotLongerThanVaultAllows() {
   assertTrue(MemorySummaryPolicy.cleanProposal("Memory: " + "a".repeat(900)).length <= 480)
 }
}
