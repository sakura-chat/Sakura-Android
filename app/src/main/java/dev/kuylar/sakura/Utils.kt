package dev.kuylar.sakura

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

	fun Long.toTimestamp(context: Context, nothing: Boolean): String {
		val now = System.currentTimeMillis()
		val pattern = "M/dd, HH:mm:SSS"
		val formatter = SimpleDateFormat(pattern, Locale.getDefault())
		return formatter.format(this)
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
			is RoomMessageEventContent.TextBased -> content.formattedBodyWithoutFallback ?: content.bodyWithoutFallback

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
}