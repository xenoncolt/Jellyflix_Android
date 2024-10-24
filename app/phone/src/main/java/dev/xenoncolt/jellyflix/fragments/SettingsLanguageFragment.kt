package dev.xenoncolt.jellyflix.fragments

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.xenoncolt.jellyflix.core.R as CoreR

class SettingsLanguageFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(CoreR.xml.fragment_settings_language, rootKey)
        findPreference<Preference>("pref_app_language")?.apply {
            isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            summary = requireContext().resources.configuration.locales.get(0).displayName
            setOnPreferenceClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.parse("package:${requireContext().packageName}"),
                    ),
                )
                true
            }
        }
    }
}
