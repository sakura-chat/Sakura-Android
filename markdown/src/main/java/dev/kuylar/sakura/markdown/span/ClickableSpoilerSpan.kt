package dev.kuylar.sakura.markdown.span

import android.text.Spannable
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.core.text.getSpans

class ClickableSpoilerSpan() : ClickableSpan() {
	override fun onClick(widget: View) {
		val textView = widget as? TextView ?: return
		val spannable = textView.text as? Spannable ?: return
		val start = spannable.getSpanStart(this)
		val end = spannable.getSpanEnd(this)

		if (start == -1 || end == -1) return

		spannable.getSpans<SpoilerSpan>(start, end).forEach {
			it.reveal()
		}
	}
}
