package dev.kuylar.sakura.emoji

import kotlinx.serialization.Serializable

@Serializable
data class Emoji (
	val names: List<String>,
	val surrogates: String,
	val unicodeVersion: Double,
	val spriteIndex: Int? = null,
	val hasMultiDiversity: Boolean? = null,
	val diversityChildren: List<Int>? = null,
	val hasDiversity: Boolean? = null,
	val hasMultiDiversityParent: Boolean? = null,
	val diversity: List<String>? = null,
	val hasDiversityParent: Boolean? = null
)