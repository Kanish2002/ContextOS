package dev.contextos.app.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dev.contextos.app.model.DeviceBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Single-flight inference wrapper. Model lifecycle is controlled by the ViewModel. */
class LocalInferenceEngine {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun open(modelPath: String, deviceBackend: DeviceBackend, cacheDir: String) =
        withContext(Dispatchers.IO) {
            close()
            val runtime = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = when (deviceBackend) {
                        DeviceBackend.CPU -> Backend.CPU()
                        DeviceBackend.GPU -> Backend.GPU()
                    },
                    cacheDir = cacheDir
                )
            )
            try {
                runtime.initialize()
                val chat = runtime.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "You are ContextOS, an offline personal assistant. " +
                            "Be helpful and concise. Do not claim to have access to " +
                            "live sensors, personal files, calendar, location, or internet. " +
                            "Only use optional approximate context explicitly attached to the user message; " +
                            "never claim independent access to device data."
                        ),
                        maxOutputToken = 384
                    )
                )
                engine = runtime
                conversation = chat
            } catch (t: Throwable) {
                runtime.close()
                throw t
            }
        }

    fun generate(prompt: String): Flow<String> {
        val chat = checkNotNull(conversation) { "Load a local model first." }
        return chat.sendMessageAsync(prompt).map { it.toString() }
    }

    suspend fun freshConversation() = withContext(Dispatchers.IO) {
        val runtime = checkNotNull(engine) { "Load a local model first." }
        conversation?.close()
        conversation = runtime.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are ContextOS, a helpful offline personal assistant. " +
                    "Device context is available only when the user opts to attach a summary to a message. " +
                    "Do not pretend to have any live sensors or network access."
                ),
                maxOutputToken = 384
            )
        )
    }

    fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
