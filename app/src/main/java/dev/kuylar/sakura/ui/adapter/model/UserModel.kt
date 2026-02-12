package dev.kuylar.sakura.ui.adapter.model

import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.UserId
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class UserModel(
	val userId: UserId,
	val flow: Flow<RoomUser?>,
	val client: Matrix,
	var snapshot: RoomUser? = null,
	var onChange: (() -> Unit)? = null,
) {
	var presence: UserPresence? = null
	private var collectJob: Job? = null
	private var presenceJob: Job? = null

	init {
		collectJob = CoroutineScope(Dispatchers.Main).launch {
			flow.collect {
				snapshot = it
				onChange?.invoke()
			}
		}
		presenceJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.user.getPresence(userId).collect {
				presence = it
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
}