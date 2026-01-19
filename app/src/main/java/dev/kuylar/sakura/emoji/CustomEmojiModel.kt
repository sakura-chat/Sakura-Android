package dev.kuylar.sakura.emoji

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import dev.kuylar.sakura.R
import dev.kuylar.sakura.emojipicker.model.EmojiModel
import net.folivo.trixnity.core.model.RoomId

class CustomEmojiModel(name: String) : EmojiModel(name) {
	override fun bind(view: View) {
		view.findViewById<TextView>(R.id.text).visibility = View.VISIBLE
		view.findViewById<ImageView>(R.id.image).visibility = View.GONE
		super.bind(view)
	}
}