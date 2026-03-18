package dev.kuylar.sakura.ui.adapter.spaces

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Presence
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.getIndicatorColor
import dev.kuylar.sakura.Utils.getName
import dev.kuylar.sakura.Utils.loadAvatar
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.databinding.ItemRoomBinding
import dev.kuylar.sakura.databinding.ItemRoomCategoryBinding
import dev.kuylar.sakura.databinding.ItemSpaceListDividerBinding
import dev.kuylar.sakura.ui.activity.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import com.google.android.material.R as MaterialR

class SpaceTreeListAdapter(
	val activity: MainActivity, val client: Matrix, var selectedRoom: RoomId? = null
) : ListAdapter<SpaceTreeModel, SpaceTreeListAdapter.ViewHolder>(SpaceTreeModel.DiffCallback()) {
	private val inflater = activity.layoutInflater
	private var currentSpace: MatrixSpace = MatrixSpace(null, emptyList(), emptyList())
	private var expandedRooms = HashMap<RoomId, Boolean>()
	override fun getItemViewType(position: Int): Int {
		return when (getItem(position)) {
			is SpaceTreeModel.Category -> 1
			is SpaceTreeModel.Room -> 2
			else -> 0
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
		1 -> CategoryViewHolder(ItemRoomCategoryBinding.inflate(inflater, parent, false))
		2 -> RoomViewHolder(ItemRoomBinding.inflate(inflater, parent, false))
		else -> ViewHolder(ItemSpaceListDividerBinding.inflate(inflater, parent, false))
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = getItem(position)
		if (holder is RoomViewHolder && item is SpaceTreeModel.Room) {
			holder.bind(item, client, activity.lifecycleScope)
		}
		if (holder is CategoryViewHolder && item is SpaceTreeModel.Category) {
			holder.bind(item, client, activity.lifecycleScope)
		}
	}

	fun changeSpace(roomId: RoomId) {
		client.getSpaceChildrenRecursive(roomId)?.let { space ->
			currentSpace = space
			refreshList()
		}
	}

	fun isSpaceExpanded(roomId: RoomId) = expandedRooms[roomId] ?: true

	fun toggleSpace(roomId: RoomId): Boolean {
		val newValue = !isSpaceExpanded(roomId)
		expandedRooms[roomId] = newValue
		refreshList()
		return newValue
	}

	private fun refreshList() {
		val list = mutableListOf<SpaceTreeModel>()
		list.addAll(currentSpace.children.map { SpaceTreeModel.from(it) })
		currentSpace.childSpaces.forEach {
			if (it.parent == null) return@forEach
			// TODO: Handle sub spaces
			list.add(SpaceTreeModel.from(it))
			if (isSpaceExpanded(it.parent.roomId)) {
				list.addAll(it.children.map { r -> SpaceTreeModel.from(r) })
			}
		}
		submitList(list)
	}

	open class ViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
	class CategoryViewHolder(val binding: ItemRoomCategoryBinding) : ViewHolder(binding) {
		fun bind(
			item: SpaceTreeModel.Category,
			client: Matrix,
			lifecycleScope: LifecycleCoroutineScope
		) {
			val space = item.room
			val adapter = (bindingAdapter as SpaceTreeListAdapter)
			val url = space.avatarUrl
			if (url != null) Glide.with(binding.root).load(url).into(binding.icon)
			else binding.icon.visibility = View.GONE
			lifecycleScope.launch {
				binding.title.text = space.getName(binding.title.context, client)
			}
			binding.root.setOnClickListener {
				handleIndicator(adapter.toggleSpace(space.roomId))
			}
			handleIndicator(adapter.isSpaceExpanded(space.roomId))
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

	fun changeRoom(room: Room) {
		synchronized(currentList) {
			val lastItem = currentList.indexOfFirst { it.id == selectedRoom }
			val newItem = currentList.indexOfFirst { it.id == room.roomId }
			selectedRoom = room.roomId
			if (lastItem >= 0) notifyItemChanged(lastItem)
			if (newItem >= 0) notifyItemChanged(newItem)
		}
		activity.openRoomTimeline(room)
	}

	class RoomViewHolder(val binding: ItemRoomBinding) : ViewHolder(binding) {
		private var job: Job? = null

		@OptIn(ExperimentalTime::class)
		fun bind(
			item: SpaceTreeModel.Room,
			client: Matrix,
			lifecycleScope: LifecycleCoroutineScope
		) {
			val room = item.room
			job?.cancel()
			binding.avatar.indicatorEnabled = false
			binding.avatar.indicatorColor =
				Presence.OFFLINE.getIndicatorColor(binding.avatar.context)

			// TODO: if room.isDirect, show avatar & presence
			lifecycleScope.launch {
				room.getName(binding.title.context, client).let {
					binding.title.text = it
					binding.avatar.loadAvatar(room.avatarUrl, it)
				}
			}
			binding.subtitle.visibility = View.VISIBLE
			binding.subtitle.text = room.lastRelevantEventTimestamp?.toString() ?: "null"

			handleUnread(item.isUnread, item.mentions)

			with((bindingAdapter as SpaceTreeListAdapter)) {
				binding.root.setOnClickListener {
					changeRoom(room)
				}
				if (room.roomId != selectedRoom) {
					binding.container.setBackgroundColor(Color.TRANSPARENT)
				} else {
					val typedValue = android.util.TypedValue()
					binding.root.context.theme.resolveAttribute(
						MaterialR.attr.colorSecondaryContainer, typedValue, true
					)
					binding.container.setBackgroundColor(typedValue.data)
				}
				job = lifecycleScope.launch {
					combine(
						client.getIsUnread(room.roomId),
						client.getNotificationCount(room.roomId),
						client.getRoomPresence(room) ?: flowOf(null)
					) { a, b, c -> Triple(a, b, c) }.collect {
						item.isUnread = it.first
						item.mentions = it.second
						if (it.third != null) {
							binding.avatar.indicatorEnabled = true
							binding.avatar.indicatorColor =
								it.third!!.presence.getIndicatorColor(binding.avatar.context)
						} else {
							binding.avatar.indicatorEnabled = false
						}
						handleUnread(it.first, it.second)
					}
				}
			}
		}

		fun handleUnread(isUnread: Boolean, mentions: Int) {
			binding.unreadIndicator.visibility = if (isUnread) View.VISIBLE else View.INVISIBLE
			val unreadLabel = mentions.takeIf { it > 0 }
			if (unreadLabel != null) {
				binding.mentions.visibility = View.VISIBLE
				binding.mentions.text = unreadLabel.takeIf { it < 100 }?.toString() ?: "99+"
			} else binding.mentions.visibility = View.GONE
		}
	}
}