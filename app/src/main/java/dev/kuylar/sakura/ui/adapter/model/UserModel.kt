package dev.kuylar.sakura.ui.adapter.model

import androidx.recyclerview.widget.DiffUtil
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
		presence?.presence ?: Presence.OFFLINE
	)

	data class State(
		val userId: UserId,
		val avatar: String?,
		val username: String,
		val presence: Presence
	)

	private var collectJob: Job? = null
	private var presenceJob: Job? = null

	init {
		collectJob = CoroutineScope(Dispatchers.Main).launch {
			flow.collect {
				state = state.copy(
					avatar = it?.avatarUrl,
					username = it?.name ?: userId.full
				)
				snapshot = it
				onChange?.invoke()
			}
		}
		presenceJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.user.getPresence(userId).collect {
				state = state.copy(
					presence = it?.presence ?: Presence.OFFLINE
				)
				onChange?.invoke()
			}
		}
	}

	fun dispose() {
		collectJob?.cancel()
		collectJob = null
		presenceJob?.cancel()
		presenceJob = null
	}

	class DiffCallback : DiffUtil.ItemCallback<State>() {
		override fun areItemsTheSame(oldItem: State, newItem: State) =
			oldItem.userId == newItem.userId

		override fun areContentsTheSame(oldItem: State, newItem: State) =
			oldItem == newItem
	}
}