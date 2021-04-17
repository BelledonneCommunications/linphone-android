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
package org.linphone.activities.main.contact.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import org.linphone.R
import org.linphone.activities.main.contact.adapters.SyncAccountAdapter
import org.linphone.core.tools.Log
import org.linphone.databinding.ContactSyncAccountPickerFragmentBinding

class SyncAccountPickerFragment(private val listener: SyncAccountPickedListener) : DialogFragment() {
    private var _binding: ContactSyncAccountPickerFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SyncAccountAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.assistant_country_dialog_style)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ContactSyncAccountPickerFragmentBinding.inflate(inflater, container, false)

        adapter = SyncAccountAdapter()
        binding.accountsList.adapter = adapter

        binding.accountsList.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < adapter.count) {
                val account = adapter.getItem(position)
                Log.i("[Sync Account Picker] Picked ${account.first} / ${account.second}")
                listener.onSyncAccountClicked(account.first, account.second)
            }
            dismiss()
        }

        binding.setLocalSyncAccountClickListener {
            Log.i("[Sync Account Picker] Picked local account")
            listener.onSyncAccountClicked(null, null)
            dismiss()
        }

        return binding.root
    }

    interface SyncAccountPickedListener {
        fun onSyncAccountClicked(name: String?, type: String?)
    }
}
