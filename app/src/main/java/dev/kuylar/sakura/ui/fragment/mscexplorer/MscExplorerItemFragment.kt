package dev.kuylar.sakura.ui.fragment.mscexplorer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.kuylar.recyclerviewbuilder.ExtensibleRecyclerAdapter
import dev.kuylar.recyclerviewbuilder.RecyclerViewBuilder
import dev.kuylar.sakura.R
import dev.kuylar.sakura.databinding.FragmentMscExplorerItemBinding
import dev.kuylar.sakura.databinding.ItemMscExplorerItemBinding
import dev.kuylar.sakura.mscexplorer.MscExplorer
import dev.kuylar.sakura.mscexplorer.MscExplorerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MscExplorerItemFragment : Fragment() {
	private lateinit var binding: FragmentMscExplorerItemBinding
	private lateinit var adapter: ExtensibleRecyclerAdapter
	private var page = 0
	public var onMscSelected: ((id: Int, url: String) -> Unit)? = null

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentMscExplorerItemBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		adapter = RecyclerViewBuilder(requireContext())
			.addView<MscExplorerItem, ItemMscExplorerItemBinding> { binding, item, _ ->
				binding.title.text = item.title
				binding.body.text = item.body
				binding.info.text = listOf(
					item.user,
					item.state,
					item.labels.joinToString(", ")
				).joinToString(" • ")
				binding.root.setOnClickListener {
					onMscSelected?.invoke(item.num, item.spec)
				}
			}
			.setScrollToBottomListener { nextPage() }
			.setLoadingItem(R.layout.item_loading_spinner)
			.setMaterialDivider()
			.build(binding.root)

		lifecycleScope.launch {
			withContext(Dispatchers.IO) {
				nextPage()
			}
		}
	}

	private fun nextPage() {
		if (adapter.loading) return
		adapter.loading = true
		lifecycleScope.launch {
			withContext(Dispatchers.Main) {
				val newItems = withContext(Dispatchers.IO) {
					MscExplorer.list(++page)
				}
				adapter.loading = false
				adapter.addItems(newItems)
			}
		}
	}
}