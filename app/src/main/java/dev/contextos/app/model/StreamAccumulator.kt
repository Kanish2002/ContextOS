package dev.contextos.app.model

/** LiteRT-LM streams message fragments. Keep a single user-visible response. */
class StreamAccumulator {
    private val buffer = StringBuilder()
    fun append(fragment: String): String {
        buffer.append(fragment)
        return buffer.toString()
    }
    fun value() = buffer.toString()
    fun clear() = buffer.clear()
}
