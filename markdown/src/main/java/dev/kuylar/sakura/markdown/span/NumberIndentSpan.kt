package dev.kuylar.sakura.markdown.span

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan

// Source - https://stackoverflow.com/a/27541189
// Posted by Håvard, modified by community. See post 'Timeline' for change history
// Retrieved 2026-02-11, License - CC BY-SA 4.0
class NumberIndentSpan(
	private val gapWidth: Int,
	private val index: Int
) : LeadingMarginSpan {

	override fun getLeadingMargin(first: Boolean) = gapWidth
	override fun drawLeadingMargin(
		c: Canvas,
		p: Paint,
		x: Int,
		dir: Int,
		top: Int,
		baseline: Int,
		bottom: Int,
		text: CharSequence,
		start: Int,
		end: Int,
		first: Boolean,
		l: Layout
	) {
		if (first) {
			val orgStyle = p.style
			p.style = Paint.Style.FILL
			c.drawText("$index.", x.toFloat(), baseline.toFloat(), p)
			p.style = orgStyle
		}
	}
}
