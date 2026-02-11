package dev.kuylar.sakura.markdown.span

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import androidx.annotation.ColorRes

// Source - https://stackoverflow.com/a/27541189
// Posted by Håvard, modified by community. See post 'Timeline' for change history
// Retrieved 2026-02-11, License - CC BY-SA 4.0
class SpoilerSpan(
	@param:ColorRes private val foregroundColor: Int,
	@param:ColorRes private val backgroundColor: Int,
	@param:ColorRes private val textColor: Int,
) : ReplacementSpan() {
	var onReveal: (() -> Unit)? = null
	private var isRevealed = false

	override fun getSize(
		paint: Paint,
		text: CharSequence?,
		start: Int,
		end: Int,
		fm: Paint.FontMetricsInt?
	) = paint.measureText(text, start, end).toInt()

	override fun draw(
		canvas: Canvas,
		text: CharSequence,
		start: Int,
		end: Int,
		x: Float,
		top: Int,
		y: Int,
		bottom: Int,
		paint: Paint
	) {
		if (isRevealed) {
			paint.color = textColor
			canvas.drawText(text, start, end, x, y.toFloat(), paint)
		} else {
			val width = paint.measureText(text, start, end)
			val rect = RectF(x, top.toFloat(), x + width, bottom.toFloat())
			paint.color = if (isRevealed) backgroundColor else foregroundColor
			canvas.drawRect(rect, paint)
		}
	}

	fun reveal() {
		isRevealed = !isRevealed
		onReveal?.invoke()
	}
}
