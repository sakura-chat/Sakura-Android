package dev.kuylar.sakura.ui.adapter.model

import androidx.recyclerview.widget.DiffUtil
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

interface TimelineModel {
	val type: Int
	val eventId: EventId
	val roomId: RoomId

	fun dispose()

	class ItemCallback : DiffUtil.ItemCallback<TimelineModel>() {
		override fun areItemsTheSame(oldItem: TimelineModel, newItem: TimelineModel) =
			oldItem.eventId == newItem.eventId &&
					oldItem.roomId == newItem.roomId &&
					oldItem.type == newItem.type

		override fun areContentsTheSame(oldItem: TimelineModel, newItem: TimelineModel) =
			oldItem.eventId == newItem.eventId
	}

	companion object {
		const val TYPE_EVENT = 0
		const val TYPE_OUTBOX = 1
	}
}