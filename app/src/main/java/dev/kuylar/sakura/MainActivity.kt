package dev.kuylar.sakura

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import dev.kuylar.recyclerviewbuilder.ExtensibleRecyclerAdapter
import dev.kuylar.recyclerviewbuilder.RecyclerViewBuilder
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.ui.activity.LoginActivity
import dev.kuylar.sakura.ui.fragment.verification.VerificationBottomSheetFragment
import dev.kuylar.sakura.databinding.ActivityMainBinding
import dev.kuylar.sakura.databinding.ItemDebugRoomBinding
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.media
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.verification.ActiveDeviceVerification

class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	private lateinit var client: Matrix
	private lateinit var adapter: ExtensibleRecyclerAdapter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.ime())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}

		val loggedInAccounts = Matrix.getAvailableAccounts(this)
		if (loggedInAccounts.isEmpty()) {
			startActivity(Intent(this, LoginActivity::class.java))
			return
		}

		adapter = RecyclerViewBuilder(this)
			.addView<MatrixSpace, ItemDebugRoomBinding> { binding, item, context ->
				binding.title.text = item.parent?.name?.explicitName ?: "null"
				binding.subtitle.text =
					"[${item.order}] ${item.children.size} children (${item.childSpaces.size} spaces)"
				binding.root.setOnClickListener {
					adapter.clearItems()
					adapter.addItems(item.children)
					adapter.addItems(item.childSpaces)
				}
				Glide.with(this)
					.load(item.parent?.avatarUrl)
					.into(binding.icon)
			}
			.addView<Room, ItemDebugRoomBinding> { binding, item, context ->
				binding.title.text = item.name?.explicitName ?: "null"
				binding.subtitle.text = item.roomId.full
				Glide.with(this)
					.load(item.avatarUrl)
					.into(binding.icon)
			}.build(binding.recycler)

		binding.refresh.setOnClickListener { onClientReady() }

		suspendThread {
			client = Matrix.loadClient(this, "main")
			runOnUiThread {
				onClientReady()
			}
			client.startSync()
			lifecycleScope.launch {
				client.addSyncStateListener {
					binding.text.text = "Sync state: $it"
					Log.i("MainActivity", "Sync state: $it")
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

	fun onClientReady() {
		suspendThread {
			val startTime = System.currentTimeMillis()
			client.getSpaceTree().let {
				runOnUiThread {
					adapter.clearItems()
					adapter.addItems(it.sortedBy {
						it.order
					}.filterNot { it.parent?.name?.explicitName == "CHP" })
				}
			}
			val endTime = System.currentTimeMillis()
			Log.i("MainActivity", "Got space tree in ${endTime - startTime}ms")
		}
	}
}