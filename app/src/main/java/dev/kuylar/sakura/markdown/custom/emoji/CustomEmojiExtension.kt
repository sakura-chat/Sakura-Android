package dev.kuylar.sakura.markdown.custom.emoji

import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer

class CustomEmojiExtension : Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension,
	TextContentRenderer.TextContentRendererExtension {
	override fun extend(parserBuilder: Parser.Builder) {
		parserBuilder.customInlineContentParserFactory(CustomEmojiParser.Factory())
	}

	override fun extend(rendererBuilder: HtmlRenderer.Builder) {
		rendererBuilder.nodeRendererFactory { context ->
			CustomEmojiNodeRenderer(context)
		}
	}

	override fun extend(rendererBuilder: TextContentRenderer.Builder) {
		rendererBuilder.nodeRendererFactory { context ->
			CustomEmojiTextNodeRenderer(context)
		}
	}

	private class CustomEmojiNodeRenderer(
		private val context: HtmlNodeRendererContext
	) : NodeRenderer {
		override fun getNodeTypes(): Set<Class<out Node?>?> = setOf(CustomEmojiNode::class.java)

		override fun render(node: Node) {
			if (node is CustomEmojiNode) {
				val writer = context.writer
				writer.tag(
					"img", mapOf(
						"data-mx-emoticon" to "",
						"src" to node.uri,
						"alt" to node.shortcode,
						"title" to node.shortcode,
						"height" to "32"
					)
				)
			}
		}
	}

	private class CustomEmojiTextNodeRenderer(
		private val context: TextContentNodeRendererContext
	) : NodeRenderer {
		override fun getNodeTypes(): Set<Class<out Node?>?> = setOf(CustomEmojiNode::class.java)

		override fun render(node: Node) {
			if (node is CustomEmojiNode) {
				val writer = context.writer
				writer.write(":${node.shortcode}:")
			}
		}
	}
}