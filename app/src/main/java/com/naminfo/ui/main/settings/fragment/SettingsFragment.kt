/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.naminfo.ui.main.settings.fragment

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.naminfo.R
import com.naminfo.compatibility.Compatibility
import org.linphone.core.tools.Log
import com.naminfo.databinding.SettingsFragmentBinding
import com.naminfo.ui.main.fragment.GenericMainFragment
import com.naminfo.utils.ConfirmationDialogModel
import com.naminfo.ui.main.settings.viewmodel.SettingsViewModel
import com.naminfo.utils.AppUtils
import com.naminfo.utils.DialogUtils
import com.naminfo.utils.Event
import java.lang.Exception

@UiThread
class SettingsFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Settings Fragment]"

        private const val RINGTONE_PICKER_INTENT_ID = 89
    }

    private lateinit var binding: SettingsFragmentBinding

    private lateinit var viewModel: SettingsViewModel

    private val sortContactsByListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val label = viewModel.sortContactsByNames[position]
            val value = viewModel.sortContactsByValues[position]
            Log.i("$TAG Selected contact sorting is now [$label] ($value)")
            viewModel.setContactSorting(value)

            sharedViewModel.forceRefreshContactsList.postValue(Event(true))
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private val layoutListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val label = viewModel.availableLayoutsNames[position]
            val value = viewModel.availableLayoutsValues[position]
            Log.i("$TAG Selected meeting default layout is now [$label] ($value)")
            viewModel.setDefaultLayout(value)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private val themeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val label = viewModel.availableThemesNames[position]
            val value = viewModel.availableThemesValues[position]
            Log.i("$TAG Selected theme is now [$label] ($value)")
            viewModel.setTheme(value)

            when (value) {
                0 -> Compatibility.forceLightMode(requireContext())
                1 -> Compatibility.forceDarkMode(requireContext())
                else -> Compatibility.setAutoLightDarkMode(requireContext())
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private val colorListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val label = viewModel.availableColorsNames[position]
            val value = viewModel.availableColorsValues[position]
            Log.i("$TAG Selected color is now [$label] ($value)")
            // Be careful not to create an infinite loop
            if (value != viewModel.color.value.orEmpty()) {
                viewModel.setColor(value)
                requireActivity().recreate()
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private val tunnelModeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            viewModel.tunnelModeIndex.value = position
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        binding.setAdvancedCallSettingsClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                val action = SettingsFragmentDirections.actionSettingsFragmentToSettingsAdvancedCallFragment()
                findNavController().navigate(action)
            }
        }

        binding.setAdvancedSettingsClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                val action = SettingsFragmentDirections.actionSettingsFragmentToSettingsAdvancedFragment()
                findNavController().navigate(action)
            }
        }

        binding.setDeveloperSettingsClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                val action = SettingsFragmentDirections.actionSettingsFragmentToSettingsDeveloperFragment()
                findNavController().navigate(action)
            }
        }

        viewModel.recreateActivityEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG Recreate Activity")
                requireActivity().recreate()
            }
        }

        viewModel.goToIncomingCallNotificationChannelSettingsEvent.observe(viewLifecycleOwner) {
            it.consume { currentRingtone ->
                try {
                    /*
                    Log.w("$TAG Going to incoming call channel settings")
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        putExtra(
                            Settings.EXTRA_CHANNEL_ID,
                            getString(R.string.notification_channel_without_ringtone_incoming_call_id)
                        )
                    }
                    startActivity(intent)
                    */
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_TYPE,
                            RingtoneManager.TYPE_RINGTONE
                        )
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtone)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, AppUtils.getString(R.string.settings_calls_change_ringtone_pick_title))
                    }
                    startActivityForResult(intent, RINGTONE_PICKER_INTENT_ID)
                } catch (e: Exception) {
                    Log.e("$TAG Failed start ringtone picker: $e")
                    // TODO: show error to user
                }
            }
        }

        // Setup sort contacts by spinner
        val sortContactsByAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.sortContactsByNames
        )
        sortContactsByAdapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.contactsSettings.sortContactsByFirstNameSpinner.adapter = sortContactsByAdapter

        viewModel.sortContactsBy.observe(viewLifecycleOwner) { sort ->
            binding.contactsSettings.sortContactsByFirstNameSpinner.setSelection(
                viewModel.sortContactsByValues.indexOf(sort)
            )
        }
        binding.contactsSettings.sortContactsByFirstNameSpinner.onItemSelectedListener = sortContactsByListener

        viewModel.addLdapServerEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                    val action =
                        SettingsFragmentDirections.actionSettingsFragmentToLdapServerConfigurationFragment(
                            null
                        )
                    findNavController().navigate(action)
                }
            }
        }

        viewModel.editLdapServerEvent.observe(viewLifecycleOwner) {
            it.consume { name ->
                if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                    val action =
                        SettingsFragmentDirections.actionSettingsFragmentToLdapServerConfigurationFragment(
                            name
                        )
                    findNavController().navigate(action)
                }
            }
        }

        viewModel.addCardDavServerEvent.observe(viewLifecycleOwner) {
            it.consume {
                if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                    val action =
                        SettingsFragmentDirections.actionSettingsFragmentToCardDavAddressBookConfigurationFragment(
                            null
                        )
                    findNavController().navigate(action)
                }
            }
        }

        viewModel.editCardDavServerEvent.observe(viewLifecycleOwner) {
            it.consume { name ->
                if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                    val action =
                        SettingsFragmentDirections.actionSettingsFragmentToCardDavAddressBookConfigurationFragment(
                            name
                        )
                    findNavController().navigate(action)
                }
            }
        }

        // Meeting default layout related
        val layoutAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableLayoutsNames
        )
        layoutAdapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.meetingsSettings.layoutSpinner.adapter = layoutAdapter

        viewModel.defaultLayout.observe(viewLifecycleOwner) { layout ->
            binding.meetingsSettings.layoutSpinner.setSelection(
                viewModel.availableLayoutsValues.indexOf(layout)
            )
        }
        binding.meetingsSettings.layoutSpinner.onItemSelectedListener = layoutListener

        // Light/Dark theme related
        val themeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableThemesNames
        )
        themeAdapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.userInterfaceSettings.themeSpinner.adapter = themeAdapter

        viewModel.theme.observe(viewLifecycleOwner) { theme ->
            binding.userInterfaceSettings.themeSpinner.setSelection(
                viewModel.availableThemesValues.indexOf(theme)
            )
            binding.userInterfaceSettings.themeSpinner.onItemSelectedListener = themeListener
        }

        // Choose main color
        val colorAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableColorsNames
        )
        colorAdapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.userInterfaceSettings.colorSpinner.adapter = colorAdapter

        viewModel.color.observe(viewLifecycleOwner) { color ->
            binding.userInterfaceSettings.colorSpinner.setSelection(
                viewModel.availableColorsValues.indexOf(color)
            )
            binding.userInterfaceSettings.colorSpinner.onItemSelectedListener = colorListener
        }

        // Tunnel mode
        val tunnelModeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.tunnelModeLabels
        )
        tunnelModeAdapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.tunnelSettings.tunnelModeSpinner.adapter = tunnelModeAdapter
        binding.tunnelSettings.tunnelModeSpinner.onItemSelectedListener = tunnelModeListener

        viewModel.tunnelModeIndex.observe(viewLifecycleOwner) { index ->
            binding.tunnelSettings.tunnelModeSpinner.setSelection(index)
        }

        binding.setTurnOnVfsClickListener {
            showConfirmVfsDialog()
        }

        startPostponedEnterTransition()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == RINGTONE_PICKER_INTENT_ID) {
            val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                Log.i("$TAG Ringtone picker result is OK, URI found in intent is [$uri]")
                viewModel.setRingtoneUri(uri)
            } else {
                Log.e("$TAG Ringtone picker result is OK but URI is null!")
                // TODO: show error to user
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.reloadLdapServers()
        viewModel.reloadConfiguredCardDavServers()
        viewModel.reloadShowDeveloperSettings()
    }

    override fun onPause() {
        if (viewModel.isTunnelAvailable.value == true) {
            viewModel.saveTunnelConfig()
        }

        super.onPause()
    }

    private fun showConfirmVfsDialog() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getConfirmTurningOnVfsDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.isVfsEnabled.value = false
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG Try turning on VFS")
                viewModel.enableVfs()

                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
