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

class OutboxModel(
	flow: Flow<RoomOutboxMessage<*>?>,
	var snapshot: RoomOutboxMessage<*>,
	val client: Matrix,
	var onChange: (() -> Unit)? = null
) : TimelineModel {
	var uploadProgress: FileTransferProgress? = null
	var userSnapshot: RoomUser? = null
	override val eventId: EventId
		get() = snapshot.eventId ?: EventId(snapshot.transactionId)
	override val roomId: RoomId
		get() = snapshot.roomId
	override val type: Int
		get() = TimelineModel.TYPE_OUTBOX
	private val jobs = mutableListOf<Job>()

	init {
		jobs.add(
			suspendThread {
				flow.collect {
					it?.let {
						snapshot = it
						onChange?.invoke()
					}
				}
			})
		jobs.add(
			suspendThread {
				snapshot.mediaUploadProgress.collect {
					it?.let {
						uploadProgress = it
						onChange?.invoke()
					}
				}
			}
		)
		jobs.add(
			suspendThread {
				client.client.user.getById(roomId, client.userId).collect {
					it?.let { snapshot ->
						userSnapshot = snapshot
						onChange?.invoke()
					}
				}
			}
		)
	}

	override fun dispose() {
		jobs.forEach { it.cancel() }
		jobs.clear()
	}
}