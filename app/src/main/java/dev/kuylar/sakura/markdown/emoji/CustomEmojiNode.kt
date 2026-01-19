package dev.kuylar.sakura.markdown.emoji

import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited

class CustomEmojiNode(val uri: String, val shortcode: String) : CustomNode(), Delimited {
	override fun getOpeningDelimiter() = ":"

	override fun getClosingDelimiter() = ":"
}