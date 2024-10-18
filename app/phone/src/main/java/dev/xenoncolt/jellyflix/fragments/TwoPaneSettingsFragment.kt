package dev.xenoncolt.jellyflix.fragments

import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceHeaderFragmentCompat

class TwoPaneSettingsFragment : PreferenceHeaderFragmentCompat() {
    override fun onCreatePreferenceHeader(): PreferenceFragmentCompat {
        return SettingsFragment()
    }
}
