package dev.kuylar.sakura.ui.fragment.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import dev.kuylar.sakura.R
import dev.kuylar.sakura.ui.activity.LoginActivity
import dev.kuylar.sakura.databinding.FragmentHomeserverSelectBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeserverSelectFragment : Fragment(), TextWatcher {
	private lateinit var binding: FragmentHomeserverSelectBinding
	private var debounceJob: Job? = null
	private var scope = MainScope()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentHomeserverSelectBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		binding.inputHomeserver.editText!!.addTextChangedListener(this)
	}

	private fun checkHomeserver(homeserverUrl: String) {
		if (homeserverUrl.isBlank()) return
		if (!homeserverUrl.startsWith("http")) return
		(activity as LoginActivity).getLoginFlow(homeserverUrl) { loginFlow ->
			if (loginFlow == null) {
				binding.inputHomeserver.error = getString(R.string.error_invalid_homeserver_url)
				return@getLoginFlow
			}
			binding.inputHomeserver.error = null
			binding.buttonPassword.isEnabled = loginFlow.any { it.name == "m.login.password" }
			binding.buttonPassword.setOnClickListener {
				findNavController().navigate(
					R.id.nav_login_password,
					bundleOf("homeserver" to homeserverUrl)
				)
			}
		}
	}

	override fun afterTextChanged(editable: Editable) {
		binding.inputHomeserver.error = null
		binding.buttonPassword.isEnabled = false
		debounceJob?.cancel()
		debounceJob = scope.launch {
			delay(200)
			checkHomeserver(editable.toString())
		}
	}

	override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
	override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

	override fun onDestroyView() {
		super.onDestroyView()
		debounceJob?.cancel()
	}
}