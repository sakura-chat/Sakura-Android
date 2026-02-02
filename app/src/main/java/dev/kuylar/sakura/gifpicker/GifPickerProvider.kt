package dev.kuylar.sakura.gifpicker

import android.content.Context
import dev.kuylar.sakura.gifpicker.model.GifPage
import dev.kuylar.sakura.gifpicker.provider.IGifProvider
import dev.kuylar.sakura.gifpicker.provider.TenorGifProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GifPickerProvider @Inject constructor() {
	private var innerProvider: IGifProvider? = null

	fun init(context: Context) {
		// TODO: Get active GIF picker from preferences
		var selectedSource = "tenor"
		innerProvider = when (selectedSource) {
			"tenor" -> TenorGifProvider()
			else -> TenorGifProvider()
		}
	}

	fun getName() = innerProvider?.getName() ?: "null"

	suspend fun getTrendingCategories() =
		innerProvider?.getTrendingCategories() ?: emptyList()

	suspend fun searchGifs(query: String, cursor: String? = null) =
		innerProvider?.searchGifs(query, cursor) ?: GifPage(emptyList(), null)
}