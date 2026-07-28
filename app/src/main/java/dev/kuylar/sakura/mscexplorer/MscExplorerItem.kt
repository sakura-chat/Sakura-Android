package dev.kuylar.sakura.mscexplorer

data class MscExplorerItem(
	val num: Int,
	val title: String,
	val body: String,
	val spec: String,
	val user: String,
	val state: String,
	val labels: List<String>
)
