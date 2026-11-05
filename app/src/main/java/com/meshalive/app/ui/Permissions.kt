package com.meshalive.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PermissionInfo(val permissions: List<String>, val title: String, val why: String, val required: Boolean)

/** The runtime permissions MeshAlive needs on the current Android version (8 - 14). */
object Permissions {
    fun groups(): List<PermissionInfo> {
        val sdk = Build.VERSION.SDK_INT
        val list = mutableListOf<PermissionInfo>()
        list += PermissionInfo(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            "Location",
            "Attaches your GPS position to an SOS so rescuers can find you." +
                if (sdk < Build.VERSION_CODES.S) " On this Android version it is also needed to find nearby phones over Bluetooth." else "",
            required = sdk < Build.VERSION_CODES.S,
        )
        if (sdk >= Build.VERSION_CODES.S) {
            list += PermissionInfo(
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT),
                "Nearby devices (Bluetooth)",
                "Finds and connects to other MeshAlive phones around you, with no towers or internet.",
                required = true,
            )
        }
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            list += PermissionInfo(
                listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                "Nearby Wi-Fi devices",
                "Lets phones switch to a faster direct Wi-Fi link to pass messages along.",
                required = true,
            )
            list += PermissionInfo(
                listOf(Manifest.permission.POST_NOTIFICATIONS),
                "Notifications",
                "Shows the \"MeshAlive relay active\" notification so relaying continues in the background.",
                required = false,
            )
        }
        if (sdk < Build.VERSION_CODES.Q) {
            list += PermissionInfo(
                listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                "Storage",
                "Saves simulator results as CSV in your Downloads folder.",
                required = false,
            )
        }
        return list
    }

    fun granted(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun groupGranted(context: Context, g: PermissionInfo) = g.permissions.all { granted(context, it) } ||
        // coarse-only location is acceptable as "granted" for display purposes
        (g.title == "Location" && granted(context, Manifest.permission.ACCESS_FINE_LOCATION))

    fun allRequiredGranted(context: Context) = groups().filter { it.required }.all { groupGranted(context, it) }

    fun allRequested(): Array<String> = groups().flatMap { it.permissions }.toTypedArray()
}
