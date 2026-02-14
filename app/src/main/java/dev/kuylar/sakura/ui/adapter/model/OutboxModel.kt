package dev.kuylar.sakura.ui.adapter.model

import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OutboxModel(
	flow: Flow<RoomOutboxMessage<*>?>,
	var snapshot: RoomOutboxMessage<*>,
	val client: Matrix,
	var onChange: ((OutboxModel) -> Unit)? = null
) : TimelineModel {
	var uploadProgress: FileTransferProgress? = null
	var userSnapshot: RoomUser? = null
	override val eventId: EventId
		get() = snapshot.eventId ?: EventId(snapshot.transactionId)
	override val roomId: RoomId
		get() = snapshot.roomId
	override val timestamp: Long
		get() = snapshot.createdAt.toEpochMilliseconds() + 1000 * 60 * 60
	override val type: Int
		get() = TimelineModel.TYPE_OUTBOX
	private var job: Job? = null

	init {
		job = suspendThread {
			combine(
				flow,
				snapshot.mediaUploadProgress,
				client.client.user.getById(roomId, client.userId)
			) { message, progress, user ->
				Triple(
					message, progress, user
				)
			}.collect { (message, progress, user) ->
				message?.let { snapshot = it }
				uploadProgress = progress
				userSnapshot = user

				onChange?.invoke(this)
			}
		}
	}

	override fun dispose() {
		job?.cancel()
		job = null
	}
}