package dev.kuylar.sakura.emoji

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import dev.kuylar.mentionsedittext.ImageMentionSpan
import dev.kuylar.mentionsedittext.MentionSpan
import dev.kuylar.sakura.R

class RoomCustomEmojiModel(val uri: String, val shortcode: String) : CustomEmojiModel(":$shortcode:") {
	override fun bind(view: View) {
		view.findViewById<TextView>(R.id.text).visibility = View.GONE
		val iv = view.findViewById<ImageView>(R.id.image)
		iv.visibility = View.VISIBLE
		iv.contentDescription = shortcode
		Glide.with(view).load(uri).into(iv)
		view.setOnLongClickListener {
			Toast.makeText(view.context, shortcode, Toast.LENGTH_SHORT).show()
			true
		}
	}

	fun toMention(fragment: Fragment): MentionSpan {
		return ImageMentionSpan(":$shortcode~${uri.substringAfter("mxc://")}:") {
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