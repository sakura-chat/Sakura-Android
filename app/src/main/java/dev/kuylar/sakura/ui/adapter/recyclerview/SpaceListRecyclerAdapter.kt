package dev.kuylar.sakura.ui.adapter.recyclerview

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.google.android.material.shape.ShapeAppearanceModel
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.databinding.ItemSpaceBinding
import dev.kuylar.sakura.databinding.ItemSpaceHomeBinding
import dev.kuylar.sakura.databinding.ItemSpaceListDividerBinding
import dev.kuylar.sakura.ui.activity.MainActivity
import net.folivo.trixnity.client.room

@SuppressLint("NotifyDataSetChanged")
class SpaceListRecyclerAdapter(val activity: MainActivity, var selectedSpaceId: String? = null) :
	RecyclerView.Adapter<SpaceListRecyclerAdapter.SpaceListViewModel>() {
	var layoutInflater = activity.layoutInflater
	var client = Matrix.getClient()
	var spaceTree: Map<String, MatrixSpace> = emptyMap()

	init {
		suspendThread {
			// Will be called every room change (hopefully)
			client.client.room.getAll().collect {
				spaceTree = client.getSpaceTree()
					.associateBy { it.parent?.roomId?.full ?: "!home:SakuraNative" }
				activity.runOnUiThread {
					if (selectedSpaceId != null)
						spaceTree[selectedSpaceId]?.let {
							openSpaceTree(it)
						}
					notifyDataSetChanged()
				}
			}
		}
	}

	open class SpaceListViewModel(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
	class DividerViewModel(binding: ItemSpaceListDividerBinding) : SpaceListViewModel(binding)
	class HomeViewModel(val binding: ItemSpaceHomeBinding) : SpaceListViewModel(binding) {
		fun bind(space: MatrixSpace) {
			binding.icon.post {
				binding.icon.shapeAppearanceModel =
					if ((bindingAdapter as SpaceListRecyclerAdapter).selectedSpaceId == space.parent?.roomId?.full) {
						ShapeAppearanceModel.builder().setAllCornerSizes(binding.icon.height / 4f)
							.build()
					} else {
						ShapeAppearanceModel.builder().setAllCornerSizes(binding.icon.height / 2f)
							.build()
					}
			}
			binding.unreadIndicator.visibility =
				if (space.isUnread) View.VISIBLE else View.INVISIBLE
			binding.root.setOnClickListener {
				(bindingAdapter as SpaceListRecyclerAdapter).openSpaceTree(space)
			}
		}
	}

	class SpaceViewModel(val binding: ItemSpaceBinding) : SpaceListViewModel(binding) {
		fun bind(space: MatrixSpace) {
			binding.icon.post {
				binding.icon.shapeAppearanceModel =
					if ((bindingAdapter as SpaceListRecyclerAdapter).selectedSpaceId == space.parent?.roomId?.full) {
						ShapeAppearanceModel.builder().setAllCornerSizes(binding.icon.height / 4f)
							.build()
					} else {
						ShapeAppearanceModel.builder().setAllCornerSizes(binding.icon.height / 2f)
							.build()
					}
			}
			// TODO: Doesn't update
			binding.unreadIndicator.visibility =
				if (space.isUnread) View.VISIBLE else View.INVISIBLE

			Glide.with(binding.root).load(space.parent?.avatarUrl).into(binding.icon)
			binding.root.setOnClickListener {
				(bindingAdapter as SpaceListRecyclerAdapter).openSpaceTree(space)
			}
		}
	}

	private fun getIdAtIndex(position: Int): String {
		return when (position) {
			0 -> "!home:SakuraNative"
			1 -> "!divider:SakuraNative"
			else -> spaceTree.keys.elementAt(position - 1)
		}
	}

	override fun getItemViewType(position: Int): Int {
		return when (getIdAtIndex(position)) {
			"!home:SakuraNative" -> 0
			"!divider:SakuraNative" -> 1
			else -> 2
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpaceListViewModel {
		return when (viewType) {
			0 -> HomeViewModel(ItemSpaceHomeBinding.inflate(layoutInflater, parent, false))
			2 -> SpaceViewModel(ItemSpaceBinding.inflate(layoutInflater, parent, false))
			else -> DividerViewModel(
				ItemSpaceListDividerBinding.inflate(
					layoutInflater, parent, false
				)
			)
		}
	}

	override fun onBindViewHolder(holder: SpaceListViewModel, position: Int) {
		when (holder) {
			is HomeViewModel -> spaceTree["!home:SakuraNative"]?.let { holder.bind(it) }
			is SpaceViewModel -> spaceTree[getIdAtIndex(position)]?.let { holder.bind(it) }
		}
	}

	override fun getItemCount(): Int {
		return if (spaceTree.isEmpty()) 2    // One for the home icon, one for the divider
		else spaceTree.size + 1              // One for the divider
	}

	private fun openSpaceTree(space: MatrixSpace) {
		val lastSelectedSpaceId = selectedSpaceId
		selectedSpaceId = space.parent?.roomId?.full
		if (selectedSpaceId == null)
			notifyItemChanged(0)
		else
			notifyItemChanged(spaceTree.keys.indexOf(selectedSpaceId) + 1)
		notifyItemChanged(spaceTree.keys.indexOf(lastSelectedSpaceId) + 1)
		activity.openSpaceTree(space)
	}
}