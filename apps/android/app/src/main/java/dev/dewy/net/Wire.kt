package dev.dewy.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val wireJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
enum class TaskState {
    @SerialName("working") Working,
    @SerialName("idle") Idle,
    @SerialName("stalled") Stalled,
    @SerialName("failed") Failed,
    @SerialName("stopped") Stopped;

    /** The agent is waiting for the user: it finished, got stuck, or broke. */
    val needsYou get() = this == Idle || this == Stalled || this == Failed
    val isLive get() = this == Working || this == Stalled
}

@Serializable
data class Task(
    val id: String,
    val box: String,
    val project: String,
    val agent: String,
    val title: String,
    val session: String = "",
    val preview: String = "",
    val activity: String = "",
    val state: TaskState,
    val archived: Boolean = false,
    /** Its turns run without permission prompts. */
    val yolo: Boolean = false,
    /** opencode's provider/model; empty for the agent's default. */
    val model: String = "",
    val created: Long,
    val updated: Long,
    val turns: Int = 0,
) {
    val projectName get() = project.trimEnd('/').substringAfterLast('/')
}

@Serializable
data class Project(val name: String, val path: String)

@Serializable
data class BoxStats(
    val load: Double = 0.0,
    val cpus: Int = 0,
    val memTotal: Double = 0.0,
    val memUsed: Double = 0.0,
    val uptime: Double = 0.0,
)

@Serializable
data class Box(
    val name: String,
    val host: String = "",
    val os: String = "",
    val online: Boolean = false,
    val lastSeen: Long = 0,
    val agents: List<String> = emptyList(),
    val projects: List<Project> = emptyList(),
    /** The models each agent offers, where the box lists them. */
    val models: Map<String, List<String>> = emptyMap(),
    val stats: BoxStats = BoxStats(),
    val sessions: List<TerminalSession> = emptyList(),
)

/** A Claude Code session running in a terminal on a box, which a task can follow. */
@Serializable
data class TerminalSession(
    val id: String,
    val project: String = "",
    val name: String = "",
    val state: TaskState = TaskState.Idle,
    val started: Long = 0,
) {
    val projectName get() = project.trimEnd('/').substringAfterLast('/')
}

@Serializable
data class BoardState(val seq: Long, val boxes: List<Box>, val tasks: List<Task>, val epoch: Long = 0)

/**
 * One line of a task's thread. The server merges updates by id, so a tool
 * call arrives once running and again with its result; [mergedWith] does the
 * same here.
 */
@Serializable(with = EntrySerializer::class)
data class Entry(val fields: JsonObject) {
    val id get() = str("id")
    val kind get() = EntryKind.from(str("kind"))
    val text get() = str("text")
    val tool get() = str("tool")
    val detail get() = str("detail")
    val output get() = str("output")
    val toolState get() = str("state")
    val at get() = (fields["at"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0L

    /** Images you attached, or that the agent looked at. */
    val images: List<ImageRef>
        get() = (fields["images"] as? JsonArray).orEmpty().mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            ImageRef(id, (o["type"] as? JsonPrimitive)?.content.orEmpty())
        }

    private fun str(key: String) = (fields[key] as? JsonPrimitive)?.content ?: ""

    fun mergedWith(update: Entry) = Entry(JsonObject(fields + update.fields))
}

/** An image stored on the orchestrator, named by the SHA-256 of its bytes. */
data class ImageRef(val id: String, val type: String)

enum class EntryKind {
    User, Agent, Tool, Note, Error;

    companion object {
        fun from(name: String) = when (name) {
            "user" -> User
            "tool" -> Tool
            "note" -> Note
            "error" -> Error
            else -> Agent
        }
    }
}

@Serializable
data class TaskThread(
    val id: String,
    val entries: List<Entry> = emptyList(),
    /** The feed position the thread was read at. */
    val seq: Long = 0,
)

/** A change the server streams: an updated task or box, or a thread entry. */
/** An event and its position in the orchestrator's feed; a reset has none. */
class StreamEvent(val seq: Long?, val event: ServerEvent)

sealed interface ServerEvent {
    data class TaskChanged(val task: Task) : ServerEvent
    data class BoxChanged(val box: Box) : ServerEvent
    data class EntryAdded(val taskId: String, val entry: Entry) : ServerEvent
    data object Reset : ServerEvent

    companion object {
        fun parse(type: String?, data: String): ServerEvent? = when (type) {
            "task" -> TaskChanged(wireJson.decodeFromString(data))
            "box" -> BoxChanged(wireJson.decodeFromString(data))
            "entry" -> {
                val o = wireJson.parseToJsonElement(data).jsonObject
                EntryAdded(o.getValue("task").jsonPrimitive.content, Entry(o.getValue("entry").jsonObject))
            }
            "reset" -> Reset
            else -> null
        }
    }
}

internal object EntrySerializer : KSerializer<Entry> {
    override val descriptor = JsonObject.serializer().descriptor
    override fun deserialize(decoder: Decoder) =
        Entry(JsonObject.serializer().deserialize(decoder))

    override fun serialize(encoder: Encoder, value: Entry) =
        JsonObject.serializer().serialize(encoder, value.fields)
}
