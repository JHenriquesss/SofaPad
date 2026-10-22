package br.com.sofapad

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "sofapad_preferences")
enum class Orientation(val label: String) { AUTO("Automática"), PORTRAIT("Vertical"), LANDSCAPE("Horizontal") }
data class UiPreferences(val orientation: Orientation = Orientation.AUTO, val leftHanded: Boolean = false, val sensitivity: Float = 1f)

class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.preferences
    private val orientationKey = stringPreferencesKey("orientation")
    private val handedKey = booleanPreferencesKey("left_handed")
    private val sensitivityKey = floatPreferencesKey("sensitivity")

    val values = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        UiPreferences(
            Orientation.entries.find { it.name == values[orientationKey] } ?: Orientation.AUTO,
            values[handedKey] ?: false,
            (values[sensitivityKey] ?: 1f).let { if (it.isFinite()) it.coerceIn(0.4f, 1.8f) else 1f },
        )
    }

    suspend fun save(value: UiPreferences) {
        store.edit {
            it[orientationKey] = value.orientation.name
            it[handedKey] = value.leftHanded
            it[sensitivityKey] = value.sensitivity
        }
    }
}
