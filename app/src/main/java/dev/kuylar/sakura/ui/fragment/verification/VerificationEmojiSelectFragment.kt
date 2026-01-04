package dev.kuylar.sakura.ui.fragment.verification

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentVerificationEmojiSelectBinding
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState

class VerificationEmojiSelectFragment : Fragment() {
	private lateinit var binding: FragmentVerificationEmojiSelectBinding
	private lateinit var client: Matrix
	private lateinit var id: String
	private lateinit var verification: ActiveDeviceVerification

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (arguments == null) return
		client = Matrix.getClient()
		id = requireArguments().getString("verification") ?: return
		client.getVerification(id)
			?.let { verification = it as ActiveDeviceVerification }
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentVerificationEmojiSelectBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		suspendThread {
			val method = (verification.state.first() as? ActiveVerificationState.Start)
				?.method as? ActiveSasVerificationMethod ?: return@suspendThread
			method.state.collect { state ->
				activity?.runOnUiThread {
					when (state) {
						is ActiveSasVerificationState.ComparisonByUser -> {
							binding.emojis.text = state.emojis.joinToString("") { it.second }
							binding.loading.visibility = View.GONE
							binding.emojis.visibility = View.VISIBLE

							binding.accept.setOnClickListener {
								binding.accept.isEnabled = false
								suspendThread {
									state.match()
								}
							}

							binding.reject.setOnClickListener {
								binding.reject.isEnabled = false
								suspendThread {
									state.noMatch()
								}
							}
						}

						is ActiveSasVerificationState.TheirSasStart -> {
							suspendThread {
								state.accept()
							}
						}

						else -> {
							binding.loading.visibility = View.VISIBLE
							binding.emojis.visibility = View.GONE
						}
					}
				}
			}
		}
	}
}