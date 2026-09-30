package dev.dewy

import dev.dewy.net.Entry
import dev.dewy.tasks.deflateEntry
import dev.dewy.tasks.inflateEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCodecTest {
    private fun entry(vararg fields: Pair<String, String>) = Entry(JsonObject(fields.associate { it.first to JsonPrimitive(it.second) }))

    @Test
    fun smallEntryRoundTrips() {
        val e = entry("id" to "w.a2.1", "kind" to "agent", "text" to "33 files.")
        assertEquals(e, inflateEntry(deflateEntry(e)))
    }

    @Test
    fun longOutputRoundTripsSmaller() {
        val output = (1..400).joinToString("\n") { "$it  worker stream reconnect entry thread äöü ✳" }
        val e = entry("id" to "t1.toolu_01", "kind" to "tool", "tool" to "Bash", "state" to "ok", "output" to output)
        val packed = deflateEntry(e)
        assertEquals(e, inflateEntry(packed))
        assertTrue("deflate shrinks a repetitive output", packed.size * 3 < output.encodeToByteArray().size)
    }
}
