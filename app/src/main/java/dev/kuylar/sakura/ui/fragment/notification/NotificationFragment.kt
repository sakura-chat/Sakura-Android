package dev.kuylar.sakura.ui.fragment.notification

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentNotificationBinding
import dev.kuylar.sakura.markdown.MarkdownHandler
import dev.kuylar.sakura.ui.adapter.listadapter.NotificationsListAdapter
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationFragment : Fragment() {
	private lateinit var binding: FragmentNotificationBinding
	private lateinit var adapter: NotificationsListAdapter
	private val viewModel: NotificationViewModel by viewModels()

	@Inject
	lateinit var markdown: MarkdownHandler

	@Inject
	lateinit var client: Matrix

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentNotificationBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		adapter = NotificationsListAdapter(this, markdown, client)
		binding.recycler.layoutManager = LinearLayoutManager(requireContext())
		binding.recycler.adapter = adapter
		binding.recycler.addItemDecoration(
			MaterialDividerItemDecoration(
				binding.recycler.context,
				(binding.recycler.layoutManager as LinearLayoutManager).orientation
			)
		)
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.notifications.collect { items ->
					Log.i("NotificationFragment", "Collected ${items.size} items (${items.count { it.dismissed }} dismissed)")
					if (this@NotificationFragment::adapter.isInitialized)
						adapter.submitList(items.filterNot { it.dismissed }.sortedBy { it.sortKey })
				}
			}
		}
	}
}