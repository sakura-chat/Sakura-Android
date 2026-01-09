package dev.kuylar.sakura.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.os.bundleOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.ui.activity.MainActivity
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import kotlin.io.path.Path
import kotlin.io.path.writeText

class SakuraFirebaseMessagingService : FirebaseMessagingService() {
	override fun onNewToken(token: String) {
		super.onNewToken(token)
		Log.i("SakuraFirebaseMessagingService", "Refreshed token: $token")
		Path(applicationContext.filesDir.absolutePath, "fcm_token").writeText("$token\n")
		suspendThread {
			Matrix.getClient().registerFcmPusher(token)
		}
	}

	override fun onMessageReceived(message: RemoteMessage) {
		super.onMessageReceived(message)
		Log.i("SakuraFirebaseMessagingService", "Received message")
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
				val matrix = Matrix.loadClient(applicationContext)
				Log.d("SakuraFirebaseMessagingService", "Loading event")
				val notificationEvent = matrix.client.room
					.getTimelineEvent(roomId, eventId).first()
				Log.d("SakuraFirebaseMessagingService", "Loading user")
				val senderUser = (senderUserId ?: notificationEvent?.sender)?.let {
					matrix.client.user.getById(roomId, it).first()
				}
				Log.d("SakuraFirebaseMessagingService", "Loading room")
				val room = matrix.client.room.getById(roomId).first()
				Log.d(
					"SakuraFirebaseMessagingService",
					"Creating and sending the notification (event=${notificationEvent?.eventId?.full}, room=${room?.roomId?.full}, senderUser=${senderUser?.userId?.full})"
				)
				buildNotification(
					priority == "high",
					notificationEvent,
					room,
					senderUser,
					unread,
					missedCalls
				)
			}
		} else {
			Log.d("SakuraFirebaseMessagingService", "Somehow ended up here?")
			buildNotification(priority == "high", null, null, null, unread, missedCalls)
		}
	}

	fun buildNotification(
		isHighPriority: Boolean,
		event: TimelineEvent?,
		room: Room?,
		sender: RoomUser?,
		unread: Int,
		missedCalls: Int
	) {
		val channel =
			if (event != null) "dev.kuylar.sakura.room.${event.roomId}" else "dev.kuylar.sakura.other"
		createNotificationChannel(room)
		val notification = NotificationCompat.Builder(applicationContext, channel).apply {
			setSmallIcon(android.R.drawable.ic_dialog_info)
			setPriority(if (isHighPriority) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
			setChannelId(channel)
			setAutoCancel(true)
			setGroup("dev.kuylar.sakura.messages")
			val intent = Intent(applicationContext, MainActivity::class.java)
			intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			if (event != null && sender != null) {
				setContentTitle(room?.name?.explicitName ?: sender.name)
				setContentText(Utils.getEventBodyText(event))
				val person = Person.Builder().apply {
					setName(sender.name)
					setKey(sender.userId.full)
				}.build()
				val style = NotificationCompat.MessagingStyle(person)
				style.addMessage(Utils.getEventBodyText(event), event.originTimestamp, person)
				setStyle(style)
				setShortcutId(event.roomId.full)
				setShortcutInfo(
					ShortcutInfoCompat.Builder(applicationContext, event.roomId.full).apply {
						this.setShortLabel(room?.name?.explicitName ?: sender.name)
						this.setLongLabel(room?.name?.explicitName ?: sender.name)
						this.setActivity(
							ComponentName(
								applicationContext,
								MainActivity::class.java
							)
						)
						this.setIsConversation()
						this.setIntent(intent)
					}.build()
				)
				setCategory(NotificationCompat.CATEGORY_MESSAGE)
				intent.putExtra("roomId", event.roomId.full)
				intent.putExtra("eventId", event.eventId.full)
				val remoteInput: RemoteInput =
					RemoteInput.Builder("dev.kuylar.sakura.notification.reply")
						.run { setLabel(resources.getString(R.string.notification_reply_label)) }
						.build()
				val replyIntent = Intent(applicationContext, ReplyReceiver::class.java)
				replyIntent.putExtra("roomId", event.roomId.full)
				replyIntent.putExtra("eventId", event.eventId.full)
				val replyPendingIntent = PendingIntent.getBroadcast(
					applicationContext,
					0,
					replyIntent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
				)
				NotificationCompat.Action.Builder(null, remoteInput.label, replyPendingIntent)
					.addRemoteInput(remoteInput)
					.addExtras(bundleOf("roomId" to event.roomId.full))
					.let {
						addAction(it.build())
					}
			} else {
				setContentTitle(getString(R.string.notification_unread, unread))
			}
			setContentIntent(
				PendingIntent.getActivity(
					applicationContext, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
				)
			)
		}.build()
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
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = getString(R.string.notification_channel_room_description)
			}
		)
		extraChannel?.invoke()?.let { notificationManager.createNotificationChannel(it) }
	}

	private fun createNotificationChannel(room: Room?) {
		createDefaultNotificationChannels {
			room?.let {
				NotificationChannel(
					"dev.kuylar.sakura.room.${room.roomId.full}",
					room.name?.explicitName ?: room.roomId.full,
					NotificationManager.IMPORTANCE_DEFAULT
				).apply {
					setConversationId("dev.kuylar.sakura.room", room.roomId.full)
					setAllowBubbles(true)
				}
			}
		}
	}
}