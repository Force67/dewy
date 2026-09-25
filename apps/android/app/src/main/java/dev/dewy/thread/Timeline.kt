package dev.dewy.thread

import dev.dewy.net.Entry
import dev.dewy.net.EntryKind

/** What the thread shows: runs of tool calls fold into one [Steps] item. */
sealed interface Item {
    val key: String

    data class Said(val entry: Entry) : Item {
        override val key get() = entry.id
    }

    data class Steps(val tools: List<Entry>) : Item {
        override val key get() = "steps:" + tools.first().id
        val running get() = tools.count { it.toolState == "running" }
        val failed get() = tools.count { it.toolState == "error" }
    }
}

fun timeline(entries: List<Entry>): List<Item> {
    val out = mutableListOf<Item>()
    val run = mutableListOf<Entry>()
    fun flush() {
        if (run.isNotEmpty()) out += Item.Steps(run.toList())
        run.clear()
    }
    for (e in entries) {
        if (e.kind == EntryKind.Tool) {
            run += e
        } else {
            flush()
            out += Item.Said(e)
        }
    }
    flush()
    return out
}

/** "Ran 3 commands, edited 2 files, read 4 files". */
fun summarize(tools: List<Entry>): String {
    val counts = linkedMapOf<String, Int>()
    for (t in tools) {
        val verb = when (t.tool.lowercase()) {
            "bash", "shell" -> "command"
            "edit", "multiedit", "write", "notebookedit" -> "edit"
            "read" -> "read"
            "grep", "glob", "search", "websearch" -> "search"
            "webfetch" -> "fetch"
            "task", "agent" -> "subagent"
            "todowrite" -> "plan"
            else -> "tool call"
        }
        counts[verb] = (counts[verb] ?: 0) + 1
    }
    return counts.entries.joinToString(", ") { (verb, n) ->
        when (verb) {
            "command" -> "ran $n ${plural(n, "command")}"
            "edit" -> "made $n ${plural(n, "edit")}"
            "read" -> "read $n ${plural(n, "file")}"
            "search" -> "searched $n ${plural(n, "time")}"
            "fetch" -> "fetched $n ${plural(n, "page")}"
            "subagent" -> "sent $n ${plural(n, "subagent")}"
            "plan" -> "updated the plan"
            else -> "$n ${plural(n, "tool call")}"
        }
    }.replaceFirstChar { it.uppercase() }
}

private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
