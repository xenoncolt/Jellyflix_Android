package dev.xenoncolt.jellyflix.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.xenoncolt.jellyflix.core.R as CoreR

class SettingsPlayerFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(CoreR.xml.fragment_settings_player, rootKey)
        findPreference<EditTextPreference>("pref_player_seek_back_inc")?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
        findPreference<EditTextPreference>("pref_player_seek_forward_inc")?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
        findPreference<Preference>("pref_player_subtitles")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS))
            true
        }
    }
}
