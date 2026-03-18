package dev.kuylar.sakura.client

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log
import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.MatrixClientImpl
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.client.notification
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.TimelineEventAggregation
import de.connect2x.trixnity.client.room.TimelineStateChange
import de.connect2x.trixnity.client.room.getAllState
import de.connect2x.trixnity.client.room.getTimelineEventReactionAggregation
import de.connect2x.trixnity.client.room.getTimelineEventReplaceAggregation
import de.connect2x.trixnity.client.room.message.file
import de.connect2x.trixnity.client.room.message.image
import de.connect2x.trixnity.client.room.message.reply
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.room.message.video
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.relatesTo
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import de.connect2x.trixnity.client.store.repository.room.room
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.client.store.type
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.client.user.getAccountData
import de.connect2x.trixnity.client.verification
import de.connect2x.trixnity.client.verification.ActiveDeviceVerification
import de.connect2x.trixnity.client.verification.ActiveUserVerification
import de.connect2x.trixnity.client.verification.ActiveVerification
import de.connect2x.trixnity.client.verification.ActiveVerificationState
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLogin
import de.connect2x.trixnity.clientserverapi.client.getAccountData
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.clientserverapi.model.authentication.LoginType
import de.connect2x.trixnity.clientserverapi.model.push.PusherData
import de.connect2x.trixnity.clientserverapi.model.push.SetPushers
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.DirectEventContent
import de.connect2x.trixnity.core.model.events.m.MarkedUnreadEventContent
import de.connect2x.trixnity.core.model.events.m.PushRulesEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.push.PushRuleSet
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappingsBuilder
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.events.globalAccountDataOf
import de.connect2x.trixnity.core.serialization.events.messageOf
import de.connect2x.trixnity.core.serialization.events.stateOf
import dev.kuylar.sakura.Utils.compareRoomsByTimestamp
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.customevent.ElementRecentEmojiEventContent
import dev.kuylar.sakura.client.customevent.EmoteRoomsEventContent
import dev.kuylar.sakura.client.customevent.MatrixEmote
import dev.kuylar.sakura.client.customevent.RecentEmoji
import dev.kuylar.sakura.client.customevent.RoomImagePackEventContent
import dev.kuylar.sakura.client.customevent.ShortcodeReactionEventContent
import dev.kuylar.sakura.client.customevent.StickerMessageEventContent
import dev.kuylar.sakura.client.customevent.UserImagePackEventContent
import dev.kuylar.sakura.client.customevent.UserNoteEventContent
import dev.kuylar.sakura.emoji.CustomEmojiCategoryModel
import dev.kuylar.sakura.emoji.CustomEmojiModel
import dev.kuylar.sakura.emoji.RoomCustomEmojiModel
import dev.kuylar.sakura.emoji.RoomEmojiCategoryModel
import dev.kuylar.sakura.emoji.RoomStickerModel
import dev.kuylar.sakura.emojipicker.model.CategoryModel
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.adapter.model.RoomModel
import dev.kuylar.sakura.ui.adapter.timeline.TimelineItem
import dev.kuylar.sakura.ui.models.AttachmentInfo
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Map.entry
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.time.ExperimentalTime
import androidx.room.Room as AndroidRoom

@Singleton
class Matrix {
	val userId: UserId
		get() = client.userId

	@Inject
	lateinit var markdown: MarkdownHandler
	private val context: Context
	private val from: String
	lateinit var client: MatrixClient
	private val activeVerifications = HashMap<String, ActiveVerification>()
	private var recentEmojiCache: List<RecentEmoji> = emptyList()
	private var loadedRecentEmoji = false
	private var syncStarted = false
	lateinit var pushRules: Flow<PushRuleSet>
	private val roomCache = HashMap<RoomId, StateFlow<Room?>>()
	var spaceTree: Flow<List<MatrixSpace>> = emptyFlow()
		private set
	val clientScope = MainScope()

	constructor(context: Context, from: String) {
		this.context = context
		this.from = from
	}

	@Inject
	constructor(application: Application) {
		this.context = application
		this.from = "Hilt"
	}

	val initialized: Boolean
		get() = this::client.isInitialized

