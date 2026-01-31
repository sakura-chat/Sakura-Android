package dev.kuylar.sakura.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.os.bundleOf
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.getBubbleMetadata
import dev.kuylar.sakura.Utils.getIntent
import dev.kuylar.sakura.Utils.getReplyIntent
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toNotificationPerson
import dev.kuylar.sakura.Utils.toShortcut
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class ReplyReceiver : BroadcastReceiver() {
	@Inject
	lateinit var matrix: Matrix

	override fun onReceive(context: Context, intent: Intent) {
		val remoteInput = RemoteInput.getResultsFromIntent(intent)
		val replyMessage =
			remoteInput?.getCharSequence("dev.kuylar.sakura.notification.reply")?.toString() ?: return
		val roomId = intent.getStringExtra("roomId")?.let { RoomId(it) } ?: return
		val eventId = intent.getStringExtra("eventId")?.let { EventId(it) } ?: return
		runBlocking {
			if (!matrix.initialized)
				matrix.initialize("main")
		}
		Log.i("ReplyReceiver", "Received reply @ $roomId: $replyMessage")
		suspendThread {
			val room = matrix.client.room.getById(roomId).firstOrNull()
			Log.i("ReplyReceiver", "Sending reply...")
			matrix.client.api.room.sendMessageEvent(
				roomId,
				RoomMessageEventContent.TextBased.Text(replyMessage)
			).getOrNull() ?: return@suspendThread
			Log.i("ReplyReceiver", "Getting required items to build a notification update")
			val replyUser = matrix.getUser(matrix.userId, roomId)
			Log.i(
				"ReplyReceiver",
				"Got everything? replyUser: ${replyUser?.userId?.full}"
			)

			if (room == null || replyUser == null) return@suspendThread

			val channel = "dev.kuylar.sakura.room.${roomId.full}"
			val notification =
				NotificationCompat.Builder(context, channel).apply {
					val person = replyUser.toNotificationPerson(context, matrix)
					val shortcut = room.toShortcut(context)

					val style = NotificationCompat.MessagingStyle(person)
					style.isGroupConversation = !room.isDirect

					// Append to existing notifications messages
					val notificationManager = NotificationManagerCompat.from(context)
					val existingNotification = notificationManager.activeNotifications
						.find { it.id == channel.hashCode() }
					existingNotification?.notification?.let { existing ->
						NotificationCompat.MessagingStyle
							.extractMessagingStyleFromNotification(existing)
							?.messages
							?.forEach { msg ->
								style.addMessage(msg)
							}
					}
					style.addMessage(replyMessage, System.currentTimeMillis(), person)
					style.setConversationTitle(room.name?.explicitName ?: room.roomId.full)
					style.messages
						.mapNotNull { it.person }
						.distinctBy { it.key }.forEach {
							addPerson(it)
						}

					setContentTitle(room.name?.explicitName)
					setContentText(replyMessage)
					setContentIntent(
						PendingIntent.getActivity(
							context, 0, room.getIntent(context),
							PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
						)
					)
					setStyle(style)
					setShortcutId(shortcut.id)
					setBubbleMetadata(room.getBubbleMetadata(context))
					setLocusId(LocusIdCompat(room.roomId.full))
					setPriority(NotificationCompat.PRIORITY_DEFAULT)
					setSmallIcon(R.drawable.ic_notification_icon)
					setCategory(NotificationCompat.CATEGORY_MESSAGE)
					setAutoCancel(true)
					setGroup("dev.kuylar.sakura.messages")
					setOnlyAlertOnce(true)

					val (remoteInput, replyPendingIntent) = room.getReplyIntent(context)
					NotificationCompat.Action.Builder(null, remoteInput.label, replyPendingIntent)
						.addRemoteInput(remoteInput)
						.addExtras(bundleOf("roomId" to room.roomId.full))
						.let {
							addAction(it.build())
						}
				}.build()
			Log.i("ReplyReceiver", "Notification ready!")
			with(NotificationManagerCompat.from(context)) {
				// Check if we *still* have the notification permission
				if (ContextCompat.checkSelfPermission(
						context,
						Manifest.permission.POST_NOTIFICATIONS
					) != PackageManager.PERMISSION_GRANTED
				) return@with

				Log.d("ReplyReceiver", "Everything seems to be okay, sending...")
				notify(channel.hashCode(), notification)
				Log.d("ReplyReceiver", "Sent update")
			}
		}
	}
}