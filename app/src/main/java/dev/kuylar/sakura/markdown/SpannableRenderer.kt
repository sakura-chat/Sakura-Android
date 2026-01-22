package dev.kuylar.sakura.markdown

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import androidx.core.text.toSpannable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import dev.kuylar.mentionsedittext.ImageMentionSpan
import dev.kuylar.sakura.markdown.emoji.CustomEmojiNode
import com.google.android.material.R as MaterialR
import org.commonmark.node.*

class SpannableRenderer(val context: Context) {
	fun render(node: Node): Spannable {
		val b = SpannableStringBuilder()
		visitNode(node, b)
		return b.trim().toSpannable()
	}

	private fun visitNode(node: Node, builder: SpannableStringBuilder) {
		when (node) {
			is Text -> {
				builder.append(node.literal)
			}

			is Paragraph -> {
				//if (builder.isNotEmpty() && node.parent !is ListItem)
				//	builder.append("\n\n")
				visitChildren(node, builder)
			}

			is Emphasis -> {
				val start = builder.length
				visitChildren(node, builder)
				builder.setSpan(
					StyleSpan(Typeface.ITALIC),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			is StrongEmphasis -> {
				val start = builder.length
				visitChildren(node, builder)
				builder.setSpan(
					StyleSpan(Typeface.BOLD),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			is Heading -> {
				if (builder.isNotEmpty()) {
					builder.append("\n")
				}
				val start = builder.length
				visitChildren(node, builder)
				builder.append("\n")
				val headerSize = 1 + ((6 - node.level) / 10f)
				builder.setSpan(
					RelativeSizeSpan(headerSize),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			is BulletList -> {
				if (builder.isNotEmpty()) {
					builder.append("\n")
				}
				visitChildren(node, builder)
				if (builder.isNotEmpty()) {
					builder.append("\n")
				}
			}

			is OrderedList -> {
				if (builder.isNotEmpty()) {
					builder.append("\n")
				}
				visitChildren(node, builder)
				if (builder.isNotEmpty()) {
					builder.append("\n")
				}
			}

			is ListItem -> {
				if (node.parent is OrderedList) {
					val index = (node.parent.firstChild.let {
						var count = 1
						var item = it
						while (item != node) {
							count++
							item = item.next
						}
						count
					}).toString()
					builder.append("$index. ")
				} else {
					builder.append("• ")
				}
				visitChildren(node, builder)
				builder.append("\n")
			}

			is Code -> {
				val start = builder.length
				builder.append(node.literal)
				builder.setSpan(
					TypefaceSpan(Typeface.MONOSPACE),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			is FencedCodeBlock -> {
				//if (builder.isNotEmpty())
				//	builder.append("\n\n")
				val start = builder.length
				builder.append(node.info + "\n" + node.literal)
				builder.setSpan(
					TypefaceSpan(Typeface.MONOSPACE),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			is IndentedCodeBlock -> {
				//if (builder.isNotEmpty())
				//	builder.append("\n\n")
				val start = builder.length
				builder.append(node.literal)
				builder.setSpan(
					TypefaceSpan(Typeface.MONOSPACE),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			is Link -> {
				val start = builder.length
				val typedValue = TypedValue()
				context.theme.resolveAttribute(MaterialR.attr.colorPrimaryFixed, typedValue, true)
				visitChildren(node, builder)
				builder.setSpan(
					URLSpan(node.destination),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				builder.setSpan(
					ForegroundColorSpan(typedValue.data),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			is BlockQuote -> {
				if (builder.isNotEmpty())
					builder.append("\n")
				val start = builder.length
				visitChildren(node, builder)
				builder.setSpan(
					QuoteSpan(),
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				builder.append("\n")
			}

			is HardLineBreak -> builder.append("\n")

			is CustomEmojiNode -> {
				val start = builder.length
				builder.append(":${node.shortcode}:", builder)
				builder.setSpan(
					ImageMentionSpan(":${node.shortcode}:") {
						Glide.with(context)
							.asDrawable()
							.load(node.uri)
							.into(object : CustomTarget<Drawable>() {
								override fun onResourceReady(
									resource: Drawable,
									transition: Transition<in Drawable>?
								) {
									it(resource)
								}

								override fun onLoadCleared(placeholder: Drawable?) {}
							})
					},
					start,
					builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			else -> visitChildren(node, builder)
		}
	}

	private fun visitChildren(parent: Node, builder: SpannableStringBuilder) {
		var node = parent.firstChild
		while (node != null) {
			visitNode(node, builder)
			node = node.next
		}
	}
}

