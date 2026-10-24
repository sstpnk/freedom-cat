package tech.stpnk.freedomcat.core.ui

import android.content.Context
import androidx.core.app.NotificationCompat
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.FreedomCatApp
import tech.stpnk.freedomcat.ktx.Logs

class ConnectionTestNotification(val context: Context, val title: String) {
    private val channelId = "connection-test"
    private val notificationId = 1001

    fun updateNotification(progress: Int, max: Int, finished: Boolean) {
        try {
            if (finished) {
                FreedomCatApp.notification.cancel(notificationId)
                return
            }
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_service_active)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setContentText("$progress / $max").setProgress(max, progress, false)
            FreedomCatApp.notification.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Logs.w(e)
        }
    }
}