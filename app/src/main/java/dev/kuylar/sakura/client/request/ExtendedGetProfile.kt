package dev.kuylar.sakura.client.request

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/profile/{userId}")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class ExtendedGetProfile(
	@SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Unit, ExtendedGetProfile.Response> {
	@Serializable
	data class Response(
		@SerialName("displayname") val displayName: String?,
		@SerialName("avatar_url") val avatarUrl: String?,
		@SerialName("m.tz") val timezone: String?,
		// This one got merged, but Element on my desktop set this as the value, so I'm parsing
		// this just in case.
		@SerialName("us.cloke.msc4175.tz") val timezoneUnsafe: String?,
		// Doesn't have its own MSC, but is mentioned in MSC4208 as a "possible value"
		// so... just add it, why not
		@SerialName("uk.tcpip.msc4208.u.bio") val bio: String?,
		@SerialName("io.fsky.nyx.pronouns") val pronouns: List<Pronouns>?,
	) {
		@Serializable
		data class Pronouns(
			@SerialName("summary") val summary: String?,
			@SerialName("language") val language: String?,
			@SerialName("grammatical_gender") val grammaticalGender: String?
		)
	}
}