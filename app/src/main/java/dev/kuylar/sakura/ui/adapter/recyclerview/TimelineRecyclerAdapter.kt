package dev.kuylar.sakura.ui.adapter.recyclerview

import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.Timeline
import de.connect2x.trixnity.client.room.TimelineState
import de.connect2x.trixnity.client.room.TimelineStateChange
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.relatesTo
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toTimestamp
import dev.kuylar.sakura.Utils.toTimestampDate
import dev.kuylar.sakura.Utils.withinSameDay
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.ShortcodeReactionEventContent
import dev.kuylar.sakura.databinding.AttachmentImageBinding
import dev.kuylar.sakura.databinding.ItemLoadingSpinnerBinding
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.databinding.ItemReactionBinding
import dev.kuylar.sakura.databinding.ItemReactionSelectedBinding
import dev.kuylar.sakura.databinding.ItemSpaceListDividerBinding
import dev.kuylar.sakura.databinding.LayoutErrorBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.fragment.TimelineFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.EventBottomSheetFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.ProfileBottomSheetFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration

class TimelineRecyclerAdapter(
	val fragment: Fragment,
	val roomId: String,
	val recycler: RecyclerView,
	val client: Matrix,
	val markdown: MarkdownHandler,
	val loadIndicator: ((Pair<Boolean, Boolean>) -> Unit)? = null
) : RecyclerView.Adapter<TimelineRecyclerAdapter.TimelineViewHolder>() {
	private lateinit var timeline: Timeline<EventModel>
	private lateinit var timelineState: TimelineState<EventModel>
	private lateinit var room: Room
	private val layoutInflater = fragment.layoutInflater
	private var eventModels = CopyOnWriteArrayList<EventModel>()
	private var getRecentJob: Job? = null
	private var getReceiptJob: Job? = null
	private var unreadEventId: EventId? = null
	private var ex: Throwable? = null
	var lastEventId: EventId? = null
	var lastEventTimestamp = 0L
	var firstEventId: EventId? = null
	var firstEventTimestamp = Long.MAX_VALUE
	var isReady: Boolean = false
		private set

	init {
		setHasStableIds(true)
		suspendThread {
			client.getRoom(roomId)?.let {
				room = it
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
				val change = timeline.init(
					RoomId(roomId),
					room.lastRelevantEventId ?: room.lastEventId ?: EventId(""),
					{},
					{},
					{})
				getReceiptJob = suspendThread {
					client.client.user.getReceiptsById(RoomId(roomId), client.userId)
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
				isReady = true
			}
		}
		recycler.adapter = this
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	): TimelineViewHolder {
		return when (viewType) {
			1 -> EventViewHolder(ItemMessageBinding.inflate(layoutInflater, parent, false), client)
			2 -> LoadingIconViewHolder(
				ItemLoadingSpinnerBinding.inflate(
					layoutInflater,
					parent,
					false
				)
			)

			3 -> ErrorViewHolder(LayoutErrorBinding.inflate(layoutInflater, parent, false))

			else -> TimelineViewHolder(
				ItemSpaceListDividerBinding.inflate(
					layoutInflater,
					parent,
					false
				)
			)
		}
	}

	override fun getItemViewType(position: Int): Int {
		return if (!this::timelineState.isInitialized || !timelineState.isInitialized) 2
		else if (ex != null && position == 0) 3
		else 1
	}

	override fun onBindViewHolder(
		holder: TimelineViewHolder,
		position: Int
	) {
		if (holder is EventViewHolder) {
			if (position >= eventModels.size) return
			holder.bind(
				eventModels[position],
				eventModels.getOrNull(position + 1),
				eventModels.getOrNull(position - 1),
				markdown,
				unreadEventId
			)
		} else if (holder is ErrorViewHolder) {
			holder.bind(ex)
		}
	}

	override fun getItemCount(): Int {
		if (ex != null) return 1
		return eventModels.size
	}

	override fun getItemId(position: Int): Long {
		return eventModels.getOrNull(position)?.eventId?.hashCode()?.toLong() ?: 0L
	}

	private fun updateEventById(eventId: EventId) {
		eventModels
			.indexOfFirst { it.eventId == eventId }
			.takeIf { it >= 0 }
			?.let { index ->
				notifyItemChanged(index)
				if (index > 0) notifyItemChanged(index - 1)
				if (index < eventModels.size - 1) notifyItemChanged(index + 1)
			}
	}

	private fun shouldDisplayEvent(event: TimelineEvent): Boolean {
		return (event.relatesTo?.relationType == RelationType.Replace ||
				event.content?.getOrNull() is RedactionEventContent ||
				event.content?.getOrNull() is RedactedEventContent ||
				event.content?.getOrNull() is ReactionEventContent ||
				event.content?.getOrNull() is ShortcodeReactionEventContent).not()
	}

	fun canLoadMoreBackward(): Boolean {
		return this::timelineState.isInitialized && timelineState.canLoadBefore
	}

	fun canLoadMoreForward(): Boolean {
		return this::timelineState.isInitialized && timelineState.canLoadAfter && getRecentJob == null
	}

	suspend fun loadMoreBackwards() {
		if (!this::room.isInitialized || !this::timelineState.isInitialized) return
		if (!timelineState.canLoadBefore) return
		loadIndicator?.invoke(Pair(true, false))
		timeline.loadBefore {
			this.minSize = 0
			this.maxSize = PAGINATION_MAX_SIZE
			this.fetchSize = PAGINATION_FETCH_SIZE
		}
	}

	suspend fun loadMoreForwards() {
		if (!this::room.isInitialized || !this::timelineState.isInitialized) return
		if (!timelineState.canLoadAfter) return
		loadIndicator?.invoke(Pair(false, true))
		timeline.loadAfter {
			this.minSize = 0
			this.maxSize = PAGINATION_MAX_SIZE
			this.fetchSize = PAGINATION_FETCH_SIZE
		}
	}

	suspend fun loadAroundEvent(eventId: EventId) {
		if (!this::room.isInitialized || !this::timelineState.isInitialized) return
		timeline.init(RoomId(roomId), eventId, {}, {}, {})
	}

	fun scrollToEventId(eventId: EventId) {
		val index = eventModels.indexOfFirst { it.eventId == eventId }
		if (index >= 0) {
			recycler.smoothScrollToPosition(index)
		} else {
			notifyItemRangeRemoved(0, eventModels.size)
			getRecentJob?.cancel()
			getRecentJob = null
			suspendThread {
				loadAroundEvent(eventId)
			}
		}
	}

	fun dispose() {
		eventModels.forEach {
			it.dispose()
		}
		eventModels.clear()
		getRecentJob?.cancel()
		getReceiptJob?.cancel()
	}

	private fun handleError(e: Throwable) {
		if (e is CancellationException) {
			return
		}
		eventModels.clear()
		Log.e("TimelineRecyclerAdapter", "An error has occurred", e)
		ex = e
		fragment.context?.let {
			Handler(it.mainLooper).post {
				notifyItemRangeRemoved(0, itemCount)
				notifyItemInserted(0)
			}
		}
	}

	suspend fun handleStateChange(delta: TimelineStateChange<EventModel>) {
		if (delta.addedElements.isEmpty() && delta.removedElements.isEmpty()) return
		timelineState = timeline.state.first()

		// We use a reversed LinearLayoutManager
		val newEventModels = delta.elementsAfterChange
			.filter { shouldDisplayEvent(it.snapshot) }
			.sortedByDescending { it.snapshot.originTimestamp }

		// Dispose all unused EventModels
		delta.removedElements.forEach {
			it.dispose()
		}

		// Calculate diff
		val diffCallback = TimelineDiffCallback(eventModels.toList(), newEventModels)
		val diffResult = DiffUtil.calculateDiff(diffCallback)

		if (!timelineState.canLoadAfter && getRecentJob == null) {
			startListeningToRecentMessages()
		}

		Handler(recycler.context.mainLooper).post {
			eventModels.clear()
			eventModels.addAll(newEventModels)
			loadIndicator?.invoke(
				Pair(
					timelineState.isLoadingBefore,
					timelineState.isLoadingAfter && getRecentJob == null
				)
			)
			diffResult.dispatchUpdatesTo(this@TimelineRecyclerAdapter)
		}
	}

	private fun startListeningToRecentMessages() {
		Handler(recycler.context.mainLooper).post {
			Toast.makeText(recycler.context, "startListeningToRecentMessages", Toast.LENGTH_LONG)
				.show()
		}
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

	open class TimelineViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
	open class EventViewHolder(val binding: ItemMessageBinding, val client: Matrix) :
		TimelineViewHolder(binding) {
		private val layoutInflater =
			binding.root.context.getSystemService<LayoutInflater>() as LayoutInflater
		private var lastEventId: EventId? = null

		fun bind(
			eventModel: EventModel,
			lastEventModel: EventModel?,
			nextEventModel: EventModel?,
			markdown: MarkdownHandler,
			unreadEventId: EventId?
		) {
			val event = eventModel.snapshot
			val lastEvent = lastEventModel?.snapshot
			val nextEvent = nextEventModel?.snapshot
			val repliedEvent = eventModel.repliedSnapshot
			var lastClick = 0L

			suspendThread {
				val user = client.getUser(event.sender, event.roomId)
				Handler(binding.root.context.mainLooper).post {
					user?.let {
						binding.senderName.text = it.name
						Glide.with(binding.root)
							.load(it.avatarUrl)
							.into(binding.avatar)
					}
				}
			}
			if (eventModel.eventId != lastEventId) {
				resetBindingState()
			}
			binding.unreadSeparator.visibility =
				if (event.eventId == unreadEventId && nextEvent != null) View.VISIBLE else View.GONE

			(bindingAdapter as? TimelineRecyclerAdapter)?.let { adapter ->
				binding.root.setOnLongClickListener {
					val f = EventBottomSheetFragment()
					f.arguments = bundleOf(
						"eventId" to event.eventId.full,
						"roomId" to event.roomId.full,
					)
					f.show(adapter.fragment.parentFragmentManager, "eventBottomSheet")
					true
				}
				binding.avatar.setOnLongClickListener {
					val f = ProfileBottomSheetFragment()
					f.arguments = bundleOf(
						"userId" to event.sender.full,
						"roomId" to event.roomId.full,
					)
					f.show(adapter.fragment.parentFragmentManager, "eventBottomSheet")
					true
				}
				binding.root.setOnClickListener {
					val now = System.currentTimeMillis()
					if (now - lastClick < ViewConfiguration.getDoubleTapTimeout()) {
						(adapter.fragment as? TimelineFragment)?.handleReply(event.eventId)
					}
					@Suppress("AssignedValueIsNeverRead")
					lastClick = now
				}
			}
			if (lastEvent?.sender == event.sender && event.originTimestamp - lastEvent.originTimestamp < 5 * 60 * 1000) {
				binding.avatar.visibility = View.GONE
				binding.messageInfo.visibility = View.GONE
			}
			if (!event.originTimestamp.withinSameDay(lastEvent?.originTimestamp ?: 0)) {
				binding.dateSeparator.visibility = View.VISIBLE
				binding.dateSeparatorText.text =
					event.originTimestamp.toTimestampDate(binding.root.context)
				binding.avatar.visibility = View.VISIBLE
				binding.messageInfo.visibility = View.VISIBLE
			}
			binding.eventTimestamp.text =
				event.originTimestamp.toTimestamp(binding.eventTimestamp.context)
			if (eventModel.replaces?.history?.isNotEmpty() == true)
				binding.edited.visibility = View.VISIBLE
			if (eventModel.reactions?.reactions?.isNotEmpty() == true) {
				binding.reactions.visibility = View.VISIBLE
				handleReactions(event, eventModel.reactions!!.reactions)
			} else
				binding.reactions.visibility = View.GONE
			val content = event.content?.getOrNull()
			repliedEvent?.let {
				handleReply(it)
			}
			when (content) {
				is RoomMessageEventContent.TextBased.Text -> {
					if (content.formattedBody != null) {
						markdown.setTextView(binding.body, content.formattedBodyWithoutFallback)
					} else {
						binding.body.text = content.bodyWithoutFallback
					}
				}

				is RoomMessageEventContent.FileBased.Image -> {
					if (content.fileName != null && content.body != content.fileName) {
						binding.body.text = content.body
					} else {
						binding.body.visibility = View.GONE
					}

					val attachmentBinding = AttachmentImageBinding.inflate(
						layoutInflater,
						binding.attachment,
						false
					)
					val displayMetrics = attachmentBinding.root.context.resources.displayMetrics
					val maxWidth = minOf(
						displayMetrics.widthPixels * 0.7f,
						400f * displayMetrics.density
					).toInt()
					val maxHeight = minOf(
						displayMetrics.heightPixels * 0.5f,
						300f * displayMetrics.density
					).toInt()

					Glide.with(attachmentBinding.root)
						.load(content.url)
						.listener(object : RequestListener<Drawable> {
							override fun onLoadFailed(
								e: GlideException?,
								model: Any?,
								target: Target<Drawable?>,
								isFirstResource: Boolean
							) = false

							override fun onResourceReady(
								resource: Drawable,
								model: Any,
								target: Target<Drawable?>?,
								dataSource: DataSource,
								isFirstResource: Boolean
							): Boolean {
								val imageWidth = resource.intrinsicWidth
								val imageHeight = resource.intrinsicHeight

								val widthRatio = maxWidth.toFloat() / imageWidth
								val heightRatio = maxHeight.toFloat() / imageHeight
								val ratio = minOf(widthRatio, heightRatio, 1f)

								val newWidth = (imageWidth * ratio).toInt()
								val newHeight = (imageHeight * ratio).toInt()

								val params = attachmentBinding.root.layoutParams
								params.width = newWidth
								params.height = newHeight
								attachmentBinding.root.layoutParams = params
								return false
							}
						})
						.into(attachmentBinding.imageAttachment)
					binding.attachment.removeAllViews()
					binding.attachment.visibility = View.VISIBLE
					binding.attachment.addView(attachmentBinding.root)
				}

				null -> {
					binding.body.setText(R.string.event_failed_to_decrypt)
				}

				else -> {
					binding.body.text = Html.fromHtml(
						"${
							content.javaClass.name.split("core.model.events.").last()
						}\n<code>${event.eventId.full}</code>",
						Html.FROM_HTML_MODE_COMPACT
					)
				}
			}
		}

		private fun handleReactions(
			event: TimelineEvent,
			reactions: Map<String, Set<TimelineEvent>>
		) {
			if (binding.reactions.childCount > 1)
				binding.reactions.removeViews(0, binding.reactions.childCount - 1)
			reactions.entries
				.sortedBy { -it.value.minBy { e -> e.originTimestamp }.originTimestamp }
				.forEach { (key, list) ->
					val weReacted = list.firstOrNull { it.sender == client.userId }
					val reactionView = if (weReacted != null) {
						ItemReactionSelectedBinding.inflate(
							layoutInflater,
							binding.reactions,
							false
						)
					} else {
						ItemReactionBinding.inflate(layoutInflater, binding.reactions, false)
					}
					val reactionBinding =
						ItemReactionBinding.bind(reactionView.root)
					val shortcode = list
						.mapNotNull { it.content?.getOrNull() as? ShortcodeReactionEventContent }
						.groupBy { (it.shortcode ?: it.beeperShortcode)?.trim(':') }
						.entries.maxByOrNull { it.value.size }?.key
					if (key.startsWith("mxc://")) {
						reactionBinding.emojiImage.visibility = View.VISIBLE
						reactionBinding.emojiUnicode.visibility = View.GONE
						Glide.with(binding.root)
							.load(key)
							.into(reactionBinding.emojiImage)
					} else {
						reactionBinding.emojiImage.visibility = View.GONE
						reactionBinding.emojiUnicode.visibility = View.VISIBLE
						reactionBinding.emojiUnicode.text = key
					}
					reactionBinding.counter.text = list.size.toString()
					if (weReacted == null) {
						reactionBinding.root.setOnClickListener {
							reactionBinding.root.setOnClickListener(null)
							suspendThread {
								client.reactToEvent(event.roomId, event.eventId, key, shortcode)
							}
						}
					} else {
						reactionBinding.root.setOnClickListener {
							reactionBinding.root.setOnClickListener(null)
							suspendThread {
								client.redactEvent(weReacted.roomId, weReacted.eventId)
							}
						}
					}

					binding.reactions.addView(reactionBinding.root, 0)
				}
		}

		private fun handleReply(event: TimelineEvent) {
			binding.avatar.visibility = View.VISIBLE
			binding.messageInfo.visibility = View.VISIBLE

			suspendThread {
				val user = client.getUser(event.sender, event.roomId)
				Handler(binding.root.context.mainLooper).post {
					user?.let {
						binding.replyingName.text = it.name
						Glide.with(binding.root)
							.load(it.avatarUrl)
							.into(binding.replyingAvatar)
					}
				}
			}
			binding.replyingEvent.visibility = View.VISIBLE
			binding.replyingEvent.setOnClickListener {
				(bindingAdapter as? TimelineRecyclerAdapter)?.scrollToEventId(event.eventId)
			}
			binding.replyingBody.setText(R.string.empty_message)
			val content =
				event.content?.getOrNull() as? RoomMessageEventContent ?: return

			when (content) {
				is RoomMessageEventContent.TextBased.Text -> {
					if (content.formattedBody != null) {
						// Extremely hacky way!! No one likes this!!!
						// Make this better!!!!!
						val split =
							content.formattedBody?.split("</mx-reply>", limit = 2)
								?: emptyList()
						binding.replyingBody.text = Html.fromHtml(
							split.last(),
							Html.FROM_HTML_MODE_COMPACT
						)
					} else {
						binding.replyingBody.text = content.body
					}
				}

				is RoomMessageEventContent.FileBased.Image -> {
					binding.replyingBody.text = content.body
				}

				else -> {
					binding.replyingBody.text = content.javaClass.name
				}
			}
		}

		private fun resetBindingState() {
			binding.dateSeparator.visibility = View.GONE
			binding.replyingEvent.visibility = View.GONE
			binding.senderBadge.visibility = View.GONE
			binding.avatar.visibility = View.VISIBLE
			binding.messageInfo.visibility = View.VISIBLE
			binding.edited.visibility = View.GONE
			binding.body.visibility = View.VISIBLE
			binding.embeds.removeAllViews()
			binding.attachment.removeAllViews()
			binding.attachment.visibility = View.GONE
			if (binding.reactions.childCount > 1)
				binding.reactions.removeViews(0, binding.reactions.childCount - 1)
			binding.avatar.setImageDrawable(null)
			binding.senderName.text = ""
			binding.body.text = ""
			binding.eventTimestamp.text = ""
		}
	}

	class LoadingIconViewHolder(val binding: ItemLoadingSpinnerBinding) :
		TimelineViewHolder(binding)

	class ErrorViewHolder(val binding: LayoutErrorBinding) : TimelineViewHolder(binding) {
		fun bind(ex: Throwable?) {
			if (ex != null) {
				val sb = StringBuilder()
				sb.appendLine(ex.javaClass.name)
					.appendLine("=".repeat(ex.javaClass.name.length))
					.appendLine(ex.message)
					.appendLine()
					.appendLine("Stack trace:")
					.appendLine(ex.stackTraceToString())
				binding.details.text = sb.toString()
			} else {
				binding.details.setText(R.string.error_no_details)
			}
		}
	}

	companion object {
		private const val PAGINATION_MAX_SIZE = 20L
		private const val PAGINATION_FETCH_SIZE = 20L
	}
}