package dev.dewy.tasks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readStore: DataStore<Preferences> by preferencesDataStore("read_marks")

/** When the user last looked at each task, so newer activity shows as unread. */
class ReadMarks(context: Context) {
    private val store = context.readStore

    val seen: Flow<Map<String, Long>> = store.data.map { p ->
        p.asMap().entries.associate { (key, value) -> key.name to (value as Long) }
    }

    suspend fun markSeen(taskId: String, at: Long) = store.edit { it[longPreferencesKey(taskId)] = at }
}
