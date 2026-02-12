package dev.kuylar.sakura

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.FileProvider
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.service.ReplyReceiver
import dev.kuylar.sakura.ui.activity.BubbleActivity
import dev.kuylar.sakura.ui.activity.MainActivity
import io.getstream.avatarview.AvatarView
import io.getstream.avatarview.glide.loadImage
import io.ktor.http.URLBuilder
import io.ktor.utils.io.charsets.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

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
		return if (listOfNotNull(
				this.info?.mimeType,
				this.fileName,
				this.body
			).any { it.contains(".gif") }
		) this.url?.let {
			URLBuilder(it).apply {
				// TODO: Make this toggleable in settings
				parameters.append("thumbnail", "false")
			}.build().toString()
		}
		else this.url
	}

	fun String.getInitials(uppercase: Boolean = false): String {
		return this
			.split(" ")
			.mapNotNull {
				it.firstOrNull { c -> c.isLetterOrDigit() }
					?.let { c ->
						if (uppercase) c.uppercase() else c
					}
			}
			.joinToString("")
	}

	fun AvatarView.loadAvatar(url: String?, name: String) {
		if (url != null)
			loadImage(url, true)
		else
			name.getInitials(true).takeIf { it.isNotBlank() }?.let {
				avatarInitials = it
			}
	}

	private fun getBubbleMetadata(
		context: Context,
		roomId: RoomId,
		eventId: EventId? = null
	): NotificationCompat.BubbleMetadata {
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

	fun TimelineEvent.getBubbleMetadata(context: Context) =
		getBubbleMetadata(context, roomId, eventId)

	fun Room.getBubbleMetadata(context: Context) = getBubbleMetadata(context, roomId)

	fun TimelineEvent.getIntent(context: Context): Intent {
		return Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
			putExtra("roomId", roomId.full)
			putExtra("eventId", eventId.full)
		}
	}

	private fun getReplyIntent(
		context: Context,
		roomId: RoomId,
		eventId: EventId? = null
	): Pair<RemoteInput, PendingIntent?> {
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

	fun Bitmap.toCircularBitmap(): Bitmap {
		val size = minOf(width, height)
		val output = createBitmap(size, size)

		val canvas = Canvas(output)
		val paint = Paint(Paint.ANTI_ALIAS_FLAG)

		val rect = Rect(0, 0, size, size)
		val rectF = RectF(rect)

		canvas.drawOval(rectF, paint)

		paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
		canvas.drawBitmap(
			this,
			Rect(
				(width - size) / 2,
				(height - size) / 2,
				(width + size) / 2,
				(height + size) / 2
			),
			rect,
			paint
		)

		return output
	}

	suspend fun RoomUser.toNotificationPerson(context: Context, client: Matrix): Person {
		val uri =
			downloadIconIfNeeded(context, client, avatarUrl, "r${roomId.full}_u${userId.full}")
		val icon = uri?.let {
			val bitmap = ImageDecoder.decodeBitmap(
				ImageDecoder.createSource(
					context.contentResolver,
					it
				)
			) { decoder, _, _ ->
				decoder.isMutableRequired = true
				decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
			}
			IconCompat.createWithBitmap(bitmap.toCircularBitmap())
		}
		return Person.Builder().apply {
			setName(name)
			setKey(userId.full)
			icon?.let { setIcon(it) }
		}.build()
	}

	private suspend fun downloadIconIfNeeded(
		context: Context,
		client: Matrix,
		mxcId: String?,
		key: String
	): Uri? {
		if (mxcId == null) return null
		val rootPath = Path(context.cacheDir.absolutePath, "icons")
		val filePath = Path(rootPath.absolutePathString(), key)
		val metaPath = Path(rootPath.absolutePathString(), "$key.meta")
		rootPath.createDirectories()
		if (!filePath.exists())
			filePath.createFile()
		val uri = FileProvider.getUriForFile(
			context,
			"${context.packageName}.iconprovider",
			File(filePath.absolutePathString())
		)
		val meta = IconMeta(mxcId)

		var shouldUpdate = true
		if (metaPath.exists()) {
			val savedMeta = Json.decodeFromString<IconMeta>(metaPath.readText())
			if (savedMeta == meta) shouldUpdate = false
		}

		Log.d("IconDownloader", "shouldDownload: [$key] $mxcId: $shouldUpdate")
		if (!shouldUpdate) return uri

		val icon = client.client.media.getThumbnail(mxcId, 128, 128, ThumbnailResizingMethod.SCALE)
		val data = icon.getOrNull() ?: return uri
		data.toByteArray()?.let { bytes ->
			filePath.writeBytes(bytes)
			metaPath.writeText(Json.encodeToString(meta))
		}
		return uri
	}

	@Serializable
	private data class IconMeta(
		val url: String,
		val version: Int = 1,
	) {
		override fun equals(other: Any?): Boolean {
			return if (other is IconMeta) {
				this.url == other.url
			} else false
		}

		override fun hashCode() = "v=$version;$url".hashCode()
	}
}