package dev.kuylar.sakura.client

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log
import dev.kuylar.sakura.Utils
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.customevent.*
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.login
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getAccountData
import net.folivo.trixnity.client.room.getAllState
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase
import net.folivo.trixnity.client.store.repository.room.createRoomRepositoriesModule
import net.folivo.trixnity.client.store.type
import net.folivo.trixnity.client.user
import net.folivo.trixnity.client.user.getAccountData
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import net.folivo.trixnity.client.verification.ActiveUserVerification
import net.folivo.trixnity.client.verification.ActiveVerification
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.push.PusherData
import net.folivo.trixnity.clientserverapi.model.push.SetPushers
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.globalAccountDataOf
import net.folivo.trixnity.core.serialization.events.messageOf
import net.folivo.trixnity.core.serialization.events.roomAccountDataOf
import net.folivo.trixnity.core.serialization.events.stateOf
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import androidx.room.Room as AndroidRoom

class Matrix(val context: Context, val client: MatrixClient) {
	val userId: UserId
		get() = client.userId
	private val activeVerifications = HashMap<String, ActiveVerification>()
	private var recentEmojiCache: List<RecentEmoji> = emptyList()
	private var loadedRecentEmoji = false

	suspend fun getRoom(roomId: String): Room? {
		return client.room.getById(RoomId(roomId)).first()
	}

	suspend fun getRooms(): List<Room> {
		return client.room.getAll().flattenValues().first()
			.filter { it.membership != Membership.LEAVE && it.membership != Membership.BAN }
			.toList()
	}

	fun getRoomsFlow(): Flow<List<Room>> {
		return flow {
			client.room.getAll().flattenValues().collect {
				emit(it.filter { it.membership != Membership.LEAVE && it.membership != Membership.BAN }
					.toList())
			}
		}
	}

	@OptIn(ExperimentalTime::class)
	suspend fun getSpaceTree(): List<MatrixSpace> {
		val allRooms = getRooms()
		val unownedRooms = allRooms.associateByTo(HashMap()) { it.roomId }
		val parentToChildren = HashMap<String, MutableList<RoomId>>()
		val roomOrderMap = HashMap<String, Long>()
		val topLevelSpaces = mutableListOf<Room>()

		suspend fun buildSpaceTree(space: Room?): MatrixSpace {
			if (space == null) return MatrixSpace(
				null,
				unownedRooms.values
					.sortedByDescending { it.lastRelevantEventTimestamp?.toEpochMilliseconds() }
					.toList(),
				emptyList(),
				Long.MIN_VALUE
			)

			val childrenState = client.room.getAllState<SpaceChildrenEventContent>(space.roomId)
				.firstOrNull()?.values?.mapNotNull { it.first() }
			val childCreationDates =
				childrenState?.associateByTo(HashMap(), { it.stateKey }) { it.originTimestamp }
					?: emptyMap()
			val childrenIds = childrenState?.map { RoomId(it.stateKey) }
				?: parentToChildren[space.roomId.toString()]
				?: emptyList()
			val children = mutableListOf<Room>()
			val childSpaces = mutableListOf<MatrixSpace>()

			childrenIds.forEach { childId ->
				unownedRooms.remove(childId)?.let { child ->
					if (child.type == CreateEventContent.RoomType.Space)
						childSpaces.add(buildSpaceTree(child))
					else children.add(child)
				}
			}

			return MatrixSpace(
				space,
				children,
				childSpaces,
				roomOrderMap[space.roomId.toString()] ?: childCreationDates[space.roomId.toString()]
				?: Long.MAX_VALUE
			)
		}

		allRooms.forEach { room ->
			val parentData =
				client.room.getAllState<SpaceParentEventContent>(room.roomId).firstOrNull()
			val parentId = parentData?.values?.firstOrNull()?.firstOrNull()?.stateKey

			if (parentId != null) parentToChildren.getOrPut(parentId) { mutableListOf() }
				.add(room.roomId)

			val order = if (room.type == CreateEventContent.RoomType.Space)
				client.room.getAccountData<SpaceOrderEventContent>(room.roomId)
					.first()?.order?.firstOrNull()?.code?.toLong()
			else null
			order?.let { roomOrderMap[room.roomId.toString()] = it }

			if (room.type == CreateEventContent.RoomType.Space && parentId == null) topLevelSpaces.add(
				room
			)
		}

		val res = ArrayList<MatrixSpace>()
		topLevelSpaces.forEach { space -> res.add(buildSpaceTree(space)) }
		res.add(buildSpaceTree(null))

		return res.sortedBy { it.order }.toList()
	}

