package dev.kuylar.sakura.markdown.emoji

import org.commonmark.parser.Parser

class CustomEmojiExtension: Parser.ParserExtension {
	override fun extend(parserBuilder: Parser.Builder) {
		parserBuilder.customInlineContentParserFactory(CustomEmojiParser.Factory())
	}
}