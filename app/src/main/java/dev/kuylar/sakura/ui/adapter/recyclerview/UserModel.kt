package dev.kuylar.sakura.ui.adapter.recyclerview

import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.UserId

class UserModel(
	val userId: UserId,
	val flow: Flow<RoomUser?>,
	var snapshot: RoomUser? = null,
	var onChange: (() -> Unit)? = null,
) {
	var presence: UserPresence? = null
	private val client = Matrix.getClient()
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