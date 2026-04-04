package dev.kuylar.sakura.work

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.bundleOf
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.getBubbleMetadata
import dev.kuylar.sakura.Utils.getIntent
import dev.kuylar.sakura.Utils.getName
import dev.kuylar.sakura.Utils.getReplyIntent
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toNotificationPerson
import dev.kuylar.sakura.Utils.toShortcut
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.ui.activity.MainActivity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

@HiltWorker
class NotificationWorker @AssistedInject constructor(
	@Assisted private val context: Context,
	@Assisted private val params: WorkerParameters,
	private val client: Matrix
) : Worker(context, params) {

	override fun doWork(): Result {
		val eventId = params.inputData.getString("eventId")?.let { EventId(it) }
		val roomId = params.inputData.getString("roomId")?.let { RoomId(it) }
		val unread = params.inputData.getString("unread")?.toIntOrNull() ?: 0
		val missedCalls = params.inputData.getString("missedCalls")?.toIntOrNull() ?: 0
		Log.i(
			"NotificationWorker",
			"Received notification: [$roomId/$eventId] ($unread/$missedCalls)"
		)

		Log.d("NotificationWorker", "Loading client")
		runBlocking {
			if (!client.initialized)
				client.initialize("main")
		}
		runBlocking {
			createNotificationChannel(null)
		}

		if (eventId != null && roomId != null) {
			suspendThread {
				val onPush = client.client.notification.onPush(roomId, eventId)
				if (isAppInForeground()) return@suspendThread
				Log.d("NotificationWorker", "Loading event")
				val notificationEvent =
					if (onPush) {
						client.getEvent(roomId, eventId) ?: return@suspendThread
					} else {
						client.client.syncOnce(presence = Presence.OFFLINE)
						client.getEvent(roomId, eventId, retryCount = 3) ?: return@suspendThread
					}
				Log.d("NotificationWorker", "Loading user")
				val senderUser = notificationEvent.sender.let {
					client.client.user.getById(roomId, it).firstOrNull()
				} ?: return@suspendThread
				Log.d("NotificationWorker", "Loading room")
				val room = client.getRoomBypassCache(roomId).firstOrNull() ?: return@suspendThread
				Log.d(
					"NotificationWorker",
					"Creating and sending the notification (event=${notificationEvent.eventId.full}, room=${room.roomId.full}, senderUser=${senderUser.userId.full})"
				)
				buildNotification(
					notificationEvent,
					room,
					senderUser
				)
			}
		} else {
			Log.d("NotificationWorker", "Somehow ended up here?")
			runBlocking {
				buildNotification(true, unread, missedCalls)
			}
		}
		return Result.success()
	}

	private suspend fun buildNotification(
		event: TimelineEvent,
		room: Room,
		sender: RoomUser
	) {
		val channel = "dev.kuylar.sakura.room.${event.roomId}"
		createNotificationChannel(room)
		val notification = NotificationCompat.Builder(context, channel).apply {
			val person = sender.toNotificationPerson(context, client)

			val shortcut = room.toShortcut(context, client)
			val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
			if (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) > shortcuts.size)
				ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut))

			val style = NotificationCompat.MessagingStyle(person)
			style.isGroupConversation = !room.isDirect

			// Append to existing notifications messages
			val existingNotification =
				NotificationManagerCompat.from(context).activeNotifications
					.find { it.id == channel.hashCode() }
			existingNotification?.notification?.let { existing ->
				NotificationCompat.MessagingStyle
					.extractMessagingStyleFromNotification(existing)
					?.messages
					?.forEach { msg ->
						style.addMessage(msg)
					}
			}
			style.addMessage(Utils.getEventBodyText(event, context), event.originTimestamp, person)
			style.setConversationTitle(room.getName(context, client))
			style.messages
				.mapNotNull { it.person }
				.distinctBy { it.key }.forEach {
					addPerson(it)
				}

			setContentTitle(room.getName(context, client))
			setContentText(Utils.getEventBodyText(event, context))
			setContentIntent(
				PendingIntent.getActivity(
					context, 0, event.getIntent(context),
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
				)
			)
			setStyle(style)
			setShortcutId(shortcut.id)
			setBubbleMetadata(event.getBubbleMetadata(context, client))
			setLocusId(LocusIdCompat(event.roomId.full))
			setPriority(NotificationCompat.PRIORITY_HIGH)
			setSmallIcon(R.drawable.ic_notification_icon)
			setCategory(NotificationCompat.CATEGORY_MESSAGE)
			setAutoCancel(true)

			// Don't spam alerts, only alert every 3 minutes
			val alertInterval = 3 * 60 * 1000
			val shouldAlert =
				existingNotification?.postTime?.let { System.currentTimeMillis() - it > alertInterval }
					?: true
			setOnlyAlertOnce(!shouldAlert)

			val (remoteInput, replyPendingIntent) = event.getReplyIntent(context)
			NotificationCompat.Action.Builder(null, remoteInput.label, replyPendingIntent)
				.addRemoteInput(remoteInput)
				.addExtras(bundleOf("roomId" to event.roomId.full))
				.let {
					addAction(it.build())
				}
		}.build()
		postNotification(channel, notification)
	}

	private suspend fun buildNotification(
		isHighPriority: Boolean,
		unread: Int,
		@Suppress("unused") missedCalls: Int
	) {
		val channel = "dev.kuylar.sakura.other"
		createNotificationChannel()
		val notification = NotificationCompat.Builder(context, channel).apply {
			val intent = Intent(context, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			}

			setContentTitle(context.getString(R.string.notification_unread, unread))
			setContentIntent(
				PendingIntent.getActivity(
					context, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
				)
			)
			setPriority(if (isHighPriority) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
			setSmallIcon(R.drawable.ic_notification_icon)
			setCategory(NotificationCompat.CATEGORY_MESSAGE)
			setGroup("dev.kuylar.sakura.messages")
			setAutoCancel(true)
		}.build()
		postNotification(channel, notification)
	}

	private fun postNotification(channel: String, notification: Notification) {
		with(NotificationManagerCompat.from(context)) {
			// Check if we have the notification permission
			if (ContextCompat.checkSelfPermission(
					context,
					Manifest.permission.POST_NOTIFICATIONS
				) != PackageManager.PERMISSION_GRANTED
			) return@with

			Log.d("NotificationWorker", "Created notification, sending")
			notify(channel.hashCode(), notification)
			Log.d("NotificationWorker", "Sent")
		}
	}

	private suspend fun createDefaultNotificationChannels(extraChannel: (suspend () -> NotificationChannel?)? = null) {
		val notificationManager: NotificationManager =
			context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.createNotificationChannel(
			NotificationChannel(
				"dev.kuylar.sakura.other",
				context.getString(R.string.notification_channel_other_name),
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = context.getString(R.string.notification_channel_other_description)
			}
		)
		notificationManager.createNotificationChannel(
			NotificationChannel(
				"dev.kuylar.sakura.room",
				context.getString(R.string.notification_channel_room_name),
				NotificationManager.IMPORTANCE_HIGH
			).apply {
				description = context.getString(R.string.notification_channel_room_description)
			}
		)
		extraChannel?.invoke()?.let { notificationManager.createNotificationChannel(it) }
	}

	private suspend fun createNotificationChannel(room: Room? = null) {
		createDefaultNotificationChannels {
			room?.let {
				NotificationChannel(
					"dev.kuylar.sakura.room.${room.roomId.full}",
					room.getName(context, client),
					NotificationManager.IMPORTANCE_HIGH
				).apply {
					setConversationId("dev.kuylar.sakura.room", room.roomId.full)
					setAllowBubbles(true)
				}
			}
		}
	}

	private fun isAppInForeground(): Boolean {
		val activityManager = context.getSystemService<ActivityManager>() ?: return false
		val appProcesses = activityManager.runningAppProcesses ?: return false
		val packageName = context.packageName
		for (appProcess in appProcesses) {
			if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
				&& appProcess.processName == packageName
			) return true
		}
		return false
	}
}