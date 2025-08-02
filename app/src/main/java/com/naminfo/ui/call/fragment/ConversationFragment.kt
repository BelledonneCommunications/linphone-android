/*
 * Copyright (c) 2010-2024 Belledonne Communications SARL.
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
package com.naminfo.ui.call.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.ui.fileviewer.FileViewerActivity
import com.naminfo.ui.fileviewer.MediaViewerActivity
import com.naminfo.ui.main.chat.fragment.ConversationFragment

class ConversationFragment : ConversationFragment() {
    companion object {
        private const val TAG = "[In-call Conversation Fragment]"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.i("$TAG Creating an in-call ConversationFragment")
        sendMessageViewModel.isCallConversation.value = true
        viewModel.isCallConversation.value = true

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        sharedViewModel.displayFileEvent.observe(viewLifecycleOwner) {
            it.consume { bundle ->
                if (findNavController().currentDestination?.id == R.id.inCallConversationFragment) {
                    val path = bundle.getString("path", "")
                    val isMedia = bundle.getBoolean("isMedia", false)
                    if (path.isEmpty()) {
                        Log.e("$TAG Can't navigate to file viewer for empty path!")
                        return@consume
                    }

                    Log.i(
                        "$TAG Navigating to [${if (isMedia) "media" else "file"}] viewer fragment with path [$path]"
                    )
                    if (isMedia) {
                        val intent = Intent(requireActivity(), MediaViewerActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    } else {
                        val intent = Intent(requireActivity(), FileViewerActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
            }
        }
    }
}
