/*
 *     Copyright (C) 2024 Phantom Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.phantom.accord.ui.fragments.settings

import android.os.Bundle
import androidx.preference.Preference
import com.phantom.accord.R
import com.phantom.accord.ui.MainActivity
import com.phantom.accord.ui.fragments.AboutFragment
import com.phantom.accord.ui.fragments.BasePreferenceFragment
import com.phantom.accord.ui.fragments.BaseSettingFragment

class MainSettingsFragment : BaseSettingFragment(R.string.home_menu_settings, { MainSettingsTopFragment() })

class MainSettingsTopFragment : BasePreferenceFragment() {
    private val languages = listOf(
        Pair("System Default", ""),
        Pair("English", "en"),
        Pair("العربية (Arabic)", "ar"),
        Pair("Беларуская (Belarusian)", "be"),
        Pair("বাংলা (Bengali)", "bn"),
        Pair("Čeština (Czech)", "cs"),
        Pair("Deutsch (German)", "de"),
        Pair("Español (Spanish)", "es"),
        Pair("فارسی (Persian)", "fa"),
        Pair("Français (French)", "fr"),
        Pair("Magyar (Hungarian)", "hu"),
        Pair("Interlingua", "ia"),
        Pair("Bahasa Indonesia (Indonesian)", "in"),
        Pair("Italiano (Italian)", "it"),
        Pair("日本語 (Japanese)", "ja"),
        Pair("Norsk Bokmål (Norwegian)", "nb-NO"),
        Pair("नेपाली (Nepali)", "ne"),
        Pair("Polski (Polish)", "pl"),
        Pair("Português (Portuguese)", "pt"),
        Pair("Português do Brasil (Brazilian Portuguese)", "pt-BR"),
        Pair("Română (Romanian)", "ro"),
        Pair("Русский (Russian)", "ru"),
        Pair("Türkçe (Turkish)", "tr"),
        Pair("Українська (Ukrainian)", "uk"),
        Pair("Tiếng Việt (Vietnamese)", "vi"),
        Pair("简体中文 (Simplified Chinese)", "zh-CN"),
        Pair("繁體中文 (Traditional Chinese)", "zh-TW")
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_top, rootKey)
        updateLanguageSummary()
    }

    private fun updateLanguageSummary() {
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val currentTag = if (!currentLocales.isEmpty) currentLocales[0]?.toLanguageTag() ?: "" else ""
        val currentLang = languages.find { 
            it.second.equals(currentTag, ignoreCase = true) || 
            (currentTag.isNotEmpty() && it.second.startsWith(currentTag.take(2), ignoreCase = true)) 
        }?.first ?: "System Default"
        findPreference<Preference>("language")?.summary = currentLang
    }

    private fun showLanguageDialog() {
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val currentTag = if (!currentLocales.isEmpty) currentLocales[0]?.toLanguageTag() ?: "" else ""
        var selectedIndex = languages.indexOfFirst { 
            it.second.equals(currentTag, ignoreCase = true) || 
            (currentTag.isNotEmpty() && it.second.startsWith(currentTag.take(2), ignoreCase = true)) 
        }
        if (selectedIndex == -1) selectedIndex = 0

        val names = languages.map { it.first }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Language")
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                val tag = languages[which].second
                val appLocales = if (tag.isEmpty()) {
                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                } else {
                    androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                }
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocales)
                updateLanguageSummary()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "appearance" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, AppearanceSettingsFragment())
                    .commit()
            }

            "behavior" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, BehaviorSettingsFragment())
                    .commit()
            }

            "about" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, AboutFragment())
                    .commit()
            }

            "audio" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, AudioSettingsFragment())
                    .commit()
            }

            "experimental" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, ExperimentalSettingsFragment())
                    .commit()
            }
            "backup_restore" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, BackupSettingsFragment())
                    .commit()
            }
            "language" -> {
                showLanguageDialog()
            }
            "kill_switch" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onDestroy() {
        (activity as MainActivity).playerBottomSheet.shouldRetractBottomNavigation(false)
        super.onDestroy()
    }

}
