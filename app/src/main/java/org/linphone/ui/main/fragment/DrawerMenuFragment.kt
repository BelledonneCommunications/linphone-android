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
package org.linphone.ui.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountPopupMenuBinding
import org.linphone.databinding.DrawerMenuBinding
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.settings.fragment.AccountProfileFragmentDirections
import org.linphone.ui.main.viewmodel.DrawerMenuViewModel
import androidx.core.net.toUri

@UiThread
class DrawerMenuFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Drawer Menu Fragment]"
    }

    private lateinit var binding: DrawerMenuBinding

    private lateinit var viewModel: DrawerMenuViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DrawerMenuBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = requireActivity().run {
            ViewModelProvider(this)[DrawerMenuViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setSettingsClickedListener {
            val navController = (requireActivity() as MainActivity).findNavController()
            navController.navigate(R.id.action_global_settingsFragment)
            (requireActivity() as MainActivity).closeDrawerMenu()
        }

        binding.setRecordingsClickListener {
            val navController = (requireActivity() as MainActivity).findNavController()
            navController.navigate(R.id.action_global_recordingsListFragment)
            (requireActivity() as MainActivity).closeDrawerMenu()
        }

        binding.setHelpClickedListener {
            val navController = (requireActivity() as MainActivity).findNavController()
            navController.navigate(R.id.action_global_helpFragment)
            (requireActivity() as MainActivity).closeDrawerMenu()
        }

        binding.setQuitClickedListener {
            coreContext.stopKeepAliveService()

            coreContext.postOnCoreThread {
                Log.i("$TAG Stopping Core Context")
                coreContext.quitSafely()
            }

            Log.i("$TAG Quitting app")
            requireActivity().finishAndRemoveTask()
        }

        viewModel.startAssistantEvent.observe(viewLifecycleOwner) {
            it.consume {
                startActivity(Intent(requireActivity(), AssistantActivity::class.java))
                (requireActivity() as MainActivity).closeDrawerMenu()
            }
        }

        viewModel.closeDrawerEvent.observe(viewLifecycleOwner) {
            it.consume {
                (requireActivity() as MainActivity).closeDrawerMenu()
            }
        }

        viewModel.showAccountPopupMenuEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                showAccountPopupMenu(pair.first, pair.second)
            }
        }

        viewModel.defaultAccountChangedEvent.observe(viewLifecycleOwner) {
            it.consume { identity ->
                Log.w(
                    "$TAG Default account has changed, now is [$identity], closing side menu in 500ms"
                )

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        delay(500)
                        withContext(Dispatchers.Main) {
                            (requireActivity() as MainActivity).closeDrawerMenu()
                        }
                    }
                }
            }
        }

        viewModel.openLinkInBrowserEvent.observe(viewLifecycleOwner) {
            it.consume { link ->
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
                    startActivity(browserIntent)
                } catch (ise: IllegalStateException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$link], IllegalStateException: $ise"
                    )
                }
            }
        }

        sharedViewModel.refreshDrawerMenuAccountsListEvent.observe(viewLifecycleOwner) {
            it.consume { recreate ->
                if (recreate) {
                    viewModel.updateAccountsList()
                } else {
                    viewModel.refreshAccountsNotificationsCount()
                }
            }
        }
    }

    private fun showAccountPopupMenu(view: View, account: Account) {
        val popupView: AccountPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.account_popup_menu,
            null,
            false
        )

        val popupWindow = PopupWindow(
            popupView.root,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.setManageProfileClickListener {
            val navController = (requireActivity() as MainActivity).findNavController()
            val identity = account.params.identityAddress?.asStringUriOnly().orEmpty()
            val action = AccountProfileFragmentDirections.actionGlobalAccountProfileFragment(
                identity
            )
            Log.i("$TAG Going to account [$identity] profile")
            navController.navigate(action)
            popupWindow.dismiss()
            (requireActivity() as MainActivity).closeDrawerMenu()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }
}
