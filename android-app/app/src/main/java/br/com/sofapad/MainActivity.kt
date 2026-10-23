package br.com.sofapad

import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var discovery: PcDiscovery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        simulator.loadCredentials(applicationContext)
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

    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStop() {
        discovery?.close(); discovery = null
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        networkCallback = null
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        // Girar o celular recria a Activity: mantém a conexão aberta e só solta os botões.
        if (isChangingConfigurations) simulator.releaseAll() else simulator.pause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        simulator.resume()
        discovery = PcDiscovery(applicationContext) { simulator.discovered(it) }.also { it.start() }
        // Sem isto o Wi-Fi do celular entra em economia entre toques e atrasa comandos em 100–200 ms.
        // Só vale com o app em primeiro plano e a tela ligada; é liberado em onStop.
        runCatching {
            val lock = wifiLock ?: getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "SofaPad:controle")
                .apply { setReferenceCounted(false) }
            wifiLock = lock
            lock.acquire()
        }
        val main = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { simulator.networkAvailable() } }
        }
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback,
            )
            networkCallback = callback
        }
    }
}
