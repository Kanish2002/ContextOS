import dev.contextos.app.model.Benchmark
import dev.contextos.app.model.StreamAccumulator

fun main() {
    val accumulator = StreamAccumulator()
    check(accumulator.append("On-") == "On-")
    check(accumulator.append("device") == "On-device")
    check(Benchmark(generateMillis = 2000L, characters = 40).charsPerSecond == 20.0)
    println("Smoke tests passed: streaming state + benchmark math")
}
