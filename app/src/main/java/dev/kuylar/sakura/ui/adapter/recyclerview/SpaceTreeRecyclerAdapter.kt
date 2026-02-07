package dev.kuylar.sakura.ui.adapter.recyclerview

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.databinding.ItemRoomBinding
import dev.kuylar.sakura.databinding.ItemRoomCategoryBinding
import dev.kuylar.sakura.ui.activity.MainActivity
import dev.kuylar.sakura.ui.adapter.model.RoomModel
import dev.kuylar.sakura.ui.adapter.model.SpaceModel
import com.google.android.material.R as MaterialR

@SuppressLint("NotifyDataSetChanged")
class SpaceTreeRecyclerAdapter(val activity: MainActivity, val client: Matrix) :
	RecyclerView.Adapter<SpaceTreeRecyclerAdapter.RoomListViewModel>() {
	var layoutInflater = activity.layoutInflater
	private var spaceTree: Map<String, MatrixSpace> = emptyMap()
	private var spaceId = "!home:SakuraNative"
	private var firstLoadComplete = false
	private var currentSpace = MatrixSpace(null, emptyList(), emptyList(), 0, MatrixSpace.Type.DirectMessages)
	private var expandedRooms = HashMap<String, Boolean>()
	private var items = mutableListOf<Any>()

	init {
		setHasStableIds(true)
		suspendThread {
			// Will be called every room change (hopefully)
			client.getSpaceTreeFlow().collect {
				Log.i("SpaceTreeRecyclerAdapter", "Space tree updated.")
				spaceTree = it.associateBy { it.parent?.roomId?.full ?: "!home:SakuraNative" }
				if (!firstLoadComplete) {
					currentSpace = spaceTree.values.first()
					firstLoadComplete = true
				}
				activity.runOnUiThread {
					rebuildItemsList()
					notifyDataSetChanged()
				}
			}
		}
	}

	private fun rebuildItemsList() {
		items.forEach {
			when (it) {
				is RoomModel -> it.dispose()
				is SpaceModel -> {}//it.dispose()
			}
		}
		items.clear()

		// Add all children first
		items.addAll(currentSpace.children.map {
			it.onChange = {
				activity.runOnUiThread {
					val index = items.indexOfFirst { e -> (e as? RoomModel)?.id == it.id }
					if (index >= 0) {
						notifyItemChanged(index)
					}
				}
			}
			it
		})

		// Add childSpaces and their expanded children
		currentSpace.childSpaces.forEach { childSpace ->
			items.add(SpaceModel(childSpace))
			val childSpaceId = childSpace.parent?.roomId?.full
			if (childSpaceId != null && (expandedRooms[childSpaceId] ?: true)) {
				items.addAll(childSpace.children.map {
					it.onChange = {
						activity.runOnUiThread {
							val index =
								items.indexOfFirst { e -> (e as? RoomModel)?.id == it.id }
							if (index >= 0) {
								notifyItemChanged(index)
							}
						}
					}
					it
				})
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

	override fun getItemId(position: Int): Long {
		return when (val item = items[position]) {
			is RoomModel -> item.id.hashCode().toLong()
			is SpaceModel -> item.snapshot.parent?.roomId?.hashCode()?.toLong() ?: 0
			else -> -1 // Will never happen
		}
	}

	open class RoomListViewModel(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
	class CategoryViewModel(val binding: ItemRoomCategoryBinding) : RoomListViewModel(binding) {
		fun bind(model: SpaceModel) {
			val space = model.snapshot
			val adapter = (bindingAdapter as SpaceTreeRecyclerAdapter)
			val url = space.parent?.avatarUrl
			if (url != null) Glide.with(binding.root).load(url).into(binding.icon)
			else binding.icon.visibility = View.GONE
			binding.title.text = space.parent?.name?.explicitName ?: "null"
			binding.root.setOnClickListener {
				handleIndicator(adapter.toggleSpace(space.parent?.roomId?.full))
			}
			handleIndicator(adapter.expandedRooms[space.parent?.roomId?.full] ?: true)
		}

		private fun handleIndicator(expanded: Boolean) {
			binding.indicator.setImageDrawable(
				ContextCompat.getDrawable(
					binding.indicator.context, if (expanded) R.drawable.ic_expanded
					else R.drawable.ic_collapsed
				)
			)
		}
	}

	class RoomViewModel(val binding: ItemRoomBinding) : RoomListViewModel(binding) {
		fun bind(model: RoomModel) {
			val room = model.snapshot
			val lastMessage = model.lastMessage

			binding.title.text = room.name?.explicitName ?: "null"
			if (room.isDirect) {
				binding.subtitle.text =
					(lastMessage?.content?.getOrNull() as? RoomMessageEventContent.TextBased)?.body
				binding.subtitle.visibility = View.VISIBLE
			} else {
				binding.subtitle.visibility = View.GONE
			}
			binding.unreadIndicator.visibility =
				if (model.isUnread) View.VISIBLE else View.INVISIBLE
			val unreadLabel = model.mentions.takeIf { it > 0 }
			if (unreadLabel != null) {
				binding.mentions.visibility = View.VISIBLE
				binding.mentions.text = unreadLabel.takeIf { it < 100 }?.toString() ?: "99+"
			} else binding.mentions.visibility = View.GONE
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

	private fun toggleSpace(id: String?): Boolean {
		if (id == null) return true

		val wasExpanded = expandedRooms[id] ?: true
		expandedRooms[id] = !wasExpanded

		// Remove/add items accordingly
		currentSpace.childSpaces.find { it.parent?.roomId?.full == id }?.let { childSpace ->
			val spaceIndex =
				items.indexOfFirst { it is SpaceModel && it.snapshot.parent?.roomId?.full == id }
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
		return !wasExpanded
	}

	override fun getItemViewType(position: Int): Int {
		return when (items[position]) {
			is RoomModel -> 0
			is SpaceModel -> 1
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
			is RoomViewModel -> (items[position] as? RoomModel)?.let { holder.bind(it) }
			is CategoryViewModel -> (items[position] as? SpaceModel)?.let { holder.bind(it) }
		}
	}

	override fun getItemCount() = items.size

	private fun openRoom(room: Room?) {
		if (room == null) return
		val oldRoomId = currentRoomId()
		activity.openRoomTimeline(room)
		items.indexOfFirst { (it as? RoomModel)?.id?.full == oldRoomId }
			.takeUnless { it == -1 }
			?.let {
				notifyItemChanged(it)
			}
		items.indexOfFirst { (it as? RoomModel)?.id == room.roomId }
			.takeUnless { it == -1 }
			?.let {
				notifyItemChanged(it)
			}
	}

	private fun currentRoomId() = activity.getCurrentRoomId()
}