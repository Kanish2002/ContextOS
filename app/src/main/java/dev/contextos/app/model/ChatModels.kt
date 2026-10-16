package dev.contextos.app.model

enum class DeviceBackend { CPU, GPU }
enum class Role { USER, ASSISTANT }
data class ChatMessage(val id: Long, val role: Role, val content: String, val generating: Boolean = false)
data class Benchmark(
    val loadMillis: Long? = null,
    val firstChunkMillis: Long? = null,
    val generateMillis: Long? = null,
    val characters: Int = 0
) {
    // Character throughput is not the same as tokenizer-based tokens/sec.
    val charsPerSecond: Double
        get() = if (generateMillis != null && generateMillis > 0)
            characters * 1000.0 / generateMillis else 0.0
}
data class AssistantState(
    val modelName: String? = null,
    val modelSize: Long = 0,
    val backend: DeviceBackend = DeviceBackend.CPU,
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val generating: Boolean = false,
    val status: String = "Choose a LiteRT-LM model to begin",
    val importPercent: Int? = null,
    val messages: List<ChatMessage> = emptyList(),
    val benchmark: Benchmark? = null,
    val error: String? = null,
    val memorySuggestion: String? = null,
    val deviceRamBytes: Long = 0
)
