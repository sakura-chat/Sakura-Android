package dev.kuylar.sakura.ui.adapter.recyclerview

import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
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
import dev.kuylar.sakura.databinding.ItemSpaceListDividerBinding
import dev.kuylar.sakura.ui.fragment.TimelineFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.EventBottomSheetFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getTimelineEventsAround
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.avatarUrl
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import java.util.concurrent.CopyOnWriteArrayList

class TimelineRecyclerAdapter(
	val fragment: Fragment,
	val roomId: String,
	val recycler: RecyclerView
) : RecyclerView.Adapter<TimelineRecyclerAdapter.TimelineViewHolder>() {
	private lateinit var room: Room
	private val client = Matrix.getClient()
	private val layoutInflater = fragment.layoutInflater
	private var eventModels = CopyOnWriteArrayList<EventModel>()
	private var hasOlderMessages = true
	private var hasNewerMessages = false
	private var getRecentJob: Job? = null
	var lastEventId: EventId? = null
	var lastEventTimestamp = 0L
	var firstEventId: EventId? = null
	var firstEventTimestamp = Long.MAX_VALUE
	var isReady: Boolean = false
		private set

	init {
		suspendThread {
			client.getRoom(roomId)?.let {
				room = it
				val first = client.client.room.getLastTimelineEvents(
					RoomId(roomId),
				) {
					this.maxSize = 50
					this.fetchSize = 50
				}.first()?.toList() ?: emptyList()
				first.forEach { event ->
					insertEvent(event)
				}
				startListeningToEvents()
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
			1 -> EventViewHolder(ItemMessageBinding.inflate(layoutInflater, parent, false))
			2 -> LoadingIconViewHolder(
				ItemLoadingSpinnerBinding.inflate(
					layoutInflater,
					parent,
					false
				)
			)

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
		return if (hasNewerMessages && position == 0) 2
		else if (hasOlderMessages && position == eventModels.size) 2
		else 1
	}

	override fun onBindViewHolder(
		holder: TimelineViewHolder,
		position: Int
	) {
		var realPosition = position
		if (hasNewerMessages) realPosition--
		if (holder is EventViewHolder) {
			if (position >= eventModels.size) return
			holder.bind(
				eventModels[realPosition],
				eventModels.getOrNull(realPosition + 1),
				eventModels.getOrNull(realPosition - 1)
			)
		} else if (holder is LoadingIconViewHolder) {
			Log.i("TimelineRecyclerAdapter", "loading holder @ $position")
		}
	}

	override fun getItemCount(): Int {
		var size = eventModels.size
		if (hasOlderMessages) size++
		if (hasNewerMessages) size++
		return size
	}

	private fun updateEventById(eventId: EventId) {
		eventModels
			.indexOfFirst { it.eventId == eventId }
			.takeIf { it >= 0 }
			?.let { index ->
				notifyItemChanged(index)
			}
	}

	private suspend fun insertEvent(
		event: Flow<TimelineEvent>,
		index: Int? = null,
		notify: Boolean = true
	): TimelineEvent {
		val snapshot = event.first()

		if (lastEventTimestamp < snapshot.originTimestamp) {
			lastEventId = snapshot.nextEventId ?: snapshot.eventId
			lastEventTimestamp = snapshot.originTimestamp
		}
		if (firstEventTimestamp > snapshot.originTimestamp) {
			firstEventId = snapshot.previousEventId
			firstEventTimestamp = snapshot.originTimestamp
		}

		if (snapshot.relatesTo?.relationType == RelationType.Replace) return snapshot
		if (snapshot.content?.getOrNull() is RedactionEventContent ||
			snapshot.content?.getOrNull() is RedactedEventContent ||
			snapshot.content?.getOrNull() is ReactionEventContent ||
			snapshot.content?.getOrNull() is ShortcodeReactionEventContent
		) return snapshot
		if (eventModels.any { snapshot.eventId == it.eventId }) return snapshot
		fragment.activity?.runOnUiThread {
			if (index == null) {
				eventModels.add(EventModel(snapshot.roomId, snapshot.eventId, event, snapshot) {
					updateEventById(snapshot.eventId)
				})
				if (notify)
					notifyItemInserted(eventModels.size - 1)
			} else {
				eventModels.add(
					index,
					EventModel(snapshot.roomId, snapshot.eventId, event, snapshot) {
						updateEventById(snapshot.eventId)
					})
				if (notify)
					notifyItemInserted(index)
			}
		}
		return snapshot
	}

	suspend fun loadMoreBackwards() {
		if (!this::room.isInitialized) return
		if (!hasOlderMessages) return
		val events = client.client.room.getTimelineEvents(
			room.roomId,
			firstEventId ?: eventModels.lastOrNull()?.eventId ?: EventId(""),
			GetEvents.Direction.BACKWARDS
		) {
			this.maxSize = 50
			this.fetchSize = 50
		}.toList()
		Log.i("TimelineRecyclerAdapter", "backwards: ${events.size}")
		if (events.isEmpty()) {
			hasOlderMessages = false
			Handler(fragment.requireContext().mainLooper!!).post {
				notifyItemRemoved(eventModels.size)
			}
		} else events.forEach {
			val e = insertEvent(it)
			if (e.previousEventId == null) {
				hasOlderMessages = false
				Handler(fragment.requireContext().mainLooper!!).post {
					notifyItemRemoved(eventModels.size)
				}
			}
		}
	}

	fun canLoadMoreForward(): Boolean {
		return hasNewerMessages && getRecentJob == null
	}

	suspend fun loadMoreForwards() {
		if (!this::room.isInitialized) return
		if (!hasNewerMessages) return
		// If getRecentJob isn't null, that means that we're constantly
		// loading the most recent message, and this method shouldn't
		// be called
		if (getRecentJob != null) return
		Log.i(
			"TimelineRecyclerAdapter",
			"Loading more messages from ${lastEventId?.full} OR ${eventModels.firstOrNull()?.eventId?.full}"
		)
		val events = client.client.room.getTimelineEvents(
			room.roomId,
			eventModels.firstOrNull()?.eventId ?: EventId(""),
			GetEvents.Direction.FORWARDS
		) {
			this.minSize = 0
			this.maxSize = 50
			this.fetchSize = 50
		}.toList()
		events.forEach {
			val snapshot = insertEvent(it, 0)
			if (snapshot.nextEventId == null) {
				startListeningToEvents()
			}
		}
	}

	suspend fun loadAroundEvent(eventId: EventId) {
		if (!this::room.isInitialized) return
		var itemCount = eventModels.size
		if (hasOlderMessages) itemCount++
		if (hasNewerMessages) itemCount++
		hasOlderMessages = false
		hasNewerMessages = false
		lastEventId = null
		firstEventId = null
		lastEventTimestamp = 0
		firstEventTimestamp = Long.MAX_VALUE
		Handler(fragment.requireContext().mainLooper!!).post {
			notifyItemRangeRemoved(0, itemCount)
			eventModels.clear()
		}
		val events = client.client.room.getTimelineEventsAround(
			room.roomId,
			eventId
		) {
			this.maxSize = 50
			this.fetchSize = 50
		}.toList()
		events.forEach {
			insertEvent(it, index = 0, notify = false)
		}
		hasOlderMessages = true
		hasNewerMessages = true
		Handler(fragment.requireContext().mainLooper!!).post {
			notifyDataSetChanged()
		}
	}

	fun startListeningToEvents() {
		if (hasNewerMessages) {
			hasNewerMessages = false
			Handler(fragment.requireContext().mainLooper!!).post {
				notifyItemRemoved(0)
			}
		}
		getRecentJob?.cancel()
		getRecentJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.room.getTimelineEvents(
				RoomId(roomId),
				lastEventId ?: EventId(""),
				GetEvents.Direction.FORWARDS
			).collect { newEvent ->
				insertEvent(newEvent, 0)
			}
		}
	}

	fun scrollToEventId(eventId: EventId) {
		val index = eventModels.indexOfFirst { it.eventId == eventId }
		if (index >= 0) {
			recycler.smoothScrollToPosition(index)
		} else {
			notifyItemRangeRemoved(if (hasOlderMessages) 1 else 0, eventModels.size)
			getRecentJob?.cancel()
			getRecentJob = null
			suspendThread {
				loadAroundEvent(eventId)
			}
		}
	}

	open class TimelineViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)
	open class EventViewHolder(val binding: ItemMessageBinding) : TimelineViewHolder(binding) {
		private val client = Matrix.getClient()
		private val layoutInflater =
			binding.root.context.getSystemService<LayoutInflater>() as LayoutInflater

		fun bind(
			eventModel: EventModel,
			lastEventModel: EventModel? = null,
			nextEventModel: EventModel? = null
		) {
			val event = eventModel.snapshot ?: return
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
			resetBindingState()

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
				binding.root.setOnClickListener {
					val now = System.currentTimeMillis()
					if (now - lastClick < ViewConfiguration.getDoubleTapTimeout()) {
						(adapter.fragment as? TimelineFragment)?.handleReply(event.eventId)
					}
					lastClick = now
				}
			}
			if (lastEvent?.sender == event.sender && lastEvent.originTimestamp - event.originTimestamp < 5 * 60 * 1000) {
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
			val content = event.content?.getOrNull() ?: return
			repliedEvent?.let {
				handleReply(it)
			}
			when (content) {
				is RoomMessageEventContent.TextBased.Text -> {
					if (content.formattedBody != null) {
						// Extremely hacky way!! No one likes this!!!
						// Make this better!!!!!
						val split =
							content.formattedBody?.split("</mx-reply>", limit = 2)
								?: emptyList()
						binding.body.text = Html.fromHtml(
							split.last(),
							Html.FROM_HTML_MODE_COMPACT
						)
					} else {
						binding.body.text = content.body
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
					binding.attachment.visibility = View.VISIBLE
					binding.attachment.addView(attachmentBinding.root)
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
					val reactionBinding =
						ItemReactionBinding.inflate(layoutInflater, binding.reactions, false)
					val shortcode = list
						.mapNotNull { it.content?.getOrNull() as? ShortcodeReactionEventContent }
						.groupBy { (it.shortcode ?: it.beeperShortcode)?.trim(':') }
						.entries.maxByOrNull { it.value.size }?.key
					reactionBinding.root.text = "${shortcode ?: key} ${list.size}"
					reactionBinding.root.isSelected = weReacted != null
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
			binding.senderName.text = ""
			binding.body.text = ""
			binding.eventTimestamp.text = ""
		}
	}

	class LoadingIconViewHolder(val binding: ItemLoadingSpinnerBinding) :
		TimelineViewHolder(binding)
}