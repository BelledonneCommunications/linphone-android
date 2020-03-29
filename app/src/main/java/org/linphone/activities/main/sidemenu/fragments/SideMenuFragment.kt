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
package org.linphone.activities.main.sidemenu.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.assistant.AssistantActivity
import org.linphone.activities.main.settings.SettingListenerStub
import org.linphone.activities.main.sidemenu.viewmodels.SideMenuViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.SideMenuFragmentBinding
import org.linphone.utils.Event

class SideMenuFragment : Fragment() {
    private lateinit var binding: SideMenuFragmentBinding
    private lateinit var viewModel: SideMenuViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SideMenuFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = ViewModelProvider(this).get(SideMenuViewModel::class.java)
        binding.viewModel = viewModel

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        sharedViewModel.proxyConfigRemoved.observe(viewLifecycleOwner, Observer {
            Log.i("[Side Menu] Proxy config removed, update accounts list")
            viewModel.updateAccountsList()
        })

        viewModel.accountsSettingsListener = object : SettingListenerStub() {
            override fun onAccountClicked(identity: String) {
                val args = Bundle()
                args.putString("Identity", identity)
                Log.i("[Side Menu] Navigation to settings for proxy with identity: $identity")

                sharedViewModel.toggleDrawerEvent.value = Event(true)
                val deepLink = "linphone-android://account-settings/$identity"
                findNavController().navigate(Uri.parse(deepLink))
            }
        }

        binding.setAssistantClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            startActivity(Intent(context, AssistantActivity::class.java))
        }

        binding.setSettingsClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            findNavController().navigate(R.id.action_global_settingsFragment)
        }

        binding.setRecordingsClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            findNavController().navigate(R.id.action_global_recordingsFragment)
        }

        binding.setAboutClickListener {
            sharedViewModel.toggleDrawerEvent.value = Event(true)
            findNavController().navigate(R.id.action_global_aboutFragment)
        }

        binding.setQuitClickListener {
            val intent = Intent()
            intent.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            try {
                startActivity(intent)
            } catch (ise: IllegalStateException) {
                Log.e("[Side Menu] Can't start home activity: ", ise)
            }
            viewModel.quit()
        }
    }
}
