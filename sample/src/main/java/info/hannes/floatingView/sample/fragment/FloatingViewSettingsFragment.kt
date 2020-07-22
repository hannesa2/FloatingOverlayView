package info.hannes.floatingView.sample.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import info.hannes.floatingView.sample.R

class FloatingViewSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
        setPreferencesFromResource(R.xml.settings_floatingview, null)
    }

    companion object {
        fun newInstance(): FloatingViewSettingsFragment {
            return FloatingViewSettingsFragment()
        }
    }
}