	suspend fun initialize(type: String) {
		if (this::client.isInitialized) {
			Log.w("MatrixClient", "initialize() called after client was already initialized.")
			return
		}
		Log.i("MatrixClient", "MatrixClient initialized from $from")
		val (repo, media) = getModules(context, type)
		client = MatrixClient.create(
			repositoriesModule = repo,
			mediaStoreModule = media,
			cryptoDriverModule = CryptoDriverModule.vodozemac(),
			coroutineContext = Dispatchers.Default,
			configuration = ::prepClient
		).getOrThrow()

		// Update filters if required.
		updateFilters()
		// Load this beforehand so we always have a list of recent emojis in hand
		getRecentEmojis()
		listenForPushRules()
		//spaceTree = getSpaceTreeFlow()
	}

	suspend fun login(
		homeserver: String,
		id: IdentifierType.User,
		password: String,
		type: String = "main"
	) {
		val (repo, media) = getModules(context, type)
		client = MatrixClient.create(
			repositoriesModule = repo,
			mediaStoreModule = media,
			cryptoDriverModule = CryptoDriverModule.vodozemac(),
			authProviderData = MatrixClientAuthProviderData.classicLogin(
				baseUrl = Url(homeserver),
				identifier = id,
				password = password,
				loginType = LoginType.Password,
				initialDeviceDisplayName = "Sakura"
			).getOrThrow(),
			coroutineContext = Dispatchers.Default,
			configuration = ::prepClient
		).getOrThrow()
		getRecentEmojis()
		listenForPushRules()
		//spaceTree = getSpaceTreeFlow()
	}

	suspend fun initializeRoomCache() {
		client.room.getAll().collect { rooms ->
			rooms.forEach { (id, flow) ->
				if (roomCache.contains(id)) return@forEach
				val initialValue = flow.first()
				synchronized(roomCache) {
					roomCache[id] = flow.stateIn(
						scope = clientScope,
						started = SharingStarted.Eagerly,
						initialValue = initialValue
					)
				}
			}
		}
	}

