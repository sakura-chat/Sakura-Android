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
import dev.kuylar.sakura.databinding.FragmentEmojiPickerBinding
import dev.kuylar.sakura.emoji.CustomEmojiCategoryModel
import dev.kuylar.sakura.emoji.CustomEmojiModel
import dev.kuylar.sakura.emoji.EmojiManager
import dev.kuylar.sakura.emoji.RoomCustomEmojiModel
import dev.kuylar.sakura.emojipicker.model.CategoryModel
import java.util.Map.entry
import javax.inject.Inject

@AndroidEntryPoint
class EmojiPickerFragment : Fragment() {
	private lateinit var binding: FragmentEmojiPickerBinding
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
		binding = FragmentEmojiPickerBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.emojiPicker.setOnEmojiSelectedCallback { emoji ->
			if (emoji is RoomCustomEmojiModel) {
				parentFragmentManager.setFragmentResult(
					"picker_action",
					bundleOf(
						"action" to "custom_emoji",
						"params" to listOf(
							emoji.uri,
							emoji.shortcode
						).toTypedArray()
					)
				)
			} else {
				parentFragmentManager.setFragmentResult(
					"picker_action",
					bundleOf(
						"action" to "unicode_emoji",
						"params" to listOf(emoji.name).toTypedArray()
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
		val roomEmoji = roomId?.let { id ->
			client.getRoomEmoji(RoomId(id)).filter { it.value.isNotEmpty() }
		} ?: emptyMap()
		val accountEmojiPacks = client.getSavedImagePacks()
		val accountEmoji = client.getAccountEmoji()
		val recent = client.getRecentEmojis().take(24)
		EmojiManager.getInstance(requireContext()).getEmojiByCategory().let { map ->
			activity?.runOnUiThread {
				val items = emptyMap<CategoryModel, List<CustomEmojiModel>>().toMutableMap()

				val allEmojis: Map<CategoryModel, List<CustomEmojiModel>> =
					map.mapKeys { CustomEmojiCategoryModel(it.key) }
						.mapValues { it.value.map { e -> CustomEmojiModel(e.surrogates) } }
				allEmojis.entries.toMutableList().apply {
					add(
						0,
						entry(
							CustomEmojiCategoryModel("recent"),
							recent.map {
								if (it.emoji.startsWith("mxc://"))
									RoomCustomEmojiModel(it.emoji, "")
								else CustomEmojiModel(it.emoji)
							})
					)
					accountEmojiPacks.forEach {
						if (roomEmoji.keys.any { other -> other == it }) return@forEach
						add(1, it)
					}
					accountEmoji?.let {
						add(1, it)
					}
					roomEmoji.forEach {
						add(1, it)
					}
				}.associateByTo(items, { it.key }, { it.value })

				binding.emojiPicker.setEmojiLayout(R.layout.item_emoji)
				binding.emojiPicker.loadItems(items)
			}
		}
	}
}