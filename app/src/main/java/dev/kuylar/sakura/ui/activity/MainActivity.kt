package dev.kuylar.sakura.ui.activity

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ComponentCaller
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelState
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarMenuView
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.verification.ActiveDeviceVerification
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.MatrixUrlParser
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.getName
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ActivityMainBinding
import dev.kuylar.sakura.ui.BackButtonListener
import dev.kuylar.sakura.ui.adapter.spaces.SpaceTreeListAdapter
import dev.kuylar.sakura.ui.adapter.spaces.TopLevelSpacesRecyclerAdapter
import dev.kuylar.sakura.ui.fragment.RoomInfoPanelFragment
import dev.kuylar.sakura.ui.fragment.TimelineFragment
import dev.kuylar.sakura.ui.fragment.bottomsheet.ProfileBottomSheetFragment
import dev.kuylar.sakura.ui.fragment.verification.VerificationBottomSheetFragment
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import com.google.android.material.R as MaterialR

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), PanelsChildGestureRegionObserver.GestureRegionsListener,
	NavigationBarView.OnItemSelectedListener, MenuProvider {
	@Inject
	lateinit var client: Matrix
	private lateinit var binding: ActivityMainBinding
	private lateinit var navHostFragment: NavHostFragment
	private lateinit var navController: NavController
	private var autoNavigate = true
	private val notificationPermissionLauncher =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
			if (!isGranted) {
				showNotificationPermissionRationale(shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
			}
		}
	private var startPanelState: PanelState = PanelState.Closed
	private var endPanelState: PanelState = PanelState.Closed
	private var spaceTreeLoaded = false

	@SuppressLint("RestrictedApi")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (!Matrix.isInitialized()) Matrix.setClient(client)

		navHostFragment =
			supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment
		navController = navHostFragment.navController

		ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

			navHostFragment.childFragmentManager.fragments.forEach { fragment ->
				if (fragment is TimelineFragment) {
					fragment.onImeHeightChanged(ime.bottom - systemBars.bottom)
				}
			}

			binding.syncIndicator.setPadding(
				binding.syncIndicator.paddingLeft,
				systemBars.top,
				binding.syncIndicator.paddingRight,
				binding.syncIndicator.paddingBottom
			)

			binding.overlappingPanels.setPadding(
				binding.overlappingPanels.paddingLeft,
				binding.overlappingPanels.paddingTop,
				binding.overlappingPanels.paddingRight,
				max(ime.bottom, systemBars.bottom)
			)

			insets
		}

		setSupportActionBar(binding.toolbar)
		val appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_room, R.id.nav_empty))
		binding.toolbar.setupWithNavController(navController, appBarConfiguration)

		binding.toolbar.setNavigationOnClickListener {
			handleBackPressed()
		}
		addMenuProvider(this, this, Lifecycle.State.RESUMED)

		binding.bottomNav.setupWithNavController(navController)
		binding.bottomNav.setOnItemSelectedListener(this)

		PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				when (binding.overlappingPanels.getSelectedPanel()) {
					OverlappingPanelsLayout.Panel.START -> finish()
					OverlappingPanelsLayout.Panel.CENTER -> {
						handleBackPressed()
					}

					OverlappingPanelsLayout.Panel.END -> binding.overlappingPanels.closePanels()
				}
			}
		})

		val loggedInAccounts = Matrix.getAvailableAccounts(this)
		if (loggedInAccounts.isEmpty()) {
			startActivity(Intent(this, LoginActivity::class.java))
			finish()
			return
		}

		binding.roomsPanel.spacesRecycler.layoutManager = LinearLayoutManager(this)
		binding.roomsPanel.roomsRecycler.layoutManager = LinearLayoutManager(this)
		binding.overlappingPanels.registerStartPanelStateListeners(object :
			OverlappingPanelsLayout.PanelStateListener {
			override fun onPanelStateChange(panelState: PanelState) {
				onStartPanelStateChange(panelState)
			}
		})
		binding.overlappingPanels.registerEndPanelStateListeners(object :
			OverlappingPanelsLayout.PanelStateListener {
			override fun onPanelStateChange(panelState: PanelState) {
				onEndPanelStateChange(panelState)
			}
		})

		binding.bottomNav.post {
			binding.bottomNav.hide()
			onStartPanelStateChange(PanelState.Opened)
		}
		handleStateChange(SyncState.STOPPED)
		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				navController.addOnDestinationChangedListener { _, destination, _ ->
					when (destination.id) {
						R.id.nav_empty -> {
							binding.overlappingPanels.openStartPanel()
							binding.overlappingPanels.setStartPanelUseFullPortraitWidth(true)
							binding.overlappingPanels.setStartPanelLockState(OverlappingPanelsLayout.LockState.OPEN)
						}

						R.id.nav_room -> {
							binding.toolbar.navigationIcon =
								ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_menu)
							// Just calling .selectedItemId = R.id.nav_main doesn't work :(
							(binding.bottomNav.menuView as NavigationBarMenuView)
								.setCheckedItem(binding.bottomNav.menu[0])
							if (binding.bottomNav.isVisible) {
								binding.bottomNav.hide()
							}
							binding.overlappingPanels.closePanels()
							binding.overlappingPanels.setStartPanelUseFullPortraitWidth(false)
							binding.overlappingPanels.setStartPanelLockState(OverlappingPanelsLayout.LockState.UNLOCKED)
							binding.overlappingPanels.setEndPanelLockState(OverlappingPanelsLayout.LockState.UNLOCKED)
						}

						R.id.nav_settings, R.id.nav_notifications -> {
							binding.overlappingPanels.closePanels()
							binding.overlappingPanels.setStartPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
							binding.overlappingPanels.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
							binding.bottomNav.show()
						}
					}
				}
			}
		}
		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
				if (!client.initialized) {
					try {
						client.initialize("main")
					} catch (e: Exception) {
						Log.wtf("MainActivity", "Failed to initialize client", e)
						// Failed to load client. Give up and send the user to the login screen
						this@MainActivity.runOnUiThread {
							startActivity(Intent(this@MainActivity, LoginActivity::class.java))
							finish()
						}
						return@repeatOnLifecycle
					}
				}
				runOnUiThread {
					onClientReady()
				}
				client.startSync()
				// These live under here since they run .collect() inside them.
				// There is definitely a better way to do this.
				lifecycleScope.launch {
					client.initializeRoomCache()
				}
				lifecycleScope.launch {
					client.addSyncStateListener {
						Log.i("MainActivity", "Sync state: $it")
						runOnUiThread {
							handleStateChange(it)
						}
					}
				}
				lifecycleScope.launch {
					client.addOnDeviceVerificationRequestListener { it: ActiveDeviceVerification ->
						Log.i(
							"MainActivity",
							"Got device verification request: ${it.transactionId}"
						)
						val bottomSheet = VerificationBottomSheetFragment()
						bottomSheet.arguments = bundleOf("verification" to it.transactionId)
						bottomSheet.show(supportFragmentManager, "verification")
					}
				}
			}
		}
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

	override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
		R.id.menu_users -> {
			binding.overlappingPanels.openEndPanel()
			true
		}

		else -> false
	}

	private fun onClientReady() {
		if (getSystemService<NotificationManager>()?.areNotificationsEnabled() == false &&
			getSharedPreferences("main", MODE_PRIVATE).getBoolean("notificationsDismissed", false)
		) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
		FirebaseMessaging.getInstance().token.addOnCompleteListener {
			if (!it.isSuccessful) {
				Log.e("MainActivity", "Failed to get FCM token", it.exception)
				return@addOnCompleteListener
			}
			suspendThread {
				try {
					client.registerFcmPusher(it.result)
				} catch (e: Exception) {
					Log.e("MainActivity", "Failed to register FCM pusher!", e)
				}
			}
		}
		val sp = getSharedPreferences("main", MODE_PRIVATE)
		val selectedSpace =
			sp.getString("selectedSpaceId", null)?.let { RoomId(it) } ?: Matrix.DIRECT_ROOM
		val selectedRoom = sp.getString("selectedRoomId", null)?.let { RoomId(it) }
		binding.roomsPanel.spacesRecycler.adapter =
			TopLevelSpacesRecyclerAdapter(this, client, selectedSpace)
		binding.roomsPanel.roomsRecycler.adapter = SpaceTreeListAdapter(this, client, selectedRoom)
		openSpaceTree(selectedSpace)
		if (autoNavigate) {
			if (!handleIntent(intent) && selectedRoom != null
				&& navController.currentDestination?.id == R.id.nav_empty
			) openRoomTimeline(selectedRoom)
		}
	}

	fun openSpaceTree(id: RoomId) {
		val room = client.getRoom(id)
		getSharedPreferences("main", MODE_PRIVATE).edit {
			putString("selectedSpaceId", id.full)
		}
		lifecycleScope.launch {
			binding.roomsPanel.title.text = when (room?.roomId) {
				Matrix.DIRECT_ROOM -> getString(R.string.room_direct)
				Matrix.GROUPS_ROOM -> getString(R.string.room_groups)
				null -> getString(R.string.room_home)
				else -> room.getName(this@MainActivity, client)
			}
		}
		binding.roomsPanel.topic.visibility = View.GONE
		(binding.roomsPanel.roomsRecycler.adapter as? SpaceTreeListAdapter)?.changeSpace(id)
	}

	fun openRoomTimeline(room: Room, eventId: EventId? = null) = openRoomTimeline(room.roomId, eventId)
	fun openRoomTimeline(roomId: RoomId, eventId: EventId? = null) = openRoomTimeline(roomId.full, eventId)
	fun openRoomTimeline(roomId: String, eventId: EventId? = null) {
		getSharedPreferences("main", MODE_PRIVATE).edit {
			putString("selectedRoomId", roomId)
		}
		binding.overlappingPanels.closePanels()
		navController.navigate(
			R.id.nav_room,
			bundleOf(
				"roomId" to roomId,
				"eventId" to eventId?.full
			),
			NavOptions.Builder().apply {
				setLaunchSingleTop(true)
			}.build()
		)
		supportFragmentManager.beginTransaction()
			.replace(binding.usersPanel.id, RoomInfoPanelFragment().apply {
				arguments = bundleOf("roomId" to roomId)
			})
			.commit()
	}

	fun getCurrentRoomId(): String? {
		return navController.currentBackStackEntry?.savedStateHandle?.get<String>("roomId")
	}

	private fun showNotificationPermissionRationale(canRelaunch: Boolean) {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.notification_rationale_title)
			.setMessage(R.string.notification_rationale_message)
			.setPositiveButton(R.string.notification_rationale_enable) { _, _ ->
				if (canRelaunch) {
					notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
				} else {
					val intent = Intent(ACTION_APP_NOTIFICATION_SETTINGS)
					intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
					startActivity(intent)
				}
			}
			.setNeutralButton(R.string.notification_rationale_dismiss, null)
			.setNegativeButton(R.string.notification_rationale_dismiss_forever) { _, _ ->
				getSharedPreferences("main", MODE_PRIVATE).edit {
					this.putBoolean("notificationsDismissed", true)
				}
			}
			.show()
	}

	private fun handleStateChange(state: SyncState) {
		if (!spaceTreeLoaded && state != SyncState.STOPPED && state != SyncState.STARTED) {
			spaceTreeLoaded = true
			openSpaceTree(
				getSharedPreferences("main", MODE_PRIVATE)
					.getString("selectedSpaceId", null)?.let { RoomId(it) }
					?: Matrix.DIRECT_ROOM
			)
		}
		val resId = when (state) {
			SyncState.INITIAL_SYNC -> R.string.sync_status_initial
			SyncState.STARTED -> R.string.sync_status_start
			SyncState.RUNNING -> R.string.sync_status_running
			SyncState.ERROR -> R.string.sync_status_error
			SyncState.TIMEOUT -> R.string.sync_status_timeout
			SyncState.STOPPED -> R.string.sync_status_stopped
		}
		val (backgroundColor, textColor) = when (state) {
			SyncState.INITIAL_SYNC -> Pair(
				MaterialR.attr.colorPrimaryFixed,
				MaterialR.attr.colorOnPrimary
			)

			SyncState.RUNNING -> Pair(
				MaterialR.attr.colorSurface,
				MaterialR.attr.colorOnSurface
			)

			SyncState.ERROR, SyncState.TIMEOUT, SyncState.STOPPED, SyncState.STARTED -> Pair(
				MaterialR.attr.colorTertiary,
				MaterialR.attr.colorOnTertiary
			)
		}
		if (state == SyncState.RUNNING) {
			binding.syncIndicator.postDelayed(1000) {
				binding.syncIndicator.visibility = View.GONE
				binding.coordinator.setPadding(
					binding.coordinator.paddingLeft,
					binding.syncIndicator.paddingTop,
					binding.coordinator.paddingRight,
					binding.coordinator.paddingBottom
				)
			}
		} else {
			binding.syncIndicator.post {
				binding.syncIndicator.visibility = View.VISIBLE
				binding.coordinator.setPadding(
					binding.coordinator.paddingLeft,
					0,
					binding.coordinator.paddingRight,
					binding.coordinator.paddingBottom
				)
			}
		}
		binding.syncIndicatorText.setText(resId)
		binding.syncIndicatorText.setTextColor(getColorFromAttr(textColor))
		binding.syncIndicator.setBackgroundColor(getColorFromAttr(backgroundColor))
	}

	private fun getColorFromAttr(attr: Int): Int {
		val typedValue = TypedValue()
		theme.resolveAttribute(attr, typedValue, true)
		return typedValue.data
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent)
	}

	override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
		super.onNewIntent(intent, caller)
		handleIntent(intent)
	}

	private fun handleIntent(intent: Intent): Boolean {
		return if (intent.action == Intent.ACTION_VIEW) {
			val uri = intent.data ?: return false

			when (uri.scheme) {
				"dev.kuylar.sakura" -> {
					when (uri.host) {
						"room" -> {
							uri.lastPathSegment?.let { roomId ->
								autoNavigate = false
								openRoomTimeline(roomId)
								true
							} ?: false
						}

						else -> false
					}
				}
				"matrix" -> {
					when (val res = MatrixUrlParser.parse(uri)) {
						is MatrixUrlParser.Result.UserResult -> {
							val f = ProfileBottomSheetFragment()
							f.arguments = Bundle().apply {
								putString("userId", res.user.full)
							}
							f.show(supportFragmentManager, "profileBottomSheet")
							true
						}

						is MatrixUrlParser.Result.RoomAliasResult -> {
							val room = client.getRoomByAlias(res.alias)
							if (room == null) {
								Toast.makeText(
									this,
									"room not found, should show join dialog",
									Toast.LENGTH_LONG
								).show()
							} else {
								openRoomTimeline(room)
							}
							true
						}

						is MatrixUrlParser.Result.RoomIdResult -> {
							val room = client.getRoom(res.room) ?: return false
							openRoomTimeline(room)
							true
						}

						is MatrixUrlParser.Result.EventIdResult -> {
							val room = client.getRoom(res.room) ?: return false
							openRoomTimeline(room, res.event)
							true
						}

						null -> false

						else -> {
							Toast.makeText(this, "Unparseable URL: $uri", Toast.LENGTH_LONG).show()
							true
						}
					}
				}
				else -> false
			}
		} else {
			intent.getStringExtra("roomId")?.let {
				autoNavigate = false
				openRoomTimeline(it)
				true
			} ?: false
		}
	}

	override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
		binding.overlappingPanels.setChildGestureRegions(gestureRegions)
	}

	fun onStartPanelStateChange(panelState: PanelState) {
		if (startPanelState == panelState) return
		startPanelState = panelState

		val currentDestination = navController.currentDestination?.id
		val shouldKeepBottomNavVisible = currentDestination == R.id.nav_settings ||
				currentDestination == R.id.nav_notifications

		when (panelState) {
			PanelState.Closing -> {
				if (getCurrentRoomId() != null && !shouldKeepBottomNavVisible)
					binding.bottomNav.hide()
			}

			PanelState.Closed -> {
				if (shouldKeepBottomNavVisible) return
				if (opening) {
					hideAsSoonAsOpened = true
				} else if (binding.bottomNav.isVisible) {
					binding.bottomNav.hide()
				}
			}

			PanelState.Opening, PanelState.Opened -> {
				binding.bottomNav.show()
			}
		}

		navHostFragment.childFragmentManager.fragments.forEach { fragment ->
			if (fragment is TimelineFragment) {
				fragment.onBackPressed()
			}
		}
	}

	fun onEndPanelStateChange(panelState: PanelState) {
		if (endPanelState == panelState) return
		endPanelState = panelState

		if (panelState is PanelState.Opened) {
			supportFragmentManager.findFragmentById(binding.usersPanel.id)?.let {
				(it as? RoomInfoPanelFragment)?.load()
			}
		}
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.nav_main -> {
				binding.bottomNav.hide()
				binding.overlappingPanels.closePanels()
				navController.popBackStack(R.id.nav_room, false)
			}

			R.id.nav_search -> {
				Toast.makeText(this, "not implemented yet", Toast.LENGTH_LONG).show()
				false
			}

			R.id.nav_notifications -> {
				binding.overlappingPanels.closePanels()
				navController.navigate(R.id.nav_notifications)
				true
			}

			R.id.nav_settings -> {
				binding.overlappingPanels.closePanels()
				navController.navigate(R.id.nav_settings)
				true
			}

			else -> {
				false
			}
		}
	}

	private var opening = false
	private var hideAsSoonAsOpened = false
	private fun BottomNavigationView.show() {
		if (isVisible) return
		if (opening) return
		opening = true

		val parent = parent as ViewGroup
		if (!isLaidOut) {
			measure(
				View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.AT_MOST)
			)
			layout(parent.left, parent.height - measuredHeight, parent.right, parent.height)
		}

		val drawable = drawToBitmap().toDrawable(context.resources)
		drawable.setBounds(left, parent.height, right, parent.height + height)
		parent.overlay.add(drawable)
		ValueAnimator.ofInt(parent.height, top).apply {
			startDelay = 100L
			duration = 300L
			interpolator = AnimationUtils.loadInterpolator(
				context,
				android.R.interpolator.linear_out_slow_in
			)
			addUpdateListener {
				val newTop = it.animatedValue as Int
				drawable.setBounds(left, newTop, right, newTop + height)
			}
			doOnEnd {
				parent.overlay.remove(drawable)
				visibility = View.VISIBLE
				opening = false
				if (hideAsSoonAsOpened) {
					binding.bottomNav.hide()
					hideAsSoonAsOpened = false
				}
			}
			start()
		}
	}

	private fun BottomNavigationView.hide() {
		if (isGone) return

		val drawable = drawToBitmap().toDrawable(context.resources)
		val parent = parent as ViewGroup
		drawable.setBounds(left, top, right, bottom)
		parent.overlay.add(drawable)
		visibility = View.GONE
		ValueAnimator.ofInt(top, parent.height).apply {
			startDelay = 100L
			duration = 200L
			interpolator = AnimationUtils.loadInterpolator(
				context,
				android.R.interpolator.fast_out_linear_in
			)
			addUpdateListener {
				val newTop = it.animatedValue as Int
				drawable.setBounds(left, newTop, right, newTop + height)
			}
			doOnEnd {
				parent.overlay.remove(drawable)
			}
			start()
		}
	}

	private fun handleBackPressed() {
		var fragmentHandled = false
		navHostFragment.childFragmentManager.fragments.forEach { fragment ->
			if (fragment is BackButtonListener) {
				if (fragment.onBackPressed())
					fragmentHandled = true
			}
		}
		if (fragmentHandled) return
		when (navController.currentDestination?.id) {
			R.id.nav_room -> {
				binding.overlappingPanels.openStartPanel()
			}

			else -> {
				navController.popBackStack()
			}
		}
	}
}