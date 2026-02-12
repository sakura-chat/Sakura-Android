package dev.kuylar.sakura.ui.fragment.bottomsheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.postDelayed
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.getTimelineEventReactionAggregation
import de.connect2x.trixnity.client.room.getTimelineEventReplaceAggregation
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.FullyReadEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.RecentEmoji
import dev.kuylar.sakura.databinding.FragmentEventBottomSheetBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@AndroidEntryPoint
class EventBottomSheetFragment : BottomSheetDialogFragment() {
	private lateinit var binding: FragmentEventBottomSheetBinding
	private lateinit var event: TimelineEvent
	@Inject lateinit var client: Matrix
	private var eventType: String? = null
	private var eventId: EventId? = null
	private var roomId: RoomId? = null
	private var collectJob: Job? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let {
			roomId = RoomId(it.getString("roomId") ?: "")
			eventId = EventId(it.getString("eventId") ?: "")
			eventType = it.getString("type")
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentEventBottomSheetBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (roomId == null || eventId == null) {
			binding.root.postDelayed(50) { dismiss() }
			return
		}
		collectJob = suspendThread {
			val isEdited =
				client.client.room.getTimelineEventReplaceAggregation(roomId!!, eventId!!)
					.firstOrNull()?.history?.isNotEmpty() ?: false
			val hasReactions =
				client.client.room.getTimelineEventReactionAggregation(roomId!!, eventId!!)
					.firstOrNull()?.reactions?.isNotEmpty() ?: false
			client.client.room.getTimelineEvent(roomId!!, eventId!!).collect {
				val recentEmojis = client.getRecentEmojis().take(5)
				if (it == null) {
					activity?.runOnUiThread {
						binding.root.postDelayed(50) { dismiss() }
					}
				} else {
					activity?.runOnUiThread {
						event = it
						loadData(isEdited, hasReactions, recentEmojis.toMutableList())
					}
				}
			}
		}
		suspendThread {
			client.client.user.canRedactEvent(roomId!!, eventId!!).collect {
				activity?.runOnUiThread {
					binding.delete.visibility = if (it) View.VISIBLE else View.GONE
				}
			}
		}
	}

	private fun loadData(
		isEdited: Boolean,
		hasReactions: Boolean,
		recentEmojis: MutableList<RecentEmoji>
	) {
		while (recentEmojis.size < 5) recentEmojis.add(RecentEmoji(" ", 0))
		binding.edit.visibility = if (event.sender == client.userId) View.VISIBLE else View.GONE
		binding.editHistory.visibility = if (isEdited) View.VISIBLE else View.GONE
		binding.reactions.visibility = if (hasReactions) View.VISIBLE else View.GONE
		binding.copy.visibility =
			if (event.content?.getOrNull() is RoomMessageEventContent.TextBased) View.VISIBLE else View.GONE

		binding.edit.setOnClickListener {
			binding.root.postDelayed(50) {
				parentFragmentManager.setFragmentResult("timeline_action", Bundle().apply {
					putString("action", "edit")
					putString("eventId", eventId!!.full)
				})
				dismiss()
			}
		}
		binding.reply.setOnClickListener {
			binding.root.postDelayed(50) {
				parentFragmentManager.setFragmentResult("timeline_action", Bundle().apply {
					putString("action", "reply")
					putString("eventId", eventId!!.full)
				})
				dismiss()
			}
		}
		binding.copy.setOnClickListener {
			(event.content?.getOrNull() as? RoomMessageEventContent.TextBased)?.body?.let {
				context?.getSystemService(ClipboardManager::class.java)
					?.setPrimaryClip(ClipData.newPlainText("Message Content", it))
			}
			binding.root.postDelayed(50) { dismiss() }
		}
		// TODO: Show an AlertDialog to ask for a reason (only when long pressed)
		binding.delete.setOnClickListener {
			suspendThread {
				client.client.api.room.redactEvent(roomId!!, eventId!!)
			}
			binding.root.postDelayed(50) { dismiss() }
		}
		binding.editHistory.setOnClickListener {
			// TODO: EditHistoryBottomSheetFragment
			Toast.makeText(requireContext(), "not yet implemented", Toast.LENGTH_LONG).show()
			binding.root.postDelayed(50) { dismiss() }
		}
		binding.reactions.setOnClickListener {
			// TODO: EventReactionsBottomSheetFragment
			Toast.makeText(requireContext(), "not yet implemented", Toast.LENGTH_LONG).show()
			binding.root.postDelayed(50) { dismiss() }
		}
		binding.share.setOnClickListener {
			val url =
				"https://matrix.to/#/${roomId}/${eventId}?via=${roomId?.full?.substringAfterLast(':')}"
			val html =
				(event.content?.getOrNull() as? RoomMessageEventContent.TextBased)?.formattedBody
			val sendIntent: Intent = Intent().apply {
				action = Intent.ACTION_SEND
				putExtra(Intent.EXTRA_TEXT, url)
				html?.let { putExtra(Intent.EXTRA_HTML_TEXT, it) }
				type = "text/plain"
			}
			startActivity(Intent.createChooser(sendIntent, null))
		}
		binding.unread.setOnClickListener {
			suspendThread {
				client.client.api.room.setAccountData(
					FullyReadEventContent(event.previousEventId ?: event.eventId),
					roomId!!,
					client.userId
				)
			}
			binding.root.postDelayed(50) { dismiss() }
		}
		binding.report.setOnClickListener {
			// TODO: A whole ass report dialog
			Toast.makeText(requireContext(), "not yet implemented", Toast.LENGTH_LONG).show()
			binding.root.postDelayed(50) { dismiss() }
		}

		binding.quickReaction1Text.text = recentEmojis[0].emoji
		binding.quickReaction2Text.text = recentEmojis[1].emoji
		binding.quickReaction3Text.text = recentEmojis[2].emoji
		binding.quickReaction4Text.text = recentEmojis[3].emoji
		binding.quickReaction5Text.text = recentEmojis[4].emoji
		binding.quickReaction1.setOnClickListener {
			suspendThread {
				client.reactToEvent(roomId!!, eventId!!, recentEmojis[0].emoji)
			}
			binding.root.postDelayed(50) {
				dismiss()
			}
		}
		binding.quickReaction2.setOnClickListener {
			suspendThread {
				client.reactToEvent(roomId!!, eventId!!, recentEmojis[1].emoji)
			}
			binding.root.postDelayed(50) {
				dismiss()
			}
		}
		binding.quickReaction3.setOnClickListener {
			suspendThread {
				client.reactToEvent(roomId!!, eventId!!, recentEmojis[2].emoji)
			}
			binding.root.postDelayed(50) {
				dismiss()
			}
		}
		binding.quickReaction4.setOnClickListener {
			suspendThread {
				client.reactToEvent(roomId!!, eventId!!, recentEmojis[3].emoji)
			}
			binding.root.postDelayed(50) {
				dismiss()
			}
		}
		binding.quickReaction5.setOnClickListener {
			suspendThread {
				client.reactToEvent(roomId!!, eventId!!, recentEmojis[4].emoji)
			}
			binding.root.postDelayed(50) {
				dismiss()
			}
		}
		binding.reaction.setOnClickListener {
			val f = ReactionBottomSheetFragment()
			f.arguments = bundleOf("roomId" to roomId?.full, "eventId" to eventId?.full)
			f.show(parentFragmentManager, "reactionBottomSheet")
			binding.root.postDelayed(50) {
				dismiss()
			}
		}
	}
}