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

data class Connection(val serverUrl: String, val token: String)

private val Context.connectionStore: DataStore<Preferences> by preferencesDataStore("connection")

/** Where the orchestrator is, the token for it, whether to notify, and whether new tasks start in yolo mode. */
class ConnectionSettings(context: Context) {
    private val store = context.connectionStore

    val connection: Flow<Connection?> = store.data.map { p ->
        val url = p[urlKey]
        val token = p[tokenKey]
        if (url.isNullOrBlank() || token.isNullOrBlank()) null else Connection(url, token)
    }

    val notify: Flow<Boolean> = store.data.map { it[notifyKey] ?: true }

    val yolo: Flow<Boolean> = store.data.map { it[yoloKey] ?: false }

    suspend fun save(connection: Connection) = store.edit {
        it[urlKey] = connection.serverUrl
        it[tokenKey] = connection.token
    }

    suspend fun forget() = store.edit { it.clear() }

    suspend fun setNotify(on: Boolean) = store.edit { it[notifyKey] = on }

    suspend fun setYolo(on: Boolean) = store.edit { it[yoloKey] = on }

    private companion object {
        val urlKey = stringPreferencesKey("server_url")
        val tokenKey = stringPreferencesKey("token")
        val notifyKey = booleanPreferencesKey("notify")
        val yoloKey = booleanPreferencesKey("yolo")
    }
}
