package dev.kuylar.sakura

import android.net.Uri
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.kuylar.sakura.MatrixUrlParser.Result.EventIdResult
import dev.kuylar.sakura.MatrixUrlParser.Result.RoomAliasResult
import dev.kuylar.sakura.MatrixUrlParser.Result.RoomIdResult
import dev.kuylar.sakura.MatrixUrlParser.Result.UserResult
import java.net.URLDecoder

object MatrixUrlParser {
	fun parse(uri: Uri): Result? {
		val parts = uri.schemeSpecificPart.substringBefore("?").split("/")
		val query = uri.schemeSpecificPart.substringAfter("?").split("&")
			.map { it.split("=", limit = 2).map { p -> URLDecoder.decode(p, "UTF-8") } }
			.filter { it.size == 2 }
			.map { Pair(it[0], it[1]) }
			.groupBy { it.first }
			.mapValues { it.value.map { e -> e.second } }
		if (parts.size != 2 && parts.size != 4) return null
		return when (parts[0]) {
			"u" -> UserResult(
				UserId("@${parts[1].trimStart('@')}"),
				query["app.sakurachat.roomid"]?.firstOrNull()?.let { RoomId(it) },
				query["action"]?.firstOrNull() ?: "chat"
			)

			"r" -> RoomAliasResult(
				"#${parts[1].trimStart('#')}",
				query["action"]?.firstOrNull() ?: "join"
			)

			"roomid" -> when (parts.size) {
				2 -> RoomIdResult(
					RoomId("!${parts[1]}"),
					query["via"] ?: emptyList()
				)

				4 -> EventIdResult(
					RoomId("!${parts[1]}"),
					EventId("$${parts[3]}"),
					query["via"] ?: emptyList()
				)

				else -> null
			}

			// Malformed URL
			else -> null
		}
	}

	open class Result {
		data class UserResult(
			val user: UserId,
			// Used only to show room profiles.
			// Not in the standard, Sakura only
			val room: RoomId?,
			val action: String
		) : Result()

		data class RoomAliasResult(
			val alias: String,
			val action: String
		) : Result()

		data class RoomIdResult(
			val room: RoomId,
			val via: List<String>
		) : Result()

		data class EventIdResult(
			val room: RoomId,
			val event: EventId,
			val via: List<String>
		) : Result()
	}
}