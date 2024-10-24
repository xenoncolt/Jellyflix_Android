package dev.xenoncolt.jellyflix.fragments

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import dev.xenoncolt.jellyflix.Constants
import dev.xenoncolt.jellyflix.core.R as CoreR

class SettingsNetworkFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(CoreR.xml.fragment_settings_network, rootKey)

        findPreference<EditTextPreference>(Constants.PREF_NETWORK_SOCKET_TIMEOUT)?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }
}
