package dev.kuylar.sakura

import android.content.Context
import android.text.format.DateUtils
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import kotlin.concurrent.thread

object Utils {
	fun suspendThread(block: suspend (() -> Unit)) {
		thread {
			runBlocking {
				block.invoke()
			}
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

	fun Long.withinSameDay(other: Long): Boolean {
		val zoneId = ZoneId.systemDefault()
		return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate() ==
				Instant.ofEpochMilli(other).atZone(zoneId).toLocalDate()
	}
}