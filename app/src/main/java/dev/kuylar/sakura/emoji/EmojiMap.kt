package dev.kuylar.sakura.emoji

import kotlinx.serialization.Serializable

@Serializable
data class EmojiMap (
	val emojis: List<Emoji>,
	val emojisByCategory: Map<String, List<Int>>,
	val nameToEmoji: Map<String, Int>,
	val surrogateToEmoji: Map<String, Int>,
	val numDiversitySprites: Int,
	val numNonDiversitySprites: Int
)