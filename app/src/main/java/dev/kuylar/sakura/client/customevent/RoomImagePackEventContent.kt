package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.ImageInfo

@Serializable
class RoomImagePackEventContent(override val externalUrl: String?) : StateEventContent {
	var images: Map<String, MatrixEmote>? = null
	var pack: MatrixEmotePack? = null
}

@Serializable
data class MatrixEmote(
	val url: String,
	val body: String? = null,
	val info: ImageInfo? = null,
	val usage: List<String>? = null,
)

@Serializable
data class MatrixEmotePack(
	@SerialName("display_name") val displayName: String? = null,
	@SerialName("avatar_url") val avatarUrl: String? = null,
	val usage: List<String>? = null,
	val attribution: String? = null,
)