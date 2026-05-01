package dev.kuylar.sakura

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.recyclerview.widget.SortedList
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.invitedMemberCount
import de.connect2x.trixnity.client.store.joinedMemberCount
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
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
import kotlin.time.ExperimentalTime

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
			formatter.format(this)
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
	fun getEventBodyText(event: TimelineEvent, context: Context): CharSequence {
		val content = event.content?.getOrNull() ?: return event.javaClass.name
		return when (content) {
			is RoomMessageEventContent.TextBased -> content.bodyWithoutFallback

			is RoomMessageEventContent.FileBased.Image -> if (content.body != content.fileName) "🖼 " + content.bodyWithoutFallback else context.getString(R.string.notification_attachment_photo)
			is RoomMessageEventContent.FileBased.Video -> if (content.body != content.fileName) "🎞 " + content.bodyWithoutFallback else context.getString(R.string.notification_attachment_video)
			is RoomMessageEventContent.FileBased.Audio -> if (content.body != content.fileName) "🎙 " + content.bodyWithoutFallback else context.getString(R.string.notification_attachment_audio)
			is RoomMessageEventContent.FileBased.File -> if (content.body != content.fileName) "\uD83D\uDCCE " + content.bodyWithoutFallback else context.getString(R.string.notification_attachment)

			is RoomMessageEventContent.Location -> context.getString(R.string.notification_attachment_location)
			else -> null
		} ?: content.javaClass.name
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

	fun RecyclerView.isAtBottom(offset: Int = 0): Boolean {
		val lm = layoutManager as? LinearLayoutManager ?: return false
		if (lm.itemCount == 0) return true
		return if (lm.reverseLayout) {
			lm.findFirstCompletelyVisibleItemPosition() == 0 + offset
		} else {
			lm.findLastCompletelyVisibleItemPosition() == lm.itemCount - 1 + offset
		}
	}

	fun RecyclerView.scrollToBottom(offset: Int) {
		if (isAtBottom(offset)) {
			post {
				Log.i("RecyclerView.scrollToBottom", "scrollToPosition(0)")
				scrollToPosition(0)
			}
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

	fun RoomMessageEventContent.FileBased.getImageUrl(thumbnail: Boolean = true): String? {
		if (this.file != null) {
			return "mxc://sakuraNative/encrypted?data=" + URLEncoder.encode(
				Json.encodeToString(this.file),
				Charset.defaultCharset()
			)
		}
		return this.url?.let { url ->
			URLBuilder(url).apply {
				if (listOfNotNull(
						this@getImageUrl.info?.mimeType,
						this@getImageUrl.fileName,
						this@getImageUrl.body
					).any { it.contains(".gif") } && thumbnail
				) {
					// Make it so that gifs aren't received through the thumbnail endpoint
					// TODO: Make this toggleable in settings
					parameters.append("thumbnail", "false")
				} else parameters.append("thumbnail", thumbnail.toString())
			}.build().toString()
		}
	}

	fun RoomMessageEventContent.FileBased.getThumbnailUrl(): String? {
		val thumbUrl = when (this) {
			is RoomMessageEventContent.FileBased.Image -> info?.thumbnailUrl
			is RoomMessageEventContent.FileBased.Video -> info?.thumbnailUrl
			else -> null
		}
		val thumbFile = when (this) {
			is RoomMessageEventContent.FileBased.Image -> info?.thumbnailFile
			is RoomMessageEventContent.FileBased.Video -> info?.thumbnailFile
			else -> null
		}

		if (thumbFile != null) {
			return "mxc://sakuraNative/encrypted?data=" + URLEncoder.encode(
				Json.encodeToString(thumbFile),
				Charset.defaultCharset()
			)
		}
		return thumbUrl
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

	fun AvatarView.loadUser(user: RoomUser) = loadAvatar(user.avatarUrl, user.name)

	fun AvatarView.loadAvatar(url: String?, name: String) {
		if (url != null) {
			avatarInitials = null
			loadImage(url, true)
		} else name.getInitials(true).takeIf { it.isNotBlank() }?.let {
			avatarInitials = it
		}
	}

	private suspend fun getBubbleMetadata(
		context: Context,
		client: Matrix,
		roomId: RoomId,
		eventId: EventId? = null
	): NotificationCompat.BubbleMetadata {
		val bubbleIntent = Intent(context, BubbleActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
			setData("dev.kuylar.sakura://room/${roomId.full}".toUri())
			putExtra("roomId", roomId.full)
			if (eventId != null)
				putExtra("eventId", eventId.full)
		}

		val bubblePendingIntent = PendingIntent.getActivity(
			context,
			roomId.full.hashCode(),
			bubbleIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		)
		val icon = downloadIconIfNeeded(
			context,
			client,
			client.getRoom(roomId)?.avatarUrl,
			"r${roomId.full}"
		)
		return NotificationCompat.BubbleMetadata.Builder(
			bubblePendingIntent,
			if (icon != null) IconCompat.createWithContentUri(icon)
			else IconCompat.createWithResource(context, R.drawable.ic_notification_icon)
		).apply {
			setDesiredHeight(600)
			setAutoExpandBubble(false)
			setSuppressNotification(false)
		}.build()
	}

	suspend fun TimelineEvent.getBubbleMetadata(context: Context, client: Matrix) =
		getBubbleMetadata(context, client, roomId, eventId)

	suspend fun Room.getBubbleMetadata(context: Context, client: Matrix) =
		getBubbleMetadata(context, client, roomId)

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
			setData("dev.kuylar.sakura://room/${roomId.full}".toUri())
			putExtra("roomId", roomId.full)
			if (eventId != null)
				putExtra("eventId", eventId.full)
		}
		val replyPendingIntent = PendingIntent.getBroadcast(
			context,
			roomId.full.hashCode(),
			replyIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		)

		return Pair(remoteInput, replyPendingIntent)
	}

	fun TimelineEvent.getReplyIntent(context: Context) = getReplyIntent(context, roomId, eventId)
	fun Room.getReplyIntent(context: Context) = getReplyIntent(context, roomId)

	suspend fun Room.toShortcut(context: Context, client: Matrix): ShortcutInfoCompat {
		return ShortcutInfoCompat.Builder(context, roomId.full).apply {
			setCategories(mutableSetOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
			setIntent(Intent(Intent.ACTION_VIEW, "dev.kuylar.sakura://room/${roomId.full}".toUri()))
			setLongLived(true)
			setLocusId(LocusIdCompat(roomId.full))
			setShortLabel(getName(context, client))
			downloadIconIfNeeded(
				context,
				client,
				client.getRoom(roomId)?.avatarUrl,
				"r${roomId.full}"
			)?.let {
				setIcon(IconCompat.createWithContentUri(it))
			}
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
		val icon = uri?.let { IconCompat.createWithContentUri(it) }
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
			val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
			val circularBitmap = bitmap.toCircularBitmap()
			val outputStream = java.io.ByteArrayOutputStream()
			circularBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
			filePath.writeBytes(outputStream.toByteArray())
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

	val RoomMessageEventContent.content: String
		get() = this.formattedBodyWithoutFallback ?: this.bodyWithoutFallback

	fun <T> SortedList<T>.getOrNull(index: Int): T? {
		if (index < 0) return null
		if (index >= size()) return null
		return get(index)
	}

	@OptIn(ExperimentalTime::class)
	fun compareRoomsByTimestamp() = compareBy<Room> { it.lastRelevantEventTimestamp == null }
		.thenByDescending { it.lastRelevantEventTimestamp }
		.thenBy { it.roomId.full }

	fun <T> SortedList<T>.indexOfFirst(callback: ((T) -> Boolean)): Int {
		synchronized(this) {
			for (i in 0 until size()) {
				val item = get(i)
				if (callback.invoke(item)) return i
			}
			return -1
		}
	}

	val RoomUserReceipts.lastReceipt: EventId
		get() = receipts.maxBy { r -> r.value.receipt.timestamp }.value.eventId

	val units = listOf("KB", "MB", "GB")
	fun Long?.toFileSize(): String {
		val size = this ?: 0
		if (size < 1024) return "$size B"

		var value = size.toDouble()
		var unitIndex = -1

		do {
			value /= 1024
			unitIndex++
		} while (value >= 1024 && unitIndex < units.lastIndex)

		return String.format(Locale.getDefault(), "%.2f %s", value, units[unitIndex])
	}

	fun getMembershipChangeText(
		context: Context,
		stateKey: String,
		oldContent: MemberEventContent?,
		newContent: MemberEventContent,
		user: RoomUser?
	): String {
		val oldMembership = oldContent?.membership ?: Membership.LEAVE
		val oldDisplayName = oldContent?.displayName
		val oldAvatarUrl = oldContent?.avatarUrl

		val newMembership = newContent.membership
		val newDisplayName = newContent.displayName
		val newAvatarUrl = newContent.avatarUrl
		val reason = newContent.reason

		return when (oldMembership) {
			Membership.INVITE -> {
				when (newMembership) {
					Membership.INVITE -> context.getString(
						R.string.member_state_invited,
						user?.name,
						stateKey
					)

					Membership.JOIN -> context.getString(R.string.member_state_joined, user?.name)

					Membership.KNOCK -> context.getString(R.string.member_state_knocked, stateKey)

					Membership.LEAVE -> {
						if (stateKey == user?.userId?.full) {
							context.getString(R.string.member_state_invite_rejected, user.name)
						} else {
							context.getString(
								R.string.member_state_invite_rejected_by,
								user?.name,
								stateKey
							)
						}
					}

					Membership.BAN -> context.getString(
						R.string.member_state_ban,
						user?.name,
						stateKey
					)
				}
			}

			Membership.JOIN -> when (newMembership) {
				Membership.JOIN -> {
					val displayNameChanged = oldDisplayName != newDisplayName
					val avatarUrlChanged = oldAvatarUrl != newAvatarUrl

					if (displayNameChanged && avatarUrlChanged) {
						context.getString(
							R.string.member_state_change_name_and_avatar,
							oldDisplayName ?: stateKey,
							newDisplayName,
						)
					} else if (displayNameChanged) {
						context.getString(
							R.string.member_state_change_name,
							oldDisplayName ?: stateKey,
							newDisplayName,
						)
					} else if (avatarUrlChanged) {
						context.getString(R.string.member_state_change_avatar, user?.name)
					} else {
						"" // Must never happen
					}
				}

				Membership.LEAVE -> {
					if (stateKey == user?.userId?.full) {
						context.getString(R.string.member_state_left, user.name)
					} else {
						context.getString(R.string.member_state_left_by, user?.name, stateKey)
					}
				}

				Membership.BAN -> context.getString(R.string.member_state_ban, user?.name, stateKey)

				Membership.KNOCK, Membership.INVITE -> "" // Must never happen
			}

			Membership.LEAVE -> when (newMembership) {
				Membership.INVITE -> context.getString(
					R.string.member_state_invited,
					user?.name,
					stateKey
				)

				Membership.JOIN -> context.getString(R.string.member_state_joined, user?.name)

				Membership.LEAVE -> "" // No change

				Membership.BAN -> context.getString(R.string.member_state_ban, user?.name, stateKey)

				Membership.KNOCK -> context.getString(R.string.member_state_knocked, user?.name)
			}

			Membership.BAN -> when (newMembership) {
				Membership.LEAVE -> context.getString(
					R.string.member_state_unban,
					user?.name,
					stateKey
				)

				Membership.BAN -> context.getString(R.string.member_state_ban, user?.name, stateKey)

				Membership.INVITE, Membership.JOIN, Membership.KNOCK -> "" // Must never happen
			}

			Membership.KNOCK -> when (newMembership) {
				Membership.INVITE -> context.getString(
					R.string.member_state_knock_accepted,
					user?.name,
					stateKey
				)

				Membership.JOIN -> "" // Must never happen

				Membership.LEAVE -> {
					if (stateKey == user?.userId?.full) {
						context.getString(R.string.member_state_knock_cancelled, user.name)
					} else {
						context.getString(R.string.member_state_knock_denied, user?.name, stateKey)
					}
				}

				Membership.BAN -> context.getString(R.string.member_state_ban, user?.name, stateKey)

				Membership.KNOCK -> context.getString(R.string.member_state_knocked, user?.name)
			}
		}
	}

	fun getMembershipChangeDrawableId(
		oldMembership: Membership,
		newMembership: Membership
	): Int? {
		return when (oldMembership) {
			Membership.INVITE, Membership.LEAVE -> {
				when (newMembership) {
					Membership.INVITE -> R.drawable.ic_member_state_invited
					Membership.JOIN -> R.drawable.ic_member_state_joined
					Membership.KNOCK -> R.drawable.ic_member_state_knock
					Membership.LEAVE -> R.drawable.ic_member_state_leave
					Membership.BAN -> R.drawable.ic_member_state_ban
				}
			}

			Membership.JOIN -> {
				when (newMembership) {
					Membership.INVITE -> null
					Membership.JOIN -> R.drawable.ic_member_state_update
					Membership.KNOCK -> null
					Membership.LEAVE -> R.drawable.ic_member_state_leave
					Membership.BAN -> R.drawable.ic_member_state_ban
				}
			}
			Membership.KNOCK -> {
				when (newMembership) {
					Membership.INVITE -> R.drawable.ic_member_state_invited
					Membership.JOIN -> null
					Membership.KNOCK -> R.drawable.ic_member_state_knock
					Membership.LEAVE -> R.drawable.ic_member_state_leave
					Membership.BAN -> R.drawable.ic_member_state_ban
				}
			}
			Membership.BAN -> {
				when (newMembership) {
					Membership.INVITE -> null
					Membership.JOIN -> null
					Membership.KNOCK -> null
					Membership.LEAVE -> R.drawable.ic_member_state_update
					Membership.BAN -> R.drawable.ic_member_state_ban
				}
			}
		}
	}

	suspend fun Room.getName(context: Context, client: Matrix): String {
		val name = this.name
		val memberCount = (joinedMemberCount ?: 0) + (invitedMemberCount ?: 0)
		if (name != null) {
			if (name.explicitName?.isNotBlank() == true) return name.explicitName!!
			// TODO: We're not checking for `m.room.canonical_alias`
			val heroNames = name.heroes.filterNot { it == client.userId }.take(3).map {
				client.getUser(it, roomId)?.name ?: it.full
			}
			if (name.heroes.size >= memberCount - 1) {
				return context.getString(
					R.string.room_name_template_heroes,
					heroNames.joinToString(", ")
				)
			} else if (name.heroes.size < memberCount) {
				return context.getString(
					R.string.room_name_template_heroes_more,
					heroNames.joinToString(", "),
					name.heroes.size - heroNames.size
				)
			} else if (memberCount <= 1) {
				return context.getString(
					R.string.room_name_template_empty,
					heroNames.joinToString(", ")
				)
			}
		}
		return roomId.full
	}
}