package dev.kuylar.sakura.ui.fragment.bottomsheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.postDelayed
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentEventBottomSheetBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getTimelineEventReactionAggregation
import net.folivo.trixnity.client.room.getTimelineEventReplaceAggregation
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

class EventBottomSheetFragment : BottomSheetDialogFragment() {
	private lateinit var binding: FragmentEventBottomSheetBinding
	private lateinit var eventFlow: Flow<TimelineEvent>
	private lateinit var event: TimelineEvent
	private val client = Matrix.getClient()
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
		collectJob = CoroutineScope(Dispatchers.Main).launch {
			val isEdited =
				client.client.room.getTimelineEventReplaceAggregation(roomId!!, eventId!!)
					.firstOrNull()?.history?.isNotEmpty() ?: false
			val hasReactions =
				client.client.room.getTimelineEventReactionAggregation(roomId!!, eventId!!)
					.firstOrNull()?.reactions?.isNotEmpty() ?: false
			client.client.room.getTimelineEvent(roomId!!, eventId!!).collect {
				if (it == null) {
					activity?.runOnUiThread {
						binding.root.postDelayed(50) { dismiss() }
					}
				} else {
					activity?.runOnUiThread {
						event = it
						loadData(isEdited, hasReactions)
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

	private fun loadData(isEdited: Boolean, hasReactions: Boolean) {
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
		binding.viewSource.setOnClickListener {
			// TODO: EventSourceFragment
			Toast.makeText(requireContext(), "not yet implemented", Toast.LENGTH_LONG).show()
			binding.root.postDelayed(50) { dismiss() }
		}
		binding.report.setOnClickListener {
			// TODO: A whole ass report dialog
			Toast.makeText(requireContext(), "not yet implemented", Toast.LENGTH_LONG).show()
			binding.root.postDelayed(50) { dismiss() }
		}
	}
}