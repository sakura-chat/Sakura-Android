package dev.kuylar.sakura.ui.adapter.model

import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

	fun getFlow(): Flow<Triple<Room?, Boolean, Int>> {
		return combine(
			client.client.room.getById(id),
			client.client.notification.isUnread(id),
			client.client.notification.getCount(id)
		) { room, unread, count ->
			isUnread = unread
			mentions = count
			Triple(room, unread, count)
		}
	}
}