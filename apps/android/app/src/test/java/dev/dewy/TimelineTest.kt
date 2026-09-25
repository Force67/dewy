package dev.dewy

import dev.dewy.net.Entry
import dev.dewy.tasks.withEntry
import dev.dewy.thread.Item
import dev.dewy.thread.summarize
import dev.dewy.thread.timeline
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    private fun entry(id: String, kind: String, vararg more: Pair<String, String>) =
        Entry(JsonObject(mapOf("id" to JsonPrimitive(id), "kind" to JsonPrimitive(kind)) + more.associate { it.first to JsonPrimitive(it.second) }))

    @Test
    fun toolRunsFold() {
        val items = timeline(
            listOf(
                entry("u", "user"),
                entry("a", "tool", "tool" to "Bash"),
                entry("b", "tool", "tool" to "Edit"),
                entry("m", "agent"),
                entry("c", "tool", "tool" to "Read"),
            ),
        )
        assertEquals(listOf("u", "steps:a", "m", "steps:c"), items.map { it.key })
        assertEquals(2, (items[1] as Item.Steps).tools.size)
    }

    @Test
    fun summaries() {
        val tools = listOf(entry("a", "tool", "tool" to "Bash"), entry("b", "tool", "tool" to "shell"), entry("c", "tool", "tool" to "Edit"))
        assertEquals("Ran 2 commands, made 1 edit", summarize(tools))
    }

    @Test
    fun updatesMergeById() {
        val thread = listOf(entry("a", "tool", "tool" to "Bash", "state" to "running"))
        val merged = thread.withEntry(Entry(JsonObject(mapOf("id" to JsonPrimitive("a"), "state" to JsonPrimitive("ok")))))
        assertEquals(1, merged.size)
        assertEquals("ok", merged[0].toolState)
        assertEquals("Bash", merged[0].tool)
    }
}
