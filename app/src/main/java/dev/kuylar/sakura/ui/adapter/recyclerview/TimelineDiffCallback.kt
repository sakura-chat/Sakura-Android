package dev.kuylar.sakura.ui.adapter.recyclerview

import androidx.recyclerview.widget.DiffUtil
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId

class TimelineDiffCallback(
	private val oldEventModels: List<EventModel>,
	private val newEventModels: List<EventModel>
) : DiffUtil.Callback() {
	override fun getOldListSize() = oldEventModels.size
	override fun getNewListSize() = newEventModels.size

	override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
		return oldEventModels[oldItemPosition].eventId == newEventModels[newItemPosition].eventId
	}

	override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
		val oldModel = oldEventModels[oldItemPosition].snapshot
		val newModel = newEventModels[newItemPosition].snapshot

		return oldModel.eventId == newModel.eventId && oldModel.roomId == newModel.roomId
	}
}