package dev.xenoncolt.jellyflix.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import dev.xenoncolt.jellyflix.core.R as CoreR

class SettingsDownloadsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(CoreR.xml.fragment_settings_downloads, rootKey)
    }
}
