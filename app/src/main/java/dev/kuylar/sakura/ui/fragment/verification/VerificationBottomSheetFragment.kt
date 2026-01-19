package dev.kuylar.sakura.ui.fragment.verification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentVerificationBottomSheetBinding
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveVerificationState
import javax.inject.Inject

@AndroidEntryPoint
class VerificationBottomSheetFragment() : BottomSheetDialogFragment() {
	private lateinit var binding: FragmentVerificationBottomSheetBinding
	@Inject lateinit var client: Matrix
	private lateinit var id: String
	private lateinit var verification: ActiveDeviceVerification
	private lateinit var navController: NavController

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
		binding = FragmentVerificationBottomSheetBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		isCancelable = false

		val navHostFragment =
			childFragmentManager.findFragmentById(binding.fragment.id) as NavHostFragment
		navController = navHostFragment.navController

		if (!this::verification.isInitialized) {
			navController.navigate(
				R.id.nav_verification_message,
				bundleOf(
					"title" to R.string.verification_illegal_state_title,
					"subtitle" to R.string.verification_illegal_state_body
				)
			)
			return
		}

		suspendThread {
			verification.state.collect { state ->
				activity?.runOnUiThread {
					when (state) {
						ActiveVerificationState.AcceptedByOtherDevice -> navController.navigate(
							R.id.nav_verification_message,
							bundleOf(
								"state" to false,
								"title" to R.string.verification_illegal_state_title,
								"subtitle" to "AcceptedByOtherDevice"
							)
						)

						is ActiveVerificationState.Cancel -> {
							navController.navigate(
								R.id.nav_verification_message,
								bundleOf(
									"state" to false,
									"title" to R.string.verification_cancel_title
								)
							)
							this@VerificationBottomSheetFragment.isCancelable = true
						}

						is ActiveVerificationState.OwnRequest -> navController.navigate(
							R.id.nav_verification_message,
							bundleOf(
								"state" to false,
								"title" to R.string.verification_illegal_state_title,
								"subtitle" to "OwnRequest"
							)
						)

						is ActiveVerificationState.Ready -> navController.navigate(
							R.id.nav_verification_select_method,
							bundleOf(
								"verification" to verification.transactionId
							)
						)

						is ActiveVerificationState.Start -> {
							when (state.method) {
								is ActiveSasVerificationMethod -> {
									navController.navigate(
										R.id.nav_verification_emoji_select,
										bundleOf(
											"verification" to verification.transactionId
										)
									)
								}

								else -> navController.navigate(
									R.id.nav_verification_message,
									bundleOf(
										"state" to false,
										"title" to R.string.verification_illegal_state_title,
										"subtitle" to R.string.verification_illegal_state_body
									)
								)
							}
						}

						is ActiveVerificationState.TheirRequest -> navController.navigate(
							R.id.nav_verification_start,
							bundleOf(
								"verification" to verification.transactionId
							)
						)

						ActiveVerificationState.Undefined -> navController.navigate(R.id.nav_verification_loading)
						is ActiveVerificationState.WaitForDone -> navController.navigate(R.id.nav_verification_loading)
						is ActiveVerificationState.Done -> {
							navController.navigate(
								R.id.nav_verification_message,
								bundleOf(
									"state" to true,
									"title" to R.string.verification_success_title
								)
							)
							this@VerificationBottomSheetFragment.isCancelable = true
						}
					}
				}
			}
		}
	}
}