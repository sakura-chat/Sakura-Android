package dev.kuylar.sakura.emoji

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import dev.kuylar.sakura.R
import dev.kuylar.sakura.client.customevent.MatrixEmote

class RoomStickerModel(val key: String, val sticker: MatrixEmote) :
	CustomEmojiModel(sticker.body ?: "Sticker: $key") {
	override fun bind(view: View) {
		view.findViewById<TextView>(R.id.text).visibility = View.GONE
		val iv = view.findViewById<ImageView>(R.id.image)
		iv.visibility = View.VISIBLE
		iv.contentDescription = name
		Glide.with(view).load(sticker.url).into(iv)
		view.setOnLongClickListener {
			Toast.makeText(view.context, name, Toast.LENGTH_SHORT).show()
			true
		}
	}
}