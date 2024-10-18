package dev.xenoncolt.jellyflix.fragments

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.xenoncolt.jellyflix.viewmodels.SettingsDeviceViewModel
import dev.xenoncolt.jellyflix.core.R as CoreR

@AndroidEntryPoint
class SettingsDeviceFragment : PreferenceFragmentCompat() {

    private val viewModel: SettingsDeviceViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(CoreR.xml.fragment_settings_device, rootKey)

        findPreference<EditTextPreference>("deviceName")?.setOnPreferenceChangeListener { _, name ->
            viewModel.updateDeviceName(name.toString())
            true
        }
    }
}
