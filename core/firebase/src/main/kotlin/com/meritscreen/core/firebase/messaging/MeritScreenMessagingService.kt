package com.meritscreen.core.firebase.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meritscreen.core.common.messaging.FcmMessageHandler
import com.meritscreen.core.common.messaging.FcmTokenRegistrar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Push notification service for Watching.
 *
 * Handles both:
 * 1. Control plane messages (e.g. policy sync, pin sync, device revocation) dispatched to
 *    registered [FcmMessageHandler] implementations.
 * 2. Awareness & promotional notifications (e.g. child device paired, quiz streaks, daily time limits,
 *    and super admin campaigns with rich image banners).
 */
@AndroidEntryPoint
class MeritScreenMessagingService : FirebaseMessagingService() {

    @Inject lateinit var handlers: Set<@JvmSuppressWildcards FcmMessageHandler>
    @Inject lateinit var registrars: Set<@JvmSuppressWildcards FcmTokenRegistrar>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        var handled = false
        if (data.isNotEmpty()) {
            handled = handlers.any { runCatching { it.handle(data) }.getOrDefault(false) }
        }

        // Check if message has displayable content (either from notification object or data fields)
        val title = message.notification?.title ?: data["title"]
        val body = message.notification?.body ?: data["body"]
        val imageUrl = message.notification?.imageUrl?.toString() ?: data["imageUrl"]

        if (!title.isNullOrBlank() || !body.isNullOrBlank()) {
            val channelId = data["channelId"] ?: NOTIFICATION_CHANNEL_PROMOTIONS
            val route = data["route"]
            val campaignId = data["campaignId"]
            val type = data["type"]

            serviceScope.launch {
                showDisplayNotification(
                    title = title ?: "Watching",
                    body = body ?: "",
                    imageUrl = imageUrl,
                    channelId = channelId,
                    route = route,
                    campaignId = campaignId,
                    type = type,
                )
            }
        } else if (!handled && data.isNotEmpty()) {
            Log.d(TAG, "No handler for FCM message type=${data["type"]}")
        }
    }

    override fun onNewToken(token: String) {
        registrars.forEach { runCatching { it.onTokenRefreshed(token) } }
    }

    private suspend fun showDisplayNotification(
        title: String,
        body: String,
        imageUrl: String?,
        channelId: String,
        route: String?,
        campaignId: String?,
        type: String?,
    ) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        createNotificationChannels(notificationManager)

        val bitmap: Bitmap? = if (!imageUrl.isNullOrBlank()) {
            downloadBitmap(imageUrl)
        } else {
            null
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra("route", it) }
            campaignId?.let { putExtra("campaignId", it) }
            type?.let { putExtra("type", it) }
        }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                (campaignId?.hashCode() ?: (System.currentTimeMillis() % 10000).toInt()),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val iconRes = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setColor(Color.parseColor(BRAND_ACCENT_COLOR))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (bitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body),
            )
            builder.setLargeIcon(bitmap)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        pendingIntent?.let { builder.setContentIntent(it) }

        val notificationId = (campaignId?.hashCode() ?: (System.currentTimeMillis() % 100000).toInt())
        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val supervisionChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_SUPERVISION,
                "Watching Supervision",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Screen time alerts, device pairing, and child learning notifications"
                enableLights(true)
                lightColor = Color.parseColor(BRAND_ACCENT_COLOR)
                enableVibration(true)
            }

            val promotionsChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_PROMOTIONS,
                "Promotions & Announcements",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Special promotions, product updates, and educational tips"
            }

            val updatesChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Product updates and new feature announcements"
            }

            val learningChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_LEARNING,
                "Learning Milestones",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Learning quizzes, streaks, and achievements"
            }

            manager.createNotificationChannels(
                listOf(supervisionChannel, promotionsChannel, updatesChannel, learningChannel),
            )
        }
    }

    private suspend fun downloadBitmap(urlStr: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "WatchingFcm"
        const val NOTIFICATION_CHANNEL_SUPERVISION = "watching_supervision"
        const val NOTIFICATION_CHANNEL_PROMOTIONS = "watching_promotions"
        const val NOTIFICATION_CHANNEL_UPDATES = "watching_updates"
        const val NOTIFICATION_CHANNEL_LEARNING = "watching_learning"
        private const val BRAND_ACCENT_COLOR = "#0F6B66"
    }
}
