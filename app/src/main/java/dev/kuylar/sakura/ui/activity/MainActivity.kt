package dev.kuylar.sakura.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.kuylar.recyclerviewbuilder.ExtensibleRecyclerAdapter
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.MatrixSpace
import dev.kuylar.sakura.databinding.ActivityMainBinding
import dev.kuylar.sakura.ui.adapter.recyclerview.SpaceListRecyclerAdapter
import dev.kuylar.sakura.ui.adapter.recyclerview.SpaceTreeRecyclerAdapter
import dev.kuylar.sakura.ui.fragment.verification.VerificationBottomSheetFragment
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import kotlin.math.max

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
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
			v.setPadding(
				systemBars.left,
				max(ime.top, systemBars.top),
				systemBars.right,
				max(ime.bottom, systemBars.bottom)
			)
			insets
		}

		val loggedInAccounts = Matrix.getAvailableAccounts(this)
		if (loggedInAccounts.isEmpty()) {
			startActivity(Intent(this, LoginActivity::class.java))
			return
		}

		binding.roomsPanel.spacesRecycler.layoutManager = LinearLayoutManager(this)
		binding.roomsPanel.roomsRecycler.layoutManager = LinearLayoutManager(this)

		/*
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
		*/

		suspendThread {
			client = Matrix.loadClient(this, "main")
			runOnUiThread {
				onClientReady()
			}
			client.startSync()
			lifecycleScope.launch {
				client.addSyncStateListener {
					// TODO: binding.text.text = "Sync state: $it"
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

	private fun onClientReady() {
		binding.roomsPanel.spacesRecycler.adapter = SpaceListRecyclerAdapter(this)
		binding.roomsPanel.roomsRecycler.adapter = SpaceTreeRecyclerAdapter(this)
	}

	fun openSpaceTree(space: MatrixSpace) {
		binding.roomsPanel.title.text = space.parent?.name?.explicitName ?: "Home"
		binding.roomsPanel.topic.visibility = View.GONE
		(binding.roomsPanel.roomsRecycler.adapter as? SpaceTreeRecyclerAdapter)
			?.changeSpace(space.parent?.roomId?.full ?: "!home:SakuraNative")
	}
}