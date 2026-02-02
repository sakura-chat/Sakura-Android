package dev.kuylar.sakura.gifpicker.model

data class GifPage(
	val gifs: List<Gif>,
	val cursor: String?
)
