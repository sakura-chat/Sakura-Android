package dev.kuylar.sakura.ui.fragment.mscexplorer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.R
import dev.kuylar.sakura.databinding.FragmentMscExplorerBinding
import dev.kuylar.sakura.ui.BackButtonListener

@AndroidEntryPoint
class MscExplorerFragment : Fragment(), BackButtonListener {
	private lateinit var binding: FragmentMscExplorerBinding

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentMscExplorerBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (savedInstanceState == null) {
			childFragmentManager.beginTransaction()
				.replace(R.id.list_pane, MscExplorerItemFragment().apply {
					onMscSelected = { id, url -> showDetails(id, url) }
				})
				.commit()
		}
	}

	private fun showDetails(num: Int, specUrl: String, open: Boolean = true) {
		childFragmentManager.beginTransaction()
			.replace(R.id.detail_container, MscExplorerDetailFragment().apply {
				arguments = bundleOf("num" to num, "specUrl" to specUrl)
			})
			.commit()
		if (open)
			binding.root.openPane()
	}

	override fun onBackPressed(): Boolean {
		return binding.root.closePane()
	}
}