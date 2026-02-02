package dev.kuylar.sakura.client.customevent

import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.ImageInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StickerMessageEventContent(
	@SerialName("body") val body: String? = null,
	@SerialName("info") val info: ImageInfo? = null,
	@SerialName("url") val url: String? = null,
	@SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
	@SerialName("m.mentions") override val mentions: Mentions? = null,
	@SerialName("external_url") override val externalUrl: String? = null,
) : MessageEventContent {
	override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
}