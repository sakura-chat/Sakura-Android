package dev.kuylar.sakura.client

import net.folivo.trixnity.client.store.Room

data class MatrixSpace(
	// This is nullable because we can also return all the rooms without a parent here
	val parent: Room?,
	val children: List<Room>,
	val childSpaces: List<MatrixSpace>,
	val order: Long
) {
	val isUnread: Boolean
		get() = children.any { it.isUnread } || childSpaces.any { it.isUnread }
}