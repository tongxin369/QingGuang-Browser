package com.qingguang.browser.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap

/** 下载完成通知（普通链接由系统下载器通知，网页生成的 blob/data 与续传任务由这里通知） */
object DownloadNotifier {

    private const val CHANNEL_ID = "downloads"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "下载",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "下载完成通知" }
            )
        }
    }

    /** 点击通知直接打开下载好的文件 */
    fun notifyComplete(context: Context, fileName: String, uri: Uri? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(fileName)
            .setAutoCancel(true)
        if (uri != null) {
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context,
                fileName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pending)
        }
        manager.notify(fileName.hashCode(), builder.build())
    }
}
