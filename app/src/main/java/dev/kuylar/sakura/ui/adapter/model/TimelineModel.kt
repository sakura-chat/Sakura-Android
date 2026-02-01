package dev.kuylar.sakura.ui.adapter.model

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

interface TimelineModel {
	val type: Int
	val eventId: EventId
	val roomId: RoomId
	val timestamp: Long

	fun dispose()

	class ItemCallback : DiffUtil.ItemCallback<TimelineModel>() {
		override fun areItemsTheSame(oldItem: TimelineModel, newItem: TimelineModel) =
			oldItem.eventId == newItem.eventId &&
					oldItem.roomId == newItem.roomId &&
					oldItem.type == newItem.type

		@SuppressLint("DiffUtilEquals")
		override fun areContentsTheSame(oldItem: TimelineModel, newItem: TimelineModel): Boolean {
			return if (oldItem is EventModel && newItem is EventModel) {
				val oldContents = oldItem.snapshot.content?.getOrNull()
				val newContents = newItem.snapshot.content?.getOrNull()
				oldContents == newContents
			} else oldItem.eventId == newItem.eventId
		}
	}

	companion object {
		const val TYPE_EVENT = 0
		const val TYPE_OUTBOX = 1
	}
}