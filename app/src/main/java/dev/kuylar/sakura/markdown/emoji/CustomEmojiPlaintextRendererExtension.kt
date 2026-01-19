package dev.kuylar.sakura.markdown.emoji

import org.commonmark.node.Node
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer

class CustomEmojiPlaintextRendererExtension : TextContentRenderer.TextContentRendererExtension {
	override fun extend(rendererBuilder: TextContentRenderer.Builder) {
		rendererBuilder.nodeRendererFactory { context ->
			CustomEmojiNodeRenderer(context)
		}
	}

	private class CustomEmojiNodeRenderer(
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