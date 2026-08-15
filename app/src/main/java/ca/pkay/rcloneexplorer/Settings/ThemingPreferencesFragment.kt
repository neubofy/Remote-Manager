package ca.pkay.rcloneexplorer.Settings

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R

class ThemingPreferencesFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_theming_preferences, rootKey)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        requireActivity().title = getString(R.string.look_and_feel)

        val new = getString(R.string.pref_key_theme)
        val old = getString(R.string.pref_key_theme_old)

        if(sharedPreferences.contains(old)) {
            sharedPreferences.edit()
                .putString(new, sharedPreferences.getString(new, "0"))
                .remove(old)
                .apply()
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if(key == activity?.getString(R.string.pref_key_theme)) {
            requireActivity().recreate()
        }
    }

}