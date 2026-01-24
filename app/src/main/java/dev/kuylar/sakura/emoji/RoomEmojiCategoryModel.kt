package dev.kuylar.sakura.emoji

import com.google.android.material.tabs.TabLayout
import dev.kuylar.sakura.emojipicker.model.CategoryModel
import de.connect2x.trixnity.core.model.RoomId

class RoomEmojiCategoryModel(val roomId: RoomId, val stateKey: String, name: String) :
	CategoryModel(name) {
	override fun buildTab(tab: TabLayout.Tab) {
		// TODO: Put room avatar here
		super.buildTab(tab)
	}

	override fun equals(other: Any?): Boolean {
		return if (other is RoomEmojiCategoryModel) roomId == other.roomId && stateKey == other.stateKey
		else false
	}

	override fun hashCode() = "$roomId~$stateKey".hashCode()
}