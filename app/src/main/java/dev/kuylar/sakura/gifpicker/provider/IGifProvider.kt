package dev.kuylar.sakura.gifpicker.provider

import dev.kuylar.sakura.gifpicker.model.GifCategory
import dev.kuylar.sakura.gifpicker.model.GifPage

interface IGifProvider {
	fun getName(): String
	suspend fun getTrendingCategories(): List<GifCategory>
	suspend fun searchGifs(query: String, cursor: String?): GifPage
}