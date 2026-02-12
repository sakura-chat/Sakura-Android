package dev.kuylar.sakura.ui.activity

import android.Manifest
import android.animation.ValueAnimator
import android.app.ComponentCaller
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.util.Log
import android.util.TypedValue
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
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelState
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.verification.ActiveDeviceVerification
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.databinding.ActivityMainBinding
import dev.kuylar.sakura.ui.adapter.recyclerview.SpaceListRecyclerAdapter
import dev.kuylar.sakura.ui.adapter.recyclerview.SpaceTreeRecyclerAdapter
import dev.kuylar.sakura.ui.fragment.RoomInfoPanelFragment
import dev.kuylar.sakura.ui.fragment.TimelineFragment
import dev.kuylar.sakura.ui.fragment.verification.VerificationBottomSheetFragment
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import com.google.android.material.R as MaterialR

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), PanelsChildGestureRegionObserver.GestureRegionsListener,
	OverlappingPanelsLayout.PanelStateListener,
	NavigationBarView.OnItemSelectedListener {
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

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (!Matrix.isInitialized()) Matrix.setClient(client)

		navHostFragment =
			supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment
		navController = navHostFragment.navController

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

			navHostFragment.childFragmentManager.fragments.forEach { fragment ->
				if (fragment is TimelineFragment) {
					fragment.onImeHeightChanged(ime.bottom)
				}
			}

			binding.statusBarBg.layoutParams.height = systemBars.top
			v.setPadding(
				systemBars.left,
				ime.top,
				systemBars.right,
				max(ime.bottom, systemBars.bottom)
			)
			insets
		}

		setSupportActionBar(binding.toolbar)
		binding.toolbar.setNavigationOnClickListener {
			binding.overlappingPanels.openStartPanel()
		}
		binding.toolbar.setOnMenuItemClickListener {
			return@setOnMenuItemClickListener when (it.itemId) {
				R.id.menu_users -> {
					binding.overlappingPanels.openEndPanel()
					true
				}

				else -> false
			}
		}
		binding.bottomNav.setOnItemSelectedListener(this)

		PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				when (binding.overlappingPanels.getSelectedPanel()) {
					OverlappingPanelsLayout.Panel.START -> finish()
					OverlappingPanelsLayout.Panel.CENTER -> binding.overlappingPanels.openStartPanel()
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
		binding.overlappingPanels.registerStartPanelStateListeners(this)

		binding.bottomNav.post {
			binding.bottomNav.hide()
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
							binding.overlappingPanels.closePanels()
							binding.overlappingPanels.setStartPanelUseFullPortraitWidth(false)
							binding.overlappingPanels.setStartPanelLockState(OverlappingPanelsLayout.LockState.UNLOCKED)
						}
					}
				}
			}
		}
		suspendThread {
			try {
				client.initialize("main")
			} catch (_: Exception) {
				// Failed to load client. Give up and send the user to the login screen
				this@MainActivity.runOnUiThread {
					startActivity(Intent(this, LoginActivity::class.java))
					finish()
				}
				return@suspendThread
			}
			runOnUiThread {
				onClientReady()
			}
			client.startSync()
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
					Log.i("MainActivity", "Got device verification request: ${it.transactionId}")
					val bottomSheet = VerificationBottomSheetFragment()
					bottomSheet.arguments = bundleOf("verification" to it.transactionId)
					bottomSheet.show(supportFragmentManager, "verification")
				}
			}
		}
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
				client.registerFcmPusher(it.result)
			}
		}
		binding.roomsPanel.spacesRecycler.adapter = SpaceListRecyclerAdapter(
			this,
			client,
			getSharedPreferences("main", MODE_PRIVATE).getString("selectedSpaceId", null)
		)
		binding.roomsPanel.roomsRecycler.adapter = SpaceTreeRecyclerAdapter(this, client)
		if (autoNavigate) {
			val navigatedFromIntent = handleIntent(intent)
			if (!navigatedFromIntent)
				getSharedPreferences("main", MODE_PRIVATE).getString("selectedRoomId", null)?.let {
					openRoomTimeline(it)
				}
		}
	}

	fun openSpaceTree(space: MatrixSpace) {
		getSharedPreferences("main", MODE_PRIVATE).edit {
			putString("selectedSpaceId", space.parent?.roomId?.full ?: "!home:SakuraNative")
		}
		binding.roomsPanel.title.text = space.parent?.name?.explicitName ?: "Home"
		binding.roomsPanel.topic.visibility = View.GONE
		(binding.roomsPanel.roomsRecycler.adapter as? SpaceTreeRecyclerAdapter)
			?.changeSpace(space.parent?.roomId?.full ?: "!home:SakuraNative")
	}

	fun openRoomTimeline(room: Room) = openRoomTimeline(room.roomId)
	fun openRoomTimeline(roomId: RoomId) = openRoomTimeline(roomId.full)
	fun openRoomTimeline(roomId: String) {
		getSharedPreferences("main", MODE_PRIVATE).edit {
			putString("selectedRoomId", roomId)
		}
		binding.overlappingPanels.closePanels()
		navController.navigate(
			R.id.nav_room,
			bundleOf("roomId" to roomId),
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

			SyncState.STARTED -> Pair(
				MaterialR.attr.colorTertiary,
				MaterialR.attr.colorOnTertiary
			)

			SyncState.RUNNING -> Pair(
				MaterialR.attr.colorSurface,
				MaterialR.attr.colorOnSurface
			)

			SyncState.ERROR -> Pair(
				MaterialR.attr.colorTertiary,
				MaterialR.attr.colorOnTertiary
			)

			SyncState.TIMEOUT -> Pair(
				MaterialR.attr.colorTertiary,
				MaterialR.attr.colorOnTertiary
			)

			SyncState.STOPPED -> Pair(
				MaterialR.attr.colorTertiary,
				MaterialR.attr.colorOnTertiary
			)
		}
		if (state == SyncState.RUNNING) {
			binding.syncIndicator.postDelayed(1000) {
				binding.syncIndicator.visibility = View.GONE
			}
		} else {
			binding.syncIndicator.post {
				binding.syncIndicator.visibility = View.VISIBLE
			}
		}
		binding.syncIndicatorText.setText(resId)
		binding.syncIndicatorText.setTextColor(getColorFromAttr(textColor))
		binding.syncIndicator.setBackgroundColor(getColorFromAttr(backgroundColor))
		binding.statusBarBg.setBackgroundColor(getColorFromAttr(backgroundColor))
	}

	private fun getColorFromAttr(attr: Int): Int {
		val typedValue = TypedValue()
		theme.resolveAttribute(attr, typedValue, true)
		return typedValue.data
	}

	override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
		super.onNewIntent(intent, caller)
		handleIntent(intent)
	}

	private fun handleIntent(intent: Intent): Boolean {
		return if (intent.action == Intent.ACTION_VIEW) {
			val uri = intent.data ?: return false

			when (uri.host) {
				"room" -> {
					uri.lastPathSegment?.let { roomId ->
						openRoomTimeline(roomId)
						true
					} ?: false
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

	override fun onPanelStateChange(panelState: PanelState) {
		if (startPanelState == panelState) return

		when (panelState) {
			PanelState.Closing -> {
				if (getCurrentRoomId() != null)
					binding.bottomNav.hide()
			}

			PanelState.Opening -> {
				binding.bottomNav.show()
			}

			else -> {}
		}

		startPanelState = panelState
		navHostFragment.childFragmentManager.fragments.forEach { fragment ->
			if (fragment is TimelineFragment) {
				fragment.closeKeyboard()
			}
		}
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.nav_main -> {
				navController.popBackStack(R.id.nav_room, false)
			}

			R.id.nav_search -> {
				Toast.makeText(this, "not implemented yet", Toast.LENGTH_LONG).show()
				false
			}

			R.id.nav_notifications -> {
				Toast.makeText(this, "not implemented yet", Toast.LENGTH_LONG).show()
				false
			}

			R.id.nav_settings -> {
				Toast.makeText(this, "not implemented yet", Toast.LENGTH_LONG).show()
				false
			}

			else -> {
				false
			}
		}
	}

	private fun BottomNavigationView.show() {
		if (isVisible) return

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
}