package dev.kuylar.sakura.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentTimelineBinding
import dev.kuylar.sakura.ui.adapter.recyclerview.TimelineRecyclerAdapter

class TimelineFragment : Fragment() {
	private lateinit var binding: FragmentTimelineBinding
	private lateinit var roomId: String
	private lateinit var client: Matrix

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

		binding.timelineRecycler.layoutManager =
			LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, true)
		binding.timelineRecycler.adapter = TimelineRecyclerAdapter(this, roomId)
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
}