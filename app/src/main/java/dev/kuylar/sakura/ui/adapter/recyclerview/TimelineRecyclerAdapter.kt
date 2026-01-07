package dev.kuylar.sakura.ui.adapter.recyclerview

import android.os.Handler
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toTimestamp
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.AttachmentImageBinding
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.databinding.ItemReactionBinding
import dev.kuylar.sakura.databinding.ItemSpaceListDividerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.avatarUrl
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.isReplaced
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.client.user
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

class TimelineRecyclerAdapter(val fragment: Fragment, val roomId: String) :
	RecyclerView.Adapter<TimelineRecyclerAdapter.TimelineViewHolder>() {
	private val client = Matrix.getClient()
	private val layoutInflater = fragment.layoutInflater
	private lateinit var room: Room
	private var eventModels = mutableListOf<EventModel>()

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
				var lastEventId: EventId? = null
				var lastEventTimestamp = 0L
				first.forEach { event ->
					val e = event.first()
					if (e.relatesTo?.relationType == RelationType.Replace) return@forEach
					if (e.content?.getOrNull() is RedactionEventContent ||
						e.content?.getOrNull() is RedactedEventContent ||
						e.content?.getOrNull() is ReactionEventContent
					) return@forEach

					if (lastEventTimestamp < e.originTimestamp) {
						lastEventId = e.eventId
						lastEventTimestamp = e.originTimestamp
					}
					fragment.activity?.runOnUiThread {
						eventModels.add(EventModel(e.roomId, e.eventId, event, e) {
							updateEventById(e.eventId)
						})
						notifyItemInserted(eventModels.size - 1)
					}
				}
				client.client.room.getTimelineEvents(
					RoomId(roomId),
					lastEventId ?: EventId(""),
					GetEvents.Direction.FORWARDS
				).collect { newEvent ->
					val e = newEvent.first()
					if (e.eventId == lastEventId) return@collect
					if (e.relatesTo?.relationType == RelationType.Replace) return@collect
					if (e.content?.getOrNull() is RedactionEventContent ||
						e.content?.getOrNull() is RedactedEventContent ||
						e.content?.getOrNull() is ReactionEventContent
					) return@collect

					fragment.activity?.runOnUiThread {
						eventModels.add(0, EventModel(e.roomId, e.eventId, newEvent, e) {
							updateEventById(e.eventId)
						})
						notifyItemInserted(0)
					}
				}
			}
		}
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	): TimelineViewHolder {
		return when (viewType) {
			1 -> EventViewHolder(ItemMessageBinding.inflate(layoutInflater, parent, false))
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
		return 1
	}

	override fun onBindViewHolder(
		holder: TimelineViewHolder,
		position: Int
	) {
		if (holder is EventViewHolder) {
			holder.bind(
				eventModels[position],
				eventModels.getOrNull(position + 1),
				eventModels.getOrNull(position - 1)
			)
		}
	}

	override fun getItemCount() = eventModels.size

	private fun updateEventById(eventId: EventId) {
		eventModels
			.indexOfFirst { it.eventId == eventId }
			.takeIf { it >= 0 }
			?.let { index ->
				notifyItemChanged(index)
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

			if (lastEvent?.sender == event.sender && lastEvent.originTimestamp - event.originTimestamp < 5 * 60 * 1000) {
				binding.avatar.visibility = View.GONE
				binding.messageInfo.visibility = View.GONE
			}
			binding.eventTimestamp.text =
				event.originTimestamp.toTimestamp(binding.eventTimestamp.context)
			if (eventModel.replaces?.history?.isNotEmpty() == true)
				binding.edited.visibility = View.VISIBLE
			if (eventModel.reactions?.reactions?.isNotEmpty() == true) {
				binding.reactions.visibility = View.VISIBLE
				handleReactions(eventModel.reactions!!.reactions)
			} else
				binding.reactions.visibility = View.GONE
			val content =
				event.content?.getOrNull() as? RoomMessageEventContent ?: return
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
					binding.attachment.addView(attachmentBinding.root)
					Glide.with(attachmentBinding.root)
						.load(content.url)
						.into(attachmentBinding.imageAttachment)
				}

				else -> {
					binding.body.text = content.javaClass.name
				}
			}
		}

		private fun handleReactions(reactions: Map<String, Set<TimelineEvent>>) {
			if (binding.reactions.childCount > 1)
				binding.reactions.removeViews(0, binding.reactions.childCount - 1)
			reactions.entries
				.sortedBy { it.value.minBy { e -> e.originTimestamp }.originTimestamp }
				.forEach { (key, list) ->
					val weReacted = list.any { it.sender == client.client.userId }
					val reactionBinding =
						ItemReactionBinding.inflate(layoutInflater, binding.reactions, false)
					reactionBinding.root.text = key + " " + list.size
					reactionBinding.root.isSelected = weReacted
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
			binding.replyingEvent.visibility = View.GONE
			binding.senderBadge.visibility = View.GONE
			binding.avatar.visibility = View.VISIBLE
			binding.messageInfo.visibility = View.VISIBLE
			binding.edited.visibility = View.GONE
			binding.body.visibility = View.VISIBLE
			binding.embeds.removeAllViews()
			binding.attachment.removeAllViews()
			if (binding.reactions.childCount > 1)
				binding.reactions.removeViews(0, binding.reactions.childCount - 1)
			binding.senderName.text = ""
			binding.body.text = ""
			binding.eventTimestamp.text = ""
		}
	}
}