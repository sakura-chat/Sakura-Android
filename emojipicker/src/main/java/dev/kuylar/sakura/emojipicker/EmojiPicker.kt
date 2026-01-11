package dev.kuylar.sakura.emojipicker

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.core.view.children
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import dev.kuylar.sakura.emojipicker.model.CategoryModel
import dev.kuylar.sakura.emojipicker.model.EmojiModel

class EmojiPicker : LinearLayout, TabLayout.OnTabSelectedListener {
	private lateinit var recyclerView: RecyclerView
	private lateinit var tabLayout: TabLayout
	private lateinit var adapter: EmojiRecyclerAdapter
	private var tabs = emptyList<CategoryModel>()
	private var suppressNextTabSelected = false

	constructor(context: Context) : super(context) {
		init(null, 0)
	}

	constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
		init(attrs, 0)
	}

	constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
		context,
		attrs,
		defStyle
	) {
		init(attrs, defStyle)
	}

	private fun init(attrs: AttributeSet?, defStyle: Int) {
		val cols =
			(resources.displayMetrics.widthPixels / (resources.displayMetrics.density * 48)).toInt()
		val layoutManager = GridLayoutManager(context, cols)
		layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int {
				return if (adapter.isHeader(position)) cols else 1
			}
		}

		orientation = VERTICAL
		recyclerView = RecyclerView(context).apply {
			id = R.id.recycler
			layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0).apply {
				weight = 1f
			}
		}
		tabLayout = TabLayout(context).apply {
			id = R.id.tabLayout
			layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
			tabMode = TabLayout.MODE_SCROLLABLE
			addOnTabSelectedListener(this@EmojiPicker)
		}
		adapter = EmojiRecyclerAdapter(context, recyclerView)

		recyclerView.adapter = adapter
		recyclerView.layoutManager = layoutManager
		recyclerView.addItemDecoration(
			HeaderItemDecoration(
				recyclerView,
				false,
				adapter::isHeader,
				this::scrollToTab
			)
		)

		addView(recyclerView)
		addView(tabLayout)
	}

	private fun scrollToTab(model: CategoryModel) {
		suppressNextTabSelected = true
		tabLayout.selectTab(tabLayout.getTabAt(tabs.indexOf(model)))
	}

	override fun onTabSelected(tab: TabLayout.Tab) {
		if (suppressNextTabSelected) {
			suppressNextTabSelected = false
			return
		}
		(tabLayout.children.first() as ViewGroup).indexOfChild(tab.view as View).let {
			adapter.scrollToCategoryByCategoryIndex(it)
		}
	}

	override fun onTabUnselected(tab: TabLayout.Tab) {

	}

	override fun onTabReselected(tab: TabLayout.Tab) {
		if (suppressNextTabSelected) {
			suppressNextTabSelected = false
			return
		}
		tabLayout.indexOfChild(tab.view).let {
			adapter.scrollToCategoryByCategoryIndex(it)
		}
	}

	fun loadItems(list: Map<CategoryModel, List<EmojiModel>>) {
		adapter.loadItems(list)
		tabs = adapter.getTabs()
		tabs.forEach { tabLayout.addTab(tabLayout.newTab().apply { it.buildTab(this) }) }
	}

	fun setOnEmojiSelectedCallback(callback: (EmojiModel) -> Unit) {
		adapter.onEmojiSelectedCallback = callback
	}

	fun setCategoryLayout(@LayoutRes layout: Int) {
		adapter.categoryModelLayout = layout
	}

	fun setEmojiLayout(@LayoutRes layout: Int) {
		adapter.emojiModelLayout = layout
	}
}