	fun getRoom(roomId: RoomId): Room? {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getRoom() called before client was initialized.")
			return null
		}
		return roomCache[roomId]?.value
	}

	fun getRoom(roomId: String) = getRoom(RoomId(roomId))
	fun getRoomFlow(roomId: RoomId) = roomCache[roomId]

	fun getTimeline(onStateChange: suspend (TimelineStateChange<TimelineItem.Event>) -> Unit) =
		client.room.getTimeline(onStateChange) {
			val snapshot = it.first()
			val replyId = snapshot.relatesTo?.replyTo?.eventId
			val replyFlow = replyId?.let { repliedEventId ->
				client.room.getTimelineEvent(snapshot.roomId, repliedEventId)
			}
			val replySnapshot = replyFlow?.first()
			val replyUserId = replySnapshot?.sender
			TimelineItem.Event(
				snapshot,
				combine(
					it,
					client.user.getById(snapshot.roomId, snapshot.sender),
					client.room.getTimelineEventReactionAggregation(
						snapshot.roomId,
						snapshot.eventId
					),
					client.room.getTimelineEventReplaceAggregation(
						snapshot.roomId,
						snapshot.eventId
					),
					replyFlow ?: flowOf<TimelineEvent?>(null),
					replyUserId?.let { user ->
						client.user.getById(snapshot.roomId, user)
					} ?: flowOf<RoomUser?>(null)
				) { snapshots ->
					val event = snapshots[0] as TimelineEvent
					val user = snapshots[1] as RoomUser?
					val reactions = snapshots[2] as TimelineEventAggregation.Reaction
					val replaces = snapshots[3] as TimelineEventAggregation.Replace
					val repliedEvent = snapshots[4] as TimelineEvent?
					val repliedUser = snapshots[5] as RoomUser?
					val reply = if (repliedEvent != null && repliedUser != null)
						TimelineItem.Event.Snapshot.Reply(repliedEvent, repliedUser)
					else null
					TimelineItem.Event.Snapshot(event, user, reactions, replaces, reply)
				}
			)
		}

	fun getIsUnread(roomId: RoomId): Flow<Boolean> {
		return if (roomId == DIRECT_ROOM) {
			combine(roomCache.mapNotNull { it.value.value }
				.filter { it.type != CreateEventContent.RoomType.Space && it.isDirect && isRoomAnOrphan(it.roomId) }
				.map { getIsUnread(it.roomId) }) { it.any { r -> r } }
		} else if (roomId == GROUPS_ROOM) {
			combine(roomCache.mapNotNull { it.value.value }
				.filter { it.type != CreateEventContent.RoomType.Space && !it.isDirect && isRoomAnOrphan(it.roomId) }
				.map { getIsUnread(it.roomId) }) { it.any { r -> r } }
		} else if (roomCache[roomId]?.value?.type == CreateEventContent.RoomType.Space) {
			combine(getSpaceChildren(roomId).map { getIsUnread(it.roomId) }) {
				it.any { v -> v }
			}
		} else client.notification.isUnread(roomId, skipCheck = true)
	}

	fun getNotificationCount(roomId: RoomId, seen: MutableList<RoomId>? = null): Flow<Int> {
		val seenRooms = seen ?: mutableListOf()
		return if (roomId == DIRECT_ROOM) {
			combine(roomCache.mapNotNull { it.value.value }
				.filter { it.type != CreateEventContent.RoomType.Space && it.isDirect && isRoomAnOrphan(it.roomId) }
				.map { getNotificationCount(it.roomId) }) { it.sum() }
		} else if (roomId == GROUPS_ROOM) {
			combine(roomCache.mapNotNull { it.value.value }
				.filter { it.type != CreateEventContent.RoomType.Space && !it.isDirect && isRoomAnOrphan(it.roomId) }
				.map { getNotificationCount(it.roomId) }) { it.sum() }
		} else if (roomCache[roomId]?.value?.type == CreateEventContent.RoomType.Space) {
			val children = getSpaceChildren(roomId).filterNot { seenRooms.contains(it.roomId) }
			seenRooms.addAll(children.map { it.roomId })
			combine(children.map { getNotificationCount(it.roomId, seenRooms) }) {
				it.sum()
			}
		} else client.notification.getCount(roomId, includeDismissed = false)
	}

	suspend fun getTopLevelSpaces(): List<Room> =
		client.room.getAll().flattenValues().first().let { rooms ->
			val allSpaces = rooms.filter { it.type == CreateEventContent.RoomType.Space }

			return if (allSpaces.isEmpty()) {
				emptyList()
			} else {
				val topLevel = allSpaces.filter { space ->
					!allSpaces.any { it.childEvents?.containsKey(space.roomId.full) == true }
				}
				topLevel.sortedWith(
					compareBy<Room> {
						it.orderEvent?.order == null
					}.thenBy {
						it.orderEvent?.order ?: Long.MAX_VALUE
					}.thenBy {
						it.roomId.full
					}
				)
			}
		}

	private fun isRoomAnOrphan(roomId: RoomId) =
		!roomCache.values.flatMap { it.value?.childEvents?.keys ?: emptySet() }
			.contains(roomId.full)

	fun getSpaceChildren(roomId: RoomId) = getSpaceChildren(roomCache[roomId]?.value)
	fun getSpaceChildren(room: Room?): List<Room> {
		if (room == null) return emptyList()
		if (room.type != CreateEventContent.RoomType.Space) return emptyList()
		return (room.childEvents?.keys ?: emptySet()).mapNotNull { roomCache[RoomId(it)]?.value }
	}

	@OptIn(ExperimentalTime::class)
	fun getSpaceChildrenRecursive(roomId: RoomId) = when (roomId) {
		DIRECT_ROOM -> {
			val children = roomCache.mapNotNull { it.value.value }
				.filter { it.type != CreateEventContent.RoomType.Space && it.isDirect && isRoomAnOrphan(it.roomId) }
				.sortedWith(compareRoomsByTimestamp())
				.map { RoomModel(it.roomId, it, this) }
			MatrixSpace(null, children, emptyList())
		}

		GROUPS_ROOM -> {
			val children = roomCache.mapNotNull { it.value.value }
				.filter { it.type != CreateEventContent.RoomType.Space && !it.isDirect && isRoomAnOrphan(it.roomId) }
				.sortedWith(compareRoomsByTimestamp())
				.map { RoomModel(it.roomId, it, this) }
			MatrixSpace(null, children, emptyList())
		}

		else -> getSpaceChildrenRecursive(roomCache[roomId]?.value)
	}

	fun getSpaceChildrenRecursive(room: Room?, seenRooms: MutableList<RoomId>? = null): MatrixSpace? {
		val visitedRooms = seenRooms ?: mutableListOf()
		if (room == null) return null
		val children = getSpaceChildren(room)
		return MatrixSpace(
			room,
			children
				.filter { r ->
					r.type != CreateEventContent.RoomType.Space && !visitedRooms.contains(r.roomId)
				}
				.map { r ->
					visitedRooms.add(r.roomId)
					RoomModel(
						r.roomId,
						r,
						this
					)
				},
			children
				.filter { s ->
					s.type == CreateEventContent.RoomType.Space && !visitedRooms.contains(s.roomId)
				}
				.mapNotNull { s ->
					visitedRooms.add(s.roomId)
					getSpaceChildrenRecursive(s, visitedRooms)
				}
		)
	}

	suspend fun getUser(userId: UserId, roomId: RoomId): RoomUser? {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getUser() called before client was initialized.")
			return null
		}
		return client.user.getById(roomId, userId).first()
	}

	suspend fun getEvent(roomId: RoomId, eventId: EventId, retryCount: Int = 0): TimelineEvent? {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getEvent() called before client was initialized.")
			return null
		}
		val res = client.room.getTimelineEvent(roomId, eventId).firstOrNull()
		if (res == null && retryCount > 0) {
			client.syncOnce()
			return getEvent(roomId, eventId, retryCount - 1)
		}
		return res
	}

	suspend fun getEvent(roomId: String, eventId: String) =
		getEvent(RoomId(roomId), EventId(eventId))

	suspend fun sendMessage(
		roomId: String,
		msg: String,
		context: Context,
		replyTo: EventId? = null,
		attachment: AttachmentInfo? = null,
	) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "sendMessage() called before client was initialized.")
			return
		}
		client.room.sendMessage(RoomId(roomId)) {
			replyTo?.let { reply(it, null) }
			val attachmentFlow = attachment?.getAsFlow(context)
			if (attachmentFlow != null) {
				when (attachment.contentType.split('/')[0]) {
					"image" -> image(
						body = markdown.inputToPlaintext(msg),
						format = "org.matrix.custom.html",
						formattedBody = markdown.inputToHtml(msg),
						image = attachmentFlow,
						fileName = attachment.name,
						type = ContentType.parse(attachment.contentType),
						size = attachment.size
					)

					"video" -> video(
						body = markdown.inputToPlaintext(msg),
						format = "org.matrix.custom.html",
						formattedBody = markdown.inputToHtml(msg),
						video = attachmentFlow,
						fileName = attachment.name,
						type = ContentType.parse(attachment.contentType),
						size = attachment.size
						// TODO: Thumbnail
					)

					else -> file(
						body = markdown.inputToPlaintext(msg),
						format = "org.matrix.custom.html",
						formattedBody = markdown.inputToHtml(msg),
						file = attachmentFlow,
						fileName = attachment.name,
						type = ContentType.parse(attachment.contentType),
						size = attachment.size
					)
				}
			} else {
				text(
					body = markdown.inputToPlaintext(msg),
					format = "org.matrix.custom.html",
					formattedBody = markdown.inputToHtml(msg)
				)
			}
		}
	}

	suspend fun sendSticker(roomId: RoomId, sticker: MatrixEmote, replyingEvent: EventId? = null) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "sendSticker() called before client was initialized.")
			return
		}
		client.room.sendMessage(roomId) {
			content(
				StickerMessageEventContent(
					body = sticker.body,
					url = sticker.url,
					info = sticker.info,
					relatesTo = replyingEvent?.let { RelatesTo.Reply(RelatesTo.ReplyTo(it)) }
				)
			)
		}
	}

	suspend fun editEvent(roomId: String, eventId: EventId, msg: String) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "editEvent() called before client was initialized.")
			return
		}
		client.room.sendMessage(RoomId(roomId)) {
			content(
				RoomMessageEventContent.TextBased.Text(
					"* ${markdown.inputToPlaintext(msg)}",
					format = "org.matrix.custom.html",
					formattedBody = markdown.inputToHtml(msg),
					relatesTo = RelatesTo.Replace(
						eventId,
						newContent = RoomMessageEventContent.TextBased.Text(
							markdown.inputToPlaintext(msg),
							format = "org.matrix.custom.html",
							formattedBody = markdown.inputToHtml(msg)
						)
					)
				)
			)
		}
	}

	suspend fun reactToEvent(
		roomId: RoomId,
		eventId: EventId,
		reaction: String,
		shortcode: String? = null
	) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "reactToEvent() called before client was initialized.")
			return
		}
		if (reaction.isBlank()) return
		val sc = shortcode?.trim(':')
		appendRecentEmoji(reaction)
		client.room.sendMessage(roomId) {
			content(
				ShortcodeReactionEventContent(
					relatesTo = RelatesTo.Annotation(eventId, key = reaction),
					shortcode = sc,
					beeperShortcode = sc?.let { ":$it:" }
				)
			)
		}
	}

	suspend fun redactEvent(roomId: RoomId, eventId: EventId, reason: String? = null) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "redactEvent() called before client was initialized.")
			return
		}
		client.api.room.redactEvent(roomId, eventId, reason).getOrThrow()
	}

	suspend fun startSync() {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "startSync() called before client was initialized.")
			return
		}
		if (syncStarted) {
			Log.w(
				"MatrixClient",
				"startSync() called after sync was already started from elsewhere."
			)
			return
		}
		syncStarted = true
		client.startSync()
		Log.i("MatrixClient", "Sync started from $from")
	}

	suspend fun addSyncStateListener(listener: ((SyncState) -> Unit)) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "addSyncStateListener() called before client was initialized.")
			return
		}
		client.syncState.collect {
			runOnUiThread {
				listener.invoke(it)
			}
		}
	}

	suspend fun registerFcmPusher(token: String) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "registerFcmPusher() called before client was initialized.")
			return
		}
		client.api.push.setPushers(
			SetPushers.Request.Set(
				"dev.kuylar.sakura.android",
				token,
				"http",
				"Sakura",
				Build.MANUFACTURER + " " + Build.MODEL,
				"en", // TODO: Get from context or smth
				PusherData(
					// TODO: Make sure event_id_only doesn't break everything
					//format = "event_id_only",
					url = "https://sakurapush.kuylar.dev/_matrix/push/v1/notify"
				)
				// TODO: Look into append and profileTag
			)
		).getOrThrow()
	}

	fun getVerification(id: String): ActiveVerification? {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getVerification() called before client was initialized.")
			return null
		}
		synchronized(activeVerifications) {
			return activeVerifications[id]
		}
	}

	suspend fun addOnDeviceVerificationRequestListener(listener: ((ActiveDeviceVerification) -> Unit)) {
		if (!this::client.isInitialized) {
			Log.w(
				"MatrixClient",
				"addOnDeviceVerificationRequestListener() called before client was initialized."
			)
			return
		}
		client.verification.activeDeviceVerification.collect {
			if (it == null) return@collect
			val id = it.transactionId ?: "deviceVerification"
			synchronized(activeVerifications) {
				activeVerifications[id] = it
			}
			runOnUiThread {
				listener.invoke(it)
			}
			coroutineScope {
				launch {
					it.state.collect { state ->
						when (state) {
							is ActiveVerificationState.Cancel -> synchronized(activeVerifications) {
								activeVerifications.remove(id)
							}

							ActiveVerificationState.Done -> synchronized(activeVerifications) {
								activeVerifications.remove(id)
							}

							else -> {}
						}
					}
				}
			}
		}
	}

	suspend fun addOnUserVerificationRequestListener(listener: ((ActiveUserVerification) -> Unit)) {
		if (!this::client.isInitialized) {
			Log.w(
				"MatrixClient",
				"addOnUserVerificationRequestListener() called before client was initialized."
			)
			return
		}
		client.verification.activeUserVerifications.collect {
			it.forEach { v ->
				runOnUiThread {
					listener.invoke(v)
				}
			}
		}
	}

	private fun runOnUiThread(block: () -> Unit) {
		Handler(context.mainLooper).post(block)
	}

	private fun startUpdatingRecentEmojiCache() {
		if (!loadedRecentEmoji) {
			suspendThread {
				updateRecentEmojiCache()
			}
		}
	}

	private fun listenForPushRules() {
		pushRules = flow {
			client.user.getAccountData<PushRulesEventContent>().collect {
				it?.let { event ->
					event.global?.let { set ->
						emit(set)
					}
				}
			}
		}
	}

	fun getRecentEmojis(): List<RecentEmoji> {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getRecentEmojis() called before client was initialized.")
			return emptyList()
		}
		startUpdatingRecentEmojiCache()
		return recentEmojiCache.sortedByDescending { it.count }
	}

	suspend fun appendRecentEmoji(emoji: String) {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "appendRecentEmoji() called before client was initialized.")
			return
		}
		startUpdatingRecentEmojiCache()
		val recentEmojis = recentEmojiCache.toMutableList()
		val index = recentEmojis.indexOfFirst { it.emoji == emoji }
		if (index >= 0) {
			recentEmojis[index].count++
		} else {
			recentEmojis += RecentEmoji(emoji, 1)
		}
		recentEmojiCache = recentEmojis.toList()
		client.api.user.setAccountData(ElementRecentEmojiEventContent().apply {
			this.recentEmoji = recentEmojis.sortedByDescending { it.count }
		}, userId)
	}

	suspend fun getRoomImagePacks(roomId: RoomId): Map<String, RoomImagePackEventContent> {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getRoomImagePacks() called before client was initialized.")
			return emptyMap()
		}
		val res = mutableMapOf<String, RoomImagePackEventContent>()
		return try {
			client.room.getAllState<RoomImagePackEventContent>(roomId).firstOrNull()
				?.mapValues { it.value.firstOrNull() }
				?.values?.filterNotNull()?.associateByTo(res, { it.stateKey }, { it.content })
			res
		} catch (_: Exception) {
			emptyMap()
		}
	}

	suspend fun getRoomEmoji(roomId: RoomId): Map<RoomEmojiCategoryModel, List<CustomEmojiModel>> {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getRoomEmoji() called before client was initialized.")
			return emptyMap()
		}
		startUpdatingRecentEmojiCache()
		val packs = getRoomImagePacks(roomId)
		return packs.mapNotNull {
			it.let {
				Pair(
					RoomEmojiCategoryModel(
						roomId,
						it.key,
						it.value.pack?.displayName ?: it.key,
					),
					it.value.images
						?.filter { e -> e.value.usage?.contains("emoticon") ?: true }
						?.map { emoji ->
							RoomCustomEmojiModel(emoji.value.url, emoji.key)
						} ?: emptyList()
				)
			}
		}.toMap()
	}

	suspend fun getSavedEmoji(): Map<CategoryModel, List<CustomEmojiModel>> {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getSavedEmoji() called before client was initialized.")
			return emptyMap()
		}
		startUpdatingRecentEmojiCache()
		val savedRooms =
			client.user.getAccountData<EmoteRoomsEventContent>().firstOrNull() ?: return emptyMap()
		val res = mutableMapOf<CategoryModel, List<CustomEmojiModel>>()
		savedRooms.rooms?.forEach { (roomId, packs) ->
			val roomEmojis = getRoomEmoji(RoomId(roomId))
			if (packs.isEmpty()) res.putAll(roomEmojis)
			else res.putAll(roomEmojis.filterKeys { it.stateKey in packs })
		}
		return res.filter { it.value.isNotEmpty() }
	}

	suspend fun getAccountEmojiPack(): UserImagePackEventContent? {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getAccountEmojiPack() called before client was initialized.")
			return null
		}
		return client.user.getAccountData<UserImagePackEventContent>().firstOrNull()
	}

	suspend fun getAccountEmoji(): Map.Entry<CategoryModel, List<CustomEmojiModel>>? {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getAccountEmoji() called before client was initialized.")
			return null
		}
		startUpdatingRecentEmojiCache()
		val userEmojis = getAccountEmojiPack()
		if (!userEmojis?.images.isNullOrEmpty()) {
			val cat =
				CustomEmojiCategoryModel(userEmojis.pack?.displayName?.takeIf { it.isNotBlank() }
					?: "#!accountEmojiPack")
			val images = userEmojis.images?.filter { it.value.usage?.contains("emoticon") ?: true }
			return entry(
				cat,
				images?.map { RoomCustomEmojiModel(it.value.url, it.key) } ?: emptyList())
		}
		return null
	}

	suspend fun getRoomStickers(roomId: RoomId): Map<RoomEmojiCategoryModel, List<CustomEmojiModel>> {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getRoomStickers() called before client was initialized.")
			return emptyMap()
		}
		val packs = getRoomImagePacks(roomId)
		return packs.mapNotNull {
			it.let {
				Pair(
					RoomEmojiCategoryModel(
						roomId,
						it.key,
						it.value.pack?.displayName ?: it.key,
					),
					it.value.images
						?.filter { e -> e.value.usage?.contains("sticker") ?: true }
						?.map { emoji -> RoomStickerModel(emoji.key, emoji.value) } ?: emptyList()
				)
			}
		}.toMap()
	}

	suspend fun getAccountStickers(): Map.Entry<CategoryModel, List<CustomEmojiModel>>? {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getAccountStickers() called before client was initialized.")
			return null
		}
		val userEmojis = getAccountEmojiPack()
		if (!userEmojis?.images.isNullOrEmpty()) {
			val cat =
				CustomEmojiCategoryModel(userEmojis.pack?.displayName?.takeIf { it.isNotBlank() }
					?: "#!accountStickerPack")
			val images = userEmojis.images?.filter { it.value.usage?.contains("sticker") ?: true }
			return entry(cat, images?.map { RoomStickerModel(it.key, it.value) } ?: emptyList())
		}
		return null
	}

	suspend fun getSavedStickers(): Map<CategoryModel, List<CustomEmojiModel>> {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "getSavedStickers() called before client was initialized.")
			return emptyMap()
		}
		val savedRooms =
			client.user.getAccountData<EmoteRoomsEventContent>().firstOrNull() ?: return emptyMap()
		val res = mutableMapOf<CategoryModel, List<CustomEmojiModel>>()
		savedRooms.rooms?.forEach { (roomId, packs) ->
			val roomEmojis = getRoomStickers(RoomId(roomId))
			if (packs.isEmpty()) res.putAll(roomEmojis)
			else res.putAll(roomEmojis.filterKeys { it.stateKey in packs })
		}
		return res.filter { it.value.isNotEmpty() }
	}

	// TODO: Create DM channel here too
	suspend fun getDmChannel(userId: UserId): RoomId? {
		return client.user.getAccountData<DirectEventContent>().firstOrNull()?.mappings?.get(userId)
			?.firstOrNull()
	}

	private suspend fun updateRecentEmojiCache() {
		if (!this::client.isInitialized) {
			Log.w("MatrixClient", "updateRecentEmojiCache() called before client was initialized.")
			return
		}
		try {
			client.user.getAccountData<ElementRecentEmojiEventContent>().collect { data ->
				loadedRecentEmoji = true
				data?.let {
					synchronized(recentEmojiCache) {
						recentEmojiCache = it.recentEmoji ?: emptyList()
					}
				}
			}
		} catch (e: Exception) {
			Log.e("MatrixClient", "Failed to update recent emoji cache", e)
			loadedRecentEmoji = false
		}
	}

	suspend fun updateFilters(force: Boolean = false) {
		try {
			// This is f-cked up.
			// Not a good way to get the filter ID imo, but it works
			(client as? MatrixClientImpl)?.di?.get<AccountStore>()?.let { accountStore ->
				val account = accountStore.getAccount() ?: return@let
				val filterId = account.filter?.syncFilterId ?: return@let
				val filter =
					client.api.user.getFilter(account.userId, filterId).getOrNull() ?: return@let
				var changed = force

				fun Set<String>.add(value: String): Set<String> {
					if (contains(value)) return this
					changed = true
					return plus(value)
				}

				val newFilter = filter.copy(
					accountData = filter.accountData?.copy(
						types = filter.accountData?.types
							?.add("io.element.recent_emoji")
							?.add("im.ponies.emote_rooms")
							?.add("im.ponies.user_emotes")
							?.add("dev.kuylar.sakura.user_notes")
					),
					room = filter.room?.copy(
						accountData = filter.room?.accountData?.copy(
							types = filter.room?.accountData?.types
								?.add("org.matrix.msc3230.space_order")
						),
						state = filter.room?.state?.copy(
							types = filter.room?.state?.types
								?.add("m.space.parent")
								?.add("m.space.child")
								?.add("im.ponies.room_emotes"),
							lazyLoadMembers = false // TODO: Make this configurable
						),
						timeline = filter.room?.timeline?.copy(
							types = filter.room?.timeline?.types
								?.add("m.space.parent")
								?.add("m.space.child")
								?.add("im.ponies.room_emotes")
								?.add("m.reaction")
						)
					)
				)
				if (!changed) return@let
				Log.i(
					"MatrixClient",
					"New event types have been added since the last filter update, updating filter"
				)
				val newFilterId = client.api.user.setFilter(account.userId, newFilter).getOrThrow()
				Log.i("MatrixClient", "Filter updated: $newFilterId")
				accountStore.updateAccount { it?.copy(filter = it.filter?.copy(syncFilterId = newFilterId)) }
			}
		} catch (e: Exception) {
			Log.e("MatrixClient", "Failed to update filters", e)
		}
	}

	suspend fun markRead(roomId: RoomId, eventId: EventId) {
		val markedUnreadEventContent =
			client.api.room.getAccountData<MarkedUnreadEventContent>(roomId, client.userId)
				.getOrNull()
		if (markedUnreadEventContent != null)
			client.api.room.setAccountData(MarkedUnreadEventContent(false), roomId, client.userId)
		client.api.room.setReceipt(roomId, eventId)
	}

	fun getNotifications() = client.notification.getAll()
	suspend fun dismissNotification(id: String) = client.notification.dismiss(id)
	suspend fun getRoomPresence(room: Room): Flow<UserPresence?>? {
		if (room.isDirect) {
			val directEvent =
				client.user.getAccountData<DirectEventContent>().firstOrNull() ?: return null
			var userId: UserId? = null
			for ((user, rooms) in directEvent.mappings) {
				if (rooms == null) continue
				if (rooms.contains(room.roomId)) {
					userId = user
					break
				}
			}
			if (userId == null) return null
			return client.user.getPresence(userId)
		}
		return null
	}

	companion object {
		@SuppressLint("StaticFieldLeak")
		private lateinit var instance: Matrix

		val DIRECT_ROOM = RoomId("!dms:native.sakurachat.app")
		val GROUPS_ROOM = RoomId("!groups:native.sakurachat.app")

		fun getAvailableAccounts(context: Context): List<String> {
			return context.databaseList().map { it.replace("trixnity-", "") }
				.filterNot { it.endsWith("-wal") || it.endsWith("-shm") }
		}

		private fun getModules(
			context: Context,
			type: String = "main"
		): Pair<RepositoriesModule, MediaStoreModule> {
			val repositoriesModule = RepositoriesModule.room(
				AndroidRoom.databaseBuilder(
					context,
					TrixnityRoomDatabase::class.java,
					"trixnity-$type"
				)
			)
			val mediaStoreModule = MediaStoreModule.okio(
				Path(
					context.cacheDir.absolutePath,
					"mediaStore-$type"
				).absolutePathString().toPath(true)
			)
			return Pair(repositoriesModule, mediaStoreModule)
		}

		private fun prepModules(): Module {
			val customMappings = EventContentSerializerMappingsBuilder().apply {
				stateOf<RoomImagePackEventContent>("im.ponies.room_emotes")
				messageOf<RoomMessageEventContent>("m.room.message")
				messageOf<StickerMessageEventContent>("m.sticker")
				messageOf<ShortcodeReactionEventContent>("m.reaction")
				globalAccountDataOf<ElementRecentEmojiEventContent>("io.element.recent_emoji")
				globalAccountDataOf<EmoteRoomsEventContent>("im.ponies.emote_rooms")
				globalAccountDataOf<UserImagePackEventContent>("im.ponies.user_emotes")
				globalAccountDataOf<UserNoteEventContent>("dev.kuylar.sakura.user_notes")
			}.build()

			return module {
				single<EventContentSerializerMappings> {
					EventContentSerializerMappings.default + customMappings
				}
			}
		}

		private fun prepClient(config: MatrixClientConfiguration) {
			config.deleteRooms = MatrixClientConfiguration.DeleteRooms.OnLeave
			config.lastRelevantEventFilter = ::relevantEventFilter
			config.modulesFactories += ::prepModules
		}

		private fun relevantEventFilter(event: ClientEvent.RoomEvent<*>): Boolean {
			if (event !is ClientEvent.RoomEvent.MessageEvent) return false
			val content = event.content
			if (content is RoomMessageEventContent) {
				// Don't mark edits as unread messages
				if (content.relatesTo?.relationType == RelationType.Replace) return false
				// Don't mark reactions as unread messages
				if (content.relatesTo?.relationType == RelationType.Annotation) return false
				return true
			}
			return false
		}

		fun startLoginFlow(homeserver: Uri): MatrixClientServerApiClientImpl {
			return MatrixClientServerApiClientImpl(Url(homeserver.toString()))
		}

		fun isInitialized() = this::instance.isInitialized

		fun setClient(client: Matrix) {
			if (this::instance.isInitialized) {
				Log.w(
					"MatrixClient",
					"setClient called after instance was initialized!",
					Exception()
				)
			}
			instance = client
		}

		@Deprecated("Only use as a last resort for when you can't realistically inject a Matrix instance")
		fun getClient(): Matrix {
			if (!this::instance.isInitialized) throw IllegalStateException("Client not initialized")
			return instance
		}
	}
}