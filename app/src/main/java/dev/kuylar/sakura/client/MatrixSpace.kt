package dev.kuylar.sakura.client

import de.connect2x.trixnity.client.store.Room
import dev.kuylar.sakura.ui.adapter.model.RoomModel

data class MatrixSpace(
	// This is nullable because we can also return all the rooms without a parent here
	val parent: Room?,
	val children: List<RoomModel>,
	val childSpaces: List<MatrixSpace>
)