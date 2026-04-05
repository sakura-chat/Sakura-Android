package dev.kuylar.sakura.ui.fragment

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.client.user.canSendEvent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.bodyWithoutFallback
import de.connect2x.trixnity.core.model.events.m.room.formattedBodyWithoutFallback
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.bytesToString
import dev.kuylar.sakura.Utils.getBubbleMetadata
import dev.kuylar.sakura.Utils.getName
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.Utils.toNotificationPerson
import dev.kuylar.sakura.Utils.toShortcut
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.MatrixEmote
import dev.kuylar.sakura.databinding.FragmentTimelineBinding
import dev.kuylar.sakura.emoji.RoomCustomEmojiModel
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.BackButtonListener
import dev.kuylar.sakura.ui.adapter.PickerPagerAdapter
import dev.kuylar.sakura.ui.adapter.timeline.TimelineRecyclerAdapter
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
class TimelineFragment : Fragment(), MenuProvider, BackButtonListener {
	private lateinit var binding: FragmentTimelineBinding
	private lateinit var roomId: String
	private var eventId: String? = null

	@Inject
	lateinit var client: Matrix

	@Inject
	lateinit var markdown: MarkdownHandler
	private lateinit var timelineAdapter: TimelineRecyclerAdapter
	private lateinit var visualMediaPicker: ActivityResultLauncher<PickVisualMediaRequest>
	private lateinit var prefs: SharedPreferences
	private var isLoadingMore = false
	private var editingEvent: EventId? = null
	private var replyingEvent: EventId? = null
	private var typingUsersJob: Job? = null
	private var lastReadEventId: EventId? = null
	private var clearCacheUnlocked = false
	private var attachment: AttachmentInfo? = null
	private var typing = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let { args ->
			args.getString("roomId")?.let {
				roomId = it
			}
			eventId = args.getString("eventId")
		}
		visualMediaPicker =
			registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
				if (uri != null)
					loadAttachmentFromUri(uri)
			}
		prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
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

		PanelsChildGestureRegionObserver.Provider.get().unregister(binding.picker)
		PanelsChildGestureRegionObserver.Provider.get().register(binding.picker)
		setUi()
	}

	override fun onPause() {
		PanelsChildGestureRegionObserver.Provider.get().unregister(binding.picker)
		super.onPause()
	}

	fun setUi() {
		val room = client.getRoom(roomId)
		if (room == null) {
			binding.root.postDelayed(50) {
				setUi()
			}
			return
		}
		lifecycleScope.launch {
			(activity as? AppCompatActivity)?.supportActionBar?.title =
				room.getName(requireContext(), client)
		}

		val menuHost: MenuHost = requireActivity()
		menuHost.addMenuProvider(
			this,
			viewLifecycleOwner,
			Lifecycle.State.RESUMED
		)

		timelineAdapter = TimelineRecyclerAdapter(
			this,
			RoomId(roomId),
			client,
			markdown,
			binding.timelineRecycler,
			eventId?.let { EventId(it) }
		) {
			binding.loading.visibility = if (it) View.VISIBLE else View.GONE
			if (!it) isLoadingMore = false
		}
		binding.timelineRecycler.layoutManager =
			LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, true)
		binding.timelineRecycler.adapter = timelineAdapter
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
			if (binding.picker.isVisible) {
				PanelsChildGestureRegionObserver.Provider.get().unregister(binding.picker)
				binding.picker.visibility = View.GONE
			} else {
				binding.picker.visibility = View.VISIBLE
				binding.picker.post {
					PanelsChildGestureRegionObserver.Provider.get().register(binding.picker)
				}
			}
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
				val typingNew = !it.isNullOrBlank()
				if (typing != typingNew) {
					typing = typingNew
					if (prefs.getBoolean("textedit_typing", true))
						client.client.api.room.setTyping(RoomId(roomId), client.userId, typing)
				}
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
					0 -> ""
					1 -> getString(R.string.typing_indicator_1, users[0].name)
					2 -> getString(R.string.typing_indicator_2, users[0].name, users[1].name)
					3 -> getString(
						R.string.typing_indicator_3,
						users[0].name,
						users[1].name,
						users[2].name
					)

					else -> getString(R.string.typing_indicator_more, users[0].name, users[1].name, users.size - 2)
				}
				activity?.runOnUiThread {
					binding.typingIndicator.visibility =
						if (users.isEmpty()) View.GONE else View.VISIBLE
					binding.typingIndicatorText.text = text
				}
			}
		}
		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
				client.client.user.canSendEvent<RoomMessageEventContent>(RoomId(roomId)).collect { canSend ->
					activity?.runOnUiThread {
						binding.input.hint = getString(
							if (!canSend) R.string.room_hint_no_permission
							else if (room.encrypted) R.string.room_hint_encrypted
							else R.string.room_hint_unencrypted
						)
						val vis = if (canSend) View.VISIBLE else View.GONE
						binding.input.isEnabled = canSend
						binding.buttonSend.visibility = vis
						binding.buttonEmoji.visibility = vis
						binding.buttonAttachment.visibility = vis
					}
				}
			}
		}
		binding.root.postDelayed(50) {
			binding.timelineRecycler.invalidate()
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
					attachment = attachment,
					useMarkdown = prefs.getBoolean("textedit_markdown", true)
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

			"/notification bubble list" -> {
				Log.i("TimelineFragment", "Shortcuts:")
				ShortcutManagerCompat.getDynamicShortcuts(requireContext()).forEachIndexed { i, it ->
					Log.i("TimelineFragment", "- [$i] [${it.id}] ${it.longLabel ?: it.shortLabel}")
				}
				true
			}

			"/notification bubble clear" -> {
				ShortcutManagerCompat.removeAllDynamicShortcuts(requireContext())
				true
			}

			"/notification bubble" -> {
				lifecycleScope.launch {
					val sender = client.getUser(client.userId, RoomId(roomId)) ?: return@launch
					val room = client.getRoom(roomId) ?: return@launch
					val channelId = "dev.kuylar.sakura.room.${roomId}"
					val channel = NotificationChannel(
						"dev.kuylar.sakura.room.${roomId}",
						room.getName(requireContext(), client),
						NotificationManager.IMPORTANCE_HIGH
					).apply {
						setConversationId("dev.kuylar.sakura.room", room.roomId.full)
						setAllowBubbles(true)
					}
					val notification = NotificationCompat.Builder(requireContext(), channelId).apply {
						val person = sender.toNotificationPerson(requireContext(), client)

						val shortcut = room.toShortcut(requireContext(), client)
						val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
						if (ShortcutManagerCompat.getMaxShortcutCountPerActivity(requireContext()) > shortcuts.size)
							ShortcutManagerCompat.addDynamicShortcuts(requireContext(), listOf(shortcut))

						val style = NotificationCompat.MessagingStyle(person)
						style.isGroupConversation = !room.isDirect

						style.addMessage("Bubble notification!", System.currentTimeMillis(), person)
						style.setConversationTitle(room.getName(requireContext(), client))
						style.messages
							.mapNotNull { it.person }
							.distinctBy { it.key }.forEach {
								addPerson(it)
							}

						setContentTitle(room.getName(requireContext(), client))
						setContentText("Bubble notification!")
						//setContentIntent(
						//	PendingIntent.getActivity(
						//		requireContext(), 0, event.getIntent(requireContext()),
						//		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
						//	)
						//)
						setStyle(style)
						setShortcutId(shortcut.id)
						setBubbleMetadata(room.getBubbleMetadata(requireContext(), client))
						setLocusId(LocusIdCompat(roomId))
						setPriority(NotificationCompat.PRIORITY_MAX)
						setSmallIcon(R.drawable.ic_notification_icon)
						setCategory(NotificationCompat.CATEGORY_MESSAGE)
						setAutoCancel(true)
						setOnlyAlertOnce(false)
					}.build()
					with(NotificationManagerCompat.from(requireContext())) {
						// Check if we have the notification permission
						if (ContextCompat.checkSelfPermission(
								requireContext(),
								Manifest.permission.POST_NOTIFICATIONS
							) != PackageManager.PERMISSION_GRANTED
						) return@with
						createNotificationChannel(channel)
						notify(channelId.hashCode(), notification)
					}
				}
				true
			}

			"/reinit" -> {
				Log.i("TimelineFragment", "Reinitializing the recycler adapter")
				timelineAdapter.dispose()
				timelineAdapter = TimelineRecyclerAdapter(
					this,
					RoomId(roomId),
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
		if (bottom <= 0) {
			onKeyboardClosed()
		} else {
			onKeyboardOpened()
			(binding.picker.layoutParams as LinearLayout.LayoutParams).height =
				max(resources.displayMetrics.density.toInt() * 300, bottom)
		}
	}

	override fun onBackPressed(): Boolean {
		requireContext().getSystemService<InputMethodManager>()
			?.hideSoftInputFromWindow(binding.input.windowToken, 0)
		if (binding.picker.isVisible) {
			binding.picker.visibility = View.GONE
			PanelsChildGestureRegionObserver.Provider.get().unregister(binding.picker)
			return true
		}
		return false
	}

	private fun checkAndLoadMoreIfNeeded(recyclerView: RecyclerView) {
		if (isLoadingMore) return
		val layoutManager = recyclerView.layoutManager as LinearLayoutManager
		val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
		val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
		val totalItemCount = layoutManager.itemCount

		if (totalItemCount == 0 || !timelineAdapter.isReady) return
		if (!isLoadingMore) {
			if (allItemsAreVisible(firstVisibleItem, lastVisibleItem, totalItemCount)
				&& timelineAdapter.canLoadMoreBefore()
			) {
				isLoadingMore = true
				lifecycleScope.launch {
					timelineAdapter.loadMoreBefore()
				}
				return
			}
			if (lastVisibleItem > totalItemCount - 10 && timelineAdapter.canLoadMoreBefore()) {
				isLoadingMore = true
				lifecycleScope.launch {
					timelineAdapter.loadMoreBefore()
				}
				return
			}
			if (firstVisibleItem == 0 && timelineAdapter.canLoadMoreAfter()) {
				isLoadingMore = true
				lifecycleScope.launch {
					timelineAdapter.loadMoreAfter()
				}
				return
			}
		}
		if (firstVisibleItem == 0) {
			if (timelineAdapter.lastEventId != null && lastReadEventId != timelineAdapter.lastEventId) {
				lastReadEventId = timelineAdapter.lastEventId
				lifecycleScope.launch {
					Log.i("TimelineFragment", "Marking event ${timelineAdapter.lastEventId!!} as read")
					client.markRead(RoomId(roomId), timelineAdapter.lastEventId!!)
				}
			}
		}
	}

	private fun onKeyboardClosed() {

	}

	private fun onKeyboardOpened() {
		if (binding.picker.isVisible && binding.pickerTabs.selectedTabPosition == 1) return
		binding.picker.visibility = View.GONE
		PanelsChildGestureRegionObserver.Provider.get().unregister(binding.picker)
	}

	override fun onDestroy() {
		if (this::timelineAdapter.isInitialized)
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

	companion object {
		private fun allItemsAreVisible(first: Int, last: Int, total: Int) =
			first == 0 && last == total - 1
	}
}