package dev.kuylar.sakura.emojipicker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kuylar.sakura.emojipicker.model.CategoryModel
import dev.kuylar.sakura.emojipicker.model.EmojiModel

class EmojiRecyclerAdapter(context: Context, private val recyclerView: RecyclerView) :
	RecyclerView.Adapter<EmojiRecyclerAdapter.CustomViewHolder>() {
	var onEmojiSelectedCallback: ((EmojiModel) -> Unit)? = null
	private val items = mutableListOf<Any>()
	private val layoutInflater = LayoutInflater.from(context)

	@LayoutRes
	var categoryModelLayout: Int = R.layout.item_category

	@LayoutRes
	var emojiModelLayout: Int = R.layout.item_emoji

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
		return when (viewType) {
			0 -> CategoryViewHolder(layoutInflater.inflate(categoryModelLayout, parent, false))
			1 -> EmojiViewHolder(layoutInflater.inflate(emojiModelLayout, parent, false))
			else -> throw IllegalArgumentException("Invalid view type")
		}
	}

	override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
		when (holder) {
			is EmojiViewHolder -> holder.bind(items[position] as EmojiModel)
			is CategoryViewHolder -> holder.bind(items[position] as CategoryModel)
		}
	}

	override fun getItemCount() = items.size

	override fun getItemViewType(position: Int): Int {
		return when (items[position]) {
			is CategoryModel -> 0
			is EmojiModel -> 1
			else -> -1
		}
	}

	fun isHeader(position: Int) = items[position] is CategoryModel
	fun getTabs() = items.filterIsInstance<CategoryModel>()
	fun getItemAt(position: Int) = items[position]
	fun scrollToCategoryByCategoryIndex(categoryIndex: Int) {
		var currentIndex = 0
		items.forEachIndexed { index, item ->
			if (item is CategoryModel && currentIndex == categoryIndex) {
				(recyclerView.layoutManager as GridLayoutManager)
					.scrollToPositionWithOffset(index, 0)
				return
			}
			if (item is CategoryModel) currentIndex++
		}
	}

	fun loadItems(list: Map<CategoryModel, List<EmojiModel>>) {
		val itemCount = items.size
		items.clear()
		notifyItemRangeRemoved(0, itemCount)

		list.forEach { (category, emojis) ->
			items.add(category)
			items.addAll(emojis)
			notifyItemRangeInserted(itemCount, emojis.size + 1)
		}
	}

	open class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view)

	class EmojiViewHolder(val view: View) : CustomViewHolder(view) {
		fun bind(item: EmojiModel) {
			item.bind(view)
			view.setOnClickListener {
				(bindingAdapter as? EmojiRecyclerAdapter)?.onEmojiSelectedCallback?.invoke(item)
			}
		}
	}

	class CategoryViewHolder(val view: View) : CustomViewHolder(view) {
		fun bind(item: CategoryModel) {
			item.bind(view)
		}
	}
}