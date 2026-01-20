package dev.kuylar.sakura

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import java.time.Instant
import java.time.ZoneId
import kotlin.concurrent.thread

object Utils {
	fun suspendThread(block: suspend (() -> Unit)): Job {
		return CoroutineScope(Dispatchers.Main).launch {
			block.invoke()
		}
	}

	fun Long.toTimestamp(context: Context): String {
		val now = System.currentTimeMillis()
		return if (withinSameDay(now)) {
			DateUtils.getRelativeTimeSpanString(this, now, DateUtils.SECOND_IN_MILLIS)
				.toString()
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

	fun Long.toTimestampDate(context: Context) =
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
		when (content) {
			// TODO: Fill every single one of these
			is RoomMessageEventContent.TextBased.Text -> return content.body

			else -> return content.javaClass.name
		}
	}
}