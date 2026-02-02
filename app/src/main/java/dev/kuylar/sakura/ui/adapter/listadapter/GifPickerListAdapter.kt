package dev.kuylar.sakura.ui.adapter.listadapter

import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dev.kuylar.sakura.databinding.ItemGifBinding
import dev.kuylar.sakura.gifpicker.model.Gif

class GifPickerListAdapter(fragment: Fragment) :
	ListAdapter<Gif, GifPickerListAdapter.GifViewHolder>(
		AsyncDifferConfig.Builder<Gif>(GifItemCallback()).build()
	) {
	private val layoutInflater = fragment.layoutInflater
	private val parentFragmentManager = fragment.parentFragmentManager

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		GifViewHolder(ItemGifBinding.inflate(layoutInflater, parent, false))

	override fun onBindViewHolder(holder: GifViewHolder, position: Int) =
		holder.bind(getItem(position), parentFragmentManager)

	fun addItems(gifs: List<Gif>) = submitList(currentList + gifs)

	class GifViewHolder(val binding: ItemGifBinding) : RecyclerView.ViewHolder(binding.root) {
		fun bind(item: Gif, parentFragmentManager: FragmentManager) {
			binding.image.post {
				val ratio = item.height.toFloat() / item.width.toFloat()
				val params = binding.image.layoutParams
				params.height = (binding.image.width * ratio).toInt()
				binding.image.layoutParams = params
			}
			binding.root.setOnClickListener {
				parentFragmentManager.setFragmentResult(
					"picker_action",
					bundleOf(
						"action" to "gif",
						"params" to listOf(item.gifUrl).toTypedArray()
					)
				)
			}
			Glide.with(binding.root).load(item.gifUrl).into(binding.image)
		}
	}

	class GifItemCallback : DiffUtil.ItemCallback<Gif>() {
		override fun areItemsTheSame(a: Gif, b: Gif) = a.gifUrl == b.gifUrl
		override fun areContentsTheSame(a: Gif, b: Gif) = a == b
	}
}