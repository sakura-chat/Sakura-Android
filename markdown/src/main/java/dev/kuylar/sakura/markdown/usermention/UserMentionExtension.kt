package dev.kuylar.sakura.markdown.usermention

import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer

class UserMentionExtension : Parser.ParserExtension,
	HtmlRenderer.HtmlRendererExtension,
	TextContentRenderer.TextContentRendererExtension {
	override fun extend(parserBuilder: Parser.Builder) {
		parserBuilder.customInlineContentParserFactory(UserMentionParser.Factory())
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