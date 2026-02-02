package dev.kuylar.sakura.gifpicker.model

data class GifCategory(
	val name: String,
	val query: String,
	val cursor: String?,
	val featuredGif: Gif
)