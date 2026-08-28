package com.feather.upscale.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.feather.upscale.MainActivity

/**
 * Quản lý Foreground Notification hiển thị tiến độ upscale và các nút Pause / Resume / Cancel.
 */
class UpscaleNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "feather_upscale_channel"
        const val CHANNEL_NAME = "FeatherUpscale Engine"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.feather.upscale.ACTION_PAUSE"
        const val ACTION_RESUME = "com.feather.upscale.ACTION_RESUME"
        const val ACTION_CANCEL = "com.feather.upscale.ACTION_CANCEL"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị tiến độ upscale ảnh và truyện tranh nền"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Tạo ForegroundInfo cho WorkManager tương thích hoàn toàn từ Android 8 đến Android 17+.
     */
    fun createForegroundInfo(
        pageIndex: Int,
        totalPages: Int,
        tileIndex: Int,
        totalTiles: Int,
        isPaused: Boolean,
        tileSize: Int,
    ): ForegroundInfo {
        val notification = buildNotification(
            pageIndex = pageIndex,
            totalPages = totalPages,
            tileIndex = tileIndex,
            totalTiles = totalTiles,
            isPaused = isPaused,
            tileSize = tileSize
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Xây dựng Notification với thanh tiến độ và action buttons.
     */
    fun buildNotification(
        pageIndex: Int,
        totalPages: Int,
        tileIndex: Int,
        totalTiles: Int,
        isPaused: Boolean,
        tileSize: Int,
    ): Notification {
        val totalProgressPercent = if (totalPages > 1) {
            val pageWeight = 100f / totalPages
            val currentTileFraction = if (totalTiles > 0) tileIndex.toFloat() / totalTiles else 0f
            ((pageIndex - 1) * pageWeight + currentTileFraction * pageWeight).toInt().coerceIn(0, 100)
        } else {
            if (totalTiles > 0) ((tileIndex.toFloat() / totalTiles) * 100).toInt().coerceIn(0, 100) else 0
        }

        val contentTitle = if (isPaused) "UpScale — Đã tạm dừng" else "Đang upscale ảnh AI..."
        val contentText = if (totalPages > 1) {
            "Trang $pageIndex/$totalPages (Tile $tileIndex/$totalTiles, ${tileSize}px) - $totalProgressPercent%"
        } else {
            "Tile $tileIndex/$totalTiles (${tileSize}px) - $totalProgressPercent%"
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setProgress(100, totalProgressPercent, false)

        if (isPaused) {
            val resumeIntent = Intent(ACTION_RESUME).setPackage(context.packageName)
            val resumePendingIntent = PendingIntent.getBroadcast(
                context, 1, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Tiếp tục", resumePendingIntent)
        } else {
            val pauseIntent = Intent(ACTION_PAUSE).setPackage(context.packageName)
            val pausePendingIntent = PendingIntent.getBroadcast(
                context, 2, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Tạm dừng", pausePendingIntent)
        }

        val cancelIntent = Intent(ACTION_CANCEL).setPackage(context.packageName)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 3, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hủy", cancelPendingIntent)

        return builder.build()
    }

    /** Cập nhật trực tiếp notification đang hiển thị */
    fun updateProgress(
        pageIndex: Int,
        totalPages: Int,
        tileIndex: Int,
        totalTiles: Int,
        isPaused: Boolean,
        tileSize: Int,
    ) {
        try {
            val notification = buildNotification(pageIndex, totalPages, tileIndex, totalTiles, isPaused, tileSize)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {}
    }

    /** Hiển thị thông báo khi hoàn thành */
    fun showCompleted(outputFileName: String, durationMs: Long) {
        try {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("🎉 UpScale hoàn tất!")
                .setContentText("Tệp: $outputFileName • Thời gian: ${durationMs / 1000}s")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {}
    }

    fun dismiss() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Throwable) {}
    }
}
