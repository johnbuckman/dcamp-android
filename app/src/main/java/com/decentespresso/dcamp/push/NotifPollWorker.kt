package com.decentespresso.dcamp.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.decentespresso.dcamp.api.Services

/**
 * Background poll of the server's unseen-notification queue (see [PhoneNotifications]).
 * Posts each item as a phone notification, then acks it so the in-app toast, web and
 * iOS don't show it again. Items older than a day are acked silently: a first run on a
 * device that never acked would otherwise dump weeks of backlog.
 */
class NotifPollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (PhoneNotifications.inForeground || !Services.api.isAuthenticated) return Result.success()
        if (!PhoneNotifications.enabled(applicationContext)) return Result.success()
        val items = runCatching { Services.api.notifPoll() }.getOrElse { return Result.success() }
        if (items.isEmpty()) return Result.success()
        val cutoff = System.currentTimeMillis() / 1000 - 24 * 3600
        items.filter { (it.createdAt ?: 0) >= cutoff }.forEach { PhoneNotifications.post(applicationContext, it) }
        Services.api.notifSeen(items.map { it.id })
        return Result.success()
    }
}
