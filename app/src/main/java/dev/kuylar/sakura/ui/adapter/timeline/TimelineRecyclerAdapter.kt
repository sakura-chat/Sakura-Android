package dev.kuylar.sakura.ui.adapter.timeline

import android.util.Log
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import de.connect2x.trixnity.client.room.GetTimelineEventConfig
import de.connect2x.trixnity.client.room.GetTimelineEventsConfig
import de.connect2x.trixnity.client.room.Timeline
import de.connect2x.trixnity.client.room.TimelineState
import de.connect2x.trixnity.client.room.TimelineStateChange
import de.connect2x.trixnity.client.store.relatesTo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import dev.kuylar.sakura.Utils.getOrNull
import dev.kuylar.sakura.Utils.isAtBottom
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.ShortcodeReactionEventContent
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TimelineRecyclerAdapter(
	val fragment: Fragment,
	roomId: RoomId,
	val client: Matrix,
	val markdown: MarkdownHandler,
	val recycler: RecyclerView? = null,
	val isLoading: ((Boolean) -> Unit)? = null
) : RecyclerView.Adapter<TimelineEventViewHolder>() {
	val layoutInflater = fragment.layoutInflater
	private val items =
		SortedList(TimelineItem::class.java, object : SortedList.Callback<TimelineItem>() {
			override fun compare(o1: TimelineItem, o2: TimelineItem): Int =
				o1.sortTimestamp.compareTo(o2.sortTimestamp)

			override fun onInserted(position: Int, count: Int) {
				val scroll = recycler?.isAtBottom(-count) ?: false
				notifyItemRangeInserted(position, count)
				if (scroll) {
					recycler.post {
						val item = (recycler.adapter?.itemCount ?: 1) - 1
						Log.i("TimelineRecyclerAdapter", "scrollToPosition(${item})")
						recycler.scrollToPosition(item)
					}
				}
			}

			override fun onRemoved(position: Int, count: Int) =
				notifyItemRangeRemoved(position, count)

			override fun onMoved(fromPosition: Int, toPosition: Int) =
				notifyItemMoved(fromPosition, toPosition)

			override fun onChanged(position: Int, count: Int) =
				notifyItemRangeChanged(position, count)

			override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean =
				oldItem.content != null && newItem.content != null && oldItem.content == newItem.content

			override fun areItemsTheSame(item1: TimelineItem, item2: TimelineItem): Boolean =
				item1.id == item2.id
		})
	private lateinit var timeline: Timeline<TimelineItem.Event>
	private lateinit var timelineState: TimelineState<TimelineItem.Event>
	private var getRecentJob: Job? = null
	var isReady = false
		private set

	init {
		setHasStableIds(true)
		fragment.lifecycleScope.launch {
			val room = client.getRoom(roomId)
			timeline = client.getTimeline(::onStateChange)
			loadAroundEvent(roomId, room?.lastRelevantEventId ?: room?.lastEventId ?: EventId(""))
			isReady = true
		}
	}

	override fun getItemId(position: Int) = items[position].id.hashCode().toLong()

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TimelineEventViewHolder(
		ItemMessageBinding.inflate(layoutInflater, parent, false),
		markdown
	)

	override fun onBindViewHolder(holder: TimelineEventViewHolder, position: Int) {
		holder.bind(items[position], items.getOrNull(position - 1))
	}

	override fun getItemCount() = items.size()

	override fun onViewDetachedFromWindow(holder: TimelineEventViewHolder) {
		holder.detached()
		super.onViewDetachedFromWindow(holder)
	}

	private fun startListeningToRecentMessages() {
		getRecentJob?.cancel()
		getRecentJob = fragment.lifecycleScope.launch {
			while (true) {
				timeline.loadAfter(configContinuous)
			}
		}
	}

	fun canLoadMoreBefore() = this::timelineState.isInitialized && timelineState.canLoadBefore

	fun canLoadMoreAfter() =
		this::timelineState.isInitialized && timelineState.canLoadAfter && getRecentJob == null

	suspend fun loadMoreAfter() {
		if (!this::timelineState.isInitialized) return
		if (!timelineState.canLoadAfter) return
		getRecentJob?.cancel()
		getRecentJob = null
		isLoading?.invoke(true)
		timeline.loadAfter(configPaged)
	}

	suspend fun loadMoreBefore() {
		if (!this::timelineState.isInitialized) return
		if (!timelineState.canLoadBefore) return
		isLoading?.invoke(true)
		timeline.loadBefore(configPaged)
	}

	suspend fun loadAroundEvent(roomId: RoomId, eventId: EventId) {
		if (!this::timeline.isInitialized) return
		timeline.init(roomId, eventId, configStart, configPaged, configPaged)
	}

	private suspend fun onStateChange(delta: TimelineStateChange<TimelineItem.Event>) {
		isLoading?.invoke(false)
		val filteredAdded = delta.addedElements.filter { shouldDisplayEvent(it) }
		timelineState = timeline.state.first()

		items.addAll(filteredAdded)
		items.beginBatchedUpdates()
		delta.removedElements.forEach {
			items.remove(it)
		}
		items.endBatchedUpdates()

		if (!timelineState.canLoadAfter && (getRecentJob == null || getRecentJob?.isCancelled == true)) {
			startListeningToRecentMessages()
		}
	}

	fun dispose() {
		getRecentJob?.cancel()
		getRecentJob = null
	}

	companion object {
		private const val PAGINATION_MAX_SIZE = 20L
		private const val PAGINATION_FETCH_SIZE = 20L

		private val configStart: GetTimelineEventConfig.() -> Unit = {
			this.allowReplaceContent = true
			this.fetchSize = PAGINATION_FETCH_SIZE
		}
		private val configPaged: GetTimelineEventsConfig.() -> Unit = {
			this.allowReplaceContent = true
			this.maxSize = PAGINATION_MAX_SIZE
			this.fetchSize = PAGINATION_FETCH_SIZE
			this.fetchTimeout = 5.seconds
		}
		private val configContinuous: GetTimelineEventsConfig.() -> Unit = {
			this.minSize = 2 /* because minSize = 1 returns the lastEvent */
			this.maxSize = 5
			this.fetchSize = 1
			this.allowReplaceContent = true
			this.fetchTimeout = Duration.INFINITE
		}

		private fun shouldDisplayEvent(event: RoomEventContent?, relatesTo: RelatesTo?): Boolean {
			return (relatesTo?.relationType == RelationType.Replace ||
					event is RedactionEventContent ||
					event is RedactedEventContent ||
					event is ReactionEventContent ||
					event is ShortcodeReactionEventContent).not()
		}

		private fun shouldDisplayEvent(event: TimelineItem) = when (event) {
			is TimelineItem.Event -> {
				shouldDisplayEvent(event.content, event.event.relatesTo)
			}

			is TimelineItem.OutboxItem -> {
				shouldDisplayEvent(event.content, event.snapshot.content.relatesTo)
			}
		}
	}
}