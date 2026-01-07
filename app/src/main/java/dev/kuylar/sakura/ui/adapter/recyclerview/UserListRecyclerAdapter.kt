package dev.kuylar.sakura.ui.adapter.recyclerview

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toLocalized
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ItemUserBinding
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

class UserListRecyclerAdapter(val fragment: Fragment, val roomId: String) :
	RecyclerView.Adapter<UserListRecyclerAdapter.ViewHolder>() {
	private val client = Matrix.getClient()
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
		users[id] = UserModel(id, userFlow) {
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
		holder.bind(users.values.elementAt(position))
	}

	override fun getItemCount() = users.size

	class ViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
		@OptIn(ExperimentalTime::class)
		fun bind(userModel: UserModel) {
			userModel.snapshot?.let { user ->
				Glide.with(binding.root)
					.load(user.avatarUrl)
					.into(binding.avatar)
				binding.name.text = user.name
			}
			val presence = userModel.presence ?: UserPresence(
				Presence.OFFLINE,
				Instant.fromEpochMilliseconds(0)
			)
			binding.status.visibility =
				if (presence.presence == Presence.OFFLINE) View.GONE else View.VISIBLE
			binding.status.text = presence.statusMessage?.replace("\n", "\t")
				?: presence.presence.toLocalized(binding.status.context)
		}
	}
}