package dev.kuylar.sakura.ui.adapter.recyclerview

import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.TimelineEventAggregation
import net.folivo.trixnity.client.room.getTimelineEventReactionAggregation
import net.folivo.trixnity.client.room.getTimelineEventReplaceAggregation
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

@OptIn(ExperimentalCoroutinesApi::class)
class EventModel(
	val roomId: RoomId,
	val eventId: EventId,
	val flow: Flow<TimelineEvent>,
	val client: Matrix,
	var snapshot: TimelineEvent? = null,
	var onChange: (() -> Unit)? = null
) {
	var repliedSnapshot: TimelineEvent? = null
	var reactions: TimelineEventAggregation.Reaction? = null
	var replaces: TimelineEventAggregation.Replace? = null
	private var collectJob: Job? = null
	private var reactionsJob: Job? = null
	private var replacesJob: Job? = null
	private var replyJob: Job? = null

	init {
		collectJob = CoroutineScope(Dispatchers.Main).launch {
			flow.collect {
				snapshot = it
				onChange?.invoke()
				if (replyJob == null && (it.content?.getOrNull() as? RoomMessageEventContent)?.relatesTo?.replyTo?.eventId != null) {
					replyJob = CoroutineScope(Dispatchers.Main).launch {
						client.client.room.getTimelineEvent(
							roomId,
							(it.content?.getOrNull() as RoomMessageEventContent).relatesTo?.replyTo?.eventId!!
						).collect { snapshot ->
							repliedSnapshot = snapshot
							onChange?.invoke()
						}
					}
				}
			}
		}
		reactionsJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.room.getTimelineEventReactionAggregation(roomId, eventId).collect {
				reactions = it
				onChange?.invoke()
			}
		}
		replacesJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.room.getTimelineEventReplaceAggregation(roomId, eventId).collect {
				replaces = it
				onChange?.invoke()
			}
		}
	}

	fun dispose() {
		collectJob?.cancel()
		collectJob = null
		reactionsJob?.cancel()
		reactionsJob = null
		replacesJob?.cancel()
		replacesJob = null
		replyJob?.cancel()
		replyJob = null
	}
}