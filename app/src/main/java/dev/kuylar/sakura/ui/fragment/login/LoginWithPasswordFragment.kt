package dev.kuylar.sakura.ui.fragment.login

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentLoginWithPasswordBinding
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import javax.inject.Inject

@AndroidEntryPoint
class LoginWithPasswordFragment : Fragment() {
	private lateinit var binding: FragmentLoginWithPasswordBinding
	private lateinit var homeserver: String
	@Inject lateinit var matrix: Matrix

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		homeserver = arguments?.getString("homeserver") ?: ""
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentLoginWithPasswordBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		binding.title.text = getString(R.string.login_password_title, homeserver)

		binding.buttonLogin.setOnClickListener {
			binding.inputPassword.error = null
			binding.buttonLogin.isEnabled = false
			suspendThread {
				try {
					matrix.login(
						homeserver,
						IdentifierType.User(binding.inputUsername.editText?.text.toString()),
						binding.inputPassword.editText?.text.toString()
					)
					activity?.runOnUiThread {
						findNavController().navigate(R.id.nav_login_initial_sync)
					}
				} catch (e: Exception) {
					activity?.runOnUiThread {
						binding.inputPassword.error = e.message ?: "Unknown error"
						binding.buttonLogin.isEnabled = true
					}
					Log.e("LoginWithPasswordFragment", "Error logging in", e)
				}
			}
		}
	}
}