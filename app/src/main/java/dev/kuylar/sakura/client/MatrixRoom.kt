package dev.kuylar.sakura.client

import de.connect2x.trixnity.client.store.Room
import dev.kuylar.sakura.client.customevent.SpaceChildrenEventContent
import dev.kuylar.sakura.client.customevent.SpaceParentEventContent
import kotlinx.coroutines.flow.StateFlow

data class MatrixRoom(
	val room: StateFlow<Room?>,
	val parentState: StateFlow<Map<String, SpaceParentEventContent>>,
	val childState: StateFlow<Map<String, SpaceChildrenEventContent>>
)