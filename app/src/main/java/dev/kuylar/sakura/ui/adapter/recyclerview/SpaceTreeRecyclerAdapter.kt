package dev.kuylar.sakura.ui.adapter.recyclerview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.os.postDelayed
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.google.android.material.R as MaterialR
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.databinding.ItemRoomBinding
import dev.kuylar.sakura.databinding.ItemRoomCategoryBinding
import dev.kuylar.sakura.ui.activity.MainActivity
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.Room

@SuppressLint("NotifyDataSetChanged")
class SpaceTreeRecyclerAdapter(val activity: MainActivity) :
	RecyclerView.Adapter<SpaceTreeRecyclerAdapter.RoomListViewModel>() {
	var layoutInflater = activity.layoutInflater
	private var client = Matrix.getClient()
	private var spaceTree: Map<String, MatrixSpace> = emptyMap()
	private var spaceId = "!home:SakuraNative"
	private var currentSpace = MatrixSpace(null, emptyList(), emptyList(), 0)
	private var expandedRooms = HashMap<String, Boolean>()
	private var items = mutableListOf<Any>()

	init {
		suspendThread {
			// Will be called every room change (hopefully)
			client.client.room.getAll().collect {
				spaceTree = client.getSpaceTree()
					.associateBy { it.parent?.roomId?.full ?: "!home:SakuraNative" }
				currentSpace = spaceTree.values.first()
				activity.runOnUiThread {
					rebuildItemsList()
					notifyDataSetChanged()
				}
			}
		}
	}

	private fun rebuildItemsList() {
		items.clear()

		// Add all children first
		items.addAll(currentSpace.children)

		// Add childSpaces and their expanded children
		currentSpace.childSpaces.forEach { childSpace ->
			items.add(childSpace)
			val childSpaceId = childSpace.parent?.roomId?.full
			if (childSpaceId != null && (expandedRooms[childSpaceId] ?: true)) {
				items.addAll(childSpace.children)
			}
		}
	}

	fun changeSpace(spaceId: String) {
		Log.d("SpaceTreeRecyclerAdapter", "changeSpace($spaceId)")
		if (spaceTree.isEmpty()) {
			Handler(activity.mainLooper).postDelayed(50) { changeSpace(spaceId) }
			return
		}
		this.spaceId = spaceId
		currentSpace = spaceTree[spaceId] ?: spaceTree.values.first()
		expandedRooms.clear()
		rebuildItemsList()
		notifyDataSetChanged()
	}

	open class RoomListViewModel(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
	class CategoryViewModel(val binding: ItemRoomCategoryBinding) : RoomListViewModel(binding) {
		fun bind(space: MatrixSpace) {
			val url = space.parent?.avatarUrl
			if (url != null) Glide.with(binding.root).load(url).into(binding.icon)
			else binding.icon.visibility = View.GONE
			binding.title.text = space.parent?.name?.explicitName ?: "null"
			binding.root.setOnClickListener {
				(bindingAdapter as SpaceTreeRecyclerAdapter).toggleSpace(space.parent?.roomId?.full)
			}
		}
	}

	class RoomViewModel(val binding: ItemRoomBinding) : RoomListViewModel(binding) {
		fun bind(room: Room) {
			binding.unreadIndicator.visibility =
				if (!room.isUnread) View.VISIBLE else View.INVISIBLE

			binding.title.text = room.name?.explicitName ?: "null"
			if (room.isDirect) {
				// TODO: Show last message
				binding.subtitle.text = room.roomId.full
			} else {
				binding.subtitle.visibility = View.GONE
			}
			if (room.avatarUrl == null) {
				binding.icon.visibility = View.GONE
			} else {
				Glide.with(binding.root).load(room.avatarUrl).into(binding.icon)
			}

			with((bindingAdapter as SpaceTreeRecyclerAdapter)) {
				binding.root.setOnClickListener {
					openRoom(room)
				}
				if (room.roomId.full != currentRoomId()) {
					binding.container.setBackgroundColor(Color.TRANSPARENT)
				} else {
					val typedValue = android.util.TypedValue()
					binding.root.context.theme.resolveAttribute(
						MaterialR.attr.colorSecondaryContainer,
						typedValue,
						true
					)
					binding.container.setBackgroundColor(typedValue.data)
				}
			}
		}
	}

	private fun toggleSpace(id: String?) {
		if (id == null) return

		val wasExpanded = expandedRooms[id] ?: true
		expandedRooms[id] = !wasExpanded

		// Remove/add items accordingly
		currentSpace.childSpaces.find { it.parent?.roomId?.full == id }?.let { childSpace ->
			val spaceIndex = items.indexOf(childSpace)
			if (spaceIndex != -1) {
				if (wasExpanded) {
					// Was expanded, now collapsing
					val childrenToRemove = childSpace.children.size
					for (i in 0 until childrenToRemove) {
						items.removeAt(spaceIndex + 1)
					}
					notifyItemRangeRemoved(spaceIndex + 1, childrenToRemove)
				} else {
					// Was collapsed, now expanding
					val childrenToAdd = childSpace.children
					items.addAll(spaceIndex + 1, childrenToAdd)
					notifyItemRangeInserted(spaceIndex + 1, childrenToAdd.size)
				}
			}
		}
	}

	override fun getItemViewType(position: Int): Int {
		return when (items[position]) {
			is Room -> 0
			is MatrixSpace -> 1
			else -> -1 // Will never happen
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomListViewModel {
		return when (viewType) {
			0 -> RoomViewModel(ItemRoomBinding.inflate(layoutInflater, parent, false))
			1 -> CategoryViewModel(ItemRoomCategoryBinding.inflate(layoutInflater, parent, false))
			else -> null!!
		}
	}

	override fun onBindViewHolder(holder: RoomListViewModel, position: Int) {
		when (holder) {
			is RoomViewModel -> (items[position] as? Room)?.let { holder.bind(it) }
			is CategoryViewModel -> (items[position] as? MatrixSpace)?.let { holder.bind(it) }
		}
	}

	override fun getItemCount() = items.size

	private fun openRoom(room: Room?) {
		if (room == null) return
		val oldRoomId = currentRoomId()
		activity.openRoomTimeline(room)
		items.indexOfFirst { (it as? Room)?.roomId?.full == oldRoomId }
			.takeUnless { it == -1 }
			?.let {
				notifyItemChanged(it)
			}
		items.indexOfFirst { (it as? Room)?.roomId?.full == room.roomId.full }
			.takeUnless { it == -1 }
			?.let {
				notifyItemChanged(it)
			}
	}

	private fun currentRoomId() = activity.getCurrentRoomId()
}