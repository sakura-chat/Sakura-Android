package dev.kuylar.sakura.emoji

import android.view.View
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import dev.kuylar.sakura.R
import dev.kuylar.sakura.emojipicker.R as EmojiR
import dev.kuylar.sakura.emojipicker.model.CategoryModel

class CustomEmojiCategoryModel(name: String): CategoryModel(name) {
	override fun bind(view: View) {
		val titleRes = when (name) {
			"recent" -> R.string.emoji_category_recent
			"unicode:people" -> R.string.emoji_category_people
			"unicode:nature" -> R.string.emoji_category_nature
			"unicode:food" -> R.string.emoji_category_food
			"unicode:activity" -> R.string.emoji_category_activities
			"unicode:travel" -> R.string.emoji_category_travel
			"unicode:objects" -> R.string.emoji_category_objects
			"unicode:symbols" -> R.string.emoji_category_symbols
			"unicode:flags" -> R.string.emoji_category_flags
			"#!accountImagePack" -> R.string.emoji_category_account
			else -> name.substringAfter(":")
		}
		when (titleRes) {
			is Int -> view.findViewById<TextView>(EmojiR.id.text).setText(titleRes)
			is String -> view.findViewById<TextView>(EmojiR.id.text).text = titleRes
		}
	}

	override fun buildTab(tab: TabLayout.Tab) {
		tab.setIcon(when (name) {
			"recent" -> R.drawable.ic_emoji_category_recent
			"unicode:people" -> R.drawable.ic_emoji_category_people
			"unicode:nature" -> R.drawable.ic_emoji_category_nature
			"unicode:food" -> R.drawable.ic_emoji_category_food
			"unicode:activity" -> R.drawable.ic_emoji_category_activities
			"unicode:travel" -> R.drawable.ic_emoji_category_travel
			"unicode:objects" -> R.drawable.ic_emoji_category_objects
			"unicode:symbols" -> R.drawable.ic_emoji_category_symbols
			"unicode:flags" -> R.drawable.ic_emoji_category_flags
			"#!accountImagePack" -> R.drawable.ic_emoji_category_account
			else -> throw IllegalArgumentException("Invalid emoji category: $name")
		})
	}
}