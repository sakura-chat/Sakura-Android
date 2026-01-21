package dev.kuylar.sakura.ui.fragment.bottomsheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.kuylar.sakura.R
import dev.kuylar.sakura.Utils.getIndicatorColor
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.customevent.UserNoteEventContent
import dev.kuylar.sakura.client.request.ExtendedGetProfile
import dev.kuylar.sakura.databinding.FragmentProfileBottomSheetBinding
import dev.kuylar.sakura.ui.activity.MainActivity
import io.getstream.avatarview.glide.loadImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.client.user
import net.folivo.trixnity.client.user.PowerLevel
import net.folivo.trixnity.client.user.getAccountData
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class ProfileBottomSheetFragment : BottomSheetDialogFragment() {
	private lateinit var binding: FragmentProfileBottomSheetBinding
	private lateinit var userId: UserId
	private lateinit var roomId: RoomId

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

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let {
			userId = UserId(it.getString("userId") ?: "")
			roomId = RoomId(it.getString("roomId") ?: "")
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentProfileBottomSheetBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		if (userId.full.isBlank() || roomId.full.isBlank()) {
			dismiss()
			return
		}

		presenceJob = suspendThread {
			client.client.user.getPresence(userId).collect {
				it?.let { user ->
					activity?.runOnUiThread {
						updatePresence(user)
					}
				}
			}
		}
		presenceJob = suspendThread {
			client.client.user.getPresence(userId).collect {
				it?.let { user ->
					activity?.runOnUiThread {
						updatePresence(user)
					}
				}
			}
		}
		memberJob = suspendThread {
			client.client.room.getState<MemberEventContent>(roomId, userId.full).collect {
				it?.content?.let { user ->
					activity?.runOnUiThread {
						updateMember(user)
					}
				}
			}
		}
		profileJob = suspendThread {
			client.client.api.baseClient.request(ExtendedGetProfile(userId)).getOrNull()
				?.let { profile ->
					activity?.runOnUiThread {
						updateProfile(profile)
					}
				}
			client.client.api.baseClient
		}
		memberJob = suspendThread {
			client.client.user.getPowerLevel(roomId, userId).collect { powerLevel ->
				activity?.runOnUiThread {
					updatePowerLevel(powerLevel)
				}
			}
		}
		noteJob = suspendThread {
			client.client.user.getAccountData<UserNoteEventContent>().collect {
				it?.let { note ->
					activity?.runOnUiThread {
						updateUserNote(note)
					}
				}
			}
		}
		roomJob = suspendThread {
			client.client.room.getById(roomId).collect {
				it?.let { room ->
					activity?.runOnUiThread {
						updateRoom(room)
					}
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
					client.client.api.user.setAccountData(
						noteEvent!!.copyWith(userId, it?.toString() ?: ""),
						client.userId
					)
					Log.i("ProfileBottomSheetFragment", "Updated user note")
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
		binding.avatar.loadImage(member.avatarUrl, true)
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
	}


	private fun updatePowerLevel(powerLevel: PowerLevel) {
		binding.roomName.visibility = View.VISIBLE
		binding.roleChip.text = when (powerLevel) {
			PowerLevel.Creator -> getString(R.string.power_level_creator)
			is PowerLevel.User -> when {
				powerLevel.level == 100L -> getString(R.string.power_level_administrator)
				powerLevel.level > 50L -> getString(R.string.power_level_moderator)
				else -> getString(R.string.power_level_user)
			}
		}
	}

	private fun updateUserNote(note: UserNoteEventContent) {
		noteEvent = note
		binding.note.editText?.editableText?.replace(
			0,
			binding.note.editText?.editableText?.length ?: 0,
			note.notes?.get(userId) ?: ""
		)
	}

	private fun updateRoom(room: Room) {
		binding.roomName.text = room.name?.explicitName ?: room.roomId.full
	}
}