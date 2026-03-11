package dev.kuylar.sakura.markdown.span

import android.graphics.Color
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import androidx.annotation.ColorInt

class SpoilerSpan(
	@param:ColorInt private val obscuredColor: Int,
	@param:ColorInt private val backgroundColor: Int,
	@param:ColorInt private val textColor: Int,
) : ClickableSpan() {
	var onReveal: (() -> Unit)? = null
	private var isRevealed = false

	override fun onClick(widget: View) {
		isRevealed = !isRevealed
		onReveal?.invoke()
	}

	override fun updateDrawState(paint: TextPaint) {
		if (isRevealed) {
			paint.color = textColor
			paint.bgColor = backgroundColor
		} else {
			paint.color = Color.TRANSPARENT
			paint.bgColor = obscuredColor
		}
	}
}
