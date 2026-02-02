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
	private var reactionsJob: Job? = null
	private var replacesJob: Job? = null
	private var replyJob: Job? = null
	private var userJob: Job? = null
	override val type: Int
		get() = TimelineModel.TYPE_EVENT
	override val timestamp: Long
		get() = snapshot.originTimestamp

	init {
		collectJob = suspendThread {
			flow.collect {
				snapshot = it
				onChange?.invoke()
				if (replyJob == null && (it.content?.getOrNull() as? MessageEventContent)?.relatesTo?.replyTo?.eventId != null) {
					replyJob = suspendThread {
						client.client.room.getTimelineEvent(
							roomId,
							(it.content?.getOrNull() as MessageEventContent).relatesTo?.replyTo?.eventId!!
						).collect { snapshot ->
							repliedSnapshot = snapshot
							onChange?.invoke()
						}
					}
				}

			}
		}
		reactionsJob = suspendThread {
			client.client.room.getTimelineEventReactionAggregation(roomId, eventId).collect {
				reactions = it
				onChange?.invoke()
			}
		}
		replacesJob = suspendThread {
			client.client.room.getTimelineEventReplaceAggregation(roomId, eventId).collect {
				replaces = it
				onChange?.invoke()
			}
		}
		userJob = suspendThread {
			client.client.user.getById(roomId, snapshot.sender).collect { snapshot ->
				userSnapshot = snapshot
				onChange?.invoke()
			}
		}
	}

	override fun dispose() {
		collectJob?.cancel()
		collectJob = null
		reactionsJob?.cancel()
		reactionsJob = null
		replacesJob?.cancel()
		replacesJob = null
		replyJob?.cancel()
		replyJob = null
		userJob?.cancel()
		userJob = null
	}
}