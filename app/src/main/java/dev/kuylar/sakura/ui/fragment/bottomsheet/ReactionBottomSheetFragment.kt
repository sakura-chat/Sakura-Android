package dev.kuylar.sakura.ui.fragment.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentReactionBottomSheetBinding
import dev.kuylar.sakura.ui.fragment.picker.EmojiPickerFragment
import javax.inject.Inject

@AndroidEntryPoint
class ReactionBottomSheetFragment : BottomSheetDialogFragment() {
	private lateinit var binding: FragmentReactionBottomSheetBinding
	private var roomId: RoomId? = null
	private var eventId: EventId? = null

	@Inject
	lateinit var client: Matrix

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let {
			it.getString("roomId")?.let { v -> roomId = RoomId(v) }
			it.getString("eventId")?.let { v -> eventId = EventId(v) }
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentReactionBottomSheetBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (eventId == null || roomId == null) {
			dismiss()
			return
		}

		childFragmentManager.setFragmentResultListener("picker_action", this) { _, bundle ->
			val action = bundle.getString("action") ?: return@setFragmentResultListener
			val params = bundle.getStringArray("params")?.toList() ?: emptyList<String>()

			when (action) {
				"custom_emoji" -> {
					suspendThread {
						client.reactToEvent(roomId!!, eventId!!, params[0], params[1])
					}
					dismiss()
				}

				"unicode_emoji" -> {
					suspendThread {
						client.reactToEvent(roomId!!, eventId!!, params[0])
					}
					dismiss()
				}

				else -> {
					Toast.makeText(
						requireContext(),
						"Picker action $action with params (${params.joinToString(", ") { "\"$it\"" }})",
						Toast.LENGTH_LONG
					).show()
				}
			}
		}

		val f = EmojiPickerFragment()
		f.arguments = bundleOf("roomId" to roomId!!.full)
		childFragmentManager.beginTransaction()
			.replace(binding.fragment.id, f)
			.commit()
	}
}