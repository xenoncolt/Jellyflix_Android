package dev.xenoncolt.jellyflix.fragments

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.xenoncolt.jellyflix.AppPreferences
import dev.xenoncolt.jellyflix.R
import dev.xenoncolt.jellyflix.utils.restart
import javax.inject.Inject
import dev.xenoncolt.jellyflix.core.R as CoreR

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(CoreR.xml.fragment_settings, rootKey)

        findPreference<Preference>("switchServer")?.setOnPreferenceClickListener {
            findNavController().navigate(TwoPaneSettingsFragmentDirections.actionNavigationSettingsToServerSelectFragment())
            true
        }

        findPreference<Preference>("switchUser")?.setOnPreferenceClickListener {
            val serverId = appPreferences.currentServer!!
            findNavController().navigate(TwoPaneSettingsFragmentDirections.actionNavigationSettingsToUsersFragment(serverId))
            true
        }

        findPreference<Preference>("switchAddress")?.setOnPreferenceClickListener {
            val serverId = appPreferences.currentServer!!
            findNavController().navigate(TwoPaneSettingsFragmentDirections.actionNavigationSettingsToServerAddressesFragment(serverId))
            true
        }

        findPreference<Preference>("pref_offline_mode")?.setOnPreferenceClickListener {
            activity?.restart()
            true
        }

        findPreference<Preference>("requestContent")?.setOnPreferenceClickListener {
            val bundle = Bundle()
            bundle.putString("url", "https://request.oporajita.win")
            findNavController().navigate(R.id.WebViewFragment, bundle)
            true
        }

        findPreference<Preference>("appInfo")?.setOnPreferenceClickListener {
            findNavController().navigate(TwoPaneSettingsFragmentDirections.actionSettingsFragmentToAboutLibraries())
            true
        }
    }
}
