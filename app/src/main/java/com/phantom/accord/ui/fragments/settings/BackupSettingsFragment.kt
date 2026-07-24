package com.phantom.accord.ui.fragments.settings

import android.os.Bundle
import androidx.preference.Preference
import com.phantom.accord.R
import com.phantom.accord.ui.fragments.BasePreferenceFragment
import com.phantom.accord.ui.fragments.BaseSettingFragment

class BackupSettingsFragment : BaseSettingFragment(R.string.settings_category_behavior, { BackupSettingsTopFragment() }) {
    // Note: We use a generic title string here, it might be better to create a specific one for Backup & Restore,
    // but the user only asked for the UI skeleton for now.
}

class BackupSettingsTopFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_backup, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "import_backup" -> {
                // Logic to be added later
            }
            "export_backup" -> {
                // Logic to be added later
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}
