package dev.kuylar.sakura.ui.adapter.timeline

import de.connect2x.trixnity.client.room.TimelineEventAggregation
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.client.store.unsigned
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.RoomEventContent
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

sealed class TimelineItem(
	val id: String,
	open val senderId: UserId,
	var content: RoomEventContent?,
	val sortTimestamp: Long,
	val timestamp: Long
) {
	var user: RoomUser? = null

	fun compareTo(other: TimelineItem): Int {
		if (this is Event && other is Event) {
			if (this.event.eventId == other.event.previousEventId || other.event.eventId == this.event.nextEventId)
				return -1

			if (this.event.eventId == other.event.nextEventId || other.event.eventId == this.event.previousEventId)
				return 1
		}

		return sortTimestamp.compareTo(other.sortTimestamp)
	}

	data class Event(var event: TimelineEvent, var flow: Flow<Snapshot>) : TimelineItem(
		event.unsigned?.transactionId ?: event.eventId.full,
		event.sender,
		event.content?.getOrNull(),
		event.originTimestamp,
		event.originTimestamp
	) {
		var reactions = TimelineEventAggregation.Reaction(emptyMap())
		var edits = TimelineEventAggregation.Replace(null, emptyList())
		var repliedToEvent: Snapshot.Reply? = null
		fun update(snapshot: Snapshot) {
			this@Event.event = snapshot.newEvent
			user = snapshot.newUser
			reactions = snapshot.newReactions
			edits = snapshot.newReplace
			repliedToEvent = snapshot.repliedTo
			content = this@Event.event.content?.getOrNull()
		}

		data class Snapshot(
			val newEvent: TimelineEvent,
			val newUser: RoomUser?,
			val newReactions: TimelineEventAggregation.Reaction,
			val newReplace: TimelineEventAggregation.Replace,
			val repliedTo: Reply?
		) {
			data class Reply(
				val event: TimelineEvent,
				val user: RoomUser
			)
		}
	}

	@OptIn(ExperimentalTime::class)
	data class OutboxItem(
		var snapshot: RoomOutboxMessage<*>,
		override val senderId: UserId,
		var flow: Flow<RoomOutboxMessage<*>>
	) : TimelineItem(
		snapshot.transactionId,
		senderId,
		snapshot.content as? RoomEventContent,
		snapshot.createdAt.toEpochMilliseconds() + 24.hours.inWholeMilliseconds,
		snapshot.createdAt.toEpochMilliseconds()
	)
}