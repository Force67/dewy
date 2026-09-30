package dev.dewy

import dev.dewy.net.Box
import dev.dewy.net.wireJson
import dev.dewy.usage.ago
import dev.dewy.usage.compactTokens
import dev.dewy.usage.meterTitle
import dev.dewy.usage.until
import java.util.Locale
import dev.dewy.usage.parseCaps
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTest {
    @Test
    fun decodesMetersAsTheOrchestratorSendsThem() {
        val box = wireJson.decodeFromString<Box>(
            """{"name":"lab","usage":[
              {"agent":"codex","provider":"openai","plan":"prolite","at":1790779257882,
               "limits":[{"name":"7 days","used":0.78,"resets":1791143267000}],"spend":[]},
              {"agent":"opencode","provider":"opencode-go","plan":"","at":1790779264359,"limits":[],
               "spend":[{"name":"30 days","cost":4.135515057999994,"tokens":101995173}]}]}""",
        )
        assertEquals(0.78, box.usage[0].limits[0].used, 1e-9)
        assertEquals(1791143267000, box.usage[0].limits[0].resets)
        assertEquals(101995173.0, box.usage[1].spend[0].tokens, 0.0)
        assertEquals("opencode · Go", meterTitle(box.usage[1]))
        assertEquals("Codex", meterTitle(box.usage[0]))
    }

    @Test
    fun aBoxFromBeforeUsageHasNone() {
        assertEquals(emptyList<Any>(), wireJson.decodeFromString<Box>("""{"name":"old"}""").usage)
    }

    @Test
    fun capsKeepOnlyWhatReadsAsDollars() {
        assertEquals(
            mapOf("5 hours" to 12.0, "30 days" to 60.5),
            parseCaps(mapOf("5 hours" to "12", "7 days" to "", "30 days" to "60,5", "1 day" to "lots")),
        )
    }

    @Test
    fun countsAndTimesReadPlainly() {
        Locale.setDefault(Locale.ROOT)
        assertEquals("23.5K", compactTokens(23537.0))
        assertEquals("102M", compactTokens(101995173.0))
        assertEquals("2M", compactTokens(2_000_000.0))
        assertEquals("1M", compactTokens(999_960.0))
        assertEquals("950", compactTokens(950.0))
        assertEquals("4 min ago", ago(4 * 60_000 + 5_000))
        assertEquals("3 days ago", ago(3 * 86_400_000L))
        assertEquals("2 h 14 min", until((2 * 60 + 14) * 60_000L))
        assertEquals("6 days 5 h", until((6 * 24 + 5) * 3_600_000L))
    }
}
