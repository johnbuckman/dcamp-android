package com.decentespresso.dcamp.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Online/offline monitor backed by ConnectivityManager — the Android counterpart of iOS's
 * Reachability (NWPathMonitor). Exposes [isOnline] as a StateFlow the UI observes to show the
 * full-screen offline panel.
 */
class Connectivity(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { recheck() }
        override fun onLost(network: Network) { recheck() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { recheck() }
    }

    fun start() { runCatching { cm.registerDefaultNetworkCallback(callback) } }

    /** Force a fresh read of the active network — used by the offline panel's "Try again". */
    fun recheck() { _isOnline.value = currentlyOnline() }

    private fun currentlyOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
