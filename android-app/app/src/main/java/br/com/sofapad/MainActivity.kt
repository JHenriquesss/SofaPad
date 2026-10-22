package br.com.sofapad

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val simulator: Simulator by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repository = remember { PreferencesRepository(applicationContext) }
            val preferences by repository.values.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            val loaded = preferences
            if (loaded != null) {
                LaunchedEffect(loaded.orientation) {
                    val desired = when (loaded.orientation) {
                        Orientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    if (requestedOrientation != desired) requestedOrientation = desired
                }
                SofaPadApp(simulator, loaded) { updated -> scope.launch { repository.save(updated) } }
            }
        }
    }

    override fun onStop() {
        simulator.pause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        simulator.resume()
    }
}