	@OptIn(ExperimentalTime::class)
	fun getSpaceTreeFlow(): Flow<List<MatrixSpace>> {
		return flow {
			getRoomsFlow().collect { allRooms ->
				val unownedRooms = allRooms.associateByTo(HashMap()) { it.roomId }
				val parentToChildren = HashMap<String, MutableList<RoomId>>()
				val roomOrderMap = HashMap<String, Long>()
				val topLevelSpaces = mutableListOf<Room>()

				suspend fun buildSpaceTree(space: Room?): MatrixSpace {
					if (space == null) return MatrixSpace(
						null,
						unownedRooms.values
							.sortedByDescending { it.lastRelevantEventTimestamp?.toEpochMilliseconds() }
							.toList(),
						emptyList(),
						Long.MIN_VALUE
					)

					val childrenState =
						client.room.getAllState<SpaceChildrenEventContent>(space.roomId)
							.firstOrNull()?.values?.mapNotNull { it.first() }
					val childCreationDates =
						childrenState?.associateByTo(
							HashMap(),
							{ it.stateKey }) { it.originTimestamp }
							?: emptyMap()
					val childrenIds = childrenState?.map { RoomId(it.stateKey) }
						?: parentToChildren[space.roomId.toString()]
						?: emptyList()
					val children = mutableListOf<Room>()
					val childSpaces = mutableListOf<MatrixSpace>()

					childrenIds.forEach { childId ->
						unownedRooms.remove(childId)?.let { child ->
							if (child.type == CreateEventContent.RoomType.Space)
								childSpaces.add(buildSpaceTree(child))
							else children.add(child)
						}
					}

					return MatrixSpace(
						space,
						children,
						childSpaces,
						roomOrderMap[space.roomId.toString()]
							?: childCreationDates[space.roomId.toString()]
							?: Long.MAX_VALUE
					)
				}

				allRooms.forEach { room ->
					val parentData =
						client.room.getAllState<SpaceParentEventContent>(room.roomId).firstOrNull()
					val parentId = parentData?.values?.firstOrNull()?.firstOrNull()?.stateKey

					if (parentId != null) parentToChildren.getOrPut(parentId) { mutableListOf() }
						.add(room.roomId)

					val order = if (room.type == CreateEventContent.RoomType.Space)
						client.room.getAccountData<SpaceOrderEventContent>(room.roomId)
							.first()?.order?.firstOrNull()?.code?.toLong()
					else null
					order?.let { roomOrderMap[room.roomId.toString()] = it }

					if (room.type == CreateEventContent.RoomType.Space && parentId == null) topLevelSpaces.add(
						room
					)
				}

				val res = ArrayList<MatrixSpace>()
				topLevelSpaces.forEach { space -> res.add(buildSpaceTree(space)) }
				res.add(buildSpaceTree(null))

				emit(res.sortedBy { it.order }.toList())
			}
		}
	}

	suspend fun getUser(userId: UserId, roomId: RoomId): RoomUser? {
		return client.user.getById(roomId, userId).first()
	}

	suspend fun getEvent(roomId: RoomId, eventId: EventId, retryCount: Int = 0): TimelineEvent? {
		val res = client.room.getTimelineEvent(roomId, eventId).firstOrNull()
		if (res == null && retryCount > 0) {
			client.syncOnce()
			return getEvent(roomId, eventId, retryCount - 1)
		}
		return res
	}

	suspend fun getEvent(roomId: String, eventId: String) =
		getEvent(RoomId(roomId), EventId(eventId))

	suspend fun sendMessage(roomId: String, msg: String, replyTo: EventId? = null) {
		client.room.sendMessage(RoomId(roomId)) {
			val relatesTo = replyTo?.let {
				RelatesTo.Reply(RelatesTo.ReplyTo(it))
			}
			content(
				RoomMessageEventContent.TextBased.Text(
					msg,
					formattedBody = Utils.parseMarkdown(msg),
					relatesTo = relatesTo
				)
			)
		}
	}

