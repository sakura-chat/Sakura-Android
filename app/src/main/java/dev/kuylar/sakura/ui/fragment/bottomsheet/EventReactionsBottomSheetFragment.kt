package dev.kuylar.sakura.ui.fragment.bottomsheet

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.room.TimelineEventAggregation
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.kuylar.mentionsedittext.ImageMentionSpan
import dev.kuylar.recyclerviewbuilder.RecyclerViewBuilder
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.loadAvatar
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.databinding.FragmentEventReactionListBinding
import dev.kuylar.sakura.databinding.FragmentEventReactionsBottomSheetBinding
import dev.kuylar.sakura.databinding.ItemUserBinding
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EventReactionsBottomSheetFragment : BottomSheetDialogFragment() {
	private lateinit var binding: FragmentEventReactionsBottomSheetBinding
	private val eventId: EventId? by lazy { arguments?.getString("eventId")?.let { EventId(it) } }
	private val roomId: RoomId? by lazy { arguments?.getString("roomId")?.let { RoomId(it) } }

	@Inject
	lateinit var client: Matrix

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentEventReactionsBottomSheetBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (roomId == null || eventId == null) {
			dismiss()
			return
		}
		lifecycleScope.launch {
			val reactions = client.getReactions(roomId!!, eventId!!)
			activity?.runOnUiThread {
				val reactionsList = reactions.reactions
					.map { Pair(it.key, it.value) }
					.sortedByDescending { it.second.minOf { e -> e.originTimestamp } }
					.map { it.first }
				val adapter = ReactionsAdapter(
					this@EventReactionsBottomSheetFragment,
					reactions,
					reactionsList,
					roomId!!.full
				)
				binding.reactionsPager.adapter = adapter
				TabLayoutMediator(binding.reactionsTabs, binding.reactionsPager) { tab, position ->
					if (reactionsList[position].startsWith("mxc://")) {
						val span = ImageMentionSpan("￼") {
							Glide.with(requireContext())
								.asDrawable()
								.load(reactionsList[position])
								.error(R.drawable.ic_emoji)
								.into(object : CustomTarget<Drawable>() {
									override fun onResourceReady(
										resource: Drawable,
										transition: Transition<in Drawable>?
									) { it(resource) }

									override fun onLoadCleared(placeholder: Drawable?) {}
								})
						}
						span.onImageLoaded = {
							tab.text = tab.text
						}
						val s = SpannableStringBuilder()
						s.append("￼")
						s.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						tab.text = s
					} else tab.text = reactionsList[position]
				}.attach()
			}
		}
	}


	class ReactionsAdapter(
		fragment: Fragment,
		private val reactions: TimelineEventAggregation.Reaction,
		private val reactionsList: List<String>,
		private val roomId: String
	) : FragmentStateAdapter(fragment) {

		override fun getItemCount(): Int = reactions.reactions.size

		override fun createFragment(position: Int): Fragment {
			val fragment = EventReactionListFragment()
			val reaction = reactionsList[position]
			fragment.arguments = Bundle().apply {
				putString("roomId", roomId)
				putStringArray(
					"reactionUsers",
					reactions.reactions[reaction]?.map { it.sender.full }?.toTypedArray()
						?: emptyArray<String>()
				)
			}
			return fragment
		}
	}

	@AndroidEntryPoint
	class EventReactionListFragment : Fragment() {
		private lateinit var binding: FragmentEventReactionListBinding
		private val roomId: RoomId? by lazy { arguments?.getString("roomId")?.let { RoomId(it) } }
		private val reactionUsers: List<String>? by lazy {
			arguments?.getStringArray("reactionUsers")?.toList()
		}

		@Inject
		lateinit var client: Matrix

		override fun onCreateView(
			inflater: LayoutInflater,
			container: ViewGroup?,
			savedInstanceState: Bundle?
		): View {
			binding = FragmentEventReactionListBinding.inflate(inflater, container, false)
			return binding.root
		}

		override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
			if (reactionUsers == null || roomId == null) return

			val adapter = RecyclerViewBuilder(requireContext())
				.addView<RoomUser, ItemUserBinding> { binding, user, _ ->
					binding.avatar.avatarInitials = null
					binding.avatar.indicatorEnabled = false
					binding.avatar.loadAvatar(user.avatarUrl, user.name)
					binding.name.text = user.name

					binding.status.visibility = View.GONE
					binding.root.setOnClickListener {
						val f = ProfileBottomSheetFragment()
						f.arguments = Bundle().apply {
							putString("userId", user.userId.full)
							putString("roomId", roomId!!.full)
						}
						f.show(parentFragmentManager, "profileBottomSheet")
					}
				}
				.build(binding.root)

			lifecycleScope.launch {
				val users = reactionUsers?.map { client.getUser(UserId(it), roomId!!) }
					?.requireNoNulls() ?: emptyList()
				activity?.runOnUiThread {
					adapter.addItems(users)
				}
			}
		}
	}
}