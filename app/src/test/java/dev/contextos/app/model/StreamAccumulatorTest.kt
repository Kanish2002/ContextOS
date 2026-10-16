package dev.contextos.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamAccumulatorTest {
    @Test fun combinesStreamedFragmentsWithoutSpaces() {
        val a = StreamAccumulator()
        assertEquals("Hello", a.append("Hello"))
        assertEquals("Hello world", a.append(" world"))
        assertEquals("Hello world", a.value())
    }
    @Test fun emptyFragmentDoesNotModifyOutput() {
        val a = StreamAccumulator()
        a.append("A")
        assertEquals("A", a.append(""))
    }
    @Test fun characterThroughputIsNotMislabeledTokens() {
        val b = Benchmark(generateMillis = 2000, characters = 30)
        assertEquals(15.0, b.charsPerSecond, 0.001)
    }
}
