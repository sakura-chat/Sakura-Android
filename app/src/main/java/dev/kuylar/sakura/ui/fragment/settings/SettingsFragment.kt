package dev.kuylar.sakura.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.R
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentSettingsBinding
import dev.kuylar.sakura.ui.BackButtonListener
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment(), BackButtonListener {
	private lateinit var binding: FragmentSettingsBinding

	@Inject
	lateinit var client: Matrix

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentSettingsBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (savedInstanceState == null) {
			childFragmentManager.beginTransaction()
				.replace(R.id.list_pane, SettingsItemFragment().apply {
					onCategorySelected = { key -> showDetails(key) }
				})
				.commit()
			showDetails("general", false)
		}
	}

	private fun showDetails(key: String, open: Boolean = true) {
		childFragmentManager.beginTransaction()
			.replace(R.id.detail_container, SettingsDetailFragment().apply {
				arguments = bundleOf("screen" to key)
			})
			.commit()
		if (open)
			binding.root.openPane()
	}

	override fun onBackPressed(): Boolean {
		return binding.root.closePane()
	}
}