package dev.kuylar.sakura.ui.adapter.recyclerview

import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomModel(
	val id: RoomId,
	var snapshot: Room,
	val client: Matrix,
	var onChange: (() -> Unit)? = null
) {
	private var collectJob: Job? = null
	var lastMessage: TimelineEvent? = null

	init {
		collectJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.room.getById(snapshot.roomId).collect {
				snapshot = it ?: snapshot
				if (it != null && it.lastRelevantEventId != null &&
					it.lastRelevantEventId != lastMessage?.eventId
				)
					lastMessage = client.getEvent(id, it.lastRelevantEventId!!)
				onChange?.invoke()
			}
		}
	}

	fun dispose() {
		collectJob?.cancel()
		collectJob = null
	}
}