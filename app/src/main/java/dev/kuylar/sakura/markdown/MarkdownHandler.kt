package dev.kuylar.sakura.markdown

import android.app.Application
import android.util.Log
import android.widget.TextView
import androidx.core.net.toUri
import dev.kuylar.sakura.SakuraApplication
import dev.kuylar.sakura.markdown.plugin.emoji.CustomEmojiPlugin
import dev.kuylar.sakura.markdown.plugin.emoji.UserMentionPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkdownHandler @Inject constructor(application: Application) {
	private val extensions = listOf(
		CustomEmojiPlugin(application as SakuraApplication),
		TablesExtension.create(),
		StrikethroughExtension.create(),
	)
	private val plugins = listOf<MarkwonPlugin>(
		GlideImagesPlugin.create(application),
		HtmlPlugin.create(),
		StrikethroughPlugin.create(),
		TablePlugin.create(application),
		LinkifyPlugin.create(),
		CustomEmojiPlugin(application as SakuraApplication),
		UserMentionPlugin(application as SakuraApplication)
	)
	private val markwon = Markwon.builder(application).apply {
		usePlugins(plugins)
	}.build()
	private val parser = Parser.builder().apply { extensions(extensions) }.build()
	private val htmlRenderer = HtmlRenderer.builder().apply {
		extensions(extensions)
	}.build()
	private val textRenderer = TextContentRenderer.builder().apply {
		extensions(extensions)
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

	fun setTextView(textView: TextView, html: String?, isEdited: Boolean = false) {
		val content = if (html != null && isEdited) "$html *(edited)*" else html
		markwon.setMarkdown(textView, htmlToMarkdown(content))
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
						?: if (isEmoji) "emoji" else "Image"
					val url = node.attr("src").substringAfter("mxc://")
					if (isEmoji) {
						":$altText~$url:"
					} else {
						"![$altText]($url)"
					}
				}

				"a" -> {
					val url = node.attr("href").toUri()
					val text = node.text()
					// User mentions
					if (url.host == "matrix.to" && url.fragment?.startsWith("/@") == true) {
						"<${url.fragment?.substring(1)}|${text}>"
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
					sb.appendLine()
					node.childNodes().forEach {
						sb.append(htmlNodeToMarkdown(it, node.nodeName()))
					}
					sb.appendLine()
					sb.toString()
				}

				"blockquote" -> {
					val sb = StringBuilder()
					sb.appendLine()
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

				"del" -> "~~${htmlNodeToMarkdown(node.childNodes())}~~"

				"ins" -> node.outerHtml()

				else -> {
					Log.w("MarkdownHandler", "Unknown HTML tag: ${node.tagName()}")
					node.outerHtml()
				}
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

