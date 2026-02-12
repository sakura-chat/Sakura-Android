package dev.kuylar.sakura.ui.adapter.listadapter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import dev.kuylar.sakura.Utils.getIndicatorColor
import dev.kuylar.sakura.Utils.loadAvatar
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ItemUserBinding
import dev.kuylar.sakura.ui.adapter.model.UserModel
import dev.kuylar.sakura.ui.fragment.bottomsheet.ProfileBottomSheetFragment
import io.getstream.avatarview.glide.loadImage
import kotlinx.coroutines.flow.first
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class UserListAdapter(
	val fragment: Fragment,
	val roomId: String,
	val client: Matrix,
	val recycler: RecyclerView
) : ListAdapter<UserModel.State, UserListAdapter.ViewHolder>(
	AsyncDifferConfig.Builder(UserModel.DiffCallback()).build()
) {
	private val layoutInflater = fragment.layoutInflater
	private var users = mutableMapOf<UserId, UserModel>()
	private var layoutManager = recycler.layoutManager as LinearLayoutManager

	init {
		suspendThread {
			client.getRoom(roomId)?.let {
				client.client.user.getAll(RoomId(roomId)).collect { newUsers ->
					val addedUsers = newUsers.filter { it.key !in users.keys }
					val removedUsers = users.filter { it.key !in newUsers.keys }
					removedUsers.forEach { (id, _) ->
						users.remove(id)?.dispose()
					}
					addedUsers.forEach { (id, flow) ->
						val snapshot = flow.first()
						val model = UserModel(id, flow, client, snapshot) {
							val lastPos = layoutManager.findFirstCompletelyVisibleItemPosition()
							submit(users.values) {
								recycler.post {
									recycler.scrollToPosition(lastPos)
								}
							}
						}
						users[id] = model
					}
					submit(users.values)
				}
			}
		}
	}

	private fun orderList(list: MutableCollection<UserModel>): List<UserModel.State> {
		return list.map { it.state }.sortedWith(
			compareBy<UserModel.State> {
				it.presence == Presence.OFFLINE
			}.thenBy(String.CASE_INSENSITIVE_ORDER) {
				it.username
			}.thenBy {
				it.userId.full
			}
		).toList()
	}

	fun submit(list: MutableCollection<UserModel>, commitCallback: Runnable? = null) {
		super.submitList(orderList(list), commitCallback)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		ViewHolder(ItemUserBinding.inflate(layoutInflater, parent, false))

	override fun onBindViewHolder(holder: ViewHolder, position: Int) =
		holder.bind(getItem(position), roomId)

	fun dispose() {
		users.forEach { (k, it) ->
			it.dispose()
		}
		users.clear()
	}

	class ViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
		private var lastUserId: UserId? = null

		@OptIn(ExperimentalTime::class)
		fun bind(user: UserModel.State, roomId: String) {
			if (user.userId != lastUserId) resetBindingState()

			binding.avatar.avatarInitials = null
			binding.avatar.loadAvatar(user.avatar, user.username)
			binding.name.text = user.username
			val presence = UserPresence(
				user.presence ?: Presence.OFFLINE,
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
					putString("userId", user.userId.full)
					putString("roomId", roomId)
				}
				f.show(
					(bindingAdapter as UserListAdapter).fragment.parentFragmentManager,
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