package dev.kuylar.sakura.markdown.plugin.emoji

import dev.kuylar.mentionsedittext.TextMentionSpan
import dev.kuylar.sakura.SakuraApplication
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer

class UserMentionPlugin(val application: SakuraApplication) : AbstractMarkwonPlugin(),
	HtmlRenderer.HtmlRendererExtension,
	TextContentRenderer.TextContentRendererExtension {
	override fun configureParser(builder: Parser.Builder) {
		builder.postProcessor(::processMention)
	}

	override fun extend(rendererBuilder: HtmlRenderer.Builder) {
		rendererBuilder.nodeRendererFactory { context ->
			UserMentionHtmlRenderer(context)
		}
	}

	override fun extend(rendererBuilder: TextContentRenderer.Builder) {
		rendererBuilder.nodeRendererFactory { context ->
			UserMentionPlaintextRenderer(context)
		}
	}

	override fun configureVisitor(builder: MarkwonVisitor.Builder) {
		builder.on(UserMentionNode::class.java) { visitor, node ->
			val span = TextMentionSpan(
				node.name,
				node.userId,
				horizontalPadding = (application.resources.displayMetrics.density * 4).toInt()
			)
			visitor.builder().append(node.name, span)
		}
	}

	private fun processMention(block: Node): Node {
		var node = block.firstChild
		while (node != null) {
			val next = node.next
			if (node is Text) {
				val literal = node.literal
				val regex = Regex("<(.+?)\\|(.+?)>")
				val match = regex.find(literal)

				if (match != null) {
					val start = match.range.first
					val end = match.range.last + 1
					val id = match.groupValues[1]
					val name = match.groupValues[2]

					if (start > 0) {
						node.insertBefore(Text(literal.take(start)))
					}

					node.insertBefore(UserMentionNode(id, "@$name"))

					if (end < literal.length) {
						node.insertBefore(Text(literal.substring(end)))
					}

					node.unlink()
				}
			} else {
				processMention(node)
			}
			node = next
		}
		return block
	}

	private class UserMentionHtmlRenderer(
		private val context: HtmlNodeRendererContext
	) : NodeRenderer {
		override fun getNodeTypes(): Set<Class<out Node?>?> = setOf(UserMentionNode::class.java)

		override fun render(node: Node) {
			if (node is UserMentionNode) {
				val writer = context.writer
				writer.tag("a", mapOf("href" to "https://matrix.to/#/${node.userId}"))
				writer.text(node.userId)
			}
		}
	}

	private class UserMentionPlaintextRenderer(
		private val context: TextContentNodeRendererContext
	) : NodeRenderer {
		override fun getNodeTypes(): Set<Class<out Node?>?> = setOf(UserMentionNode::class.java)

		override fun render(node: Node) {
			if (node is UserMentionNode) {
				val writer = context.writer
				writer.write("@${node.userId}")
			}
		}
	}

	class UserMentionNode(val userId: String, val name: String) : CustomNode(), Delimited {
		override fun getOpeningDelimiter() = "<"

		override fun getClosingDelimiter() = ">"
	}
}