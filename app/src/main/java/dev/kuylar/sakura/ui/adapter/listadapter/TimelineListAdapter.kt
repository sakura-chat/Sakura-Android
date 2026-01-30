package dev.kuylar.sakura.ui.adapter.listadapter

import android.annotation.SuppressLint
import android.os.Handler
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.GetTimelineEventConfig
import de.connect2x.trixnity.client.room.GetTimelineEventsConfig
import de.connect2x.trixnity.client.room.Timeline
import de.connect2x.trixnity.client.room.TimelineState
import de.connect2x.trixnity.client.room.TimelineStateChange
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.relatesTo
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import dev.kuylar.sakura.Utils.isAtBottom
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.ShortcodeReactionEventContent
import dev.kuylar.sakura.databinding.ItemLoadingSpinnerBinding
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.databinding.ItemSpaceListDividerBinding
import dev.kuylar.sakura.databinding.LayoutErrorBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.adapter.model.EventModel
import dev.kuylar.sakura.ui.adapter.model.OutboxModel
import dev.kuylar.sakura.ui.adapter.model.TimelineModel
import dev.kuylar.sakura.ui.adapter.viewholder.ErrorViewHolder
import dev.kuylar.sakura.ui.adapter.viewholder.EventViewHolder
import dev.kuylar.sakura.ui.adapter.viewholder.LoadingIconViewHolder
import dev.kuylar.sakura.ui.adapter.viewholder.OutboxViewHolder
import dev.kuylar.sakura.ui.adapter.viewholder.TimelineViewHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TimelineListAdapter(
	val fragment: Fragment,
	val roomId: RoomId,
	val recycler: RecyclerView,
	val client: Matrix,
	val markdown: MarkdownHandler,
	val loadIndicator: ((Pair<Boolean, Boolean>) -> Unit)? = null
) : ListAdapter<TimelineModel, TimelineViewHolder>(
	AsyncDifferConfig.Builder<TimelineModel>(TimelineModel.ItemCallback()).build()
) {
	private lateinit var timeline: Timeline<EventModel>
	private lateinit var timelineState: TimelineState<EventModel>
	private lateinit var room: Room
	private val layoutInflater = fragment.layoutInflater

	private var outboxModels = ArrayList<OutboxModel>()

	private var getRecentJob: Job? = null
	private var getReceiptJob: Job? = null
	private var getOutboxJob: Job? = null

	private var unreadEventId: EventId? = null
	private var scrollingToEventId: EventId? = null
	var isReady = false
		private set

	var lastEventId: EventId? = null
	var lastEventTimestamp = 0L
	var firstEventId: EventId? = null
	var firstEventTimestamp = Long.MAX_VALUE

	init {
		setHasStableIds(true)
		suspendThread {
			client.getRoom(roomId)?.let { room ->
				this.room = room

				timeline = client.client.room.getTimeline<EventModel>(::handleStateChange) { flow ->
					val snapshot = flow.first()
					if (snapshot.originTimestamp > lastEventTimestamp) {
						lastEventTimestamp = snapshot.originTimestamp
						lastEventId = snapshot.eventId
					}
					if (snapshot.originTimestamp < firstEventTimestamp) {
						firstEventTimestamp = snapshot.originTimestamp
						firstEventId = snapshot.eventId
					}
					EventModel(snapshot.roomId, snapshot.eventId, flow, client, snapshot) {
						updateEventById(snapshot.eventId)
					}
				}
				timeline.init(
					roomId,
					room.lastRelevantEventId ?: room.lastEventId ?: EventId(""),
					configStart,
					configPaged,
					configPaged
				)
				listenToReceipts()
				listenToOutbox()
				isReady = true
				@SuppressLint("NotifyDataSetChanged")
				notifyDataSetChanged()
			}
		}
		recycler.layoutManager =
			LinearLayoutManager(recycler.context, LinearLayoutManager.VERTICAL, true)
		recycler.adapter = this
	}

	override fun getItem(position: Int) =
		if (position !in 0..<itemCount) null else super.getItem(position)

	override fun getItemCount() = if (isReady) super.getItemCount() else 0

	override fun submitList(list: List<TimelineModel?>?) = super.submitList(list?.reversed())

	override fun submitList(list: List<TimelineModel?>?, commitCallback: Runnable?) =
		super.submitList(list?.reversed(), commitCallback)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
		return when (viewType) {
			TYPE_EVENT -> EventViewHolder(
				ItemMessageBinding.inflate(layoutInflater, parent, false),
				client,
				markdown,
				fragment
			)

			TYPE_OUTBOX -> OutboxViewHolder(
				ItemMessageBinding.inflate(layoutInflater, parent, false),
				client,
				markdown,
				fragment
			)

			TYPE_LOADING -> LoadingIconViewHolder(
				ItemLoadingSpinnerBinding.inflate(
					layoutInflater,
					parent,
					false
				)
			)

			TYPE_ERROR -> ErrorViewHolder(LayoutErrorBinding.inflate(layoutInflater, parent, false))

			else -> TimelineViewHolder(
				ItemSpaceListDividerBinding.inflate(
					layoutInflater,
					parent,
					false
				)
			)
		}
	}

	override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
		when (holder) {
			is EventViewHolder -> {
				val item = getItem(position) as? EventModel ?: return
				holder.bind(
					item,
					getItem(position + 1) as? EventModel,
					getItem(position - 1) as? EventModel,
					unreadEventId
				)
			}

			is OutboxViewHolder -> {
				val item = getItem(position) as? OutboxModel ?: return
				holder.bind(item)
			}

			is ErrorViewHolder -> {
				// TODO: currently we don't display errors here
				//holder.bind(ex)
			}
		}
	}

	override fun getItemViewType(position: Int) = when (getItem(position)) {
		is EventModel -> TYPE_EVENT
		is OutboxModel -> TYPE_OUTBOX
		else -> TYPE_LOADING
	}

	override fun getItemId(position: Int) = getItem(position)!!.eventId.hashCode().toLong()

	private fun updateEventById(eventId: EventId) {
		synchronized(currentList) {
			val index = currentList.indexOfFirst { it.eventId == eventId }
			if (index < 0) return
			notifyItemChanged(index)
			if (index > 0) notifyItemChanged(index - 1)
			if (index < itemCount - 1) notifyItemChanged(index + 1)
		}
	}

	private fun startListeningToRecentMessages() {
		getRecentJob?.cancel()
		getRecentJob = suspendThread {
			while (true) {
				timeline.loadAfter {
					this.minSize = 2 /* because minSize = 1 returns the lastEvent */
					this.maxSize = 5
					this.fetchSize = 1
					this.allowReplaceContent = true
					this.fetchTimeout = Duration.INFINITE
				}
			}
		}
	}

	private fun shouldDisplayEvent(event: TimelineEvent): Boolean {
		return (event.relatesTo?.relationType == RelationType.Replace ||
				event.content?.getOrNull() is RedactionEventContent ||
				event.content?.getOrNull() is RedactedEventContent ||
				event.content?.getOrNull() is ReactionEventContent ||
				event.content?.getOrNull() is ShortcodeReactionEventContent).not()
	}

	private fun listenToReceipts() {
		getReceiptJob = suspendThread {
			client.client.user.getReceiptsById(roomId, client.userId)
				.collect { receipt ->
					val lastReceipt =
						receipt?.receipts?.maxBy { r -> r.value.receipt.timestamp }
							?: return@collect
					val oldUnread = unreadEventId
					unreadEventId = lastReceipt.value.eventId
					oldUnread?.let { id -> updateEventById(id) }
					unreadEventId?.let { id -> updateEventById(id) }
				}
		}
	}

	private fun listenToOutbox() {
		getOutboxJob = suspendThread {
			client.client.room.getOutbox(roomId).collect { outbox ->
				val existingModels = outboxModels.associateBy { it.eventId }.toMutableMap()
				val newOutboxModels = outbox.mapNotNull { msg ->
					val snapshot = msg.first() ?: return@mapNotNull null
					if (snapshot.sentAt != null) return@mapNotNull null
					val eventId = snapshot.eventId ?: EventId(snapshot.transactionId)
					return@mapNotNull existingModels.remove(eventId) ?: OutboxModel(
						msg,
						snapshot,
						client
					) { updateEventById(eventId) }
				}.toList()
				existingModels.values.forEach { m -> m.dispose() }
				submitList(
					timelineState.elements.filter { shouldDisplayEvent(it.snapshot) } + newOutboxModels,
					::handlePostSubmitScroll
				)
				synchronized(outboxModels) {
					outboxModels.clear()
					outboxModels.addAll(newOutboxModels)
				}
			}
		}
	}

	private suspend fun handleStateChange(delta: TimelineStateChange<EventModel>) {
		if (delta.addedElements.isEmpty() && delta.removedElements.isEmpty()) return
		timelineState = timeline.state.first()

		val newEventModels = delta.elementsAfterChange
			.filter { shouldDisplayEvent(it.snapshot) }
			.sortedBy { it.snapshot.originTimestamp }

		val toRemoveOutbox = arrayListOf<Int>()
		outboxModels.forEachIndexed { i, it ->
			if (it.snapshot.sentAt != null)
				toRemoveOutbox.add(i)
		}
		toRemoveOutbox.forEach { outboxModels.removeAt(it) }

		delta.removedElements.forEach {
			it.dispose()
		}

		if (!timelineState.canLoadAfter && (getRecentJob == null || getRecentJob?.isCancelled == true)) {
			startListeningToRecentMessages()
		}

		submitList(newEventModels + outboxModels, ::handlePostSubmitScroll)
		Handler(recycler.context.mainLooper).post {
			loadIndicator?.invoke(
				Pair(
					timelineState.isLoadingBefore,
					timelineState.isLoadingAfter && getRecentJob == null
				)
			)
		}
	}

	private fun handlePostSubmitScroll() {
		if (scrollingToEventId != null) {
			val index = currentList.indexOfFirst { it.eventId == scrollingToEventId }
			if (index >= 0) {
				scrollingToEventId = null
				recycler.scrollToPosition(index)
			}
		} else if (recycler.isAtBottom() && getRecentJob != null) {
			recycler.smoothScrollToPosition(0)
		}
	}

	fun scrollToEventId(eventId: EventId) {
		val index = currentList.indexOfFirst { it.eventId == eventId }
		if (index >= 0) {
			recycler.smoothScrollToPosition(index)
		} else {
			submitList(emptyList())
			scrollingToEventId = eventId
			getRecentJob?.cancel()
			getRecentJob = null
			suspendThread {
				loadAroundEvent(eventId)
			}
		}
	}

	fun canLoadMoreBackward() = this::timelineState.isInitialized && timelineState.canLoadBefore

	fun canLoadMoreForward() =
		this::timelineState.isInitialized && timelineState.canLoadAfter && getRecentJob == null

	suspend fun loadMoreBackwards() {
		if (!this::timelineState.isInitialized) return
		if (!timelineState.canLoadBefore) return
		getRecentJob?.cancel()
		getRecentJob = null
		loadIndicator?.invoke(Pair(true, false))
		timeline.loadBefore(configPaged)
	}

	suspend fun loadMoreForwards() {
		if (!this::timelineState.isInitialized) return
		if (!timelineState.canLoadAfter) return
		loadIndicator?.invoke(Pair(false, true))
		timeline.loadAfter(configPaged)
	}

	suspend fun loadAroundEvent(eventId: EventId) {
		if (!this::timelineState.isInitialized) return
		timeline.init(roomId, eventId, {}, {}, {})
	}

	fun dispose() {
		getRecentJob?.cancel()
		getReceiptJob?.cancel()
		getOutboxJob?.cancel()
		synchronized(currentList) {
			currentList.forEach { it.dispose() }
		}
		submitList(emptyList())
	}

	companion object {
		private const val PAGINATION_MAX_SIZE = 20L
		private const val PAGINATION_FETCH_SIZE = 20L

		private const val TYPE_EVENT = 1
		private const val TYPE_OUTBOX = 2
		private const val TYPE_LOADING = 3
		private const val TYPE_ERROR = 4


		private val configStart: GetTimelineEventConfig.() -> Unit = {
			this.allowReplaceContent = false
			this.fetchSize = PAGINATION_FETCH_SIZE
		}
		private val configPaged: GetTimelineEventsConfig.() -> Unit = {
			this.allowReplaceContent = false
			this.maxSize = PAGINATION_MAX_SIZE
			this.fetchSize = PAGINATION_FETCH_SIZE
			this.fetchTimeout = 5.seconds
		}
	}
}

