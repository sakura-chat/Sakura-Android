package dev.kuylar.sakura.markdown

import android.content.Context
import android.text.Spannable
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.text.getSpans
import dev.kuylar.mentionsedittext.ImageMentionSpan
import dev.kuylar.sakura.markdown.emoji.CustomEmojiExtension
import dev.kuylar.sakura.markdown.emoji.CustomEmojiHtmlRendererExtension
import dev.kuylar.sakura.markdown.emoji.CustomEmojiPlaintextRendererExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.LineBreakRendering
import org.commonmark.renderer.text.TextContentRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
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

	fun inputToHtml(input: String): String {
		return htmlRenderer.render(parser.parse(input.replace("\n", "<br/>")))
	}

	fun inputToPlaintext(input: String): String {
		return textRenderer.render(parser.parse(input))
	}

	fun htmlToMarkdown(html: String?): String {
		if (html == null) return ""
		val html = Jsoup.parse(html)
		return htmlNodeToMarkdown(html.body().childNodes()).trim()
	}

	private fun markdownToSpannable(input: String, context: Context): Spannable {
		return SpannableRenderer(context).render(parser.parse(input))
	}

	private fun htmlToSpannable(html: String?, context: Context): Spannable {
		return markdownToSpannable(htmlToMarkdown(html), context)
	}
	
	fun setTextView(textView: TextView, html: String?) {
		val spannable = htmlToSpannable(html, textView.context)
		textView.text = spannable
		spannable.getSpans<ImageMentionSpan>().forEach {
			it.onImageLoaded = { textView.postInvalidate() }
		}
	}

	private fun htmlNodeToMarkdown(node: Node, parentNodeName: String? = null): String {
		return when (node) {
			is TextNode -> node.nodeText()

			is Element -> when (node.tagName()) {
				"br" -> "  \n"

				"img" -> {
					val isEmoji = node.hasAttr("data-mx-emoticon")
					val altText = node.attr("alt").takeUnless { x -> x.isBlank() }?.trim(':')
						?: node.attr("title").takeUnless { x -> x.isBlank() }?.trim(':')
						?: "emoji"
					val url = node.attr("src").substringAfter("mxc://")
					if (isEmoji) {
						":$altText~$url:"
					} else {
						altText
					}
				}

				"a" -> {
					val url = node.attr("href").toUri()
					val text = node.text()
					// User mentions
					if (url.host == "matrix.to" && url.fragment?.startsWith("/@") == true) {
						"<${url.fragment?.substring(1)}>"
					} else {
						"[${text}](${url})"
					}
				}

				"strong" -> {
					"**${node.text()}**"
				}

				"em", "i" -> {
					"*${node.text()}*"
				}

				"p" -> {
					val sb = StringBuilder()
					node.childNodes().forEach {
						sb.append(htmlNodeToMarkdown(it, node.nodeName()))
					}
					sb.appendLine()
					sb.toString()
				}

				"blockquote" -> {
					val sb = StringBuilder()
					for (i in 0 until node.childNodeSize()) {
						val node = node.childNode(i)
						val value = htmlNodeToMarkdown(node)
						sb.append(value)
					}
					sb.appendLine()
					sb.appendLine()
					sb.toString().split("\n").joinToString("\n") {
						if (it.isBlank()) "" else "> $it"
					}
				}

				"ul", "ol" -> {
					val sb = StringBuilder()
					node.childNodes().forEach {
						val value = htmlNodeToMarkdown(it, node.nodeName()).trim()
						if (value.isNotBlank()) {
							sb.appendLine(value)
						}
					}
					sb.appendLine()
					sb.toString()
				}

				"li" -> {
					if (parentNodeName == "ol") {
						val index =
							(node.parentNode()?.childNodes()?.filter { it.nodeName() == "li" }
								?.indexOf(node) ?: 0) + 1
						"$index. ${htmlNodeToMarkdown(node.childNodes())}"
					} else
						"* ${htmlNodeToMarkdown(node.childNodes())}"
				}

				"code" -> {
					"`${node.text()}`"
				}

				"pre" -> {
					"```${htmlNodeToPlaintext(node.childNodes())}```"
				}

				"h1", "h2", "h3", "h4", "h5", "h6" -> {
					val level = node.tagName().substring(1).toInt()
					"${"#".repeat(level)} ${htmlNodeToMarkdown(node.childNodes())}\n\n"
				}

				else -> "!${node.nodeName()}[${node.text()}]!"
			}

			else -> "#${node.nodeName()}[${node.nodeText()}]#"
		}
	}

	private fun htmlNodeToMarkdown(node: MutableList<Node>): String {
		val sb = StringBuilder()
		node.forEach {
			sb.append(htmlNodeToMarkdown(it))
		}
		return sb.toString()
	}

	private fun htmlNodeToPlaintext(node: MutableList<Node>): String {
		val sb = StringBuilder()
		node.forEach {
			if (it.childNodes().isEmpty())
				sb.append(it.nodeValue())
			else
				sb.append(htmlNodeToPlaintext(it.childNodes()))
		}
		return sb.toString()
	}

	private fun Node.nodeText(): String {
		return nodeValue().replace("\n", "")
	}
}

