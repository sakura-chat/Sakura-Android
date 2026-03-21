package dev.kuylar.sakura.ui.adapter.model

import androidx.recyclerview.widget.DiffUtil
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.membership
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.Membership
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class UserModel(
	val userId: UserId,
	val flow: Flow<RoomUser?>,
	val client: Matrix,
	var snapshot: RoomUser? = null,
	var onChange: (() -> Unit)? = null,
) {
	var presence: UserPresence? = null
	var state: State = State(
		userId,
		snapshot?.avatarUrl,
		snapshot?.name ?: userId.full,
		presence?.presence ?: Presence.OFFLINE,
		null,
		Membership.LEAVE
	)

	data class State(
		val userId: UserId,
		val avatar: String?,
		val username: String,
		val presence: Presence,
		val statusMessage: String?,
		val membership: Membership
	)

	private var collectJob: Job? = null

	init {
		collectJob = CoroutineScope(Dispatchers.Main).launch {
			combine(flow, client.client.user.getPresence(userId)) { user, presence ->
				State(
					userId,
					user?.avatarUrl,
					user?.name ?: userId.full,
					presence?.presence ?: Presence.OFFLINE,
					presence?.statusMessage,
					user?.membership ?: Membership.LEAVE
				)
			}.collect {
				state = it
				onChange?.invoke()
			}
		}
	}

	fun dispose() {
		collectJob?.cancel()
		collectJob = null
	}

	class DiffCallback : DiffUtil.ItemCallback<State>() {
		override fun areItemsTheSame(oldItem: State, newItem: State) =
			oldItem.userId == newItem.userId

		override fun areContentsTheSame(oldItem: State, newItem: State) =
			oldItem == newItem
	}
}