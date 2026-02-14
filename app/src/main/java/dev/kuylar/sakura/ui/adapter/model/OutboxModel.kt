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
import kotlinx.coroutines.flow.first
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OutboxModel(
	flow: Flow<RoomOutboxMessage<*>?>,
	var eventSnapshot: RoomOutboxMessage<*>,
	val client: Matrix,
	var onChange: ((OutboxModel) -> Unit)? = null
) : TimelineModel {
	var uploadProgress: FileTransferProgress? = null
	var userSnapshot: RoomUser? = null
	private var combinedFlow: Flow<Snapshot>
	private var snapshot: Snapshot? = null
	override val eventId: EventId
		get() = eventSnapshot.eventId ?: EventId(eventSnapshot.transactionId)
	override val roomId: RoomId
		get() = eventSnapshot.roomId
	override val timestamp: Long
		get() = eventSnapshot.createdAt.toEpochMilliseconds() + 1000 * 60 * 60
	override val type: Int
		get() = TimelineModel.TYPE_OUTBOX
	private var job: Job? = null

	init {
		combinedFlow = combine(
			flow,
			eventSnapshot.mediaUploadProgress,
			client.client.user.getById(roomId, client.userId)
		) { message, progress, user -> Snapshot(message, progress, user) }
		suspendThread {
			collect(combinedFlow.first())
		}
	}

	override fun dispose() {
		job?.cancel()
		job = null
	}

	override fun start() {
		if (job?.isActive == true) return
		job = suspendThread {
			combinedFlow.collect { collect(it) }
		}
	}

	override fun pause() {
		job?.cancel()
		job = null
	}

	fun collect(newSnapshot: Snapshot) {
		if (snapshot == newSnapshot) return
		snapshot = newSnapshot

		newSnapshot.message?.let { eventSnapshot = it }
		uploadProgress = newSnapshot.progress
		userSnapshot = newSnapshot.user
		onChange?.invoke(this)
	}

	data class Snapshot(
		val message: RoomOutboxMessage<*>?,
		val progress: FileTransferProgress?,
		val user: RoomUser?
	)
}