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
package org.linphone.ui.conversations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.core.ChatRoom
import org.linphone.databinding.ChatRoomMenuBinding
import org.linphone.utils.LinphoneUtils

class ConversationMenuDialogFragment(
    private val chatRoom: ChatRoom,
    private val mutedCallback: ((Boolean) -> Unit)? = null
) : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "ConversationMenuDialogFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = ChatRoomMenuBinding.inflate(layoutInflater)

        val id = LinphoneUtils.getChatRoomId(chatRoom)
        view.isMuted = corePreferences.chatRoomMuted(id)
        view.isRead = chatRoom.unreadMessagesCount == 0 // FIXME: danger?

        view.setMarkAsReadClickListener {
            coreContext.postOnCoreThread { core ->
                chatRoom.markAsRead()
            }
            dismiss()
        }

        view.setCallClickListener {
            // TODO
            dismiss()
        }

        view.setMuteClickListener {
            coreContext.postOnCoreThread { core ->
                corePreferences.muteChatRoom(id, true)
                mutedCallback?.invoke(true)
            }
            dismiss()
        }

        view.setUnMuteClickListener {
            coreContext.postOnCoreThread { core ->
                corePreferences.muteChatRoom(id, false)
                mutedCallback?.invoke(false)
            }
            dismiss()
        }

        view.setDeleteClickListener {
            coreContext.postOnCoreThread { core ->
                core.deleteChatRoom(chatRoom)
            }
            dismiss()
        }

        return view.root
    }
}
