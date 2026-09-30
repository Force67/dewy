package dev.dewy.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ServerException(val status: Int, message: String) : IOException(message)

/** The orchestrator's HTTP API, see orchestrator/src/api.aki. */
class DewyClient(serverUrl: String, private val token: String, private val http: OkHttpClient = sharedHttp) {
    private val base = (serverUrl.trimEnd('/') + "/").toHttpUrl()
    private val streamHttp = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    suspend fun health() = call(request("api/health").get().build()) { }

    suspend fun state(): BoardState = call(request("api/state").get().build()) { wireJson.decodeFromString(it) }

    suspend fun thread(id: String): TaskThread =
        call(request("api/tasks/$id").get().build()) { wireJson.decodeFromString(it) }

    suspend fun startTask(
        box: String,
        project: String,
        agent: String,
        model: String,
        text: String,
        images: List<String>,
        yolo: Boolean,
    ): Task {
        val body = buildJsonObject {
            put("box", box)
            put("project", project)
            put("agent", agent)
            put("model", model)
            put("text", text)
            putJsonArray("images") { images.forEach { add(it) } }
            put("yolo", yolo)
        }
        return call(request("api/tasks").post(body.toString().toRequestBody(jsonType)).build()) {
            wireJson.decodeFromString(it)
        }
    }

    suspend fun attachSession(box: String, session: String, yolo: Boolean): Task {
        val body = buildJsonObject {
            put("box", box)
            put("session", session)
            put("yolo", yolo)
        }
        return call(request("api/sessions/attach").post(body.toString().toRequestBody(jsonType)).build()) {
            wireJson.decodeFromString(it)
        }
    }

    suspend fun steer(id: String, text: String, images: List<String> = emptyList()) =
        post(
            "api/tasks/$id/messages",
            buildJsonObject {
                put("text", text)
                putJsonArray("images") { images.forEach { add(it) } }
            }.toString(),
        )

    /** Stores an image on the orchestrator; the answer is its id. */
    suspend fun uploadImage(bytes: ByteArray, type: String): String =
        call(request("api/images").post(bytes.toRequestBody(type.toMediaType())).build()) {
            (wireJson.parseToJsonElement(it) as JsonObject).getValue("id").let { v -> (v as JsonPrimitive).content }
        }

    suspend fun image(id: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(request("api/images/$id").get().build()).await().use { response ->
            if (!response.isSuccessful) throw ServerException(response.code, "the server answered ${response.code}")
            response.body.bytes()
        }
    }

    suspend fun stop(id: String) = post("api/tasks/$id/stop", "{}")

    suspend fun setModel(id: String, model: String) =
        post("api/tasks/$id/model", buildJsonObject { put("model", model) }.toString())

    suspend fun archive(id: String, archived: Boolean) =
        post("api/tasks/$id/archive", buildJsonObject { put("archived", JsonPrimitive(archived)) }.toString())

    /**
     * Every change after [since] in the orchestrator start [epoch], until the
     * connection drops or the collector stops. A position from another start
     * gets a reset.
     */
    fun events(since: Long, epoch: Long): Flow<StreamEvent> = callbackFlow {
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                runCatching { ServerEvent.parse(type, data) }.getOrNull()?.let { trySend(StreamEvent(id?.toLongOrNull(), it)) }
            }

            override fun onClosed(eventSource: EventSource) {
                close(IOException("the stream closed"))
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: ServerException(response?.code ?: 0, "the stream failed"))
            }
        }
        val source = EventSources.createFactory(streamHttp)
            .newEventSource(request("api/stream?since=$since&epoch=$epoch").get().build(), listener)
        awaitClose { source.cancel() }
    }

    private suspend fun post(path: String, json: String) =
        call(request(path).post(json.toRequestBody(jsonType)).build()) { }

    private fun request(path: String) = Request.Builder()
        .url(base.resolve(path) ?: throw IOException("bad server address"))
        .header("Authorization", "Bearer $token")

    private suspend fun <T> call(request: Request, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val body = http.newCall(request).await().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching {
                    (wireJson.parseToJsonElement(text) as? JsonObject)?.get("error")?.let { (it as? JsonPrimitive)?.content }
                }.getOrNull()
                throw ServerException(response.code, message ?: "the server answered ${response.code}")
            }
            text
        }
        parse(body)
    }

    private companion object {
        val jsonType = "application/json".toMediaType()
        val sharedHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
    })
    cont.invokeOnCancellation { cancel() }
}
