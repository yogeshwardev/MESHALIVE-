package com.meshalive.app.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Battery, internet, Bluetooth, location helpers. Never throws. */
class DeviceStatus(private val context: Context) {

    private val _hasInternet = MutableStateFlow(false)
    val hasInternet: StateFlow<Boolean> = _hasInternet

    private var callbackRegistered = false

    fun startInternetMonitor() {
        if (callbackRegistered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        _hasInternet.value = isValidated(cm)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _hasInternet.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }

                override fun onLost(network: Network) {
                    _hasInternet.value = isValidated(cm)
                }
            })
            callbackRegistered = true
        }
    }

    private fun isValidated(cm: ConnectivityManager): Boolean = runCatching {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    fun batteryPercent(): Int = runCatching {
        val bm = context.getSystemService(BatteryManager::class.java)
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 100
    }.getOrDefault(100)

    @SuppressLint("MissingPermission")
    fun isBluetoothOn(): Boolean = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    fun isLocationOn(): Boolean = runCatching {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Best-effort current GPS fix within 8 s, falling back to the last known location. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasLocationPermission() || !isLocationOn()) return null
        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val fresh = withTimeoutOrNull(8_000) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
            if (fresh == null) cts.cancel()
            fresh ?: client.lastLocation.await()
        }.getOrNull()
    }
}
