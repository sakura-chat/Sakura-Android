package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

@Serializable
data class ShortcodeReactionEventContent(
	@SerialName("m.relates_to") override val relatesTo: RelatesTo.Annotation? = null,
	@SerialName("shortcode") val shortcode: String? = null,
	@SerialName("com.beeper.reaction.shortcode") val beeperShortcode: String? = null,
	@SerialName("external_url") override val externalUrl: String? = null,
) : MessageEventContent {
	@Transient
	override val mentions: Mentions? = null

	override fun copyWith(relatesTo: RelatesTo?): MessageEventContent =
		copy(
			relatesTo = relatesTo as? RelatesTo.Annotation,
			shortcode = shortcode,
			beeperShortcode = beeperShortcode
		)
}