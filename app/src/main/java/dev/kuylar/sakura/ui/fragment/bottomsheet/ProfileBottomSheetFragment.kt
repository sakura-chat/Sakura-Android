package dev.kuylar.sakura.ui.fragment.bottomsheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.getIndicatorColor
import dev.kuylar.sakura.Utils.getName
import dev.kuylar.sakura.Utils.loadAvatar
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.UserNoteEventContent
import dev.kuylar.sakura.client.request.ExtendedGetProfile
import dev.kuylar.sakura.databinding.FragmentProfileBottomSheetBinding
import dev.kuylar.sakura.ui.activity.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class ProfileBottomSheetFragment : BottomSheetDialogFragment() {
	private lateinit var binding: FragmentProfileBottomSheetBinding
	private val userId: UserId by lazy { UserId(arguments?.getString("userId") ?: "") }
	private val roomId: RoomId? by lazy { arguments?.getString("roomId")?.let { RoomId(it) } }
	private val roomProfile by lazy { roomId != null }

	@Inject
	lateinit var client: Matrix

	private var presenceJob: Job? = null
	private var memberJob: Job? = null
	private var profileJob: Job? = null
	private var powerLevelJob: Job? = null
	private var noteJob: Job? = null
	private var roomJob: Job? = null
	private var changeNoteJob: Job? = null
	private var noteEvent: UserNoteEventContent? = null

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentProfileBottomSheetBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (userId.full.isBlank()) {
			dismiss()
			return
		}

		presenceJob = suspendThread {
			client.getUserPresenceFlow(userId).collect {
				it?.let { user ->
					activity?.runOnUiThread {
						updatePresence(user)
					}
				}
			}
		}
		profileJob = suspendThread {
			client.getUserProfile(userId)?.let { profile ->
					activity?.runOnUiThread {
						updateProfile(profile)
					}
				}
		}
		if (roomProfile) {
			memberJob = suspendThread {
				client.getRoomState(roomId!!, MemberEventContent::class, userId.full)?.let {
					activity?.runOnUiThread {
						updateMember(it)
					}
				}
			}
			memberJob = suspendThread {
				client.getUserPowerLevel(roomId!!, userId).let { powerLevel ->
					activity?.runOnUiThread {
						updatePowerLevel(powerLevel)
					}
				}
			}
			roomJob = suspendThread {
				client.getRoom(roomId!!)?.let {
					updateRoom(it)
				}
			}
		}

		noteJob = suspendThread {
			client.getUserNote(userId)?.let {
				activity?.runOnUiThread {
					updateUserNote(it)
				}
			}
		}

		binding.buttonMessage.setOnClickListener {
			it.isEnabled = false
			suspendThread {
				client.getDmChannel(userId)?.let { roomId ->
					(activity as MainActivity).openRoomTimeline(roomId)
					dismiss()
				}
			}
		}

		binding.buttonShare.setOnClickListener {
			val url = "https://matrix.to/#/${userId.full}"
			val sendIntent: Intent = Intent().apply {
				action = Intent.ACTION_SEND
				putExtra(Intent.EXTRA_TEXT, url)
				type = "text/plain"
			}
			startActivity(Intent.createChooser(sendIntent, null))
		}

		binding.buttonCopyId.setOnClickListener {
			context?.getSystemService(ClipboardManager::class.java)
				?.setPrimaryClip(ClipData.newPlainText("User ID", userId.full))
		}

		binding.note.editText?.addTextChangedListener {
			if (noteEvent != null && it?.toString() != noteEvent!!.notes?.get(userId)) {
				changeNoteJob?.cancel()
				changeNoteJob = suspendThread {
					delay(1000)
					client.updateUserNote(userId, it?.toString() ?: "")
				}
			}
		}
	}

	override fun onDestroy() {
		presenceJob?.cancel()
		memberJob?.cancel()
		profileJob?.cancel()
		powerLevelJob?.cancel()
		noteJob?.cancel()
		roomJob?.cancel()
		super.onDestroy()
	}

	private fun updatePresence(presence: UserPresence) {
		binding.status.visibility =
			if (presence.statusMessage.isNullOrBlank()) View.GONE else View.VISIBLE
		binding.status.text = presence.statusMessage
		context?.let {
			binding.avatar.indicatorColor = presence.presence.getIndicatorColor(it)
		}
	}

	private fun updateMember(member: MemberEventContent) {
		binding.avatar.loadAvatar(member.avatarUrl, member.displayName ?: "")
		binding.displayname.text = member.displayName
		if (member.displayName == userId.full) {
			binding.username.visibility = View.GONE
		} else {
			binding.username.text = userId.full
		}
	}

	private fun updateProfile(profile: ExtendedGetProfile.Response) {
		val extraValues = listOf(
			profile.pronouns?.mapNotNull { it.summary }?.joinToString(", "),
			(profile.timezone ?: profile.timezoneUnsafe)?.let { tz ->
				val zoneId = ZoneId.of(tz)
				val time = ZonedDateTime.now(zoneId)
				val pattern =
					if (DateFormat.is24HourFormat(requireContext())) "HH:mm" else "hh:mm a"
				val formatter = DateTimeFormatter.ofPattern(pattern)
				"${time.format(formatter)} ($zoneId)"
			}
		).filterNot { it.isNullOrBlank() }
		binding.extras.visibility = if (extraValues.isNotEmpty()) View.VISIBLE else View.GONE
		binding.extras.text = extraValues.joinToString(" • ")

		if (!profile.bio.isNullOrBlank()) {
			binding.userAboutTitle.visibility = View.VISIBLE
			binding.userAbout.visibility = View.VISIBLE
			binding.userAbout.text = profile.bio
		}

		if (!roomProfile) {
			binding.avatar.loadAvatar(profile.avatarUrl, profile.displayName ?: "")
			binding.displayname.text = profile.displayName
			if (profile.displayName == userId.full) {
				binding.username.visibility = View.GONE
			} else {
				binding.username.text = userId.full
			}
		}
	}

	private fun updatePowerLevel(powerLevel: Long) {
		binding.roomName.visibility = View.VISIBLE
		binding.roleChip.visibility = View.VISIBLE
		binding.roleChip.text = when (powerLevel) {
			Long.MAX_VALUE -> getString(R.string.power_level_creator)
			100L -> getString(R.string.power_level_administrator)
			else -> {
				if (powerLevel >= 50L) getString(R.string.power_level_moderator)
				else getString(R.string.power_level_user)
			}
		}
	}

	private fun updateUserNote(note: String) {
		binding.note.editText?.editableText?.replace(
			0,
			binding.note.editText?.editableText?.length ?: 0,
			note
		)
	}

	private suspend fun updateRoom(room: Room) {
		room.getName(requireContext(), client).let {
			activity?.runOnUiThread {
				binding.roomName.text = it
			}
		}
	}
}