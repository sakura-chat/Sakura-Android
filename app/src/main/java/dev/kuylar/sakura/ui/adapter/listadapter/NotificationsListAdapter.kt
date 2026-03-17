package dev.kuylar.sakura.ui.adapter.listadapter

import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.connect2x.trixnity.client.notification.Notification
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.content
import dev.kuylar.sakura.Utils.getName
import dev.kuylar.sakura.Utils.loadAvatar
import dev.kuylar.sakura.Utils.loadUser
import dev.kuylar.sakura.Utils.toTimestamp
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.message.RoomMessageEventContent
import dev.kuylar.sakura.databinding.ItemNotificationBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import kotlinx.coroutines.launch
import kotlin.random.Random

class NotificationsListAdapter(
	val fragment: Fragment,
	val markdown: MarkdownHandler,
	val client: Matrix
) : ListAdapter<Notification, NotificationsListAdapter.ViewHolder>(
	object : DiffUtil.ItemCallback<Notification>() {
		override fun areItemsTheSame(a: Notification, b: Notification) = a.id == b.id
		override fun areContentsTheSame(a: Notification, b: Notification) = a == b
	}
) {
	private val layoutInflater = fragment.layoutInflater
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
		ItemNotificationBinding.inflate(layoutInflater, parent, false),
		fragment,
		markdown,
		client
	)

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	class ViewHolder(
		val binding: ItemNotificationBinding,
		val fragment: Fragment,
		val markdown: MarkdownHandler,
		val client: Matrix
	) : RecyclerView.ViewHolder(binding.root) {
		private var nonce = 0
		fun bind(item: Notification) {
			nonce = Random.nextInt()
			when (item) {
				is Notification.Message -> {
					bindMessage(nonce, item)
				}

				is Notification.State -> {
					bindState(nonce, item)
				}
			}
		}

		private fun bindMessage(currentNonce: Int, item: Notification.Message) {
			binding.root.setOnClickListener {
				fragment.findNavController().navigate(R.id.nav_room, bundleOf(
					"roomId" to item.timelineEvent.roomId.full,
					"eventId" to item.timelineEvent.eventId.full
				))
			}
			setRoom(item.timelineEvent.roomId)
			setUser(currentNonce, item.timelineEvent.sender, item.timelineEvent.roomId)

			binding.eventTimestamp.text =
				item.timelineEvent.originTimestamp.toTimestamp(binding.root.context)

			when (val content = item.timelineEvent.content?.getOrNull()) {
				is RoomMessageEventContent.TextBased -> {
					markdown.setTextView(binding.body, content.content, false) {
						if (nonce == currentNonce) binding.body.text = binding.body.text
					}
				}

				is RoomMessageEventContent.FileBased -> {
					markdown.setTextView(binding.body, "<i>Attachment</i>", false)
				}

				else -> {
					markdown.setTextView(
						binding.body,
						"<code>${
							content?.javaClass?.name?.substringAfterLast(".")
								?.replace("$", ".") ?: "null"
						}</code>"
					)
				}
			}
		}

		private fun bindState(currentNonce: Int, item: Notification.State) {
			setRoom(item.stateEvent.roomId)
			setUser(currentNonce, item.stateEvent.sender, item.stateEvent.roomId)

			binding.eventTimestamp.text =
				item.stateEvent.originTimestamp?.toTimestamp(binding.root.context)

			// TODO: This whole thing lol
			when (val content = item.stateEvent.content) {
				else -> {
					markdown.setTextView(
						binding.body,
						"<code>${
							content.javaClass.name.substringAfterLast(".").replace("$", ".")
						}</code>"
					)
				}
			}
		}

		private fun setUser(currentNonce: Int, userId: UserId, roomId: RoomId?) {
			binding.avatar.loadAvatar(null, userId.full.trim('@').split(':').joinToString(" "))
			binding.senderName.text = userId.full
			if (roomId == null) return
			fragment.lifecycleScope.launch {
				client.getUser(userId, roomId)?.let { user ->
					if (nonce != currentNonce) return@let
					binding.avatar.loadUser(user)
					binding.senderName.text = user.name
				}
			}
		}

		private fun setRoom(roomId: RoomId?) {
			if (roomId == null) return
			fragment.lifecycleScope.launch {
				binding.roomName.text =
					client.getRoom(roomId)?.getName(binding.roomName.context, client)
			}
		}
	}
}