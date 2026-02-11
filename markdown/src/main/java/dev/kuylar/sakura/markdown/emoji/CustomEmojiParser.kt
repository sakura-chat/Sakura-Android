package dev.kuylar.sakura.markdown.emoji

import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline

class CustomEmojiParser : InlineContentParser {
	override fun tryParse(state: InlineParserState): ParsedInline? {
		val scanner = state.scanner()

		if (scanner.peek() != ':')
			return null
		scanner.next()

		val content = StringBuilder()
		while (scanner.hasNext() && scanner.peek() != ':') {
			content.append(scanner.peek())
			scanner.next()
		}
		if (scanner.peek() != ':') {
			return null
		}

		val parts = content.toString().split("~", limit = 2)
		if (parts.size != 2) {
			return null
		}

		scanner.next()
		val node = CustomEmojiExtension.CustomEmojiNode("mxc://" + parts[1], parts[0])
		return ParsedInline.of(node, scanner.position())
	}

	class Factory : InlineContentParserFactory {
		override fun getTriggerCharacters() = setOf(':')

		override fun create() = CustomEmojiParser()
	}
}