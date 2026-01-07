package dev.kuylar.sakura.ui.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentRoomInfoPanelBinding
import dev.kuylar.sakura.ui.adapter.recyclerview.UserListRecyclerAdapter

class RoomInfoPanelFragment : Fragment() {
	private lateinit var binding: FragmentRoomInfoPanelBinding
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
		binding = FragmentRoomInfoPanelBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (!this::roomId.isInitialized) {
			return
		}

		suspendThread {
			client.getRoom(roomId)?.let { room ->
				activity?.runOnUiThread {
					Glide.with(this)
						.load(room.avatarUrl)
						.into(binding.roomIcon)
					binding.roomName.text = room.name?.explicitName ?: room.roomId.full
					binding.roomTopic.visibility = View.GONE
				}
			}
		}

		binding.recycler.layoutManager = LinearLayoutManager(requireContext())
		binding.recycler.adapter = UserListRecyclerAdapter(this, roomId)
	}
}