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
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class EventModel(
	override val roomId: RoomId,
	override val eventId: EventId,
	val flow: Flow<TimelineEvent>,
	val client: Matrix,
	var snapshot: TimelineEvent,
	var onChange: (() -> Unit)? = null
) : TimelineModel {
	var repliedSnapshot: TimelineEvent? = null
	var userSnapshot: RoomUser? = null
	var reactions: TimelineEventAggregation.Reaction? = null
	var replaces: TimelineEventAggregation.Replace? = null
	private var collectJob: Job? = null
	override val type: Int
		get() = TimelineModel.TYPE_EVENT
	override val timestamp: Long
		get() = snapshot.originTimestamp

	init {
		val repliedEventId =
			(snapshot.content?.getOrNull() as? MessageEventContent)?.relatesTo?.replyTo?.eventId
		collectJob = suspendThread {
			combine(
				flow,
				if (repliedEventId != null)
					client.client.room.getTimelineEvent(roomId, repliedEventId)
				else
					flowOf<TimelineEvent?>(null),
				client.client.room.getTimelineEventReactionAggregation(roomId, eventId),
				client.client.room.getTimelineEventReplaceAggregation(roomId, eventId),
				client.client.user.getById(roomId, snapshot.sender)
			) { message, reply, reactions, edits, user ->
				Pair(Triple(message, reply, user), Pair(reactions, edits))
			}.collect { (p1, p2) ->
				val newMessage = p1.first
				val newReply = p1.second
				val newUser = p1.third
				val newReactions = p2.first
				val newEdits = p2.second

				snapshot = newMessage
				repliedSnapshot = newReply
				reactions = newReactions
				replaces = newEdits
				userSnapshot = newUser
				onChange?.invoke()
			}
		}
	}

	override fun dispose() {
		collectJob?.cancel()
		collectJob = null
	}
}