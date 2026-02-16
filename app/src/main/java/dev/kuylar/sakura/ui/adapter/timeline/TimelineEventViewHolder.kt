package dev.kuylar.sakura.ui.adapter.timeline

import android.text.method.LinkMovementMethod
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import dev.kuylar.sakura.BuildConfig
import dev.kuylar.sakura.Utils.content
import dev.kuylar.sakura.Utils.loadUser
import dev.kuylar.sakura.Utils.toTimestamp
import dev.kuylar.sakura.databinding.ItemMessageBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

class TimelineEventViewHolder(
	val binding: ItemMessageBinding,
	private val markdown: MarkdownHandler
) : RecyclerView.ViewHolder(binding.root) {
	private var nonce = 0L
	private var collectionJob: Job? = null

	fun bind(item: TimelineItem) {
		collectionJob?.cancel()
		collectionJob = null
		nonce = Random.nextLong()
		val currentNonce = nonce
		setData(currentNonce, item)
		collectionJob =
			(bindingAdapter as? TimelineRecyclerAdapter)?.fragment?.lifecycleScope?.launch {
				when (item) {
					is TimelineItem.Event -> {
						item.flow.collect { snapshot ->
							item.update(snapshot)
							setData(currentNonce, item)
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

	private fun setData(currentNonce: Long, item: TimelineItem) {
		binding.senderName.text = item.senderId.full
		binding.eventTimestamp.text = item.timestamp.toTimestamp(binding.eventTimestamp.context)
		item.content?.let { setContent(currentNonce, it) }
		item.user?.let { setUser(it) }
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
}