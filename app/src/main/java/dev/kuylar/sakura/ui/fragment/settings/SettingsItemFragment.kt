package dev.kuylar.sakura.ui.fragment.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.kuylar.sakura.R

class SettingsItemFragment : PreferenceFragmentCompat() {
	var onCategorySelected: ((String) -> Unit)? = null

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.settings_categories, rootKey)

		listOf("general", "preferences", "developer").forEach { key ->
			findPreference<Preference>(key)?.setOnPreferenceClickListener {
				onCategorySelected?.invoke(key)
				true
			}
		}
	}
}