package dev.kuylar.sakura.gifpicker.model

data class Gif(
	val gifUrl: String,
	val caption: String,
	val fileName: String,
	val width: Int,
	val height: Int
)