package dev.kuylar.sakura.markdown.plugin.emoji

import dev.kuylar.sakura.SakuraApplication
import dev.kuylar.sakura.emoji.RoomCustomEmojiModel
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

class CustomEmojiPlugin(val application: SakuraApplication) : AbstractMarkwonPlugin(),
	HtmlRenderer.HtmlRendererExtension,
	TextContentRenderer.TextContentRendererExtension {
	override fun configureParser(builder: Parser.Builder) {
		builder.postProcessor(::processEmoji)
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

	override fun configureVisitor(builder: MarkwonVisitor.Builder) {
		builder.on(CustomEmojiNode::class.java) { visitor, node ->
			val span = RoomCustomEmojiModel(node.uri, node.shortcode).toMention(application)
			visitor.builder().append(span.value, span)
		}
	}

	private fun processEmoji(block: Node): Node {
		var node = block.firstChild
		while (node != null) {
			val next = node.next
			if (node is Text) {
				val literal = node.literal
				val regex = Regex(":([^:]+)~([^:]+):")
				val match = regex.find(literal)

				if (match != null) {
					val start = match.range.first
					val end = match.range.last + 1

					if (start > 0) {
						node.insertBefore(Text(literal.take(start)))
					}

					node.insertBefore(
						CustomEmojiNode(
							"mxc://${match.groupValues[2]}",
							match.groupValues[1]
						)
					)

					if (end < literal.length) {
						node.insertBefore(Text(literal.substring(end)))
					}

					node.unlink()
				}
			} else {
				processEmoji(node)
			}
			node = next
		}
		return block
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

	class CustomEmojiNode(val uri: String, val shortcode: String) : CustomNode(), Delimited {
		override fun getOpeningDelimiter() = ":"

		override fun getClosingDelimiter() = ":"
	}
}