package dev.kuylar.sakura.ui.adapter.model

import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.TimelineEventAggregation
import de.connect2x.trixnity.client.room.getTimelineEventReactionAggregation
import de.connect2x.trixnity.client.room.getTimelineEventReplaceAggregation
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class EventModel(
	override val roomId: RoomId,
	override val eventId: EventId,
	val flow: Flow<TimelineEvent>,
	val client: Matrix,
	var eventSnapshot: TimelineEvent,
	var onChange: (() -> Unit)? = null
) : TimelineModel {
	var repliedSnapshot: TimelineEvent? = null
	var userSnapshot: RoomUser? = null
	var reactions: TimelineEventAggregation.Reaction? = null
	var replaces: TimelineEventAggregation.Replace? = null
	var snapshot: Snapshot? = null
	private val combinedFlow: Flow<Snapshot>
	private var collectJob: Job? = null
	override val type: Int
		get() = TimelineModel.TYPE_EVENT
	override val timestamp: Long
		get() = eventSnapshot.originTimestamp

	init {
		val repliedEventId =
			(eventSnapshot.content?.getOrNull() as? MessageEventContent)?.relatesTo?.replyTo?.eventId
		combinedFlow = combine(
			flow,
			if (repliedEventId != null)
				client.client.room.getTimelineEvent(roomId, repliedEventId)
			else
				flowOf<TimelineEvent?>(null),
			client.client.room.getTimelineEventReactionAggregation(roomId, eventId),
			client.client.room.getTimelineEventReplaceAggregation(roomId, eventId),
			client.client.user.getById(roomId, eventSnapshot.sender)
		) { message, reply, reactions, edits, user ->
			Snapshot(message, reply, user, reactions, edits)
		}
		suspendThread {
			collect(combinedFlow.first())
		}
	}

	override fun start() {
		if (collectJob?.isActive == true) return
		collectJob = suspendThread {
			combinedFlow.collect { collect(it) }
		}
	}

	override fun pause() {
		collectJob?.cancel()
		collectJob = null
	}

	fun collect(newSnapshot: Snapshot) {
		if (snapshot == newSnapshot) return

		snapshot = newSnapshot
		eventSnapshot = newSnapshot.event
		repliedSnapshot = newSnapshot.repliedEvent
		reactions = newSnapshot.reactions
		replaces = newSnapshot.edits
		userSnapshot = newSnapshot.user
		onChange?.invoke()
	}

	override fun dispose() {
		collectJob?.cancel()
		collectJob = null
	}

	data class Snapshot(
		val event: TimelineEvent,
		val repliedEvent: TimelineEvent?,
		val user: RoomUser?,
		val reactions: TimelineEventAggregation.Reaction,
		val edits: TimelineEventAggregation.Replace
	)
}