package dev.kuylar.sakura.client

import de.connect2x.trixnity.client.store.Room
import dev.kuylar.sakura.ui.adapter.recyclerview.RoomModel

data class MatrixSpace(
	// This is nullable because we can also return all the rooms without a parent here
	val parent: Room?,
	val children: List<RoomModel>,
	val childSpaces: List<MatrixSpace>,
	val order: Long,
	var onChange: (() -> Unit)? = null
) {
	var isUnread: Boolean = false

	init {
		update()
		children.forEach {
			it.onChange = {
				update()
			}
		}
	}

	private fun update() {
		isUnread = children.any { it.isUnread } || childSpaces.any { it.isUnread }
	}
}