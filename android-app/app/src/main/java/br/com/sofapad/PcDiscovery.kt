package br.com.sofapad

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

data class DiscoveredPc(val name: String, val host: String, val port: Int)

@Suppress("DEPRECATION")
class PcDiscovery(context: Context, private val onDevices: (List<DiscoveredPc>) -> Unit) : AutoCloseable {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, DiscoveredPc>()
    private val waiting = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var running = false
    private var closed = false
    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) { main.post { running = true; if (closed) close() } }
        override fun onDiscoveryStopped(type: String) { main.post { running = false } }
        override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { running = false } }
        override fun onStopDiscoveryFailed(type: String, code: Int) { main.post { running = false } }
        override fun onServiceLost(service: NsdServiceInfo) { main.post { devices.remove(service.serviceName); if (!closed) onDevices(devices.values.toList()) } }
        override fun onServiceFound(service: NsdServiceInfo) {
            main.post { if (!closed && waiting.size < 32 && service.serviceType.startsWith("_couchremote._tcp")) { waiting.addLast(service); resolveNext() } }
        }
    }
    fun start() { closed = false; if (!running) runCatching { manager.discoverServices("_couchremote._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) } }
    private fun resolveNext() {
        if (resolving || closed) return
        val service = waiting.removeFirstOrNull() ?: return
        resolving = true
        runCatching { manager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { main.post { resolving = false; resolveNext() } }
            override fun onServiceResolved(info: NsdServiceInfo) {
                main.post {
                val address = info.host?.hostAddress
                if (!closed && devices.size < 32 && address != null && !address.contains(':')) {
                    devices[info.serviceName] = DiscoveredPc(info.serviceName, address, info.port)
                    onDevices(devices.values.toList())
                }
                resolving = false; resolveNext()
                }
            }
        }) }.onFailure { resolving = false; resolveNext() }
    }
    override fun close() { closed = true; waiting.clear(); if (running) runCatching { manager.stopServiceDiscovery(listener) }; running = false }
}
