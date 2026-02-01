package dev.kuylar.sakura.client.customevent

import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.Serializable

@Serializable
class UserImagePackEventContent : GlobalAccountDataEventContent {
	var images: Map<String, MatrixEmote>? = null
	var pack: MatrixEmotePack? = null
}