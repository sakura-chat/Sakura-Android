package dev.kuylar.sakura.gifpicker.provider

import dev.kuylar.sakura.gifpicker.model.Gif
import dev.kuylar.sakura.gifpicker.model.GifCategory
import dev.kuylar.sakura.gifpicker.model.GifPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TenorGifProvider : IGifProvider {
	private val client = HttpClient {
		install(ContentNegotiation) {
			json()
		}
	}

	override fun getName() = "Tenor"

	override suspend fun getTrendingCategories(): List<GifCategory> {
		val resp =
			client.get("https://tenor.googleapis.com/v2/categories?type=trending&key=$API_KEY")
				.body<TrendingResponse>()
		return resp.tags.map {
			GifCategory(
				it.name, it.searchTerm, null, Gif(
					gifUrl = it.image,
					caption = "",
					fileName = it.image.substringAfterLast('/'),
					width = 1,
					height = 1
				)
			)
		}
	}

	override suspend fun searchGifs(query: String, cursor: String?): GifPage {
		val url = URLBuilder("https://tenor.googleapis.com/v2/search")
		url.parameters.append("q", query)
		url.parameters.append("media_filter", "gif")
		cursor?.let { url.parameters.append("q", it) }
		url.parameters.append("key", API_KEY)
		val resp = client.get(url.build()).body<ResultPage>()
		return GifPage(
			resp.results.map {
				val fmt = it.mediaFormats.values.first()
				Gif(
					gifUrl = fmt.url,
					caption = it.title.takeUnless { it.isBlank() } ?: it.tags.joinToString(", "),
					fileName = "${it.itemUrl.substringBeforeLast("-")}.gif",
					width = fmt.dims[0],
					height = fmt.dims[1]
				)
			},
			resp.next
		)
	}

	@Serializable
	data class TrendingResponse(
		val locale: String, val tags: List<TrendingItem>
	)

	@Serializable
	data class ResultPage(
		val results: List<TenorGif>, val next: String? = null
	)

	@Serializable
	data class TrendingItem(
		@SerialName("searchterm") val searchTerm: String,
		val path: String,
		val image: String,
		val name: String
	)

	@Serializable
	data class TenorGif(
		val id: String,
		val title: String,
		@SerialName("media_formats") val mediaFormats: Map<String, Format>,
		val created: Double,
		@SerialName("content_description") val contentDescription: String,
		@SerialName("itemurl") val itemUrl: String,
		val url: String,
		val tags: List<String>,
		val flags: List<String>,
		@SerialName("hasaudio") val hasAudio: Boolean,
		@SerialName("content_description_source") val contentDescriptionSource: String
	)

	@Serializable
	data class Format(
		val url: String,
		val duration: Double,
		val preview: String,
		val dims: List<Int>,
		val size: Long
	)

	companion object {
		const val API_KEY = "AIzaSyAPIbDRq5UQxGeiOSbBa5fBlliM8jxDfqU"
	}
}
