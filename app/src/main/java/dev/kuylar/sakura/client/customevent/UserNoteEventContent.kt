package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.ImageInfo

@Serializable
class UserNoteEventContent(var notes: Map<UserId, String>? = null) : GlobalAccountDataEventContent {
	fun copyWith(userId: UserId, note: String) = UserNoteEventContent(notes?.plus(userId to note))
}