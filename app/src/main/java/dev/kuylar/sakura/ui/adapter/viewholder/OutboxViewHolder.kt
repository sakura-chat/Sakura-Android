package dev.kuylar.sakura.ui.adapter.viewholder

import android.graphics.drawable.Drawable
import android.text.Html
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import dev.kuylar.sakura.R
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.AttachmentImageBinding
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.adapter.model.OutboxModel
import dev.kuylar.sakura.ui.fragment.bottomsheet.ProfileBottomSheetFragment

class OutboxViewHolder(
	val binding: ItemMessageBinding,
	val client: Matrix,
	val markdown: MarkdownHandler,
	val fragment: Fragment
) : TimelineViewHolder(binding) {
	private val layoutInflater = fragment.layoutInflater
	private var lastEventId: EventId? = null

	fun bind(eventModel: OutboxModel) {
		val event = eventModel.snapshot

		if (eventModel.eventId != lastEventId) resetBindingState()
		eventModel.userSnapshot?.let { handleUser(it) }

		binding.unreadSeparator.visibility = View.GONE

		binding.root.setOnLongClickListener {
			TODO("Outbox sheet isn't implemented")
		}
		binding.avatar.setOnLongClickListener {
			val f = ProfileBottomSheetFragment()
			f.arguments = bundleOf(
				"userId" to client.userId.full,
				"roomId" to event.roomId.full,
			)
			f.show(fragment.parentFragmentManager, "userBottomSheet")
			true
		}
		binding.eventTimestamp.setText(R.string.sending)
		when (val content = event.content) {
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
							target: com.bumptech.glide.request.target.Target<Drawable?>,
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

			else -> {
				binding.body.text = Html.fromHtml(
					"${
						content.javaClass.name.split("core.model.events.").last()
					}\n<code>${eventModel.eventId}</code>",
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