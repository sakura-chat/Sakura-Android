package dev.kuylar.sakura.ui.fragment

import android.app.NotificationManager
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.bytesToString
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.MatrixEmote
import dev.kuylar.sakura.databinding.FragmentTimelineBinding
import dev.kuylar.sakura.emoji.RoomCustomEmojiModel
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.adapter.PickerPagerAdapter
import dev.kuylar.sakura.ui.adapter.listadapter.TimelineListAdapter
import dev.kuylar.sakura.ui.models.AttachmentInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.max

@Suppress("EmptyMethod")
@AndroidEntryPoint
class TimelineFragment : Fragment(), MenuProvider {
	private lateinit var binding: FragmentTimelineBinding
	private lateinit var roomId: String

	@Inject
	lateinit var client: Matrix

	@Inject
	lateinit var markdown: MarkdownHandler
	private lateinit var timelineAdapter: TimelineListAdapter
	private lateinit var visualMediaPicker: ActivityResultLauncher<PickVisualMediaRequest>
	private var isLoadingMore = false
	private var editingEvent: EventId? = null
	private var replyingEvent: EventId? = null
	private var typingUsersJob: Job? = null
	private var lastReadEventId: EventId? = null
	private var lastReadEventTimestamp: Long = 0
	private var clearCacheUnlocked = false
	private var attachment: AttachmentInfo? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.getString("roomId")?.let {
			roomId = it
		}
		visualMediaPicker =
			registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
				if (uri != null)
					loadAttachmentFromUri(uri)
			}
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

		timelineAdapter = TimelineListAdapter(
			this,
			RoomId(roomId),
			binding.timelineRecycler,
			client,
			markdown
		) {
			binding.loading.visibility = if (it.first || it.second) View.VISIBLE else View.GONE
			if (!it.first && !it.second) isLoadingMore = false
		}
		binding.timelineRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)
				checkAndLoadMoreIfNeeded(recyclerView)
			}
		})

		binding.buttonSend.setOnClickListener {
			sendMessage()
		}

		binding.buttonEmoji.setOnClickListener {
			requireContext().getSystemService<InputMethodManager>()
				?.hideSoftInputFromWindow(binding.input.windowToken, 0)
			binding.picker.visibility =
				if (binding.picker.isVisible) View.GONE else View.VISIBLE
		}

		binding.attachment.buttonRemove.setOnClickListener {
			attachment = null
			updateAttachment()
		}
		binding.buttonAttachment.setOnClickListener {
			pickAttachment()
		}

		setFragmentResultListener("timeline_action") { _, bundle ->
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
		childFragmentManager.setFragmentResultListener("picker_action", this) { _, bundle ->
			val action = bundle.getString("action") ?: return@setFragmentResultListener
			val params = bundle.getStringArray("params")?.toList() ?: emptyList<String>()

			when (action) {
				"custom_emoji" -> {
					val model = RoomCustomEmojiModel(params[0], params[1])
					binding.input.insertMention(model.toMention(requireContext()))
				}

				"unicode_emoji" -> {
					binding.input.editableText.insert(binding.input.selectionStart, params[0])
				}

				"sticker" -> {
					suspendThread {
						try {
							client.sendSticker(
								RoomId(roomId),
								Json.decodeFromString<MatrixEmote>(params[1]),
								replyingEvent = replyingEvent
							)
							activity?.runOnUiThread {
								handleReply(null)
							}
						} catch (e: Exception) {
							Log.e("TimelineFragment", "Failed to send sticker\n${params[1]}", e)
						}
					}
				}

				"gif" -> {
					if (params.isNotEmpty())
						loadAttachmentFromUri(params[0].toUri())
				}

				else -> {
					Toast.makeText(
						requireContext(),
						"Picker action $action with params (${params.joinToString(", ") { "\"$it\"" }})",
						Toast.LENGTH_LONG
					).show()
				}
			}
		}
		updateEmojiPicker()

		binding.input.addTextChangedListener {
			suspendThread {
				client.client.api.room.setTyping(RoomId(roomId), client.userId, !it.isNullOrBlank())
			}
		}
		ViewCompat.setOnReceiveContentListener(
			binding.input,
			AttachmentReceiver.MIME_TYPES,
			AttachmentReceiver(::loadAttachmentFromUri)
		)
		typingUsersJob = CoroutineScope(Dispatchers.Main).launch {
			client.client.room.usersTyping.collect {
				val thisRoom = it[RoomId(roomId)] ?: return@collect
				val users = thisRoom.users
					.filterNot { uid -> uid == client.userId }
					.mapNotNull { uid -> client.getUser(uid, RoomId(roomId)) }
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
	}

	private fun sendMessage() {
		val msg = binding.input.getValue()
		if (msg.isBlank() && attachment == null) return
		if (msg.startsWith('/')) {
			val commandExecuted = tryExecuteCommand(msg)
			if (commandExecuted) {
				binding.input.editableText.clear()
				return
			}
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
		binding.buttonSend.isEnabled = false
		suspendThread {
			try {
				client.sendMessage(
					roomId, msg, requireContext(),
					replyTo = replyingEvent,
					attachment = attachment
				)
				activity?.runOnUiThread {
					if (replyingEvent != null)
						handleReply(null)
					binding.buttonSend.isEnabled = true
					binding.input.editableText.clear()
					attachment = null
					updateAttachment()
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
				timelineAdapter = TimelineListAdapter(
					this,
					RoomId(roomId),
					binding.timelineRecycler,
					client,
					markdown
				)
				true
			}

			"/forceinitialsync" -> {
				if (!clearCacheUnlocked) {
					Toast.makeText(
						context,
						"Are you sure you want to force an initial sync?",
						Toast.LENGTH_SHORT
					).show()
					clearCacheUnlocked = true
					return true
				}
				clearCacheUnlocked = false
				suspendThread {
					client.updateFilters(true)
					client.client.clearCache()
				}
				true
			}

			else -> false
		}
	}

	fun handleEdit(eventId: EventId?) {
		if (replyingEvent != null) {
			replyingEvent = null
			binding.replyIndicator.visibility = View.GONE
		}
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
					(event.content?.getOrNull() as? RoomMessageEventContent.TextBased)?.let {
						val str = it.formattedBodyWithoutFallback ?: it.bodyWithoutFallback
						binding.input.editableText?.insert(0, markdown.htmlToMarkdown(str))
					}
				}
			}
		}
	}

	fun handleReply(eventId: EventId?) {
		if (editingEvent != null) {
			editingEvent = null
			binding.editIndicator.visibility = View.GONE
		}
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
			(binding.picker.layoutParams as LinearLayout.LayoutParams).height =
				max(resources.displayMetrics.density.toInt() * 300, bottom)
		}
	}

	fun closeKeyboard() {
		requireContext().getSystemService<InputMethodManager>()
			?.hideSoftInputFromWindow(binding.input.windowToken, 0)
		binding.picker.visibility = View.GONE
	}

	private fun checkAndLoadMoreIfNeeded(recyclerView: RecyclerView) {
		if (isLoadingMore) return
		val layoutManager = recyclerView.layoutManager as LinearLayoutManager
		val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
		val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
		val totalItemCount = layoutManager.itemCount

		if (totalItemCount == 0 || !timelineAdapter.isReady) return
		if ((totalItemCount - lastVisibleItem - 1) <= 5 && timelineAdapter.canLoadMoreBackward()) {
			isLoadingMore = true
			suspendThread {
				timelineAdapter.loadMoreBackwards()
			}
			return
		}

		if (firstVisibleItem <= 5) {
			if (timelineAdapter.canLoadMoreForward()) {
				isLoadingMore = true
				suspendThread {
					timelineAdapter.loadMoreForwards()
				}
			}
			val lastEventId = timelineAdapter.lastEventId ?: return
			val lastEventTimestamp = timelineAdapter.lastEventTimestamp
			if (lastReadEventTimestamp < lastEventTimestamp) {
				lastReadEventId = lastEventId
				lastReadEventTimestamp = lastEventTimestamp
				suspendThread {
					client.client.api.room.setReceipt(RoomId(roomId), lastEventId)
				}
			}
		}
	}

	private fun onKeyboardClosed() {

	}

	private fun onKeyboardOpened() {
		if (binding.picker.isVisible && binding.pickerTabs.selectedTabPosition == 1) return
		binding.picker.visibility = View.GONE
	}

	override fun onDestroy() {
		timelineAdapter.dispose()
		typingUsersJob?.cancel()
		super.onDestroy()
	}

	private fun updateEmojiPicker() {
		binding.pickerPager.adapter = PickerPagerAdapter(this)
		TabLayoutMediator(binding.pickerTabs, binding.pickerPager) { tab, position ->
			tab.text = when (position) {
				0 -> getString(R.string.picker_emoji)
				1 -> getString(R.string.picker_gif)
				2 -> getString(R.string.picker_sticker)
				else -> position.toString()
			}
		}.attach()
	}

	private fun pickAttachment() {
		// TODO: Support more than image/video
		pickImage()
	}

	private fun pickImage() {
		visualMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
	}

	private fun loadAttachmentFromUri(uri: Uri) {
		context?.let {
			attachment =
				if (uri.scheme?.startsWith("http") == true) AttachmentInfo.HttpUri(uri)
				else AttachmentInfo.ContentUri(uri, it)
			attachment?.onUpdate = {
				activity?.runOnUiThread {
					updateAttachment()
				}
			}
			updateAttachment()
		}
	}

	private fun updateAttachment() {
		if (attachment == null) {
			binding.attachment.thumbnail.setImageDrawable(null)
			binding.attachment.name.text = null
			binding.attachment.size.text = null
			binding.attachment.root.visibility = View.GONE
		} else {
			binding.attachment.root.visibility = View.VISIBLE
			binding.attachment.name.text = attachment!!.name
			binding.attachment.size.text =
				if (attachment!!.ready) attachment!!.size.bytesToString() else getString(R.string.loading)
			if (attachment!!.contentUri.scheme == "content")
				binding.attachment.thumbnail.setImageBitmap(
					requireContext().contentResolver.loadThumbnail(
						attachment!!.contentUri,
						Size(640, 640),
						null
					)
				)
			else {
				Glide.with(this)
					.load(attachment!!.contentUri)
					.into(binding.attachment.thumbnail)
			}
		}
	}

	class AttachmentReceiver(val handler: (Uri) -> Unit) : OnReceiveContentListener {
		override fun onReceiveContent(
			view: View,
			contentInfo: ContentInfoCompat
		): ContentInfoCompat? {
			try {
				val split = contentInfo.partition { item: ClipData.Item -> item.uri != null }
				val uriContent = split.first
				val remaining = split.second
				uriContent?.let { content ->
					(content.linkUri
						?: if (content.clip.itemCount > 0) content.clip.getItemAt(0).uri else null)?.let { uri ->
						handler.invoke(uri)
					}
				}
				return remaining
			} catch (e: Exception) {
				Log.e("TimelineFragment", "Failed to paste image", e)
				return contentInfo
			}
		}

		companion object {
			val MIME_TYPES = arrayOf("image/*", "video/*")
		}
	}
}