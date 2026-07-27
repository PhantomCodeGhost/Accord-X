package com.phantom.accord.ui.fragments.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.phantom.accord.R
import com.phantom.accord.logic.utils.PlaylistBackupUtils
import com.phantom.accord.ui.LibraryViewModel
import com.phantom.accord.ui.fragments.BasePreferenceFragment
import com.phantom.accord.ui.fragments.BaseSettingFragment
import kotlinx.coroutines.launch

class BackupSettingsFragment : BaseSettingFragment(R.string.settings_category_behavior, { BackupSettingsTopFragment() })

class BackupSettingsTopFragment : BasePreferenceFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                lifecycleScope.launch {
                    val anchor = requireActivity().findViewById<View>(android.R.id.content)
                    PlaylistBackupUtils.exportPlaylistsToJson(
                        requireContext(),
                        libraryViewModel,
                        uri,
                        anchor
                    )
                }
            }
        }

        importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                lifecycleScope.launch {
                    val anchor = requireActivity().findViewById<View>(android.R.id.content)
                    PlaylistBackupUtils.importPlaylistsFromJson(
                        requireContext(),
                        libraryViewModel,
                        uri,
                        anchor
                    )
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_backup, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "export_backup" -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "accord_playlists_backup.json")
                }
                exportLauncher.launch(intent)
            }
            "import_backup" -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importLauncher.launch(intent)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}
