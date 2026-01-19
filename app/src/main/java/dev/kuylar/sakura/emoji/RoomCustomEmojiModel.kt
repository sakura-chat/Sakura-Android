package dev.kuylar.sakura.emoji

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import dev.kuylar.mentionsedittext.ImageMentionSpan
import dev.kuylar.mentionsedittext.MentionSpan
import dev.kuylar.sakura.R
import dev.kuylar.sakura.emojipicker.model.EmojiModel

class RoomCustomEmojiModel(val uri: String, val shortcode: String) : EmojiModel(":$shortcode:") {
	override fun bind(view: View) {
		view.findViewById<TextView>(R.id.text).visibility = View.GONE
		val iv = view.findViewById<ImageView>(R.id.image)
		iv.visibility = View.VISIBLE
		Glide.with(view)
			.load(uri)
			.into(iv)
	}

	fun toMention(fragment: Fragment): MentionSpan {
		return ImageMentionSpan(":$shortcode~$uri:") {
			Glide.with(fragment)
				.asDrawable()
				.load(uri)
				.into(object : CustomTarget<Drawable>() {
					override fun onResourceReady(
						resource: Drawable,
						transition: Transition<in Drawable>?
					) {
						it(resource)
					}

					override fun onLoadCleared(placeholder: Drawable?) {}
				})
		}
	}
}