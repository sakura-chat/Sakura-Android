package dev.kuylar.sakura.ui.fragment

import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.material.tabs.TabLayout
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentTimelineBinding
import dev.kuylar.sakura.emoji.CustomEmojiCategoryModel
import dev.kuylar.sakura.emoji.EmojiManager
import dev.kuylar.sakura.emojipicker.model.CategoryModel
import dev.kuylar.sakura.emojipicker.model.EmojiModel
import dev.kuylar.sakura.ui.adapter.recyclerview.TimelineRecyclerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import java.util.Map.entry
import kotlin.math.max

class TimelineFragment : Fragment(), MenuProvider {
	private lateinit var binding: FragmentTimelineBinding
	private lateinit var roomId: String
	private lateinit var client: Matrix
	private lateinit var timelineAdapter: TimelineRecyclerAdapter
	private var isLoadingMore = false
	private var editingEvent: EventId? = null
	private var replyingEvent: EventId? = null
	private var typingUsersJob: Job? = null
	private var lastReadEvent: EventId? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.getString("roomId")?.let {
			roomId = it
		}
		client = Matrix.getClient()
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentTimelineBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (!this::roomId.isInitialized) {
			findNavController().popBackStack()
			return
		}

		suspendThread {
			client.getRoom(roomId)?.let { room ->
				(activity as? AppCompatActivity)?.let {
					it.runOnUiThread {
						it.supportActionBar?.title = room.name?.explicitName ?: room.roomId.full
					}
				}
			}
		}

		val menuHost: MenuHost = requireActivity()
		menuHost.addMenuProvider(
			this,
			viewLifecycleOwner,
			Lifecycle.State.RESUMED
		)

