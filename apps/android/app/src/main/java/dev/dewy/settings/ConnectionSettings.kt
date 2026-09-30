package dev.dewy.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

data class Connection(val serverUrl: String, val token: String)

private val Context.connectionStore: DataStore<Preferences> by preferencesDataStore("connection")

/**
 * Where the orchestrator is, the token for it, whether to notify, whether
 * new tasks start in yolo mode, the model picked last, and the budgets.
 */
class ConnectionSettings(context: Context) {
    private val store = context.connectionStore

    val connection: Flow<Connection?> = store.data.map { p ->
        val url = p[urlKey]
        val token = p[tokenKey]
        if (url.isNullOrBlank() || token.isNullOrBlank()) null else Connection(url, token)
    }

    val notify: Flow<Boolean> = store.data.map { it[notifyKey] ?: true }

    val yolo: Flow<Boolean> = store.data.map { it[yoloKey] ?: false }

    val model: Flow<String> = store.data.map { it[modelKey] ?: "" }

    /** Dollar caps per opencode provider, by spend window name ("5 hours"). */
    val budgets: Flow<Map<String, Map<String, Double>>> = store.data.map { p ->
        p[budgetsKey]?.let { runCatching { Json.decodeFromString<Map<String, Map<String, Double>>>(it) }.getOrNull() }.orEmpty()
    }

    suspend fun save(connection: Connection) = store.edit {
        it[urlKey] = connection.serverUrl
        it[tokenKey] = connection.token
    }

    suspend fun forget() = store.edit { it.clear() }

    suspend fun setNotify(on: Boolean) = store.edit { it[notifyKey] = on }

    suspend fun setYolo(on: Boolean) = store.edit { it[yoloKey] = on }

    suspend fun setModel(model: String) = store.edit { it[modelKey] = model }

    suspend fun setBudget(provider: String, caps: Map<String, Double>) = store.edit { p ->
        val all = p[budgetsKey]?.let { runCatching { Json.decodeFromString<Map<String, Map<String, Double>>>(it) }.getOrNull() }.orEmpty()
        p[budgetsKey] = Json.encodeToString(if (caps.isEmpty()) all - provider else all + (provider to caps))
    }

    private companion object {
        val urlKey = stringPreferencesKey("server_url")
        val tokenKey = stringPreferencesKey("token")
        val notifyKey = booleanPreferencesKey("notify")
        val yoloKey = booleanPreferencesKey("yolo")
        val modelKey = stringPreferencesKey("model")
        val budgetsKey = stringPreferencesKey("budgets")
    }
}
