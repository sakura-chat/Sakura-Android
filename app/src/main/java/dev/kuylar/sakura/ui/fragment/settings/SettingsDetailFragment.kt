package dev.kuylar.sakura.ui.fragment.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import dev.kuylar.sakura.R

class SettingsDetailFragment : PreferenceFragmentCompat() {
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		val screen = arguments?.getString("screen") ?: "general"

		when (screen) {
			"general" -> setPreferencesFromResource(R.xml.settings_general, rootKey)
			"preferences" -> setPreferencesFromResource(R.xml.settings_preferences, rootKey)
		}
	}
}