package dev.kuylar.sakura.markdown.emoji

import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer

class CustomEmojiHtmlRendererExtension : HtmlRenderer.HtmlRendererExtension {
	override fun extend(rendererBuilder: HtmlRenderer.Builder) {
		rendererBuilder.nodeRendererFactory { context ->
			CustomEmojiNodeRenderer(context)
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
}