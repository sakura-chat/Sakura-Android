package dev.kuylar.sakura.ui.activity

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.postDelayed
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ActivityBubbleBinding
import dev.kuylar.sakura.ui.fragment.verification.VerificationBottomSheetFragment
import kotlinx.coroutines.launch
import de.connect2x.trixnity.client.verification.ActiveDeviceVerification
import de.connect2x.trixnity.clientserverapi.client.SyncState
import javax.inject.Inject
import com.google.android.material.R as MaterialR

@AndroidEntryPoint
class BubbleActivity : AppCompatActivity() {
	private lateinit var binding: ActivityBubbleBinding
	@Inject
	lateinit var client: Matrix
	private lateinit var navController: NavController

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		binding = ActivityBubbleBinding.inflate(layoutInflater)
		setContentView(binding.root)

		if (!Matrix.isInitialized()) Matrix.setClient(client)

		val navHostFragment =
			supportFragmentManager.findFragmentById(binding.navHostFragment.id) as NavHostFragment
		navController = navHostFragment.navController

		setSupportActionBar(binding.toolbar)

		val loggedInAccounts = Matrix.getAvailableAccounts(this)
		if (loggedInAccounts.isEmpty()) {
			finish()
			return
		}

		handleStateChange(SyncState.STOPPED)
		suspendThread {
			try {
				client.initialize("main")
			} catch (_: Exception) {
				// Failed to load client. Give up, since we're in a bubble
				this@BubbleActivity.runOnUiThread {
					finish()
				}
				return@suspendThread
			}
			client.startSync()
			intent.getStringExtra("roomId")?.let {
				runOnUiThread {
					navController.navigate(R.id.nav_room, bundleOf("roomId" to it))
				}
			}
			lifecycleScope.launch {
				client.addSyncStateListener {
					Log.i("BubbleActivity", "Sync state: $it")
					runOnUiThread {
						handleStateChange(it)
					}
				}
			}
			lifecycleScope.launch {
				client.addOnDeviceVerificationRequestListener { it: ActiveDeviceVerification ->
					Log.i("BubbleActivity", "Got device verification request: ${it.transactionId}")
					val bottomSheet = VerificationBottomSheetFragment()
					bottomSheet.arguments = bundleOf("verification" to it.transactionId)
					bottomSheet.show(supportFragmentManager, "verification")
				}
			}
		}
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
	}

	private fun getColorFromAttr(attr: Int): Int {
		val typedValue = TypedValue()
		theme.resolveAttribute(attr, typedValue, true)
		return typedValue.data
	}
}