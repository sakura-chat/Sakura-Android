package dev.kuylar.sakura.ui.fragment.mscexplorer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.databinding.FragmentMscExplorerDetailBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.mscexplorer.MscExplorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MscExplorerDetailFragment : Fragment() {
	private var num: Int = 0
	private lateinit var specUrl: String
	private lateinit var binding: FragmentMscExplorerDetailBinding

	@Inject
	lateinit var markdownHandler: MarkdownHandler

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let {
			num = it.getInt("num")
			it.getString("specUrl")?.let { url -> specUrl = url }
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentMscExplorerDetailBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		if (num == 0 || !this::specUrl.isInitialized) return
		lifecycleScope.launch {
			withContext(Dispatchers.Main) {
				val markdownDoc = withContext(Dispatchers.IO) {
					MscExplorer.getSpecDoc(specUrl)
				}
				markdownHandler.setTextView(binding.text, markdownHandler.inputToHtml(markdownDoc))
				binding.loading.visibility = View.GONE
				binding.text.visibility = View.VISIBLE
			}
		}
	}
}