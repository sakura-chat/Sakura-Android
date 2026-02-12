package dev.kuylar.sakura.ui.adapter.recyclerview

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.Membership
import dev.kuylar.sakura.Utils.getIndicatorColor
import dev.kuylar.sakura.Utils.loadAvatar
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ItemUserBinding
import dev.kuylar.sakura.ui.adapter.model.UserModel
import dev.kuylar.sakura.ui.fragment.bottomsheet.ProfileBottomSheetFragment
import io.getstream.avatarview.glide.loadImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class UserListRecyclerAdapter(val fragment: Fragment, val roomId: String, val client: Matrix) :
	RecyclerView.Adapter<UserListRecyclerAdapter.ViewHolder>() {
	private val layoutInflater = fragment.layoutInflater
	private var users = mutableListOf<UserId>()
	private val userMap = mutableMapOf<UserId, UserModel>()

	init {
		setHasStableIds(true)
		suspendThread {
			client.getRoom(roomId)?.let {
				client.client.user.getAll(RoomId(roomId)).collect { users ->
					val addedUsers = users.filter { it.key !in this.userMap.keys }
					val removedUsers = this.userMap.filter { it.key !in users.keys }
					addedUsers.forEach { (id, userFlow) ->
						handleUserAdded(id, userFlow)
					}
					removedUsers.forEach { (id, _) ->
						handleUserRemoved(id)
					}
				}
			}
		}
	}

	private suspend fun handleUserAdded(id: UserId, userFlow: Flow<RoomUser?>) {
		val existing = users.indexOfFirst { it == id }
		val snapshot = userFlow.firstOrNull()
		if (snapshot?.event?.content?.membership != Membership.JOIN) {
			handleUserRemoved(id)
			return
		}
		if (existing >= 0) {
			fragment.activity?.runOnUiThread {
				notifyItemChanged(existing)
			}
			return
		}
		val model = UserModel(id, userFlow, client) {
			handleUserUpdated(id, userFlow)
		}
		userMap[id] = model
		val insertIndex = findSortedPosition(model)
		users.add(insertIndex, id)
		fragment.activity?.runOnUiThread {
			notifyItemInserted(insertIndex)
		}
	}

	private fun handleUserRemoved(id: UserId) {
		val index = users.indexOfFirst { it == id }
		if (index >= 0) {
			users.removeAt(index)
			userMap.remove(id)?.dispose()
			fragment.activity?.runOnUiThread {
				notifyItemRemoved(index)
			}
		}
	}

	private fun handleUserUpdated(id: UserId, userFlow: Flow<RoomUser?>) {
		val oldIndex = users.indexOfFirst { it == id }
		if (oldIndex >= 0) {
			val user = users.removeAt(oldIndex)
			val model = userMap[id] ?: return
			val newIndex = findSortedPosition(model)
			users.add(newIndex, user)
			if (oldIndex != newIndex) {
				notifyItemMoved(oldIndex, newIndex)
			}
			notifyItemChanged(newIndex)
		}
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	) = ViewHolder(ItemUserBinding.inflate(layoutInflater, parent, false))

	override fun onBindViewHolder(
		holder: ViewHolder,
		position: Int
	) {
		holder.bind(userMap[users[position]]!!, roomId)
	}

	override fun getItemCount() = users.size

	override fun getItemId(position: Int): Long {
		return users.getOrNull(position)?.hashCode()?.toLong() ?: 0L
	}

	private fun findSortedPosition(user: UserModel): Int {
		return users.binarySearch { other ->
			val otherUser = userMap[other]
			val otherText = otherUser?.snapshot?.name ?: other.full
			val otherPresence = otherUser?.presence?.presence ?: Presence.OFFLINE
			val userPresence = user.presence?.presence ?: Presence.OFFLINE
			val userText = user.snapshot?.name ?: user.userId.full

			when {
				otherPresence == Presence.OFFLINE && userPresence != Presence.OFFLINE -> 1
				otherPresence != Presence.OFFLINE && userPresence == Presence.OFFLINE -> -1
				else -> otherText.compareTo(userText, ignoreCase = true)
			}
		}.let { if (it < 0) -(it + 1) else it }
	}

	fun dispose() {
		userMap.forEach { (k, it) ->
			it.dispose()
		}
		userMap.clear()
	}

	class ViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
		private var lastUserId: UserId? = null

		@OptIn(ExperimentalTime::class)
		fun bind(userModel: UserModel, roomId: String) {
			if (userModel.userId != lastUserId) resetBindingState()

			binding.avatar.avatarInitials = null
			userModel.snapshot?.let { user ->
				binding.avatar.loadAvatar(user.avatarUrl, user.name)
				binding.name.text = user.name
			}
			val presence = userModel.presence ?: UserPresence(
				Presence.OFFLINE,
				Instant.fromEpochMilliseconds(0)
			)

			binding.avatar.indicatorColor =
				presence.presence.getIndicatorColor(binding.root.context)
			binding.status.visibility =
				if (presence.statusMessage.isNullOrBlank()) View.GONE else View.VISIBLE
			binding.status.text = presence.statusMessage?.replace("\n", "\t")
			binding.root.setOnClickListener {
				val f = ProfileBottomSheetFragment()
				f.arguments = Bundle().apply {
					putString("userId", userModel.userId.full)
					putString("roomId", roomId)
				}
				f.show(
					(bindingAdapter as UserListRecyclerAdapter).fragment.parentFragmentManager,
					"profileBottomSheet"
				)
			}
		}

		fun resetBindingState() {
			binding.avatar.loadImage(null)
			binding.avatar.indicatorColor = Presence.OFFLINE.getIndicatorColor(binding.root.context)
			binding.status.visibility = View.GONE
			binding.status.text = null
			binding.name.text = null
		}
	}
}