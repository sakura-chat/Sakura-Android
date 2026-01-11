package dev.kuylar.sakura.client.customevent

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

@Serializable
data class RecentEmoji(
    var emoji: String,
    var count: Int
)

object RecentEmojiListSerializer : KSerializer<List<RecentEmoji>> {
    override val descriptor: SerialDescriptor = ListSerializer(JsonArray.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<RecentEmoji>) {
        val jsonEncoder = encoder as JsonEncoder
        val array = JsonArray(value.map { emoji ->
            JsonArray(listOf(JsonPrimitive(emoji.emoji), JsonPrimitive(emoji.count)))
        })
        jsonEncoder.encodeJsonElement(array)
    }

    override fun deserialize(decoder: Decoder): List<RecentEmoji> {
        val jsonDecoder = decoder as JsonDecoder
        val array = jsonDecoder.decodeJsonElement().jsonArray
        return array.map { element ->
            val pair = element.jsonArray
            RecentEmoji(
                emoji = pair[0].jsonPrimitive.content,
                count = pair[1].jsonPrimitive.int
            )
        }
    }
}

@Serializable
class ElementRecentEmojiEventContent() : GlobalAccountDataEventContent {
	@Serializable(with = RecentEmojiListSerializer::class)
	@SerialName("recent_emoji")
	var recentEmoji: List<RecentEmoji>? = null
}