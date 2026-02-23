package dev.kuylar.sakura.ui.adapter.spaces

import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.google.android.material.shape.ShapeAppearanceModel
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.indexOfFirst
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ItemSpaceBinding
import dev.kuylar.sakura.databinding.ItemSpaceListDividerBinding
import dev.kuylar.sakura.ui.activity.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TopLevelSpacesRecyclerAdapter(
	val activity: MainActivity,
	val client: Matrix,
	var selectedSpace: RoomId? = null
) : RecyclerView.Adapter<TopLevelSpacesRecyclerAdapter.ViewHolder>() {
	private val inflater = activity.layoutInflater
	private val spaces =
		SortedList(Room::class.java, object : SortedList.Callback<Room>() {
			override fun compare(o1: Room, o2: Room) =
				o1.orderEvent?.order?.compareTo(o2.orderEvent?.order ?: "") ?: 0

			override fun areContentsTheSame(o1: Room, o2: Room) = o1.roomId == o2.roomId

			override fun areItemsTheSame(o1: Room, o2: Room) = o1.roomId == o2.roomId

			override fun onChanged(position: Int, count: Int) =
				notifyItemRangeChanged(position + 3, count)

			override fun onInserted(position: Int, count: Int) {
				notifyItemRangeChanged(0, 2)
				notifyItemRangeInserted(position + 3, count)
			}

			override fun onRemoved(position: Int, count: Int) =
				notifyItemRangeRemoved(position + 3, count)

			override fun onMoved(from: Int, to: Int) = notifyItemMoved(from + 3, to + 3)
		})
	val unreadCache = mutableMapOf<RoomId, Pair<Int, Boolean>>()

	init {
		activity.lifecycleScope.launch {
			activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
				// TODO: Update this but only when room count changes!
				spaces.replaceAll(client.getTopLevelSpaces())
			}
		}
	}

	override fun getItemViewType(position: Int): Int {
		return when (position) {
			2 -> 0 // Divider
			else -> 1
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
		1 -> SpaceViewHolder(ItemSpaceBinding.inflate(inflater, parent, false), client)
		else -> DividerViewHolder(ItemSpaceListDividerBinding.inflate(inflater, parent, false))
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		when (position) {
			0 -> { // DMs
				(holder as? SpaceViewHolder)?.bindSpecial(Matrix.Companion.DIRECT_ROOM, selectedSpace)
			}

			1 -> { // Groups
				(holder as? SpaceViewHolder)?.bindSpecial(Matrix.Companion.GROUPS_ROOM, selectedSpace)
			}

			2 -> { /* Divider */ }

			else -> {
				spaces[position - 3]?.let { (holder as? SpaceViewHolder)?.bind(it, selectedSpace) }
			}
		}
	}

	override fun getItemCount() = spaces.size() + 3

	fun changeSpace(spaceId: RoomId) {
		val lastItem = when (selectedSpace) {
			Matrix.DIRECT_ROOM -> 0
			Matrix.GROUPS_ROOM -> 1
			else -> spaces.indexOfFirst { it?.roomId == selectedSpace } + 3
		}
		val newItem = when (spaceId) {
			Matrix.DIRECT_ROOM -> 0
			Matrix.GROUPS_ROOM -> 1
			else -> spaces.indexOfFirst { it?.roomId == spaceId } + 3
		}
		selectedSpace = spaceId
		if (lastItem >= 0) notifyItemChanged(lastItem)
		if (newItem >= 0) notifyItemChanged(newItem)
		activity.openSpaceTree(spaceId)
	}

	open class ViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

	class SpaceViewHolder(val binding: ItemSpaceBinding, val client: Matrix) : ViewHolder(binding) {
		private var job: Job? = null

		private fun commonBind(roomId: RoomId, room: Room?, selectedSpace: RoomId?) {
			val adapter = bindingAdapter as? TopLevelSpacesRecyclerAdapter
			binding.icon.setImageDrawable(null)
			job?.cancel()
			binding.icon.setOnClickListener {
				adapter?.changeSpace(roomId)
			}
			val cachedData = adapter?.unreadCache?.get(roomId) ?: Pair(0, false)
			if (room != null)
				setIcon(room)
			else
				setIcon(roomId)
			setData(roomId == selectedSpace, cachedData)
			job = adapter?.activity?.lifecycleScope?.launch {
				combine(client.getNotificationCount(roomId), client.getIsUnread(roomId)) { a, b ->
					Pair(a, b)
				}.collect {
					adapter.unreadCache[roomId] = it
					setData(roomId == selectedSpace, it)
				}
			}
		}

		fun bind(item: Room, selectedSpace: RoomId?) = commonBind(item.roomId, item, selectedSpace)
		fun bindSpecial(roomId: RoomId, selectedSpace: RoomId?) =
			commonBind(roomId, null, selectedSpace)

		fun setData(isSelected: Boolean, notificationsData: Pair<Int, Boolean>) {
			binding.icon.post {
				binding.icon.shapeAppearanceModel =
					if (isSelected) {
						ShapeAppearanceModel.builder().setAllCornerSizes(binding.icon.height / 4f)
							.build()
					} else {
						ShapeAppearanceModel.builder().setAllCornerSizes(binding.icon.height / 2f)
							.build()
					}
				val lp = binding.unreadIndicator.layoutParams
				val eightDp = (8 * binding.root.context.resources.displayMetrics.density).toInt()
				lp.height = if (isSelected) binding.icon.height - eightDp else eightDp
				binding.unreadIndicator.post {
					binding.unreadIndicator.layoutParams = lp
					binding.unreadIndicator.invalidate()
				}
			}

			val isUnread = notificationsData.second
			val mentions = notificationsData.first

			binding.unreadIndicator.visibility =
				if (isUnread || isSelected) View.VISIBLE else View.GONE
			binding.mentions.visibility = if (mentions > 0) View.VISIBLE else View.GONE
			binding.mentions.text = mentions.takeIf { it < 100 }?.toString() ?: "99+"
		}

		fun setIcon(item: Room) {
			Glide.with(binding.root)
				.load(item.avatarUrl)
				.into(binding.icon)
		}

		fun setIcon(roomId: RoomId) {
			val id = when (roomId) {
				Matrix.Companion.DIRECT_ROOM -> R.drawable.ic_directmessages
				Matrix.Companion.GROUPS_ROOM -> R.drawable.ic_groups
				else -> null
			} ?: return
			binding.icon.setImageDrawable(ContextCompat.getDrawable(binding.icon.context, id))
		}
	}

	class DividerViewHolder(binding: ItemSpaceListDividerBinding) : ViewHolder(binding)
}