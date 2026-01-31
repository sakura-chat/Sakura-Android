package dev.kuylar.sakura.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.bundleOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
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
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.getBubbleMetadata
import dev.kuylar.sakura.Utils.getIntent
import dev.kuylar.sakura.Utils.getReplyIntent
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toNotificationPerson
import dev.kuylar.sakura.Utils.toShortcut
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.ui.activity.MainActivity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.writeText

@AndroidEntryPoint
class SakuraFirebaseMessagingService : FirebaseMessagingService() {
	@Inject
	lateinit var client: Matrix

	override fun onNewToken(token: String) {
		super.onNewToken(token)
		Log.i("SakuraFirebaseMessagingService", "Refreshed token: $token")
		Path(applicationContext.filesDir.absolutePath, "fcm_token").writeText("$token\n")
		suspendThread {
			client.registerFcmPusher(token)
		}
	}

	override fun onMessageReceived(message: RemoteMessage) {
		super.onMessageReceived(message)
		Log.i("SakuraFirebaseMessagingService", "Received message")
		runBlocking {
			if (!client.initialized)
				client.initialize("main")
		}
		val priority = message.data["priority"] ?: "high"
		val eventType = message.data["type"]
		val eventId = message.data["eventId"]?.let { EventId(it) }
		val roomId = message.data["roomId"]?.let { RoomId(it) }
		val senderUserId = message.data["sender"]?.let { UserId(it) }

		val unread = message.data["unread"]?.toIntOrNull() ?: 0
		val missedCalls = message.data["missedCalls"]?.toIntOrNull() ?: 0
		Log.i(
			"SakuraFirebaseMessagingService",
			"Received notification: [$roomId/$eventId] ($unread/$missedCalls)"
		)
		createNotificationChannel(null)
		if (eventId != null && roomId != null) {
			suspendThread {
				Log.d("SakuraFirebaseMessagingService", "Loading client")
				Log.d("SakuraFirebaseMessagingService", "Loading event")
				val notificationEvent =
					if (client.client.notification.onPush(roomId, eventId)) {
						client.getEvent(roomId, eventId) ?: return@suspendThread
					} else {
						client.client.syncOnce(presence = Presence.OFFLINE)
						client.getEvent(roomId, eventId, retryCount = 3) ?: return@suspendThread
					}
				Log.d("SakuraFirebaseMessagingService", "Loading user")
				val senderUser = (senderUserId ?: notificationEvent.sender).let {
					client.client.user.getById(roomId, it).firstOrNull()
				} ?: return@suspendThread
				Log.d("SakuraFirebaseMessagingService", "Loading room")
				val room = client.getRoom(roomId) ?: return@suspendThread
				Log.d(
					"SakuraFirebaseMessagingService",
					"Creating and sending the notification (event=${notificationEvent.eventId.full}, room=${room.roomId.full}, senderUser=${senderUser.userId.full})"
				)
				buildNotification(
					priority == "high",
					notificationEvent,
					room,
					senderUser
				)
			}
		} else {
			Log.d("SakuraFirebaseMessagingService", "Somehow ended up here?")
			buildNotification(priority == "high", unread, missedCalls)
		}
	}

