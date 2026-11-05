package com.meshalive.app.mesh

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meshalive.app.MainActivity
import com.meshalive.app.R

/** Real system notifications: SOS alarm on gateways, chats, delivery receipts, relay notices. */
class Notifier(private val context: Context) {
    private var nextId = 1000

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val alarm = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(
                NotificationChannel(CH_SOS, "Incoming SOS alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Loud alert when an SOS reaches this phone"
                    enableVibration(true)
                    vibrationPattern = SOS_PATTERN
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarm)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_MSG, "Messages and receipts", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Chats and Delivered receipts" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_RELAY, "Relay activity", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown when this phone relays an emergency for someone else" }
            )
        }
    }

    private fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun open(route: String): PendingIntent = PendingIntent.getActivity(
        context, route.hashCode(),
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(builder: NotificationCompat.Builder) {
        if (!canPost()) return
        runCatching { NotificationManagerCompat.from(context).notify(nextId++, builder.build()) }
    }

    fun sosReceived(type: String, from: String, text: String, lat: Double?, lng: Double?, hops: Int) {
        val where = if (lat != null && lng != null) "\n📍 %.5f, %.5f".format(lat, lng) else ""
        post(
            NotificationCompat.Builder(context, CH_SOS)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFD32F2F.toInt())
                .setContentTitle("🆘 $type from $from")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$text$where\nRelayed over $hops hop(s) through the mesh"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(SOS_PATTERN)
                .setAutoCancel(true)
                .setContentIntent(open("inbox"))
        )
        vibrate(SOS_PATTERN)
    }

    fun chatReceived(from: String, text: String, hops: Int) = post(
        NotificationCompat.Builder(context, CH_MSG)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(from)
            .setContentText(text)
            .setSubText("via mesh · $hops hop(s)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open("messages"))
    )

    fun delivered(type: String, gateway: String, hops: Int, latencyMs: Long) = post(
        NotificationCompat.Builder(context, CH_MSG)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2E7D32.toInt())
            .setContentTitle("✅ $type delivered")
            .setContentText("Reached $gateway in $hops hop(s) · ACK after $latencyMs ms")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open("messages"))
    )

    fun relayed(type: String, from: String, nextHop: String) = post(
        NotificationCompat.Builder(context, CH_RELAY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Relayed $type from $from")
            .setContentText("Passed on to $nextHop — your phone is helping")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(60_000)
            .setAutoCancel(true)
            .setContentIntent(open("network"))
    )

    fun rescueUpdate(title: String, note: String) {
        post(
            NotificationCompat.Builder(context, CH_SOS)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF2E7D32.toInt())
                .setContentTitle("🚤 $title")
                .setContentText(note.ifBlank { "Message from the district control room" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { "Message from the district control room" }))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(open("home"))
        )
        vibrate(longArrayOf(0, 300, 150, 300))
    }

    fun alert(level: String, title: String, text: String) {
        post(
            NotificationCompat.Builder(context, CH_SOS)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFD32F2F.toInt())
                .setContentTitle("📢 $level: $title")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(open("home"))
        )
        vibrate(SOS_PATTERN)
    }

    fun vibrate(pattern: LongArray = longArrayOf(0, 80)) {
        runCatching {
            val v = context.getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }

    companion object {
        const val CH_SOS = "sos_alerts_v2"
        const val CH_MSG = "messages"
        const val CH_RELAY = "relay"
        val SOS_PATTERN = longArrayOf(0, 500, 200, 500, 200, 900)
    }
}
