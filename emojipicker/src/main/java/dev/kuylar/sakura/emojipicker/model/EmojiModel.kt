package dev.kuylar.sakura.emojipicker.model

import android.view.View
import android.widget.TextView
import dev.kuylar.sakura.emojipicker.R

open class EmojiModel(val name: String) {
	open fun bind(view: View) {
		view.findViewById<TextView>(R.id.text).text = name
	}
}