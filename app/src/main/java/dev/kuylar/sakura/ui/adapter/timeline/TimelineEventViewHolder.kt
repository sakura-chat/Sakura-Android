package dev.kuylar.sakura.ui.adapter.timeline

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.connect2x.trixnity.client.room.TimelineEventAggregation
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import dev.kuylar.sakura.BuildConfig
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.content
import dev.kuylar.sakura.Utils.lastReceipt
import dev.kuylar.sakura.Utils.loadUser
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toTimestamp
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.ShortcodeReactionEventContent
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.databinding.ItemReactionBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.fragment.TimelineFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.EventBottomSheetFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.ReactionBottomSheetFragment
import io.getstream.avatarview.glide.loadImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class TimelineEventViewHolder(
	val binding: ItemMessageBinding,
	val client: Matrix,
	private val markdown: MarkdownHandler
) : RecyclerView.ViewHolder(binding.root) {
	private var nonce = 0L
	private var collectionJob: Job? = null
	private val adapter: TimelineRecyclerAdapter?
		get() = bindingAdapter as? TimelineRecyclerAdapter
	private val fragment: TimelineFragment?
		get() = adapter?.fragment as? TimelineFragment
	private var lastClick = 0L

	fun bind(item: TimelineItem, prevItem: TimelineItem?) {
		collectionJob?.cancel()
		collectionJob = null
		nonce = Random.nextLong()
		val currentNonce = nonce
		resetBindingState()
		setData(currentNonce, item, prevItem)
		collectionJob = fragment?.lifecycleScope?.launch {
			when (item) {
				is TimelineItem.Event -> {
					item.flow.collect { snapshot ->
						item.update(snapshot)
						setData(currentNonce, item, prevItem)
					}
				}

				else -> {}
			}
		}
		binding.body.movementMethod = LinkMovementMethod.getInstance()
	}

	fun detached() {
		collectionJob?.cancel()
		collectionJob = null
	}

	private fun setData(currentNonce: Long, item: TimelineItem, prevItem: TimelineItem?) {
		binding.senderName.text = item.senderId.full
		binding.eventTimestamp.text = item.timestamp.toTimestamp(binding.eventTimestamp.context)
		val eventId = (item as? TimelineItem.Event)?.event?.eventId ?: EventId(item.id)
		adapter?.selfReceipts?.let {
			binding.unreadSeparator.visibility =
				if (it.lastReceipt == eventId) View.VISIBLE else View.GONE
		}
		setAvatarVisibility(item, prevItem)
		item.content?.let { setContent(currentNonce, it) }
		item.user?.let { setUser(it) }
		if (item is TimelineItem.Event) {
			item.repliedToEvent?.let { setReply(currentNonce, it) }
			setReactions(item.event, item.reactions)

			listOf(binding.root, binding.body, binding.attachment).forEach {
				fragment?.let { frag ->
					it.setOnClickListener {
						val now = System.currentTimeMillis()
						if (now - lastClick < ViewConfiguration.getDoubleTapTimeout()) {
							frag.handleReply(item.event.eventId)
						}
						lastClick = now
					}
					it.setOnLongClickListener {
						val f = EventBottomSheetFragment()
						f.arguments = bundleOf(
							"eventId" to item.event.eventId.full,
							"roomId" to item.event.roomId.full,
						)
						f.show(frag.parentFragmentManager, "eventBottomSheet")
						true
					}
				}
			}
		}
	}

	private fun setAvatarVisibility(item: TimelineItem, prevItem: TimelineItem?) {
		(if (!(item.senderId == prevItem?.senderId && prevItem.timestamp - item.timestamp < 5.minutes.inWholeMilliseconds) || when (item) {
				is TimelineItem.Event -> {
					item.repliedToEvent != null
				}

				is TimelineItem.OutboxItem -> {
					item.snapshot.content.relatesTo?.replyTo != null
				}
			}
		) View.VISIBLE else View.GONE).let {
			binding.avatar.visibility = it
			binding.messageInfo.visibility = it
		}
	}

	private fun setContent(currentNonce: Long, content: RoomEventContent) {
		when (content) {
			is RoomMessageEventContent.TextBased -> {
				markdown.setTextView(
					binding.body,
					content.content,
					false
				) { updateSpans(currentNonce) }
			}

			else -> {
				markdown.setTextView(
					binding.body,
					"<code>${content.javaClass.name}</code>",
					false
				) { updateSpans(currentNonce) }
			}
		}
		// because BuildConfig.DEBUG is not always true
		@Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
		if (BuildConfig.DEBUG && binding.body.text.isEmpty()) {
			binding.body.text = content.javaClass.name
		}
	}

	private fun updateSpans(currentNonce: Long) {
		if (currentNonce == nonce)
			binding.body.text = binding.body.text
	}

	private fun setUser(user: RoomUser) {
		binding.avatar.loadUser(user)
		binding.senderName.text = user.name
	}

	private fun setReply(currentNonce: Long, reply: TimelineItem.Event.Snapshot.Reply) {
		binding.replyingEvent.visibility = View.VISIBLE
		Glide.with(binding.root).load(reply.user.avatarUrl).into(binding.replyingAvatar)
		binding.replyingName.text = reply.user.name
		when (val content = reply.event.content?.getOrNull() as? MessageEventContent) {
			is RoomMessageEventContent.TextBased -> {
				markdown.setTextView(binding.replyingBody, content.content, false) {
					if (currentNonce == nonce)
						binding.replyingBody.text = binding.replyingBody.text
				}
			}

			is RoomMessageEventContent.FileBased -> {
				binding.replyingBody.setText(R.string.message_attachment)
			}
		}
	}

	private fun setReactions(event: TimelineEvent, reactions: TimelineEventAggregation.Reaction) {
		while (binding.reactions.childCount > 1) binding.reactions.removeViewAt(0)
		fragment?.let {
			binding.reactionAdd.root.setOnClickListener { v ->
				val f = ReactionBottomSheetFragment()
				f.arguments = bundleOf("roomId" to event.roomId.full, "eventId" to event.eventId.full)
				f.show(it.parentFragmentManager, "reactionBottomSheet")
			}
		}
		if (reactions.reactions.isEmpty()) {
			binding.reactions.visibility = View.GONE
			return
		} else {
			binding.reactions.visibility = View.VISIBLE
		}
		reactions.reactions.forEach { (key, events) ->
			val shortcode = events
				.mapNotNull { it.content?.getOrNull() as? ShortcodeReactionEventContent }
				.groupBy { (it.shortcode ?: it.beeperShortcode)?.trim(':') }
				.entries.maxByOrNull { it.value.size }?.key
			val userReaction = events.firstOrNull { it.sender == client.userId }

			val reactionBinding = ItemReactionBinding.inflate(
				LayoutInflater.from(binding.root.context),
				binding.reactions,
				false
			)
			reactionBinding.counter.text = events.size.toString()
			if (key.startsWith("mxc://")) {
				reactionBinding.emojiUnicode.visibility = View.GONE
				reactionBinding.emojiImage.visibility = View.VISIBLE
				Glide.with(reactionBinding.root)
					.load(key)
					.into(reactionBinding.emojiImage)
			} else {
				reactionBinding.emojiUnicode.visibility = View.GONE
				reactionBinding.emojiImage.visibility = View.VISIBLE
				reactionBinding.emojiUnicode.text = key
			}
			reactionBinding.root.setBackgroundResource(
				if (userReaction != null) R.drawable.background_reaction_selected
				else R.drawable.background_reaction
			)
			reactionBinding.root.setOnClickListener {
				reactionBinding.root.setOnClickListener(null)
				reactionBinding.root.alpha = .5f
				suspendThread {
					if (userReaction == null) {
						client.reactToEvent(event.roomId, event.eventId, key, shortcode)
					} else {
						client.redactEvent(userReaction.roomId, userReaction.eventId)
					}
				}
			}

			binding.reactions.addView(reactionBinding.root, 0)
		}
	}



	private fun resetBindingState() {
		binding.unreadSeparator.visibility = View.GONE
		binding.dateSeparator.visibility = View.GONE

		binding.replyingEvent.visibility = View.GONE
		binding.replyingBody.text = null
		binding.replyingAvatar.setImageDrawable(null)
		binding.replyingName.text = null

		binding.avatar.avatarInitials = null
		binding.avatar.loadImage(null)

		binding.senderName.text = null
		binding.senderBadge.text = null
		binding.senderBadge.visibility = View.GONE

		binding.eventTimestamp.text = null
		binding.body.text = null

		binding.attachment.removeAllViews()
		binding.embeds.removeAllViews()

		while (binding.reactions.childCount > 1) {
			binding.reactions.removeViewAt(0)
		}
	}
}