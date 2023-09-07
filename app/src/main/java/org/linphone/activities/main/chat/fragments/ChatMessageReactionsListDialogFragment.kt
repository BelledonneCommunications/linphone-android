/*
 * Copyright (c) 2010-2022 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import org.linphone.R
import org.linphone.activities.main.chat.data.ChatMessageReactionsListData
import org.linphone.core.ChatMessage
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatMessageReactionsListDialogBinding
import org.linphone.utils.AppUtils

class ChatMessageReactionsListDialogFragment() : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "ChatMessageReactionsListDialogFragment"
    }

    private lateinit var binding: ChatMessageReactionsListDialogBinding

    private lateinit var data: ChatMessageReactionsListData

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatMessageReactionsListDialogBinding.inflate(layoutInflater)
        binding.lifecycleOwner = viewLifecycleOwner
        if (::data.isInitialized) {
            binding.data = data

            data.reactions.observe(viewLifecycleOwner) {
                binding.tabs.removeAllTabs()
                binding.tabs.addTab(
                    binding.tabs.newTab().setText(
                        AppUtils.getStringWithPlural(
                            R.plurals.chat_message_reactions_count,
                            it.orEmpty().size
                        )
                    ).setId(0)
                )

                var index = 1
                data.reactionsMap.forEach { (key, value) ->
                    binding.tabs.addTab(
                        binding.tabs.newTab().setText("$key $value").setId(index).setTag(key)
                    )
                    index += 1
                }
            }
        } else {
            Log.w("$TAG View created but no message has been set, dismissing...")
            dismiss()
        }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (::data.isInitialized) {
                    if (tab.id == 0) {
                        data.updateFilteredReactions("")
                    } else {
                        data.updateFilteredReactions(tab.tag.toString())
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        return binding.root
    }

    fun setMessage(chatMessage: ChatMessage) {
        data = ChatMessageReactionsListData(chatMessage)
    }

    override fun onDestroy() {
        if (::data.isInitialized) {
            data.onDestroy()
        }
        super.onDestroy()
    }
}
