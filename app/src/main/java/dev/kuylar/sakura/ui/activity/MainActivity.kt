package dev.kuylar.sakura.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.postDelayed
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.R as MaterialR
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.databinding.ActivityMainBinding
import dev.kuylar.sakura.ui.adapter.recyclerview.SpaceListRecyclerAdapter
import dev.kuylar.sakura.ui.adapter.recyclerview.SpaceTreeRecyclerAdapter
import dev.kuylar.sakura.ui.fragment.RoomInfoPanelFragment
import dev.kuylar.sakura.ui.fragment.verification.VerificationBottomSheetFragment
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import net.folivo.trixnity.clientserverapi.client.SyncState
import kotlin.math.max

class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	private lateinit var client: Matrix
	private lateinit var navController: NavController

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
			binding.statusBarBg.layoutParams.height = systemBars.top
			v.setPadding(
				systemBars.left,
				ime.top,
				systemBars.right,
				max(ime.bottom, systemBars.bottom)
			)
			insets
		}

		val navHostFragment =
			supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment
		navController = navHostFragment.navController

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

		val loggedInAccounts = Matrix.getAvailableAccounts(this)
		if (loggedInAccounts.isEmpty()) {
			startActivity(Intent(this, LoginActivity::class.java))
			finish()
			return
		}

		binding.roomsPanel.spacesRecycler.layoutManager = LinearLayoutManager(this)
		binding.roomsPanel.roomsRecycler.layoutManager = LinearLayoutManager(this)

		handleStateChange(SyncState.STOPPED)
		suspendThread {
			try {
				client = Matrix.loadClient(this, "main")
			} catch (e: Exception) {
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
		binding.roomsPanel.spacesRecycler.adapter = SpaceListRecyclerAdapter(
			this,
			getSharedPreferences("main", MODE_PRIVATE).getString("selectedSpaceId", null)
		)
		binding.roomsPanel.roomsRecycler.adapter = SpaceTreeRecyclerAdapter(this)
		getSharedPreferences("main", MODE_PRIVATE).getString("selectedRoomId", null)?.let {
			openRoomTimeline(it)
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

	fun openRoomTimeline(room: Room) = openRoomTimeline(room.roomId.full)
	fun openRoomTimeline(roomId: String) {
		getSharedPreferences("main", MODE_PRIVATE).edit {
			putString("selectedRoomId", roomId)
		}
		binding.overlappingPanels.closePanels()
		navController.navigate(R.id.nav_room, bundleOf("roomId" to roomId))
		supportFragmentManager.beginTransaction()
			.replace(binding.usersPanel.id, RoomInfoPanelFragment().apply {
				arguments = bundleOf("roomId" to roomId)
			})
			.commit()
	}

	fun getCurrentRoomId(): String? {
		return navController.currentBackStackEntry?.savedStateHandle?.get<String>("roomId")
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
}