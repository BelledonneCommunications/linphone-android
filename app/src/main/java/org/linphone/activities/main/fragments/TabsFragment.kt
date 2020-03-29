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
package org.linphone.activities.main.fragments

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.activities.main.viewmodels.TabsViewModel
import org.linphone.databinding.TabsFragmentBinding

class TabsFragment : Fragment() {
    private lateinit var binding: TabsFragmentBinding
    private lateinit var viewModel: TabsViewModel

    private var dialerSelected: Boolean = false
    private var contactsSelected: Boolean = false
    private var chatSelected: Boolean = false
    private var historySelected: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = TabsFragmentBinding.inflate(inflater, container, false)

        binding.historySelect.visibility = if (historySelected) View.VISIBLE else View.GONE
        binding.contactsSelect.visibility = if (contactsSelected) View.VISIBLE else View.GONE
        binding.dialerSelect.visibility = if (dialerSelected) View.VISIBLE else View.GONE
        binding.chatSelect.visibility = if (chatSelected) View.VISIBLE else View.GONE

        return binding.root
    }

    override fun onInflate(context: Context, attrs: AttributeSet, savedInstanceState: Bundle?) {
        super.onInflate(context, attrs, savedInstanceState)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.TabsFragment)
        historySelected = attributes.getBoolean(R.styleable.TabsFragment_history_selected, false)
        contactsSelected = attributes.getBoolean(R.styleable.TabsFragment_contacts_selected, false)
        dialerSelected = attributes.getBoolean(R.styleable.TabsFragment_dialer_selected, false)
        chatSelected = attributes.getBoolean(R.styleable.TabsFragment_chat_selected, false)

        attributes.recycle()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        viewModel = activity?.run {
            ViewModelProvider(this).get(TabsViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
        binding.viewModel = viewModel

        binding.setHistoryClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_masterCallLogsFragment)
                R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterCallLogsFragment)
                R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_masterCallLogsFragment)
            }
        }

        binding.setContactsClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_masterContactsFragment)
                R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterContactsFragment)
                R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_masterContactsFragment)
            }
        }

        binding.setDialerClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_dialerFragment)
                R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_dialerFragment)
                R.id.masterChatRoomsFragment -> findNavController().navigate(R.id.action_masterChatRoomsFragment_to_dialerFragment)
            }
        }

        binding.setChatClickListener {
            when (findNavController().currentDestination?.id) {
                R.id.masterCallLogsFragment -> findNavController().navigate(R.id.action_masterCallLogsFragment_to_masterChatRoomsFragment)
                R.id.masterContactsFragment -> findNavController().navigate(R.id.action_masterContactsFragment_to_masterChatRoomsFragment)
                R.id.dialerFragment -> findNavController().navigate(R.id.action_dialerFragment_to_masterChatRoomsFragment)
            }
        }
    }
}
