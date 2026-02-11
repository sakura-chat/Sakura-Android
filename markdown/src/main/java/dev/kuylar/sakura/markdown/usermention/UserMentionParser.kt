package dev.kuylar.sakura.markdown.usermention

import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline

class UserMentionParser : InlineContentParser {
	override fun tryParse(state: InlineParserState): ParsedInline? {
		val scanner = state.scanner()

		if (scanner.peek() != '<')
			return null
		scanner.next()

		val content = StringBuilder()
		while (scanner.hasNext() && scanner.peek() != '>') {
			content.append(scanner.peek())
			scanner.next()
		}
		if (scanner.peek() != '>') {
			return null
		}

		val str = scanner.toString()
		if (str.firstOrNull() != '@') return null
		val split = str.split('|', limit = 2)

		scanner.next()
		val node = UserMentionExtension.UserMentionNode(
			split[0],
			if (split.size > 1) split[1] else split[0]
		)
		return ParsedInline.of(node, scanner.position())
	}

	class Factory : InlineContentParserFactory {
		override fun getTriggerCharacters() = setOf('<')

		override fun create() = UserMentionParser()
	}
}