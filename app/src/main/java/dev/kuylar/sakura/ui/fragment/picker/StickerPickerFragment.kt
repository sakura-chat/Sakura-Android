package dev.kuylar.sakura.ui.fragment.picker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.core.model.RoomId
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentStickerPickerBinding
import dev.kuylar.sakura.emoji.CustomEmojiModel
import dev.kuylar.sakura.emoji.EmojiManager
import dev.kuylar.sakura.emoji.RoomStickerModel
import dev.kuylar.sakura.emojipicker.model.CategoryModel
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class StickerPickerFragment : Fragment() {
	private lateinit var binding: FragmentStickerPickerBinding
	private var roomId: String? = null

	@Inject
	lateinit var client: Matrix

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let {
			roomId = it.getString("roomId")
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentStickerPickerBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.emojiPicker.setOnEmojiSelectedCallback { it ->
			if (it is RoomStickerModel) {
				parentFragmentManager.setFragmentResult(
					"picker_action",
					bundleOf(
						"action" to "sticker",
						"params" to listOf(
							it.key,
							Json.encodeToString(it.sticker)
						).toTypedArray()
					)
				)
			}
		}
		val ignoreView =
			binding.emojiPicker.findViewById<TabLayout>(dev.kuylar.sakura.emojipicker.R.id.tabLayout)
		PanelsChildGestureRegionObserver.Provider.get().register(ignoreView)
		suspendThread {
			updateEmojiPicker()
		}
	}

	private suspend fun updateEmojiPicker() {
		val roomStickers = roomId?.let { id ->
			client.getRoomStickers(RoomId(id)).filter { it.value.isNotEmpty() }
		} ?: emptyMap()
		val accountStickerPacks = client.getSavedStickers()
		val accountStickers = client.getAccountStickers()
		EmojiManager.getInstance(requireContext()).getEmojiByCategory().let { map ->
			activity?.runOnUiThread {
				val items = emptyMap<CategoryModel, List<CustomEmojiModel>>().toMutableMap()
				roomStickers.forEach { items[it.key] = it.value }
				accountStickers?.let { items[it.key] = it.value }
				accountStickerPacks.forEach { items[it.key] = it.value }

				binding.emojiPicker.setEmojiLayout(R.layout.item_sticker)
				binding.emojiPicker.loadItems(items)
			}
		}
	}
}