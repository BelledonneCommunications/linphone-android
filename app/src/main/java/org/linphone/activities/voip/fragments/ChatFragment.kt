/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.voip.fragments

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.View
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.chat.adapters.ChatMessagesListAdapter
import org.linphone.activities.main.chat.viewmodels.*
import org.linphone.activities.main.viewmodels.ListTopBarViewModel
import org.linphone.core.ChatRoom
import org.linphone.core.Factory
import org.linphone.core.tools.Log
import org.linphone.databinding.VoipChatFragmentBinding
import org.linphone.utils.FileUtils
import org.linphone.utils.PermissionHelper

class ChatFragment : GenericFragment<VoipChatFragmentBinding>() {
    private lateinit var adapter: ChatMessagesListAdapter
    private lateinit var viewModel: ChatRoomViewModel
    private lateinit var listViewModel: ChatMessagesListViewModel
    private lateinit var chatSendingViewModel: ChatMessageSendingViewModel

    override fun getLayoutId(): Int = R.layout.voip_chat_fragment

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart == adapter.itemCount - itemCount) {
                adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
                scrollToBottom()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.setCancelClickListener {
            goBack()
        }

        binding.setAttachFileClickListener {
            if (PermissionHelper.get().hasReadExternalStorage() && PermissionHelper.get().hasCameraPermission()) {
                pickFile()
            } else {
                Log.i("[Chat Room] Asking for READ_EXTERNAL_STORAGE and CAMERA permissions")
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.CAMERA
                    ),
                    0
                )
            }
        }

        val localSipUri = arguments?.getString("LocalSipUri")
        val remoteSipUri = arguments?.getString("RemoteSipUri")
        var chatRoom: ChatRoom? = null
        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments")

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            chatRoom = coreContext.core.searchChatRoom(null, localAddress, remoteSipAddress, arrayOfNulls(0))
        }
        chatRoom ?: return

        viewModel = ViewModelProvider(
            this,
            ChatRoomViewModelFactory(chatRoom)
        )[ChatRoomViewModel::class.java]
        binding.viewModel = viewModel

        listViewModel = ViewModelProvider(
            this,
            ChatMessagesListViewModelFactory(chatRoom)
        )[ChatMessagesListViewModel::class.java]

        chatSendingViewModel = ViewModelProvider(
            this,
            ChatMessageSendingViewModelFactory(chatRoom)
        )[ChatMessageSendingViewModel::class.java]
        binding.chatSendingViewModel = chatSendingViewModel

        val listSelectionViewModel = ViewModelProvider(this).get(ListTopBarViewModel::class.java)
        adapter = ChatMessagesListAdapter(listSelectionViewModel, this)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        binding.chatMessagesList.adapter = adapter
        adapter.registerAdapterDataObserver(observer)

        // Disable context menu on each message
        adapter.disableContextMenu()

        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        binding.chatMessagesList.layoutManager = layoutManager

        listViewModel.events.observe(
            viewLifecycleOwner,
            { events ->
                adapter.submitList(events)
            }
        )

        chatSendingViewModel.textToSend.observe(
            viewLifecycleOwner,
            {
                chatSendingViewModel.onTextToSendChanged(it)
            }
        )
    }

    override fun onResume() {
        super.onResume()

        if (this::viewModel.isInitialized) {
            // Prevent notifications for this chat room to be displayed
            val peerAddress = viewModel.chatRoom.peerAddress.asStringUriOnly()
            coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = peerAddress
            viewModel.chatRoom.markAsRead()
        } else {
            Log.e("[Chat] Fragment resuming but viewModel lateinit property isn't initialized!")
        }
    }

    override fun onPause() {
        coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null

        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                for (
                    fileToUploadPath in FileUtils.getFilesPathFromPickerIntent(
                        data,
                        chatSendingViewModel.temporaryFileUploadPath
                    )
                ) {
                    chatSendingViewModel.addAttachment(fileToUploadPath)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        var atLeastOneGranted = false
        for (result in grantResults) {
            atLeastOneGranted = atLeastOneGranted || result == PackageManager.PERMISSION_GRANTED
        }

        when (requestCode) {
            0 -> {
                if (atLeastOneGranted) {
                    pickFile()
                }
            }
        }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            binding.chatMessagesList.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun pickFile() {
        val intentsList = ArrayList<Intent>()

        val pickerIntent = Intent(Intent.ACTION_GET_CONTENT)
        pickerIntent.type = "*/*"
        pickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        if (PermissionHelper.get().hasCameraPermission()) {
            // Allows to capture directly from the camera
            val capturePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val tempFileName = System.currentTimeMillis().toString() + ".jpeg"
            val file = FileUtils.getFileStoragePath(tempFileName)
            chatSendingViewModel.temporaryFileUploadPath = file
            val publicUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getString(R.string.file_provider),
                file
            )
            capturePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, publicUri)
            capturePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            capturePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intentsList.add(capturePictureIntent)

            val captureVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            intentsList.add(captureVideoIntent)
        }

        val chooserIntent =
            Intent.createChooser(pickerIntent, getString(R.string.chat_message_pick_file_dialog))
        chooserIntent.putExtra(
            Intent.EXTRA_INITIAL_INTENTS,
            intentsList.toArray(arrayOf<Parcelable>())
        )

        startActivityForResult(chooserIntent, 0)
    }
}
