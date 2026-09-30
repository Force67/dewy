package dev.dewy

import dev.dewy.net.Box
import dev.dewy.net.wireJson
import dev.dewy.usage.ago
import dev.dewy.usage.compactTokens
import dev.dewy.usage.meterTitle
import dev.dewy.usage.until
import dev.dewy.usage.spendLine
import dev.dewy.net.Task
import dev.dewy.net.TaskState
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

    @Test
    fun aTaskSaysWhatItUsed() {
        Locale.setDefault(Locale.ROOT)
        val claude = wireJson.decodeFromString<Task>(
            """{"id":"7","box":"godbox","project":"/src","agent":"claude","title":"t","state":"working","created":1,"updated":2,
               "spend":{"tokens":184000,"cost":0.42,"share":[{"name":"5 hours","used":0.031},{"name":"7 days","used":0.004},{"name":"x","used":0.0001}]}}""",
        )
        assertEquals("184K tokens · $0.42 at API prices · ≈ 3.1% of 5 hours · ≈ 0.4% of 7 days", spendLine(claude, emptyMap()))
        val go = claude.copy(agent = "opencode", model = "opencode-go/kimi-k3", spend = claude.spend.copy(share = emptyList()))
        assertEquals("opencode-go", go.provider)
        assertEquals("184K tokens · $0.42 · 3.5% of the budget for 5 hours", spendLine(go, mapOf("5 hours" to 12.0)))
        assertEquals(null, spendLine(claude.copy(spend = claude.spend.copy(tokens = 0.0, cost = 0.0)), emptyMap()))
        assertEquals(TaskState.Working, claude.state)
    }
}
