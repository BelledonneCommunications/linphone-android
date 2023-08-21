/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.settings.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.main.settings.viewmodels.AccountSettingsViewModel
import org.linphone.activities.main.settings.viewmodels.AccountSettingsViewModelFactory
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.navigateToPhoneLinking
import org.linphone.core.tools.Log
import org.linphone.databinding.SettingsAccountFragmentBinding
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event

class AccountSettingsFragment : GenericSettingFragment<SettingsAccountFragmentBinding>() {
    private lateinit var viewModel: AccountSettingsViewModel

    override fun getLayoutId(): Int = R.layout.settings_account_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.sharedMainViewModel = sharedViewModel

        val identity = arguments?.getString("Identity")
        if (identity == null) {
            Log.e("[Account Settings] Identity is null, aborting!")
            goBack()
            return
        }

        try {
            viewModel = ViewModelProvider(this, AccountSettingsViewModelFactory(identity))[AccountSettingsViewModel::class.java]
        } catch (nsee: NoSuchElementException) {
            Log.e("[Account Settings] Failed to find Account object, aborting!")
            goBack()
            return
        }
        binding.viewModel = viewModel

        viewModel.linkPhoneNumberEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val authInfo = viewModel.account.findAuthInfo()
                if (authInfo == null) {
                    Log.e(
                        "[Account Settings] Failed to find auth info for account ${viewModel.account}"
                    )
                } else {
                    val args = Bundle()
                    args.putString("Username", authInfo.username)
                    args.putString("Password", authInfo.password)
                    args.putString("HA1", authInfo.ha1)
                    navigateToPhoneLinking(args)
                }
            }
        }

        viewModel.accountRemovedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                sharedViewModel.accountRemoved.value = true
                goBack()
            }
        }

        viewModel.accountDefaultEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                sharedViewModel.defaultAccountChanged.value = true
            }
        }

        viewModel.deleteAccountRequiredEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                val defaultDomainAccount = viewModel.account.params.identityAddress?.domain == corePreferences.defaultDomain
                Log.i(
                    "[Account Settings] User clicked on delete account, showing confirmation dialog for ${if (defaultDomainAccount) "default domain account" else "third party account"}"
                )
                val dialogViewModel = if (defaultDomainAccount) {
                    DialogViewModel(
                        getString(
                            R.string.account_setting_delete_sip_linphone_org_confirmation_dialog
                        ),
                        getString(R.string.account_setting_delete_dialog_title)
                    )
                } else {
                    DialogViewModel(
                        getString(R.string.account_setting_delete_generic_confirmation_dialog),
                        getString(R.string.account_setting_delete_dialog_title)
                    )
                }
                val dialog: Dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

                dialogViewModel.showIcon = true
                dialogViewModel.iconResource = R.drawable.dialog_delete_icon
                dialogViewModel.showSubscribeLinphoneOrgLink = defaultDomainAccount

                dialogViewModel.showCancelButton {
                    Log.i("[Account Settings] User cancelled account removal")
                    dialog.dismiss()
                }

                dialogViewModel.showDeleteButton(
                    {
                        viewModel.startDeleteAccount()
                        dialog.dismiss()
                    },
                    getString(R.string.dialog_delete)
                )

                dialog.show()
            }
        }

        view.doOnPreDraw {
            // Notifies fragment is ready to be drawn
            sharedViewModel.accountSettingsFragmentOpenedEvent.value = Event(true)
        }
    }
}
