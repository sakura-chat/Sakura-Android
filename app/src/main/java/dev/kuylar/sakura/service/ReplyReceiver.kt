package dev.kuylar.sakura.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
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
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.ui.activity.MainActivity
import kotlinx.coroutines.flow.firstOrNull
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

class ReplyReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val remoteInput = RemoteInput.getResultsFromIntent(intent)
		val replyMessage =
			remoteInput?.getCharSequence("dev.kuylar.sakura.notification.reply")?.toString()
		val roomId = intent.getStringExtra("roomId")?.let { RoomId(it) }
		val eventId = intent.getStringExtra("eventId")?.let { EventId(it) }
		if (roomId == null || eventId == null || replyMessage == null) return
		Log.i("ReplyReceiver", "Received reply @ $roomId: $replyMessage")
		suspendThread {
			val matrix = Matrix.getClient()
			val room = matrix.client.room.getById(roomId).firstOrNull()
			Log.i("ReplyReceiver", "Sending reply...")
			matrix.client.api.room.sendMessageEvent(
				roomId,
				RoomMessageEventContent.TextBased.Text(replyMessage)
			).getOrNull() ?: return@suspendThread
			Log.i("ReplyReceiver", "Getting required items to build a notification update")
			val ogEvent = matrix.client.room.getTimelineEvent(roomId, eventId).firstOrNull()
			val ogUser = ogEvent?.sender?.let { matrix.getUser(it, roomId) }
			val replyUser = matrix.getUser(matrix.userId, roomId)
			Log.i(
				"ReplyReceiver",
				"Got everything? ogEvent:${ogEvent?.eventId?.full}, ogUser: ${ogUser?.userId?.full}, replyUser: ${replyUser?.userId?.full}"
			)

			if (room == null || ogEvent == null || ogUser == null || replyUser == null) return@suspendThread

			val channel = "dev.kuylar.sakura.room.${roomId.full}"
			val notification =
				NotificationCompat.Builder(context, channel).apply {
					setSmallIcon(android.R.drawable.ic_dialog_info)
					setPriority(NotificationCompat.PRIORITY_DEFAULT)
					setOnlyAlertOnce(true)
					setChannelId(channel)
					setAutoCancel(true)
					setGroup("dev.kuylar.sakura.messages")
					val intent = Intent(context, MainActivity::class.java)
					intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
					setContentTitle(room.name?.explicitName)
					setContentText(replyMessage)
					val ogPerson = ogUser.let {
						Person.Builder().apply {
							setName(it.name)
							setKey(it.userId.full)
						}.build()
					}
					val replyPerson = replyUser.let {
						Person.Builder().apply {
							setName(it.name)
							setKey(it.userId.full)
						}.build()
					}
					val style = NotificationCompat.MessagingStyle(replyPerson)
					style.addMessage(
						Utils.getEventBodyText(ogEvent),
						ogEvent.originTimestamp,
						ogPerson
					)
					style.addMessage(
						replyMessage,
						System.currentTimeMillis(),
						replyPerson
					)
					setStyle(style)
					setShortcutId(roomId.full)
					setShortcutInfo(
						ShortcutInfoCompat.Builder(context, roomId.full).apply {
							this.setShortLabel(room.name?.explicitName ?: room.roomId.full)
							this.setLongLabel(room.name?.explicitName ?: room.roomId.full)
							this.setActivity(
								ComponentName(
									context,
									MainActivity::class.java
								)
							)
							this.setIsConversation()
							this.setIntent(intent)
						}.build()
					)
					setCategory(NotificationCompat.CATEGORY_MESSAGE)
					intent.putExtra("roomId", roomId.full)
					intent.putExtra("eventId", ogEvent.eventId.full)
					val remoteInput: RemoteInput =
						RemoteInput.Builder("dev.kuylar.sakura.notification.reply")
							.run { setLabel(context.getString(R.string.notification_reply_label)) }
							.build()
					val replyIntent = Intent(context, ReplyReceiver::class.java)
					replyIntent.putExtra("roomId", roomId.full)
					val replyPendingIntent = PendingIntent.getBroadcast(
						context,
						0,
						replyIntent,
						PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
					)
					NotificationCompat.Action.Builder(null, remoteInput.label, replyPendingIntent)
						.addRemoteInput(remoteInput)
						.addExtras(bundleOf("roomId" to roomId.full))
						.let {
							addAction(it.build())
						}
					setContentIntent(
						PendingIntent.getActivity(
							context, 0, intent,
							PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
						)
					)
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