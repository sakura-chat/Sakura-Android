package dev.kuylar.sakura.ui.adapter.recyclerview

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import dev.kuylar.sakura.Utils.getIndicatorColor
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ItemUserBinding
import dev.kuylar.sakura.ui.fragment.bottomsheet.ProfileBottomSheetFragment
import io.getstream.avatarview.glide.loadImage
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.client.store.avatarUrl
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class UserListRecyclerAdapter(val fragment: Fragment, val roomId: String, val client: Matrix) :
	RecyclerView.Adapter<UserListRecyclerAdapter.ViewHolder>() {
	private val layoutInflater = fragment.layoutInflater
	private var users = mutableMapOf<UserId, UserModel>()

	init {
		suspendThread {
			client.getRoom(roomId)?.let {
				client.client.user.getAll(RoomId(roomId)).collect { users ->
					val addedUsers = users.filter { it.key !in this.users.keys }
					val removedUsers = this.users.filter { it.key !in users.keys }
					fragment.activity?.runOnUiThread {
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
	}

	private fun handleUserAdded(id: UserId, userFlow: Flow<RoomUser?>) {
		val existing = users.keys.indexOf(id)
		if (existing >= 0) {
			notifyItemChanged(existing)
			return
		}
		users[id] = UserModel(id, userFlow, client) {
			notifyItemChanged(users.keys.indexOf(id))
		}
		notifyItemInserted(users.size - 1)
	}

	private fun handleUserRemoved(id: UserId) {
		users.remove(id)?.dispose()
		notifyItemRemoved(users.size)
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	) = ViewHolder(ItemUserBinding.inflate(layoutInflater, parent, false))

	override fun onBindViewHolder(
		holder: ViewHolder,
		position: Int
	) {
		holder.bind(users.values.elementAt(position), roomId)
	}

	override fun getItemCount() = users.size

	class ViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
		@OptIn(ExperimentalTime::class)
		fun bind(userModel: UserModel, roomId: String) {
			userModel.snapshot?.let { user ->
				binding.avatar.loadImage(user.avatarUrl, true)
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
	}
}