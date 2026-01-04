package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

@Serializable
class SpaceChildrenEventContent(override val externalUrl: String?) : StateEventContent {
	val via: List<String>? = null
	val suggested: Boolean? = null
}