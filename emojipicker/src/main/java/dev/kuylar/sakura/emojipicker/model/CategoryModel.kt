package dev.kuylar.sakura.emojipicker.model

import android.view.View
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import dev.kuylar.sakura.emojipicker.R

open class CategoryModel(val name: String) {
	open fun bind(view: View) {
		view.findViewById<TextView>(R.id.text).text = name
	}

	open fun buildTab(tab: TabLayout.Tab) {
		tab.text = name
	}
}