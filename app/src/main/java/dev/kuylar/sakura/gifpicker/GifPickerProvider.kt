package dev.kuylar.sakura.gifpicker

import android.content.Context
import androidx.preference.PreferenceManager
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
		val sp = PreferenceManager.getDefaultSharedPreferences(context)
		val selectedSource = sp.getString("textedit_gifpicker", "klipy") ?: "klipy"
		innerProvider = when (sp.getString("textedit_gifpicker", "klipy") ?: "klipy") {
			"tenor" -> TenorGifProvider("tenor.googleapis.com", "Tenor", "AIzaSyAPIbDRq5UQxGeiOSbBa5fBlliM8jxDfqU")
			"klipy" -> TenorGifProvider("api.klipy.com", "KLIPY", "M32EbIbyWi72jwJzQPff9rA1OVcwsgf3uHWhRgZMxml9AHDbniYqfqToUo9wYB8P")
			else -> TenorGifProvider()
		}
	}

	fun getName() = innerProvider?.getName() ?: "null"

	suspend fun getTrendingCategories() =
		innerProvider?.getTrendingCategories() ?: emptyList()

	suspend fun searchGifs(query: String, cursor: String? = null) =
		innerProvider?.searchGifs(query, cursor) ?: GifPage(emptyList(), null)
}