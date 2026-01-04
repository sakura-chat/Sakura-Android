package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent

@Serializable
class SpaceOrderEventContent() : RoomAccountDataEventContent {
	val order: String? = null
}