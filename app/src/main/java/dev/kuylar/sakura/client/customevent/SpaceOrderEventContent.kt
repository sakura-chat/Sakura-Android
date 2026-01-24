package dev.kuylar.sakura.client.customevent

import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import kotlinx.serialization.Serializable

@Serializable
class SpaceOrderEventContent() : RoomAccountDataEventContent {
	val order: String? = null
}