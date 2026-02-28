package dev.kuylar.sakura.ui.adapter.timeline

import android.graphics.drawable.Drawable
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import de.connect2x.trixnity.client.room.TimelineEventAggregation
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import dev.kuylar.sakura.BuildConfig
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.content
import dev.kuylar.sakura.Utils.getImageUrl
import dev.kuylar.sakura.Utils.lastReceipt
import dev.kuylar.sakura.Utils.loadUser
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toFileSize
import dev.kuylar.sakura.Utils.toTimestamp
import dev.kuylar.sakura.Utils.toTimestampDate
import dev.kuylar.sakura.Utils.withinSameDay
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.ShortcodeReactionEventContent
import dev.kuylar.sakura.client.customevent.StickerMessageEventContent
import dev.kuylar.sakura.databinding.AttachmentFileBinding
import dev.kuylar.sakura.databinding.AttachmentImageBinding
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
		if (prevItem?.timestamp?.withinSameDay(item.timestamp) == false) {
			binding.dateSeparator.visibility = View.VISIBLE
			binding.dateSeparatorText.text = item.timestamp.toTimestampDate(binding.root.context)
		}
		item.user?.let { setUser(it) }
		item.content?.let {
			setContent(
				currentNonce,
				it,
				item.user,
				(item as TimelineItem.Event).event.event
			)
		}
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
		var showAvatar = true
		// If last message is less than 5 minutes ago, hide avatar
		if (prevItem != null) {
			if (item.senderId == prevItem.senderId &&
				item.timestamp - prevItem.timestamp < 5.minutes.inWholeMilliseconds
			)
				showAvatar = false
		}
		if (prevItem?.timestamp?.withinSameDay(item.timestamp) == false) showAvatar = true
		// If current message is a reply, hide avatar
		when (item) {
			is TimelineItem.Event -> {
				if (item.repliedToEvent != null) showAvatar = true
			}

			is TimelineItem.OutboxItem -> {
				if (item.snapshot.content.relatesTo?.replyTo != null) showAvatar = true
			}
		}
		(if (showAvatar) View.VISIBLE else View.GONE).let {
			binding.avatar.visibility = it
			binding.messageInfo.visibility = it
		}
	}

	private fun setContent(
		currentNonce: Long,
		content: RoomEventContent,
		user: RoomUser?,
		rawEvent: ClientEvent.RoomEvent<*>?,
		edited: Boolean = false
	) {
		when (content) {
			is RoomMessageEventContent.TextBased.Text, RoomMessageEventContent.TextBased.Notice -> {
				markdown.setTextView(
					binding.body,
					(content as? RoomMessageEventContent.TextBased)?.content,
					edited
				)
			}

			is RoomMessageEventContent.TextBased.Emote -> {
				markdown.setTextView(
					binding.body,
					"* <b>%s</b> %s".format(
						user?.name,
						(content as? RoomMessageEventContent.TextBased)?.content
					),
					edited
				) { updateSpans(currentNonce) }
				binding.senderName.visibility = View.GONE
			}

			is RoomMessageEventContent.FileBased -> {
				if (content.fileName != null && content.body != content.fileName) {
					markdown.setTextView(
						binding.body,
						content.content,
						edited
					) { updateSpans(currentNonce) }
				}
				binding.attachment.visibility = View.VISIBLE
				when (content) {
					is RoomMessageEventContent.FileBased.Image -> {
						setAttachment(content)
					}

					else -> {
						setAttachment(content)
					}
				}
			}

			is MemberEventContent -> {
				val memberEvent = rawEvent as? ClientEvent.RoomEvent.StateEvent<*> ?: return
				val oldContent = memberEvent.unsigned?.previousContent as? MemberEventContent
				val stateKey = memberEvent.stateKey
				val context = binding.root.context
				markdown.setTextView(
					binding.body,
					Utils.getMembershipChangeText(context, stateKey, oldContent, content, user),
					false
				)
				Utils.getMembershipChangeDrawableId(
					oldContent?.membership ?: Membership.LEAVE,
					content.membership
				)?.let { id ->
					binding.avatar.setImageDrawable(ContextCompat.getDrawable(context, id))
				}
			}

			is StickerMessageEventContent -> {
				setAttachment(content)
			}

			else -> {
				markdown.setTextView(
					binding.body,
					"<code>${
						content.javaClass.name.substringAfterLast(".").replace("$", ".")
					}</code>",
					edited
				) { updateSpans(currentNonce) }
			}
		}
		if (binding.body.text.isEmpty()) {
			// because BuildConfig.DEBUG is not always true
			@Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
			if (BuildConfig.DEBUG) {
				binding.body.text = content.javaClass.name
			} else {
				binding.body.visibility = View.GONE
			}
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
				f.arguments =
					bundleOf("roomId" to event.roomId.full, "eventId" to event.eventId.full)
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
				reactionBinding.emojiUnicode.visibility = View.VISIBLE
				reactionBinding.emojiImage.visibility = View.GONE
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

	private fun setAttachment(content: RoomMessageEventContent.FileBased.Image) {
		if (fragment == null) return
		val attachmentBinding = AttachmentImageBinding.inflate(
			fragment!!.layoutInflater,
			binding.attachment,
			false
		)
		val displayMetrics = fragment!!.resources.displayMetrics
		val maxWidth = minOf(
			displayMetrics.widthPixels * 0.7f,
			400f * displayMetrics.density
		).toInt()
		val maxHeight = minOf(
			displayMetrics.heightPixels * 0.5f,
			300f * displayMetrics.density
		).toInt()

		Glide.with(attachmentBinding.root)
			.load(content.getImageUrl())
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
					attachmentBinding.loading.visibility = View.GONE
					return false
				}
			})
			.into(attachmentBinding.imageAttachment)
		binding.attachment.removeAllViews()
		binding.attachment.visibility = View.VISIBLE
		binding.attachment.addView(attachmentBinding.root)
	}

	private fun setAttachment(content: RoomMessageEventContent.FileBased) {
		// TODO: File downloads
		if (fragment == null) return
		val attachmentBinding =
			AttachmentFileBinding.inflate(fragment!!.layoutInflater, binding.attachment, false)
		attachmentBinding.title.text = content.fileName ?: content.body
		attachmentBinding.subtitle.text = content.info?.size.toFileSize()
		binding.attachment.addView(attachmentBinding.root)
		binding.attachment.visibility = View.VISIBLE
	}

	private fun setAttachment(content: StickerMessageEventContent) {
		if (fragment == null) return
		val attachmentBinding = AttachmentImageBinding.inflate(
			fragment!!.layoutInflater,
			binding.attachment,
			false
		)
		val displayMetrics = fragment!!.resources.displayMetrics
		val maxSize = minOf(
			displayMetrics.widthPixels * 0.7f,
			112f * displayMetrics.density
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
					val params = attachmentBinding.root.layoutParams
					params.width = maxSize
					params.height = maxSize
					attachmentBinding.root.layoutParams = params
					attachmentBinding.loading.visibility = View.GONE
					return false
				}
			})
			.into(attachmentBinding.imageAttachment)
		binding.attachment.removeAllViews()
		binding.attachment.visibility = View.VISIBLE
		binding.attachment.addView(attachmentBinding.root)
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

		binding.senderName.visibility = View.VISIBLE
		binding.senderName.text = null
		binding.senderBadge.text = null
		binding.senderBadge.visibility = View.GONE

		binding.eventTimestamp.text = null
		binding.body.visibility = View.VISIBLE
		binding.body.text = null

		binding.attachment.removeAllViews()
		binding.embeds.removeAllViews()

		while (binding.reactions.childCount > 1) {
			binding.reactions.removeViewAt(0)
		}
	}
}