package dev.kuylar.sakura

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import dev.kuylar.sakura.service.ReplyReceiver
import dev.kuylar.sakura.ui.activity.BubbleActivity
import dev.kuylar.sakura.ui.activity.MainActivity
import io.ktor.utils.io.charsets.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

object Utils {
	fun suspendThread(block: suspend (() -> Unit)): Job {
		return CoroutineScope(Dispatchers.Main).launch {
			block.invoke()
		}
	}

	fun Long.toTimestamp(context: Context): String {
		val now = System.currentTimeMillis()
		return if (withinSameDay(now)) {
			val pattern =
				if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a"
			val formatter = SimpleDateFormat(pattern, Locale.getDefault())
			return formatter.format(this)
		} else {
			DateUtils.getRelativeDateTimeString(
				context,
				this,
				DateUtils.MINUTE_IN_MILLIS,
				DateUtils.DAY_IN_MILLIS,
				DateUtils.FORMAT_SHOW_DATE
			).toString()
		}
	}

	fun Long.toTimestampDate(context: Context): String? =
		DateFormat.getMediumDateFormat(context).format(this)


	fun Long.withinSameDay(other: Long): Boolean {
		val zoneId = ZoneId.systemDefault()
		return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate() ==
				Instant.ofEpochMilli(other).atZone(zoneId).toLocalDate()
	}

	fun Presence.toLocalized(): Int = when (this) {
		Presence.ONLINE -> R.string.user_status_online
		Presence.OFFLINE -> R.string.user_status_offline
		Presence.UNAVAILABLE -> R.string.user_status_unavailable
	}

	fun Presence.toLocalized(context: Context): String = context.getString(toLocalized())
	fun getEventBodyText(event: TimelineEvent): CharSequence {
		val content = event.content?.getOrNull() ?: return event.javaClass.name
		return when (content) {
			// TODO: Fill every single one of these
			is RoomMessageEventContent.TextBased -> content.formattedBodyWithoutFallback
				?: content.bodyWithoutFallback

			else -> content.javaClass.name
		}
	}

	fun Presence.getIndicatorColor(context: Context): Int = context.getColor(
		when (this) {
			Presence.ONLINE -> R.color.status_online
			Presence.OFFLINE -> R.color.status_offline
			Presence.UNAVAILABLE -> R.color.status_unavailable
		}
	)

	fun InputStream.asFlow(bufferSize: Int = DEFAULT_BUFFER_SIZE): Flow<ByteArray> = flow {
		withContext(Dispatchers.IO) {
			val buffer = ByteArray(bufferSize)
			var bytesRead: Int

			while (read(buffer).also { bytesRead = it } != -1) {
				emit(buffer.copyOf(bytesRead))
			}
		}
	}

	fun Long.bytesToString(): String {
		if (this < 1024) return "$this B"

		val units = listOf("KB", "MB", "GB")
		var value = this.toDouble()
		var unitIndex = -1

		do {
			value /= 1024
			unitIndex++
		} while (value >= 1024 && unitIndex < units.lastIndex)

		return String.format(Locale.getDefault(), "%.2f %s", value, units[unitIndex])
	}

	fun RecyclerView.isAtBottom(): Boolean {
		val lm = layoutManager as? LinearLayoutManager ?: return false
		if (lm.itemCount == 0) return true
		return if (lm.reverseLayout) {
			lm.findFirstCompletelyVisibleItemPosition() == 0
		} else {
			lm.findLastCompletelyVisibleItemPosition() == lm.itemCount - 1
		}
	}

	fun getMimeTypeFromExtension(lastPathSegment: String?): String {
		val extension = lastPathSegment?.substringAfterLast('.')?.substringBefore('?')
		return when (extension) {
			"gif" -> "image/gif"
			"jpeg", "jpg" -> "image/jpeg"
			"png" -> "image/png"
			"webp" -> "image/webp"
			else -> "application/octet-stream"
		}
	}

	fun RoomMessageEventContent.FileBased.getImageUrl(): String? {
		if (this.file != null) {
			return "mxc://sakuraNative/encrypted?data=" + URLEncoder.encode(
				Json.encodeToString(this.file),
				Charset.defaultCharset()
			)
		}
		return this.url
	}

	private fun getBubbleMetadata(context: Context, roomId: RoomId, eventId: EventId? = null): NotificationCompat.BubbleMetadata {
		val bubbleIntent = Intent(context, BubbleActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
			putExtra("roomId", roomId.full)
			if (eventId != null)
				putExtra("eventId", eventId.full)
		}

		val bubblePendingIntent = PendingIntent.getActivity(
			context,
			0,
			bubbleIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		)
		return NotificationCompat.BubbleMetadata.Builder(
			bubblePendingIntent,
			// TODO: Room icon
			IconCompat.createWithResource(context, R.drawable.ic_notification_icon)
		).apply {
			setDesiredHeight(600)
			setAutoExpandBubble(false)
			setSuppressNotification(false)
		}.build()
	}

	fun TimelineEvent.getBubbleMetadata(context: Context) = getBubbleMetadata(context, roomId, eventId)
	fun Room.getBubbleMetadata(context: Context) = getBubbleMetadata(context, roomId)

	fun TimelineEvent.getIntent(context: Context): Intent {
		return Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			putExtra("roomId", roomId.full)
			putExtra("eventId", eventId.full)
		}
	}

	private fun getReplyIntent(context: Context, roomId: RoomId, eventId: EventId? = null): Pair<RemoteInput, PendingIntent?> {
		val remoteInput: RemoteInput =
			RemoteInput.Builder("dev.kuylar.sakura.notification.reply")
				.run { setLabel(context.resources.getString(R.string.notification_reply_label)) }
				.build()
		val replyIntent = Intent(context, ReplyReceiver::class.java).apply {
			putExtra("roomId", roomId.full)
			if (eventId != null)
				putExtra("eventId", eventId.full)
		}
		val replyPendingIntent = PendingIntent.getBroadcast(
			context,
			0,
			replyIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		)

		return Pair(remoteInput, replyPendingIntent)
	}

	fun TimelineEvent.getReplyIntent(context: Context) = getReplyIntent(context, roomId, eventId)
	fun Room.getReplyIntent(context: Context) = getReplyIntent(context, roomId)

	fun Room.toShortcut(context: Context): ShortcutInfoCompat {
		return ShortcutInfoCompat.Builder(context, roomId.full).apply {
			setCategories(mutableSetOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
			setIntent(Intent(Intent.ACTION_VIEW, "dev.kuylar.sakura://room/${roomId.full}".toUri()))
			setLongLived(true)
			setLocusId(LocusIdCompat(roomId.full))
			setShortLabel(name?.explicitName ?: roomId.full)
		}.build()
	}

	fun Room.getIntent(context: Context): Intent {
		return Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			putExtra("roomId", roomId.full)
		}
	}

	fun RoomUser.toNotificationPerson() : Person {
		return Person.Builder().apply {
			setName(name)
			setKey(userId.full)
			// TODO: User avatar
		}.build()
	}
}