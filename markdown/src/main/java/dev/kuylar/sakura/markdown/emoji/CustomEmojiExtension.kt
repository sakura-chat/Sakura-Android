package dev.kuylar.sakura.markdown.emoji

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import dev.kuylar.mentionsedittext.ImageMentionSpan
import org.commonmark.node.CustomNode
import org.commonmark.node.Delimited
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

	class CustomEmojiNode(val uri: String, val shortcode: String) : CustomNode(), Delimited {
		override fun getOpeningDelimiter() = ":"

		override fun getClosingDelimiter() = ":"

		fun toMention(context: Context) = ImageMentionSpan(":$shortcode~${uri.substringAfter("mxc://")}:") {
			Log.d("ImageMentionSpan", "Loading image $uri")
			Glide.with(context)
				.asDrawable()
				.load(uri)
				.into(object : CustomTarget<Drawable>() {
					override fun onResourceReady(
						resource: Drawable,
						transition: Transition<in Drawable>?
					) {
						Log.d("ImageMentionSpan", "Image $uri loaded")
						it(resource)
					}

					override fun onLoadCleared(placeholder: Drawable?) {}
				})
		}
	}
}