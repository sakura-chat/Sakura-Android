package dev.kuylar.sakura.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.ActivityLoginBinding
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType

@AndroidEntryPoint
class LoginActivity : AppCompatActivity(), InitialSyncCompleteListener {
	private lateinit var binding: ActivityLoginBinding
	private lateinit var client: MatrixClientServerApiClient
	private lateinit var navController: NavController

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		binding = ActivityLoginBinding.inflate(layoutInflater)
		setContentView(binding.root)

		ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.ime())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}

		val navHostFragment =
			supportFragmentManager.findFragmentById(binding.fragment.id) as NavHostFragment
		navController = navHostFragment.navController
	}

	fun getLoginFlow(homeserver: String, callback: ((Set<LoginType>?) -> Unit)) {
		client = Matrix.startLoginFlow(homeserver.toUri())
		suspendThread {
			try {
				val res = client.authentication.getLoginTypes().getOrNull()
				runOnUiThread {
					callback.invoke(res)
				}
			} catch (e: Exception) {
				callback.invoke(null)
				return@suspendThread
			}
		}
	}

	override fun onInitialSyncComplete() {
		startActivity(Intent(this, MainActivity::class.java))
		finish()
	}
}