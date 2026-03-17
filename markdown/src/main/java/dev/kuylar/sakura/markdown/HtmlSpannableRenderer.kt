package dev.kuylar.sakura.markdown

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.LineBackgroundSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import dev.kuylar.mentionsedittext.TextMentionSpan
import dev.kuylar.sakura.markdown.emoji.CustomEmojiExtension
import dev.kuylar.sakura.markdown.span.NumberIndentSpan
import dev.kuylar.sakura.markdown.span.SpoilerSpan
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import com.google.android.material.R as MaterialR

class HtmlSpannableRenderer {
	private val allowedUrlSchemes = listOf("https", "http", "ftp", "mailto", "magnet")
	private val allowedImageUrlSchemes = listOf("mxc")
	fun fromHtml(html: String?, textView: TextView): Spannable {
		if (html == null) return SpannableString("")
		val doc = Jsoup.parseBodyFragment(html)
		doc.outputSettings().prettyPrint(false)
		val sb = SpannableStringBuilder()
		val state = State(textView.context, sb) { textView.width }
		visitChildren(state, doc.body().childNodes())
		collapseConsecutiveNewlines(sb)
		return sb.trim() as Spannable
	}

	private fun visitChildren(
		state: State,
		childNodes: MutableList<Node>
	) {
		childNodes.forEach { node ->
			when (node) {
				is TextNode -> state.builder.append(
					if (state.preserveLineBreaks) node.nodeValue()
					else node.nodeValue().replace("\n", "")
				)

				is Element -> visitChildren(state, node)
				else -> Pair(node.nodeValue(), null)
			}
		}
	}

