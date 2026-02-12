package dev.kuylar.sakura.ui.fragment.settings

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import dev.kuylar.sakura.Utils.getIndicatorColor
import dev.kuylar.sakura.Utils.loadAvatar
import dev.kuylar.sakura.Utils.suspendThread
import dev.kuylar.sakura.client.Matrix
import dev.kuylar.sakura.client.request.ExtendedGetProfile
import dev.kuylar.sakura.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Job
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {
	private lateinit var binding: FragmentSettingsBinding

	@Inject
	lateinit var client: Matrix

	private var presenceJob: Job? = null
	private var profileJob: Job? = null

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentSettingsBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		presenceJob = suspendThread {
			client.client.user.getPresence(client.userId).collect {
				it?.let { user ->
					activity?.runOnUiThread {
						updatePresence(user)
					}
				}
			}
		}
		profileJob = suspendThread {
			// TODO: Update to the new extensible profile API
			client.client.api.baseClient.request(ExtendedGetProfile(client.userId)).getOrNull()
				?.let { profile ->
					activity?.runOnUiThread {
						updateProfile(profile)
					}
				}
			client.client.api.baseClient
		}
	}

	override fun onDestroy() {
		presenceJob?.cancel()
		profileJob?.cancel()
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
		binding.avatar.loadAvatar(profile.avatarUrl, profile.displayName ?: "")
		binding.displayname.text = profile.displayName
		if (profile.displayName == client.userId.full) {
			binding.username.visibility = View.GONE
		} else {
			binding.username.text = client.userId.full
		}
	}
}