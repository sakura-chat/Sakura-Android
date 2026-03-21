package dev.kuylar.sakura.ui.fragment.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.kuylar.sakura.R

class SettingsDetailFragment : PreferenceFragmentCompat() {
	private val fragmentPreferences = mapOf(
		"developer_crashreports" to R.id.nav_crash_reports
	)

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		val screen = arguments?.getString("screen") ?: "general"

		when (screen) {
			"general" -> setPreferencesFromResource(R.xml.settings_general, rootKey)
			"preferences" -> setPreferencesFromResource(R.xml.settings_preferences, rootKey)
			"developer" -> setPreferencesFromResource(R.xml.settings_developer, rootKey)
		}

		fragmentPreferences.forEach { (key, fragmentId) ->
			findPreference<Preference>(key)?.setOnPreferenceClickListener {
				findNavController().navigate(fragmentId)
				true
			}
		}

		findPreference<Preference>("developer_crash")?.setOnPreferenceClickListener {
			throw Exception("whoopsies!")
		}
	}
}