	suspend fun editEvent(roomId: String, eventId: EventId, msg: String) {
		client.room.sendMessage(RoomId(roomId)) {
			content(
				RoomMessageEventContent.TextBased.Text(
					"* $msg",
					formattedBody = Utils.parseMarkdown(msg),
					relatesTo = RelatesTo.Replace(
						eventId,
						newContent = RoomMessageEventContent.TextBased.Text(
							msg,
							formattedBody = Utils.parseMarkdown(msg)
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
		client.api.room.redactEvent(roomId, eventId, reason).getOrThrow()
	}

	suspend fun startSync() {
		client.startSync()
	}

	suspend fun addSyncStateListener(listener: ((SyncState) -> Unit)) {
		client.syncState.collect {
			runOnUiThread {
				listener.invoke(it)
			}
		}
	}

	suspend fun registerFcmPusher(token: String) {
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
		synchronized(activeVerifications) {
			return activeVerifications[id]
		}
	}

	suspend fun addOnDeviceVerificationRequestListener(listener: ((ActiveDeviceVerification) -> Unit)) {
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

	fun getRecentEmojis(): List<RecentEmoji> {
		if (!loadedRecentEmoji) {
			suspendThread {
				updateRecentEmojiCache()
			}
		}
		return recentEmojiCache.sortedByDescending { it.count }
	}

	suspend fun appendRecentEmoji(emoji: String) {
		updateRecentEmojiCache()
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

	private suspend fun updateRecentEmojiCache() {
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

	companion object {
		@SuppressLint("StaticFieldLeak")
		private lateinit var instance: Matrix

		fun getAvailableAccounts(context: Context): List<String> {
			return context.databaseList().map { it.replace("trixnity-", "") }
				.filterNot { it.endsWith("-wal") || it.endsWith("-shm") }
		}

		private fun getModules(context: Context, type: String = "main"): Pair<Module, Module> {
			val repositoriesModule = createRoomRepositoriesModule(
				AndroidRoom.databaseBuilder(
					context,
					TrixnityRoomDatabase::class.java,
					"trixnity-$type"
				)
			)
			val mediaStoreModule = createOkioMediaStoreModule(
				Path(
					context.filesDir.absolutePath,
					"mediaStore-$type"
				).absolutePathString().toPath(true)
			)
			return Pair(repositoriesModule, mediaStoreModule)
		}

		private fun prepClient(): Module {
			val customMappings = createEventContentSerializerMappings {
				stateOf<SpaceParentEventContent>("m.space.parent")
				stateOf<SpaceChildrenEventContent>("m.space.child")
				roomAccountDataOf<SpaceOrderEventContent>("org.matrix.msc3230.space_order")
				messageOf<ShortcodeReactionEventContent>("m.reaction")
				globalAccountDataOf<ElementRecentEmojiEventContent>("io.element.recent_emoji")
			}

			return module {
				single<EventContentSerializerMappings> {
					DefaultEventContentSerializerMappings + customMappings
				}
			}
		}

		suspend fun loadClient(context: Context, type: String = "main"): Matrix {
			val (repo, media) = getModules(context, type)
			val client = MatrixClient.fromStore(
				repositoriesModule = repo,
				mediaStoreModule = media,
				onSoftLogin = null,
				coroutineContext = Dispatchers.Default
			) {
				modulesFactories += ::prepClient
			}.getOrThrow()!!
			instance = Matrix(context, client)
			return instance
		}

		suspend fun login(
			context: Context,
			homeserver: String,
			id: IdentifierType,
			password: String,
			type: String = "main",
		): Matrix {
			val (repo, media) = getModules(context, type)
			val client = MatrixClient.login(
				baseUrl = Url(homeserver),
				identifier = id,
				password = password,
				initialDeviceDisplayName = "Sakura",
				repositoriesModule = repo,
				mediaStoreModule = media,
			) {
				modulesFactories += ::prepClient
			}.getOrThrow()
			prepClient()
			instance = Matrix(context, client)
			return instance
		}

		fun startLoginFlow(
			context: Context,
			homeserver: Uri,
			type: String = "main",
		): MatrixClientServerApiClientImpl {
			val (repo, media) = getModules(context, type)
			return MatrixClientServerApiClientImpl(Url(homeserver.toString()))
		}

		fun getClient(): Matrix {
			if (!this::instance.isInitialized) throw IllegalStateException("Client not initialized")
			return instance
		}
	}
}