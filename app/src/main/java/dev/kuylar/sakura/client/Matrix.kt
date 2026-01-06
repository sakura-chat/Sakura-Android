package dev.kuylar.sakura.client

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import dev.kuylar.sakura.client.customevent.SpaceChildrenEventContent
import dev.kuylar.sakura.client.customevent.SpaceOrderEventContent
import dev.kuylar.sakura.client.customevent.SpaceParentEventContent
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.fromStore
import net.folivo.trixnity.client.login
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getAccountData
import net.folivo.trixnity.client.room.getAllState
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase
import net.folivo.trixnity.client.store.repository.room.createRoomRepositoriesModule
import net.folivo.trixnity.client.store.type
import net.folivo.trixnity.client.user
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import net.folivo.trixnity.client.verification.ActiveUserVerification
import net.folivo.trixnity.client.verification.ActiveVerification
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiBaseClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientFactory
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.roomAccountDataOf
import net.folivo.trixnity.core.serialization.events.stateOf
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.time.ExperimentalTime
import androidx.room.Room as AndroidRoom

class Matrix(val context: Context, val client: MatrixClient) {
	private val activeVerifications = HashMap<String, ActiveVerification>()

	suspend fun getRoom(roomId: String): Room? {
		return client.room.getById(RoomId(roomId)).first()
	}

	suspend fun getRooms(): List<Room> {
		return client.room.getAll().flattenValues().first()
			.filter { it.membership != Membership.LEAVE && it.membership != Membership.BAN }
			.toList()
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

	suspend fun getUser(userId: UserId, roomId: RoomId): RoomUser? {
		return client.user.getById(roomId, userId).first()
	}

	suspend fun sendMessage(roomId: String, msg: String) {
		client.room.sendMessage(RoomId(roomId)) {
			content(RoomMessageEventContent.TextBased.Text(msg))
		}
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