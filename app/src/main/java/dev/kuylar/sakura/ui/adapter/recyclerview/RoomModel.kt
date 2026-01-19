package dev.kuylar.sakura.ui.adapter.recyclerview

import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getAccountData
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent

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