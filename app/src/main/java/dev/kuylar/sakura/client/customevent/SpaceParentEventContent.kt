package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.StateEventContent

@Serializable
class SpaceParentEventContent(override val externalUrl: String?) : StateEventContent {
	val via: List<String>? = null
	val canonical: Boolean? = null
}