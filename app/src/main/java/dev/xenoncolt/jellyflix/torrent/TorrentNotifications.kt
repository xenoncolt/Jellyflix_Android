package dev.xenoncolt.jellyflix.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.xenoncolt.jellyflix.MainActivity
import dev.xenoncolt.jellyflix.R
import dev.xenoncolt.jellyflix.core.Format

/**
 * The ongoing download notification and its actions.
 *
 * One summary notification for the whole session rather than one per torrent:
 * a foreground service needs exactly one notification, and a shade full of
 * per-torrent rows is noise when the common case is a single season pack.
 */
object TorrentNotifications {

    const val CHANNEL_PROGRESS = "downloads"
    const val CHANNEL_COMPLETE = "downloads_complete"
    const val FOREGROUND_ID = 1001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.torrent_channel_name),
            NotificationManager.IMPORTANCE_LOW, // silent: it updates every second
        ).apply {
            description = context.getString(R.string.torrent_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }

        val complete = NotificationChannel(
            CHANNEL_COMPLETE,
            "Completed downloads",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Fires once when a download finishes"
        }

        manager.createNotificationChannels(listOf(progress, complete))
    }

    fun buildProgress(
        context: Context,
        active: List<Pair<String, TorrentProgress>>,
        paused: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        when {
            active.isEmpty() -> {
                builder.setContentTitle("Jellyflix")
                    .setContentText("No active downloads")
                    .setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
            }

            active.size == 1 -> {
                val (title, progress) = active.first()
                val percent = (progress.progress * 100).toInt()
                builder.setContentTitle(title)
                    .setContentText(statusLine(progress, paused))
                    .setProgress(100, percent, progress.totalBytes <= 0L)
                    .setSubText("$percent%")
            }

            else -> {
                val overall = active.map { it.second.progress }.average().toFloat()
                val totalRate = active.sumOf { it.second.downloadRate }
                builder.setContentTitle("${active.size} downloads")
                    .setContentText(
                        if (paused) "Paused" else "${Format.speed(totalRate)} · " +
                            "${(overall * 100).toInt()}% overall"
                    )
                    .setProgress(100, (overall * 100).toInt(), false)
                    .setStyle(
                        NotificationCompat.InboxStyle().also { style ->
                            active.take(5).forEach { (title, progress) ->
                                style.addLine("${(progress.progress * 100).toInt()}% · $title")
                            }
                        }
                    )
            }
        }

        if (active.isNotEmpty()) {
            if (paused) {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    servicePendingIntent(context, TorrentService.ACTION_RESUME_ALL, 1),
                )
            } else {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    servicePendingIntent(context, TorrentService.ACTION_PAUSE_ALL, 2),
                )
            }
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                servicePendingIntent(context, TorrentService.ACTION_STOP, 3),
            )
        }

        return builder.build()
    }

    fun buildComplete(context: Context, title: String, notificationId: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

    private fun statusLine(progress: TorrentProgress, paused: Boolean): String = when {
        paused -> "Paused · ${Format.bytes(progress.downloadedBytes)} of " +
            Format.bytes(progress.totalBytes)

        progress.totalBytes <= 0L -> "Fetching metadata…"

        else -> buildString {
            append(Format.speed(progress.downloadRate))
            append(" · ")
            append("${progress.seeds} seeds")
            progress.etaSeconds?.let { Format.eta(it) }?.let {
                append(" · ")
                append(it)
            }
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, TorrentService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
