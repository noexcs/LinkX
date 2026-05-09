package com.noexcs.indolent.task

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.noexcs.indolent.R

object ForegroundInfoFactory {

    fun create(
        context: Context,
        channelId: String,
        titleResId: Int,
        notificationId: Int,
        silent: Boolean = false,
        ongoing: Boolean = false,
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(titleResId))
            .apply { if (silent) setSilent(true) }
            .apply { if (ongoing) setOngoing(true) }
            .build()
        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
