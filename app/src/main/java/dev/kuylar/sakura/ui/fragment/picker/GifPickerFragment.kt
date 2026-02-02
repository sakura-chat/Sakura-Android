package dev.kuylar.sakura.ui.fragment.picker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.databinding.FragmentGifPickerBinding
import dev.kuylar.sakura.gifpicker.GifPickerProvider
import dev.kuylar.sakura.ui.adapter.listadapter.GifPickerListAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class GifPickerFragment : Fragment() {
	private lateinit var binding: FragmentGifPickerBinding
	private lateinit var adapter: GifPickerListAdapter
	private var cursor: String? = null
	private var query: String? = null
	private var debounceJob: Job? = null
	private var loadingMore = false
	private val loadMoreThreshold = 3

	@Inject
	lateinit var provider: GifPickerProvider
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentGifPickerBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		provider.init(requireContext())

		binding.searchBar.hint = getString(R.string.gif_search_template, provider.getName())

		val gifSize = (resources.displayMetrics.density * 200).toInt()
		val layoutManager = StaggeredGridLayoutManager(
			max(2, resources.displayMetrics.widthPixels / gifSize),
			StaggeredGridLayoutManager.VERTICAL
		)
		adapter = GifPickerListAdapter(this)
		binding.recycler.layoutManager = layoutManager
		binding.recycler.adapter = adapter
		binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
				if (dy <= 0) return

				val totalItemCount = layoutManager.itemCount
				val lastVisibleItems = layoutManager.findLastVisibleItemPositions(null)
				val lastVisibleItem = lastVisibleItems.maxOrNull() ?: return

				if (!loadingMore && lastVisibleItem >= totalItemCount - loadMoreThreshold) {
					loadingMore = true
					suspendThread {
						nextPage()
					}
				}
			}
		})
		binding.searchBar.editText?.addTextChangedListener {
			val query = it?.toString() ?: return@addTextChangedListener
			debounceJob?.cancel()
			debounceJob = suspendThread {
				delay(1.seconds)
				search(query)
			}
		}
	}

	private suspend fun search(query: String) {
		val gifs = provider.searchGifs(query)
		cursor = gifs.cursor
		activity?.runOnUiThread {
			adapter.submitList(gifs.gifs)
		}
	}

	private suspend fun nextPage() {
		if (query == null || cursor == null) return
		val gifs = provider.searchGifs(query!!, cursor!!)
		cursor = gifs.cursor
		activity?.runOnUiThread {
			loadingMore = false
			adapter.addItems(gifs.gifs)
		}
	}
}