package com.meshalive.app.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.meshalive.app.MainActivity
import com.meshalive.app.MeshAliveApp
import com.meshalive.app.R

/** Foreground service that keeps advertising, discovering and relaying in the background. */
class MeshService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            // Android 14 refuses a connectedDevice FGS without the Bluetooth/Wi-Fi permissions.
            Log.w("MeshService", "Cannot start foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        (application as MeshAliveApp).mesh.start()
        return START_STICKY
    }

    override fun onDestroy() {
        (application as MeshAliveApp).mesh.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mesh relay", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Keeps MeshAlive relaying messages for nearby phones" }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshAlive relay active")
            .setContentText("Relaying emergency messages for nearby phones")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mesh_relay"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.meshalive.app.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java)) }
                .onFailure { Log.w("MeshService", "start failed: ${it.message}") }
        }
    }
}
