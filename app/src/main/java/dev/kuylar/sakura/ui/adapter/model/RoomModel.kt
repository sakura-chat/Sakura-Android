package dev.kuylar.sakura.ui.adapter.model

import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoomModel(
	val id: RoomId,
	var snapshot: Room,
	val client: Matrix,
	var onChange: (() -> Unit)? = null
) {
	private var collectJob: Job? = null
	var lastMessage: TimelineEvent? = null
	var isUnread = false
	var mentions = 0
	var muted = false

	init {
		collectJob = suspendThread {
			combine(
				client.client.room.getById(id),
				client.client.notification.isUnread(id),
				client.client.notification.getCount(id)
			) { room, unread, count ->
				Triple(room, unread, count)
			}.collect { (room, unread, count) ->
				room?.let {
					snapshot = it
					snapshot.lastRelevantEventId?.let { eventId ->
						lastMessage = client.getEvent(id, eventId)
					}
				}
				isUnread = unread
				mentions = count
				onChange?.invoke()
			}
			client.client.room.getById(id).collect {
				snapshot = it ?: snapshot
				onChange?.invoke()
			}
		}
	}

	fun dispose() {
		collectJob?.cancel()
		collectJob = null
	}
}