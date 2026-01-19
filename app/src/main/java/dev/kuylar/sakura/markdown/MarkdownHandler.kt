package dev.kuylar.sakura.markdown

import dev.kuylar.sakura.markdown.emoji.CustomEmojiExtension
import dev.kuylar.sakura.markdown.emoji.CustomEmojiHtmlRendererExtension
import dev.kuylar.sakura.markdown.emoji.CustomEmojiPlaintextRendererExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.LineBreakRendering
import org.commonmark.renderer.text.TextContentRenderer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkdownHandler @Inject constructor() {
	private val parser = Parser.builder().apply {
		extensions(listOf(CustomEmojiExtension()))
	}.build()
	private val htmlRenderer = HtmlRenderer.builder().apply {
		extensions(listOf(CustomEmojiHtmlRendererExtension()))
		omitSingleParagraphP(true)
	}.build()
	private val textRenderer = TextContentRenderer.builder().apply {
		extensions(listOf(CustomEmojiPlaintextRendererExtension()))
		lineBreakRendering(LineBreakRendering.SEPARATE_BLOCKS)
	}.build()

	fun inputToMarkdown(input: String): String {
		return htmlRenderer.render(parser.parse(input.replace("\n", "<br/>")))
	}

	fun inputToPlaintext(input: String): String {
		return textRenderer.render(parser.parse(input))
	}
}