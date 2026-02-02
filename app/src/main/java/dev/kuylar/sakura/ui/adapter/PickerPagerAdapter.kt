package dev.kuylar.sakura.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import dev.kuylar.sakura.ui.fragment.EmptyFragment
import dev.kuylar.sakura.ui.fragment.picker.EmojiPickerFragment
import dev.kuylar.sakura.ui.fragment.picker.GifPickerFragment
import dev.kuylar.sakura.ui.fragment.picker.StickerPickerFragment

class PickerPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
	private val cache = mutableMapOf<Int, Fragment>()

	override fun createFragment(position: Int): Fragment {
		if (!cache.containsKey(position))
			cache[position] = when (position) {
				0 -> EmojiPickerFragment()
				1 -> GifPickerFragment()
				2 -> StickerPickerFragment()
				else -> EmptyFragment()
			}
		return cache[position]!!
	}

	override fun getItemCount() = 3
}