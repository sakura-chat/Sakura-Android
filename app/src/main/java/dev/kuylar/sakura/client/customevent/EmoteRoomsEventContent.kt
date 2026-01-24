package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent

@Serializable
class EmoteRoomsEventContent() : GlobalAccountDataEventContent {
	var rooms: Map<String, Map<String, EmoteRoom>>? = null

	@Serializable
	class EmoteRoom // Empty object as of 2026-01-19
}