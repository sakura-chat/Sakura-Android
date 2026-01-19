package dev.kuylar.sakura.ui.fragment.verification

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentVerificationStartBinding
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import net.folivo.trixnity.client.verification.ActiveVerificationState
import javax.inject.Inject

class VerificationStartFragment : Fragment() {
	private lateinit var binding: FragmentVerificationStartBinding
	@Inject lateinit var client: Matrix
	private lateinit var id: String
	private lateinit var verification: ActiveDeviceVerification

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (arguments == null) return
		id = requireArguments().getString("verification") ?: return
		client.getVerification(id)
			?.let { verification = it as ActiveDeviceVerification }
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentVerificationStartBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		suspendThread {
			val currentState = verification.state.first() as? ActiveVerificationState.TheirRequest
				?: return@suspendThread
			activity?.runOnUiThread {
				binding.text.text = getString(
					R.string.verification_start,
					if (verification.theirUserId == client.client.userId) verification.theirDeviceId else verification.theirUserId
				)
				binding.emojiVerification.setOnClickListener {
					binding.emojiVerification.isEnabled = false
					suspendThread {
						currentState.ready()
					}
				}
			}
		}
	}
}