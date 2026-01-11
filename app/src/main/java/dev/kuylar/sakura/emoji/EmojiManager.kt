package dev.kuylar.sakura.emoji

import android.content.Context
import dev.kuylar.sakura.R
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@OptIn(ExperimentalSerializationApi::class)
class EmojiManager(context: Context) {
	private val emoji: EmojiMap
	private lateinit var cachedEmojiByCategory: Map<String, List<Emoji>>

	init {
		val inputStream = context.resources.openRawResource(R.raw.emoji)
		emoji = Json.decodeFromStream<EmojiMap>(inputStream)
	}

	fun getEmojiByCategory(): Map<String, List<Emoji>> {
		if (!this::cachedEmojiByCategory.isInitialized)
			cachedEmojiByCategory = emoji.emojisByCategory
				.mapKeys { "unicode:${it.key}" }
				.mapValues {
					val start = it.value.first()
					val end = it.value.last()
					emoji.emojis.subList(start, end + 1)
				}
		return cachedEmojiByCategory
	}

	companion object {
		private lateinit var instance: EmojiManager

		fun getInstance(context: Context): EmojiManager {
			if (!this::instance.isInitialized) instance = EmojiManager(context)
			return instance
		}
	}
}