	private fun visitChildren(state: State, element: Element) {
		val start = state.builder.length
		when (val name = element.tagName()) {
			"p" -> {
				if (state.builder.isNotEmpty()) {
					if (state.builder.last() != '\n')
						state.builder.append("\n")
					else
						state.builder.append("\n\n")
				}
				visitChildren(state, element.childNodes())
				state.builder.append("\n")
			}

			"div" -> {
				if (state.builder.isNotEmpty() && state.builder.last() != '\n') state.builder.append(
					"\n"
				)
				visitChildren(state, element.childNodes())
				state.builder.append("\n")
			}

			"br" -> state.builder.append("\n")

			"b", "strong" -> {
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					StyleSpan(Typeface.BOLD),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"i", "em" -> {
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					StyleSpan(Typeface.ITALIC),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"del", "s" -> {
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					StrikethroughSpan(),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"a" -> {
				visitChildren(state, element.childNodes())
				val href = element.attr("href").toUri()
				if (allowedUrlSchemes.contains(href.scheme)) {
					if (href.host == "matrix.to") {
						val id = href.fragment?.substringAfter('/') ?: ""
						if (id.firstOrNull() == '@') {
							state.builder.setSpan(
								TextMentionSpan(
									state.builder.subSequence(
										start,
										state.builder.length
									).toString(),
									href.toString(),
									horizontalPadding = 8
								),
								start,
								state.builder.length,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
							)
						}
					}
					state.builder.setSpan(
						URLSpan(href.toString()),
						start,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
			}

			"blockquote" -> {
				if (state.builder.isNotEmpty() && state.builder.last() != '\n')
					state.builder.append("\n")
				visitChildren(state, element.childNodes())
				var quoteStart = start
				for (i in start until state.builder.length) {
					if (!state.builder[i].isWhitespace()) {
						quoteStart = i
						break
					}
				}
				state.builder.setSpan(
					QuoteSpan(Color.WHITE, 6, 8 * 2),
					quoteStart,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"sup" -> {
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					SuperscriptSpan(),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				state.builder.setSpan(
					RelativeSizeSpan(.8f),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"sub" -> {
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					SubscriptSpan(),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				state.builder.setSpan(
					RelativeSizeSpan(.8f),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"hr" -> {
				if (state.builder.isNotEmpty() && state.builder.last() != '\n') state.builder.append(
					"\n"
				)
				state.builder.append("—————————————")
				state.builder.append("\n")
			}

			"u" -> {
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					UnderlineSpan(),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"h1", "h2", "h3", "h4", "h5", "h6" -> {
				if (state.builder.isNotEmpty() && state.builder.last() != '\n') state.builder.append(
					"\n"
				)
				visitChildren(state, element.childNodes())
				state.builder.append("\n")
				state.builder.setSpan(
					RelativeSizeSpan(1 + ((6 - name[1].digitToInt()) / 10f)),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"span" -> {
				val desc = element.attr("data-mx-spoiler")
				if (desc.isNotBlank()) {
					state.builder.append("($desc)")
					state.builder.setSpan(
						RelativeSizeSpan(.8f),
						start,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
					state.builder.setSpan(
						StyleSpan(Typeface.ITALIC),
						start,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
					state.builder.append(" ")
				}
				val spoilerStart = state.builder.length
				visitChildren(state, element.childNodes())

				if (element.hasAttr("data-mx-bg-color")) {
					state.builder.setSpan(
						BackgroundColorSpan(element.attr("data-mx-bg-color").toColorIntSafe()),
						start,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
				if (element.hasAttr("data-mx-color")) {
					state.builder.setSpan(
						BackgroundColorSpan(element.attr("data-mx-color").toColorIntSafe()),
						start,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
				if (element.hasAttr("data-mx-spoiler")) {
					state.builder.setSpan(
						SpoilerSpan(
							state.context.getColorFromAttr(MaterialR.attr.colorOnSurface),
							state.context.getColorFromAttr(MaterialR.attr.colorSecondaryContainer),
							state.context.getColorFromAttr(MaterialR.attr.colorOnSecondaryContainer),
						),
						spoilerStart,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				}
			}

			"ul", "ol" -> {
				if (state.builder.isNotEmpty() && state.builder.last() != '\n') state.builder.append(
					"\n"
				)

				state.listState = State.ListState(isOrdered = (name == "ol"))
				visitChildren(state, element.childNodes())

				state.builder.append("\n")
			}

			"li" -> {
				if (state.builder.isNotEmpty() && state.builder.last() != '\n') state.builder.append(
					"\n"
				)

				val span = if (state.listState?.isOrdered == true) {
					NumberIndentSpan(40, state.listState?.index++ ?: 1)
				} else {
					BulletSpan(20)
				}

				val contentStart = state.builder.length
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					span,
					contentStart,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
			}

			"code" -> {
				visitChildren(state, element.childNodes())
				state.builder.setSpan(
					TypefaceSpan(Typeface.MONOSPACE),
					start,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				if (!state.preserveLineBreaks) // Don't set the background color twice
					state.builder.setSpan(
						BackgroundColorSpan(state.context.getColorFromAttr(MaterialR.attr.colorSurfaceContainerLowest)),
						start,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
			}

			"pre" -> {
				if (state.builder.isNotEmpty() && state.builder.last() != '\n')
					state.builder.append("\n")
				state.builder.append("\n")
				val bgStart = state.builder.length
				state.preserveLineBreaks = true
				visitChildren(state, element.childNodes())
				state.preserveLineBreaks = false
				state.builder.setSpan(
					LineBackgroundSpan.Standard(state.context.getColorFromAttr(MaterialR.attr.colorSurfaceContainerLowest)),
					bgStart,
					state.builder.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
				state.builder.append("\n")
			}

			"img" -> {
				if (element.hasAttr("data-mx-emoticon")) {
					val node = CustomEmojiExtension.CustomEmojiNode(
						element.attr("src"),
						element.attr("alt").trim(':')
					)
					state.builder.append(":${node.shortcode}:")
					state.builder.setSpan(
						node.toMention(state.context),
						start,
						state.builder.length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				} else {
					val text = listOf(
						element.attr("alt"),
						element.attr("title")
					).firstNotNullOfOrNull { it.takeIf { it.isNotBlank() } }
					state.builder.append("\n")
					val imageStart = state.builder.length
					state.builder.append(text ?: "￼")
					val imageEnd = state.builder.length
					state.builder.append("\n")
					val src = element.attr("src").toUri()
					if (allowedImageUrlSchemes.contains(src.scheme))
						state.builder.setSpan(
							BlockImageSpan(
								src.toString(),
								maxWidthPx = state.getWidth,
								glide = Glide.with(state.context)
							),
							imageStart,
							imageEnd,
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
						)
				}
			}

			else -> {
				state.builder.append(element.text())
			}
		}
	}

	private fun String.toColorIntSafe(): Int {
		return try {
			this.toColorInt()
		} catch (_: IllegalArgumentException) {
			0
		}
	}

	private data class State(
		val context: Context,
		val builder: SpannableStringBuilder,
		var listState: ListState? = null,
		var preserveLineBreaks: Boolean = false,
		val getWidth: () -> Int
	) {
		data class ListState(
			val isOrdered: Boolean,
			var index: Int = 1
		)
	}

	private fun Context.getColorFromAttr(attr: Int): Int {
		val typedValue = TypedValue()
		theme.resolveAttribute(attr, typedValue, true)
		return typedValue.data
	}

	private fun collapseConsecutiveNewlines(builder: SpannableStringBuilder) {
		var i = 0
		while (i < builder.length) {
			if (builder[i] == '\n') {
				var consecutiveNewlines = 1
				while (i + consecutiveNewlines < builder.length && builder[i + consecutiveNewlines] == '\n') {
					consecutiveNewlines++
				}
				if (consecutiveNewlines > 2) {
					builder.delete(i + 2, i + consecutiveNewlines)
				}
				i += 2
			} else {
				i++
			}
		}
	}
}