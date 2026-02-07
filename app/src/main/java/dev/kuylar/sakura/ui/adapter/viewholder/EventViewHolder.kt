package dev.kuylar.sakura.ui.adapter.viewholder

import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.Html
import android.view.View
import android.view.ViewConfiguration
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.getImageUrl
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toTimestamp
import dev.kuylar.sakura.Utils.toTimestampDate
import dev.kuylar.sakura.Utils.withinSameDay
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.ShortcodeReactionEventContent
import dev.kuylar.sakura.client.customevent.StickerMessageEventContent
import dev.kuylar.sakura.databinding.AttachmentImageBinding
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.databinding.ItemReactionBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.adapter.listadapter.TimelineListAdapter
import dev.kuylar.sakura.ui.adapter.model.EventModel
import dev.kuylar.sakura.ui.fragment.TimelineFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.EventBottomSheetFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.ProfileBottomSheetFragment

class EventViewHolder(
	val binding: ItemMessageBinding,
	val client: Matrix,
	val markdown: MarkdownHandler,
	val fragment: Fragment
) : TimelineViewHolder(binding) {
	private val layoutInflater = fragment.layoutInflater
	private var lastEventId: EventId? = null

	fun bind(
		eventModel: EventModel,
		lastEventModel: EventModel?,
		nextEventModel: EventModel?,
		unreadEventId: EventId?
	) {
		val event = eventModel.snapshot
		val lastEvent = lastEventModel?.snapshot
		val nextEvent = nextEventModel?.snapshot
		val repliedEvent = eventModel.repliedSnapshot
		var lastClick = 0L

		if (eventModel.eventId != lastEventId) resetBindingState()
		eventModel.userSnapshot?.let { handleUser(it) }

		binding.unreadSeparator.visibility =
			if (event.eventId == unreadEventId && nextEvent != null) View.VISIBLE else View.GONE

		binding.avatar.setOnLongClickListener {
			val f = ProfileBottomSheetFragment()
			f.arguments = bundleOf(
				"userId" to event.sender.full,
				"roomId" to event.roomId.full,
			)
			f.show(fragment.parentFragmentManager, "userBottomSheet")
			true
		}
		listOf(binding.root, binding.body, binding.attachment).forEach {
			it.setOnClickListener {
				val now = System.currentTimeMillis()
				if (now - lastClick < ViewConfiguration.getDoubleTapTimeout()) {
					(fragment as? TimelineFragment)?.handleReply(event.eventId)
				}
				lastClick = now
			}
			it.setOnLongClickListener {
				val f = EventBottomSheetFragment()
				f.arguments = bundleOf(
					"eventId" to event.eventId.full,
					"roomId" to event.roomId.full,
				)
				f.show(fragment.parentFragmentManager, "eventBottomSheet")
				true
			}
		}

		var userInfoVisible = View.VISIBLE

		if (lastEvent?.sender == event.sender && event.originTimestamp - lastEvent.originTimestamp < 5 * 60 * 1000) {
			userInfoVisible = View.GONE
			binding.messageInfo.visibility = View.GONE
		}

		if (!event.originTimestamp.withinSameDay(lastEvent?.originTimestamp ?: 0)) {
			binding.dateSeparator.visibility = View.VISIBLE
			binding.dateSeparatorText.text =
				event.originTimestamp.toTimestampDate(binding.root.context)
			userInfoVisible = View.VISIBLE
		}

		binding.avatar.visibility = userInfoVisible
		binding.messageInfo.visibility = userInfoVisible

		binding.eventTimestamp.text =
			event.originTimestamp.toTimestamp(binding.eventTimestamp.context)

		if (eventModel.reactions?.reactions?.isNotEmpty() == true) {
			binding.reactions.visibility = View.VISIBLE
			handleReactions(event, eventModel.reactions!!.reactions)
		} else
			binding.reactions.visibility = View.GONE

		repliedEvent?.let { handleReply(it) }
		event.content?.getOrNull().let {
			handleContent(
				event,
				eventModel.userSnapshot,
				it,
				eventModel.replaces?.history?.isNotEmpty() ?: false
			)
		}
	}

	private fun handleContent(
		event: TimelineEvent,
		sender: RoomUser?,
		content: RoomEventContent?,
		edited: Boolean
	) {
		when (content) {
			is RoomMessageEventContent.TextBased.Text -> {
				if (content.formattedBody != null) {
					markdown.setTextView(binding.body, content.formattedBodyWithoutFallback, edited)
				} else {
					binding.body.text = content.bodyWithoutFallback
				}
			}

			is RoomMessageEventContent.TextBased.Notice -> {
				if (content.formattedBody != null) {
					markdown.setTextView(binding.body, content.formattedBodyWithoutFallback, edited)
				} else {
					binding.body.text = content.bodyWithoutFallback
				}
			}

			is RoomMessageEventContent.TextBased.Emote -> {
				binding.senderName.visibility = View.GONE
				val body = "\\* **${sender?.name ?: event.sender.full}** " +
						(content.formattedBodyWithoutFallback?.let { markdown.htmlToMarkdown(it) }
							?: content.body)
				markdown.setTextView(binding.body, body, edited)
			}

			is RoomMessageEventContent.FileBased.Image -> {
				if (content.formattedBodyWithoutFallback != null) {
					markdown.setTextView(binding.body, content.formattedBodyWithoutFallback, edited)
				} else if (content.fileName != null && content.body != content.fileName) {
					markdown.setTextView(binding.body, content.bodyWithoutFallback, edited)
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

			is StickerMessageEventContent -> {
				val attachmentBinding = AttachmentImageBinding.inflate(
					layoutInflater,
					binding.attachment,
					false
				)
				val displayMetrics = attachmentBinding.root.context.resources.displayMetrics
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

	private fun handleUser(user: RoomUser) {
		binding.senderName.text = user.name
		Glide.with(binding.root)
			.load(user.avatarUrl)
			.into(binding.avatar)
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
				reactionBinding.root.setBackgroundResource(
					if (weReacted != null) R.drawable.background_reaction_selected
					else R.drawable.background_reaction
				)
				reactionBinding.counter.text = list.size.toString()
				reactionBinding.root.setOnClickListener {
					reactionBinding.root.setOnClickListener(null)
					reactionBinding.root.alpha = .5f
					suspendThread {
						if (weReacted == null) {
							client.reactToEvent(event.roomId, event.eventId, key, shortcode)
						} else {
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
			(bindingAdapter as? TimelineListAdapter)?.scrollToEventId(event.eventId)
		}
		binding.replyingBody.setText(R.string.empty_message)
		val content =
			event.content?.getOrNull() as? MessageEventContent ?: return

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

			is StickerMessageEventContent -> {
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