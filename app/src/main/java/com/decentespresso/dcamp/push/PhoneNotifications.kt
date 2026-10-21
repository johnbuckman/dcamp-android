package com.decentespresso.dcamp.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.decentespresso.dcamp.MainActivity
import com.decentespresso.dcamp.R
import com.decentespresso.dcamp.api.Notif
import com.decentespresso.dcamp.api.PlainText
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

/**
 * System (phone) notifications. Phase 1 = no push service: [NotifPollWorker] polls the same
 * `notif_poll` queue the in-app toast uses about every 15 minutes (WorkManager's floor) while
 * the app is in the background, posts one notification per item, and acks them with
 * `notif_seen` — exactly what the web and iOS toasts do (`seen` is only the toast queue; the
 * bell's read state is separate). While the app is on screen the in-app toast handles them.
 *
 * Without POST_NOTIFICATIONS in the manifest, Android 13+ shows the app's notification switch
 * greyed out with "All notifications from this app are blocked".
 */
object PhoneNotifications {
    const val EXTRA_ROUTE = "dcamp_route"
    private const val WORK_NAME = "dcamp_notif_poll"

    /** Route from a tapped notification, consumed by MainView once logged in. */
    val pendingRoute = MutableStateFlow<String?>(null)

    /** True while an activity is visible — the worker leaves those to the in-app toast. */
    @Volatile var inForeground = false

    private data class Channel(val id: String, val name: String, val desc: String)
    private val channels = listOf(
        Channel("dm", "Direct messages", "Someone sent you a direct message"),
        Channel("mention", "Mentions", "Someone @mentioned you"),
        Channel("replies", "Replies", "Replies to your threads and threads you follow"),
        Channel("other", "Other activity", "New topics and other forum activity"),
    )

    private fun channelFor(kind: String?): String = when (kind) {
        "dm" -> "dm"
        "mention" -> "mention"
        "comment_own", "comment_followed" -> "replies"
        else -> "other"
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(channels.map { ch ->
            NotificationChannel(ch.id, ch.name, NotificationManager.IMPORTANCE_DEFAULT).apply { description = ch.desc }
        })
    }

    /** Start (or keep) the periodic background poll. Idempotent. */
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<NotifPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** Runtime permission (Android 13+) granted? Older versions need none. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Permission granted AND not switched off in system settings. */
    fun enabled(context: Context): Boolean =
        hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Open the system notification settings page for this app. */
    fun openSettings(context: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    fun post(context: Context, n: Notif) {
        if (!enabled(context)) return
        val who = n.actor?.name?.takeIf { it.isNotBlank() } ?: "dcamp"
        val preview = n.preview?.let { PlainText.strip(it) }.orEmpty()
        val tap = Intent(context, MainActivity::class.java)
            .setAction("com.decentespresso.dcamp.NOTIF_${n.id}")
            .putExtra(EXTRA_ROUTE, n.route ?: "")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, n.id, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(context, channelFor(n.kind))
            .setSmallIcon(R.drawable.ic_stat_dcamp)
            .setContentTitle("$who ${n.kindLabel}")
            .setContentText(preview.ifEmpty { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(if (n.kind == "dm") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
        n.createdAt?.let { b.setWhen(it * 1000L).setShowWhen(true) }
        try {
            NotificationManagerCompat.from(context).notify(n.id, b.build())
        } catch (_: SecurityException) {
            // permission revoked between the check and the post
        }
    }
}
