package dev.kuylar.sakura.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentTimelineBinding
import dev.kuylar.sakura.ui.adapter.recyclerview.TimelineRecyclerAdapter

class TimelineFragment : Fragment(), MenuProvider {
	private lateinit var binding: FragmentTimelineBinding
	private lateinit var roomId: String
	private lateinit var client: Matrix
	private lateinit var timelineAdapter: TimelineRecyclerAdapter
	private var isLoadingMore = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.getString("roomId")?.let {
			roomId = it
		}
		client = Matrix.getClient()
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentTimelineBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (!this::roomId.isInitialized) {
			findNavController().popBackStack()
			return
		}

		suspendThread {
			client.getRoom(roomId)?.let { room ->
				(activity as? AppCompatActivity)?.let {
					it.runOnUiThread {
						it.supportActionBar?.title = room.name?.explicitName ?: room.roomId.full
					}
				}
			}
		}

		val menuHost: MenuHost = requireActivity()
		menuHost.addMenuProvider(
			this,
			viewLifecycleOwner,
			Lifecycle.State.RESUMED
		)

		timelineAdapter = TimelineRecyclerAdapter(this, roomId, binding.timelineRecycler)
		binding.timelineRecycler.layoutManager =
			LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, true)
		binding.timelineRecycler.addOnScrollListener(object :
			androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
			override fun onScrolled(
				recyclerView: androidx.recyclerview.widget.RecyclerView,
				dx: Int,
				dy: Int
			) {
				super.onScrolled(recyclerView, dx, dy)
				val layoutManager = recyclerView.layoutManager as LinearLayoutManager
				val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
				val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
				val totalItemCount = layoutManager.itemCount

				if (dy == 0 || totalItemCount == 0) return
				if (!isLoadingMore && timelineAdapter.isReady) {
					if (firstVisibleItem == 0) {
						Log.d("TimelineFragment", "Loading more messages (forward)")
						isLoadingMore = true
						suspendThread {
							timelineAdapter.loadMoreForwards()
							Log.d("TimelineFragment", "Loading complete")
							isLoadingMore = false
						}
					} else if (lastVisibleItem == totalItemCount - 1) {
						Log.d("TimelineFragment", "Loading more messages (backward)")
						isLoadingMore = true
						suspendThread {
							timelineAdapter.loadMoreBackwards()
							Log.d("TimelineFragment", "Loading complete")
							isLoadingMore = false
						}
					}
				}
			}
		})



		binding.buttonSend.setOnClickListener {
			it.isEnabled = false
			val msg = binding.input.getValue()
			suspendThread {
				try {
					client.sendMessage(roomId, msg)
					activity?.runOnUiThread {
						it.isEnabled = true
						binding.input.editableText.clear()
					}
				} catch (e: Exception) {
					Log.e("TimelineFragment", "Error sending message", e)
					activity?.runOnUiThread {
						it.isEnabled = true
					}
				}
			}
		}
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.top_app_bar, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return false
	}
}