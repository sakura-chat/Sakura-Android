package dev.kuylar.sakura.markdown

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan

class QuoteSpan : LeadingMarginSpan, LineBackgroundSpan {
	private val stripWidth = 6f
	private val gapWidth = 8 * 2f

	override fun getLeadingMargin(first: Boolean): Int {
		return (stripWidth + gapWidth).toInt()
	}

	override fun drawLeadingMargin(
		canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
		text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
	) {
		val style = paint.style
		val color = paint.color

		paint.style = Paint.Style.FILL
		paint.color = Color.WHITE

		canvas.drawRect(
			x.toFloat(),
			top.toFloat(),
			x + stripWidth,
			bottom.toFloat(),
			paint
		)

		paint.style = style
		paint.color = color
	}

	override fun drawBackground(
		c: Canvas, p: Paint, l: Int, r: Int, t: Int, base: Int, b: Int,
		text: CharSequence, s: Int, e: Int, line: Int
	) {
	}
}