		timelineAdapter = TimelineRecyclerAdapter(this, roomId, binding.timelineRecycler)
		binding.timelineRecycler.layoutManager =
			LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, true)
		binding.timelineRecycler.addOnScrollListener(object :
			androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
			override fun onScrolled(
				recyclerView: androidx.recyclerview.widget.RecyclerView,
				dx: Int,
				dy: Int
			) {
				super.onScrolled(recyclerView, dx, dy)
				val layoutManager = recyclerView.layoutManager as LinearLayoutManager
				val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
				val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
				val totalItemCount = layoutManager.itemCount

				if (dy == 0 || totalItemCount == 0) return
				if (!isLoadingMore && timelineAdapter.isReady) {
					if (firstVisibleItem == 0) {
						if (timelineAdapter.canLoadMoreForward()) {
							Log.d("TimelineFragment", "Loading more messages (forward)")
							isLoadingMore = true
							suspendThread {
								timelineAdapter.loadMoreForwards()
								Log.d("TimelineFragment", "Loading complete")
								isLoadingMore = false
							}
						} else {
							Log.d("TimelineFragment", "Marking room as read")
							timelineAdapter.lastEventId?.let {
								if (it == lastReadEvent) return@let
								lastReadEvent = it
								suspendThread {
									client.client.api.room.setReceipt(RoomId(roomId), it)
								}
							}
						}
					} else if (lastVisibleItem == totalItemCount - 1) {
						Log.d("TimelineFragment", "Loading more messages (backward)")
						isLoadingMore = true
						suspendThread {
							timelineAdapter.loadMoreBackwards()
							Log.d("TimelineFragment", "Loading complete")
							isLoadingMore = false
						}
					}
				}
			}
		})

		binding.buttonSend.setOnClickListener {
			sendMessage()
		}

		binding.buttonEmoji.setOnClickListener {
			requireContext().getSystemService<InputMethodManager>()
				?.hideSoftInputFromWindow(binding.input.windowToken, 0)
			binding.emojiPicker.visibility =
				if (binding.emojiPicker.visibility == View.VISIBLE) View.GONE else View.VISIBLE
		}
		binding.emojiPicker.setOnEmojiSelectedCallback { emoji ->
			binding.input.editableText.insert(binding.input.selectionStart, emoji.name)
		}

		setFragmentResultListener("timeline_action") { key, bundle ->
			val action = bundle.getString("action")
			val eventId = bundle.getString("eventId")?.let { EventId(it) }

			if (eventId == null) return@setFragmentResultListener

			when (action) {
				"edit" -> handleEdit(eventId)
				"reply" -> handleReply(eventId)
				else -> {
					Toast.makeText(
						requireContext(),
						"Action $action for event ${eventId.full}",
						Toast.LENGTH_LONG
					).show()
				}
			}
		}

		val recent = client.getRecentEmojis().take(24)
		EmojiManager.getInstance(requireContext()).getEmojiByCategory().let { map ->
			activity?.runOnUiThread {
				val items =
					emptyMap<CustomEmojiCategoryModel, List<EmojiModel>>().toMutableMap()
				map.mapKeys { CustomEmojiCategoryModel(it.key) }
					.mapValues { it.value.map { e -> EmojiModel(e.surrogates) } }
					.entries.toMutableList().apply {
						add(
							0,
							entry(
								CustomEmojiCategoryModel("recent"),
								recent.map { EmojiModel(it.emoji) })
						)
					}
					.associateByTo(items, { it.key }, { it.value })

				@Suppress("UNCHECKED_CAST")
				binding.emojiPicker.loadItems(items as Map<CategoryModel, List<EmojiModel>>)
			}
		}

		binding.input.addTextChangedListener {
			suspendThread {
				client.client.api.room.setTyping(RoomId(roomId), client.userId, !it.isNullOrBlank())
			}
		}
		typingUsersJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.room.usersTyping.collect {
				val thisRoom = it[RoomId(roomId)] ?: return@collect
				val users = thisRoom.users.mapNotNull { client.getUser(it, RoomId(roomId)) }
				val text = when (users.size) {
					1 -> getString(R.string.typing_indicator_1, users[0].name)
					2 -> getString(R.string.typing_indicator_2, users[0].name, users[1].name)
					3 -> getString(
						R.string.typing_indicator_3,
						users[0].name,
						users[1].name,
						users[2].name
					)

					else -> getString(R.string.typing_indicator_more)
				}
				activity?.runOnUiThread {
					binding.typingIndicator.visibility =
						if (users.isEmpty()) View.GONE else View.VISIBLE
					binding.typingIndicatorText.text = text
				}
			}
		}
		val ignoreView =
			binding.emojiPicker.findViewById<TabLayout>(dev.kuylar.sakura.emojipicker.R.id.tabLayout)
		PanelsChildGestureRegionObserver.Provider.get().register(ignoreView)
	}

	private fun sendMessage() {
		val msg = binding.input.getValue()
		if (msg.isBlank()) return
		if (msg.startsWith('/')) {
			val commandExecuted = tryExecuteCommand(msg)
			if (commandExecuted) return
		}
		if (editingEvent != null) {
			suspendThread {
				client.editEvent(roomId, editingEvent!!, msg)
				activity?.runOnUiThread {
					handleEdit(null)
					binding.input.editableText.clear()
					binding.buttonSend.isEnabled = true
				}
			}
			return
		}
		if (replyingEvent != null) {
			suspendThread {
				client.sendMessage(roomId, msg, replyTo = replyingEvent)
				activity?.runOnUiThread {
					handleReply(null)
					binding.input.editableText.clear()
					binding.buttonSend.isEnabled = true
				}
			}
			return
		}
		binding.buttonSend.isEnabled = false
		suspendThread {
			try {
				client.sendMessage(roomId, msg)
				activity?.runOnUiThread {
					binding.buttonSend.isEnabled = true
					binding.input.editableText.clear()
				}
			} catch (e: Exception) {
				Log.e("TimelineFragment", "Error sending message", e)
				activity?.runOnUiThread {
					binding.buttonSend.isEnabled = true
				}
			}
		}
	}

	private fun tryExecuteCommand(command: String): Boolean {
		return when (command) {
			"/notification channels" -> {
				Log.i("TimelineFragment", "Notification channels:")
				context?.getSystemService<NotificationManager>()?.notificationChannels?.forEach {
					Log.i("TimelineFragment", "- [${it.id}] ${it.name} [${it.importance}]")
				}
				true
			}
			"/notification deletechannels" -> {
				Log.i("TimelineFragment", "Deleting notification channels:")
				val nm = context?.getSystemService<NotificationManager>() ?: return true
				nm.notificationChannels?.forEach {
					nm.deleteNotificationChannel(it.id)
					Log.i("TimelineFragment", "- [${it.id}] ${it.name} [${it.importance}]")
				}
				true
			}
			"/reinit" -> {
				Log.i("TimelineFragment", "Reinitializing the recycler adapter")
				timelineAdapter.dispose()
				timelineAdapter = TimelineRecyclerAdapter(this, roomId, binding.timelineRecycler)
				true
			}
			else -> false
		}
	}

	fun handleEdit(eventId: EventId?) {
		if (replyingEvent != null) replyingEvent = null
		if (eventId == null) {
			editingEvent = null
			binding.editIndicator.visibility = View.GONE
			return
		}
		binding.buttonCancelEdit.setOnClickListener {
			editingEvent = null
			binding.editIndicator.visibility = View.GONE
			binding.input.editableText?.clear()
		}
		suspendThread {
			client.getEvent(RoomId(roomId), eventId)?.let { event ->
				activity?.runOnUiThread {
					editingEvent = event.eventId
					binding.editIndicator.visibility = View.VISIBLE
					binding.input.editableText?.clear()
					// TODO: Handle spans for this
					binding.input.editableText?.insert(0, Utils.getEventBodyText(event))
				}
			}
		}
	}

	fun handleReply(eventId: EventId?) {
		if (editingEvent != null) editingEvent = null
		if (eventId == null) {
			replyingEvent = null
			binding.replyIndicator.visibility = View.GONE
			return
		}
		binding.buttonCancelReply.setOnClickListener {
			replyingEvent = null
			binding.replyIndicator.visibility = View.GONE
			binding.input.editableText?.clear()
		}
		suspendThread {
			client.getEvent(RoomId(roomId), eventId)?.let { event ->
				activity?.runOnUiThread {
					replyingEvent = event.eventId
					binding.replyIndicator.visibility = View.VISIBLE
				}
				client.getUser(event.sender, event.roomId)?.let { user ->
					activity?.runOnUiThread {
						binding.replyIndicatorText.text = getString(R.string.replying_to, user.name)
					}
				}
			}
		}
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.top_app_bar, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return false
	}

	fun onImeHeightChanged(bottom: Int) {
		if (bottom == 0) {
			onKeyboardClosed()
		} else {
			onKeyboardOpened()
			(binding.emojiPicker.layoutParams as LinearLayout.LayoutParams).height =
				max(resources.displayMetrics.density.toInt() * 300, bottom)
		}
	}

	private fun onKeyboardClosed() {

	}

	private fun onKeyboardOpened() {
		binding.emojiPicker.visibility = View.GONE
	}

	override fun onDestroy() {
		typingUsersJob?.cancel()
		super.onDestroy()
	}
}