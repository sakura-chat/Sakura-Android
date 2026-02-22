package dev.kuylar.sakura.ui.adapter.spaces

import androidx.recyclerview.widget.DiffUtil
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.ui.adapter.model.RoomModel
import de.connect2x.trixnity.client.store.Room as MatrixRoom

sealed class SpaceTreeModel(val id: RoomId) {
	data class Category(val room: MatrixRoom) : SpaceTreeModel(room.roomId)
	data class Room(val room: MatrixRoom, var isUnread: Boolean = false, var mentions: Int = 0) :
		SpaceTreeModel(room.roomId)

	class DiffCallback : DiffUtil.ItemCallback<SpaceTreeModel>() {
		override fun areItemsTheSame(oldItem: SpaceTreeModel, newItem: SpaceTreeModel) =
			oldItem.id == newItem.id

		override fun areContentsTheSame(oldItem: SpaceTreeModel, newItem: SpaceTreeModel) =
			oldItem == newItem
	}

	companion object {
		fun from(model: RoomModel) = Room(model.snapshot, model.isUnread, model.mentions)
		fun from(model: MatrixSpace) = Category(model.parent!!)
	}
}