package dev.kuylar.sakura.client.customevent

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import kotlinx.serialization.Serializable

@Serializable
class UserNoteEventContent(var notes: Map<UserId, String>? = null) : GlobalAccountDataEventContent {
	fun copyWith(userId: UserId, note: String) = UserNoteEventContent(notes?.plus(userId to note))
}