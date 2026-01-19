package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.ImageInfo

@Serializable
class UserImagePackEventContent : GlobalAccountDataEventContent {
	var images: Map<String, MatrixEmote>? = null
	var packs: MatrixEmotePack? = null
}