	private fun buildNotification(
		isHighPriority: Boolean,
		event: TimelineEvent,
		room: Room,
		sender: RoomUser
	) {
		val channel = "dev.kuylar.sakura.room.${event.roomId}"
		createNotificationChannel(room)
		val notification = NotificationCompat.Builder(applicationContext, channel).apply {
			val person = sender.toNotificationPerson()

			val shortcut = room.toShortcut(applicationContext)
			val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(applicationContext)
			if (ShortcutManagerCompat.getMaxShortcutCountPerActivity(applicationContext) > shortcuts.size)
				ShortcutManagerCompat.addDynamicShortcuts(applicationContext, listOf(shortcut))

			val style = NotificationCompat.MessagingStyle(person)
			style.isGroupConversation = !room.isDirect

			// Append to existing notifications messages
			val existingNotification =
				NotificationManagerCompat.from(applicationContext).activeNotifications
					.find { it.id == channel.hashCode() }
			existingNotification?.notification?.let { existing ->
				NotificationCompat.MessagingStyle
					.extractMessagingStyleFromNotification(existing)
					?.messages
					?.forEach { msg ->
						style.addMessage(msg)
					}
			}
			style.addMessage(Utils.getEventBodyText(event), event.originTimestamp, person)
			style.setConversationTitle(room.name?.explicitName ?: room.roomId.full)
			style.messages
				.mapNotNull { it.person }
				.distinctBy { it.key }.forEach {
					addPerson(it)
				}

			setContentTitle(room.name?.explicitName ?: room.roomId.full)
			setContentText(Utils.getEventBodyText(event))
			setContentIntent(
				PendingIntent.getActivity(
					applicationContext, 0, event.getIntent(applicationContext),
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
				)
			)
			setStyle(style)
			setShortcutId(shortcut.id)
			setBubbleMetadata(event.getBubbleMetadata(applicationContext))
			setLocusId(LocusIdCompat(event.roomId.full))
			setPriority(if (isHighPriority) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
			setSmallIcon(R.drawable.ic_notification_icon)
			setCategory(NotificationCompat.CATEGORY_MESSAGE)
			setAutoCancel(true)

			// Don't spam alerts, only alert every 3 minutes
			val alertInterval = 3 * 60 * 1000
			val shouldAlert =
				existingNotification?.postTime?.let { System.currentTimeMillis() - it > alertInterval }
					?: true
			setOnlyAlertOnce(!shouldAlert)

			val (remoteInput, replyPendingIntent) = event.getReplyIntent(applicationContext)
			NotificationCompat.Action.Builder(null, remoteInput.label, replyPendingIntent)
				.addRemoteInput(remoteInput)
				.addExtras(bundleOf("roomId" to event.roomId.full))
				.let {
					addAction(it.build())
				}
		}.build()
		postNotification(channel, notification)
	}

	private fun buildNotification(
		isHighPriority: Boolean,
		unread: Int,
		@Suppress("unused") missedCalls: Int
	) {
		val channel = "dev.kuylar.sakura.other"
		createNotificationChannel()
		val notification = NotificationCompat.Builder(applicationContext, channel).apply {
			val intent = Intent(applicationContext, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			}

			setContentTitle(getString(R.string.notification_unread, unread))
			setContentIntent(
				PendingIntent.getActivity(
					applicationContext, 0, intent,
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
		with(NotificationManagerCompat.from(applicationContext)) {
			// Check if we have the notification permission
			if (ContextCompat.checkSelfPermission(
					applicationContext,
					Manifest.permission.POST_NOTIFICATIONS
				) != PackageManager.PERMISSION_GRANTED
			) return@with

			Log.d("SakuraFirebaseMessagingService", "Created notification, sending")
			notify(channel.hashCode(), notification)
			Log.d("SakuraFirebaseMessagingService", "Sent")
		}
	}

	private fun createDefaultNotificationChannels(extraChannel: (() -> NotificationChannel?)? = null) {
		val notificationManager: NotificationManager =
			getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		notificationManager.createNotificationChannel(
			NotificationChannel(
				"dev.kuylar.sakura.other",
				getString(R.string.notification_channel_other_name),
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = getString(R.string.notification_channel_other_description)
			}
		)
		notificationManager.createNotificationChannel(
			NotificationChannel(
				"dev.kuylar.sakura.room",
				getString(R.string.notification_channel_room_name),
				NotificationManager.IMPORTANCE_HIGH
			).apply {
				description = getString(R.string.notification_channel_room_description)
			}
		)
		extraChannel?.invoke()?.let { notificationManager.createNotificationChannel(it) }
	}

	private fun createNotificationChannel(room: Room? = null) {
		createDefaultNotificationChannels {
			room?.let {
				NotificationChannel(
					"dev.kuylar.sakura.room.${room.roomId.full}",
					room.name?.explicitName ?: room.roomId.full,
					NotificationManager.IMPORTANCE_HIGH
				).apply {
					setConversationId("dev.kuylar.sakura.room", room.roomId.full)
					setAllowBubbles(true)
				}
			}
		}
	}
}