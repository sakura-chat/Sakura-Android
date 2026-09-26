package dev.kuylar.sakura.ui.adapter.model

import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.client.Matrix
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoomModel(
	val id: RoomId,
	var snapshot: Room,
	val client: Matrix
) {
	var isUnread = false
		private set
	var mentions = 0
		private set
}