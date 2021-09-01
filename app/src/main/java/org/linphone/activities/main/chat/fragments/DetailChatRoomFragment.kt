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
package org.linphone.activities.main.chat.fragments

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.lang.IllegalArgumentException
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.ChatScrollListener
import org.linphone.activities.main.chat.adapters.ChatMessagesListAdapter
import org.linphone.activities.main.chat.data.ChatMessageData
import org.linphone.activities.main.chat.data.EventLogData
import org.linphone.activities.main.chat.viewmodels.*
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToContacts
import org.linphone.activities.navigateToImageFileViewer
import org.linphone.activities.navigateToImdn
import org.linphone.compatibility.Compatibility
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomDetailFragmentBinding
import org.linphone.utils.*
import org.linphone.utils.Event

class DetailChatRoomFragment : MasterFragment<ChatRoomDetailFragmentBinding, ChatMessagesListAdapter>() {
    private lateinit var viewModel: ChatRoomViewModel
    private lateinit var chatSendingViewModel: ChatMessageSendingViewModel
    private lateinit var listViewModel: ChatMessagesListViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            adapter.notifyItemChanged(positionStart - 1) // For grouping purposes

            if (positionStart == adapter.itemCount - itemCount) {
                scrollToBottom()
            }
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.chat_room_detail_fragment
    }

    override fun onDestroyView() {
        if (_adapter != null) {
            adapter.unregisterAdapterDataObserver(observer)
        }
        binding.chatMessagesList.adapter = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }
        binding.sharedMainViewModel = sharedViewModel

        val localSipUri = arguments?.getString("LocalSipUri")
        val remoteSipUri = arguments?.getString("RemoteSipUri")

        val textToShare = arguments?.getString("TextToShare")
        val filesToShare = arguments?.getStringArrayList("FilesToShare")

        arguments?.clear()
        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat Room] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments")

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            sharedViewModel.selectedChatRoom.value = coreContext.core.searchChatRoom(
                null, localAddress, remoteSipAddress, arrayOfNulls(
                    0
                )
            )
        }

        val chatRoom = sharedViewModel.selectedChatRoom.value
        if (chatRoom == null) {
            Log.e("[Chat Room] Chat room is null, aborting!")
            // (activity as MainActivity).showSnackBar(R.string.error)
            goBack()
            return
        }

        Compatibility.setLocusIdInContentCaptureSession(binding.root, chatRoom)

        isSecure = chatRoom.currentParams.encryptionEnabled()

        viewModel = ViewModelProvider(
            this,
            ChatRoomViewModelFactory(chatRoom)
        )[ChatRoomViewModel::class.java]
        binding.viewModel = viewModel

        chatSendingViewModel = ViewModelProvider(
            this,
            ChatMessageSendingViewModelFactory(chatRoom)
        )[ChatMessageSendingViewModel::class.java]
        binding.chatSendingViewModel = chatSendingViewModel

        listViewModel = ViewModelProvider(
            this,
            ChatMessagesListViewModelFactory(chatRoom)
        )[ChatMessagesListViewModel::class.java]

        _adapter = ChatMessagesListAdapter(listSelectionViewModel, viewLifecycleOwner)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        binding.chatMessagesList.adapter = adapter
        adapter.registerAdapterDataObserver(observer)

        val layoutManager = LinearLayoutManager(activity)
        layoutManager.stackFromEnd = true
        binding.chatMessagesList.layoutManager = layoutManager

        // Swipe action
        /*val swipeConfiguration = RecyclerViewSwipeConfiguration()
        swipeConfiguration.leftToRightAction = RecyclerViewSwipeConfiguration.Action(icon = R.drawable.menu_reply_default)
        val swipeListener = object : RecyclerViewSwipeListener {
            override fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder) {
                adapter.notifyItemChanged(viewHolder.adapterPosition)

                val chatMessageEventLog = adapter.currentList[viewHolder.adapterPosition]
                val chatMessage = chatMessageEventLog.chatMessage
                if (chatMessage != null) {
                    chatSendingViewModel.pendingChatMessageToReplyTo.value?.destroy()
                    chatSendingViewModel.pendingChatMessageToReplyTo.value =
                        ChatMessageData(chatMessage)
                    chatSendingViewModel.isPendingAnswer.value = true
                }
            }

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {}
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.RIGHT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.chatMessagesList)*/

        val chatScrollListener: ChatScrollListener = object : ChatScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int) {
                listViewModel.loadMoreData(totalItemsCount)
            }
        }
        binding.chatMessagesList.addOnScrollListener(chatScrollListener)

        chatSendingViewModel.textToSend.observe(viewLifecycleOwner, {
            chatSendingViewModel.onTextToSendChanged(it)
        })

        chatSendingViewModel.requestRecordAudioPermissionEvent.observe(viewLifecycleOwner, {
            it.consume {
                Log.i("[Chat Room] Asking for RECORD_AUDIO permission")
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 2)
            }
        })

        listViewModel.events.observe(viewLifecycleOwner, { events ->
            adapter.submitList(events)
        })

        listViewModel.messageUpdatedEvent.observe(viewLifecycleOwner, {
            it.consume { position ->
                adapter.notifyItemChanged(position)
            }
        })

        listViewModel.requestWriteExternalStoragePermissionEvent.observe(viewLifecycleOwner, {
            it.consume {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        })

        adapter.deleteMessageEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                listViewModel.deleteMessage(chatMessage)
            }
        })

        adapter.resendMessageEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                listViewModel.resendMessage(chatMessage)
            }
        })

        adapter.forwardMessageEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                // Remove observer before setting the message to forward
                // as we don't want to forward it in this chat room
                sharedViewModel.messageToForwardEvent.removeObservers(viewLifecycleOwner)
                sharedViewModel.messageToForwardEvent.value = Event(chatMessage)
                sharedViewModel.isPendingMessageForward.value = true

                if (sharedViewModel.canSlidingPaneBeClosed.value == true) {
                    Log.i("[Chat Room] Forwarding message, going to chat rooms list")
                    sharedViewModel.closeSlidingPaneEvent.value = Event(true)
                }
            }
        })

        adapter.replyMessageEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                chatSendingViewModel.pendingChatMessageToReplyTo.value?.destroy()
                chatSendingViewModel.pendingChatMessageToReplyTo.value = ChatMessageData(chatMessage)
                chatSendingViewModel.isPendingAnswer.value = true
            }
        })

        adapter.showImdnForMessageEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                val args = Bundle()
                args.putString("MessageId", chatMessage.messageId)
                navigateToImdn(args)
            }
        })

        adapter.addSipUriToContactEvent.observe(viewLifecycleOwner, {
            it.consume { sipUri ->
                Log.i("[Chat Room] Going to contacts list with SIP URI to add: $sipUri")
                navigateToContacts(sipUri)
            }
        })

        adapter.openContentEvent.observe(viewLifecycleOwner, {
            it.consume { content ->
                val path = content.filePath.orEmpty()

                if (!File(path).exists()) {
                    (requireActivity() as MainActivity).showSnackBar(R.string.chat_room_file_not_found)
                } else {
                    Log.i("[Chat Message] Opening file: $path")
                    sharedViewModel.contentToOpen.value = content

                    if (corePreferences.useInAppFileViewerForNonEncryptedFiles || content.isFileEncrypted) {
                        val preventScreenshots =
                            viewModel.chatRoom.currentParams.encryptionEnabled()
                        when {
                            FileUtils.isExtensionImage(path) -> navigateToImageFileViewer(
                                preventScreenshots
                            )
                            FileUtils.isExtensionVideo(path) -> navigateToVideoFileViewer(
                                preventScreenshots
                            )
                            FileUtils.isExtensionAudio(path) -> navigateToAudioFileViewer(
                                preventScreenshots
                            )
                            FileUtils.isExtensionPdf(path) -> navigateToPdfFileViewer(
                                preventScreenshots
                            )
                            FileUtils.isPlainTextFile(path) -> navigateToTextFileViewer(
                                preventScreenshots
                            )
                            else -> {
                                if (content.isFileEncrypted) {
                                    Log.w("[Chat Message] File is encrypted and can't be opened in one of our viewers...")
                                    showDialogForUserConsentBeforeExportingFileInThirdPartyApp(content)
                                } else if (!FileUtils.openFileInThirdPartyApp(requireActivity(), path)) {
                                    showDialogToSuggestOpeningFileAsText()
                                }
                            }
                        }
                    } else {
                        if (!FileUtils.openFileInThirdPartyApp(requireActivity(), path)) {
                            showDialogToSuggestOpeningFileAsText()
                        }
                    }
                }
            }
        })

        adapter.scrollToChatMessageEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                val events = listViewModel.events.value.orEmpty()
                val eventLog = events.find { eventLog ->
                    if (eventLog.eventLog.type == EventLog.Type.ConferenceChatMessage) {
                        (eventLog.data as ChatMessageData).chatMessage.messageId == chatMessage.messageId
                    } else false
                }
                val index = events.indexOf(eventLog)
                try {
                    binding.chatMessagesList.smoothScrollToPosition(index)
                } catch (iae: IllegalArgumentException) {
                    Log.e("[Chat Room] Can't scroll to position $index")
                }
            }
        })

        binding.setBackClickListener {
            goBack()
        }

        binding.setTitleClickListener {
            binding.sipUri.visibility = if (!viewModel.oneToOneChatRoom ||
                binding.sipUri.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.setMenuClickListener {
            showPopupMenu(chatRoom)
        }

        binding.setEditClickListener {
            enterEditionMode()
        }

        binding.setSecurityIconClickListener {
            showParticipantsDevices()
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
                    ), 0
                )
            }
        }

        binding.setVoiceRecordingTouchListener { _, event ->
            if (corePreferences.holdToRecordVoiceMessage) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.i("[Chat Room] Start recording voice message as long as recording button is held")
                        chatSendingViewModel.startVoiceRecording()
                    }
                    MotionEvent.ACTION_UP -> {
                        val voiceRecordingDuration = chatSendingViewModel.voiceRecordingDuration.value ?: 0
                        if (voiceRecordingDuration < 1000) {
                            Log.w("[Chat Room] Voice recording button has been held for less than a second, considering miss click")
                            chatSendingViewModel.cancelVoiceRecording()
                            (requireActivity() as MainActivity).showSnackBar(R.string.chat_message_voice_recording_hold_to_record)
                        } else {
                            Log.i("[Chat Room] Voice recording button has been released, stop recording")
                            chatSendingViewModel.stopVoiceRecording()
                        }
                    }
                }
                true
            }
            false
        }

        if (textToShare?.isNotEmpty() == true) {
            Log.i("[Chat Room] Found text to share")
            chatSendingViewModel.textToSend.value = textToShare
        }
        if (filesToShare?.isNotEmpty() == true) {
            for (path in filesToShare) {
                Log.i("[Chat Room] Found $path file to share")
                chatSendingViewModel.addAttachment(path)
            }
        }

        sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                Log.i("[Chat Room] Found message to transfer")
                showForwardConfirmationDialog(chatMessage)
                sharedViewModel.isPendingMessageForward.value = false
            }
        })
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<EventLogData>()
        for (index in indexesOfItemToDelete) {
            val eventLog = adapter.currentList[index]
            list.add(eventLog)
        }
        listViewModel.deleteEventLogs(list)
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
            2 -> {
                if (atLeastOneGranted) {
                    chatSendingViewModel.startVoiceRecording()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (this::viewModel.isInitialized) {
            // Prevent notifications for this chat room to be displayed
            val peerAddress = viewModel.chatRoom.peerAddress.asStringUriOnly()
            coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = peerAddress
            viewModel.chatRoom.markAsRead()
        } else {
            Log.e("[Chat Room] Fragment resuming but viewModel lateinit property isn't initialized!")
        }
    }

    override fun onPause() {
        coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null

        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                for (fileToUploadPath in FileUtils.getFilesPathFromPickerIntent(
                    data,
                    chatSendingViewModel.temporaryFileUploadPath
                )) {
                    chatSendingViewModel.addAttachment(fileToUploadPath)
                }
            }
        }
    }

    override fun goBack() {
        if (!findNavController().popBackStack()) {
            if (sharedViewModel.canSlidingPaneBeClosed.value == true) {
                sharedViewModel.closeSlidingPaneEvent.value = Event(true)
            } else {
                navigateToEmptyChatRoom()
            }
        }
    }

    private fun enterEditionMode() {
        listSelectionViewModel.isEditionEnabled.value = true
    }

    private fun showParticipantsDevices() {
        if (corePreferences.limeSecurityPopupEnabled) {
            val dialogViewModel = DialogViewModel(getString(R.string.dialog_lime_security_message))
            dialogViewModel.showDoNotAskAgain = true
            val dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

            dialogViewModel.showCancelButton { doNotAskAgain ->
                if (doNotAskAgain) corePreferences.limeSecurityPopupEnabled = false
                dialog.dismiss()
            }

            val okLabel = if (viewModel.oneParticipantOneDevice) getString(R.string.dialog_call) else getString(
                R.string.dialog_ok
            )
            dialogViewModel.showOkButton({ doNotAskAgain ->
                if (doNotAskAgain) corePreferences.limeSecurityPopupEnabled = false

                val address = viewModel.onlyParticipantOnlyDeviceAddress
                if (viewModel.oneParticipantOneDevice) {
                    if (address != null) {
                        coreContext.startCall(address, true)
                    }
                } else {
                    navigateToDevices()
                }

                dialog.dismiss()
            }, okLabel)

            dialog.show()
        } else {
            val address = viewModel.onlyParticipantOnlyDeviceAddress
            if (viewModel.oneParticipantOneDevice) {
                if (address != null) {
                    coreContext.startCall(address, true)
                }
            } else {
                navigateToDevices()
            }
        }
    }

    private fun showGroupInfo(chatRoom: ChatRoom) {
        sharedViewModel.selectedGroupChatRoom.value = chatRoom
        sharedViewModel.chatRoomParticipants.value = arrayListOf()
        navigateToGroupInfo()
    }

    private fun showEphemeralMessages() {
        navigateToEphemeralInfo()
    }

    private fun showForwardConfirmationDialog(chatMessage: ChatMessage) {
        val viewModel = DialogViewModel(getString(R.string.chat_message_forward_confirmation_dialog))
        viewModel.iconResource = R.drawable.forward_message_default
        viewModel.showIcon = true
        val dialog: Dialog = DialogUtils.getDialog(requireContext(), viewModel)

        viewModel.showCancelButton {
            Log.i("[Chat Room] Transfer cancelled")
            dialog.dismiss()
        }

        viewModel.showOkButton({
            Log.i("[Chat Room] Transfer confirmed")
            chatSendingViewModel.transferMessage(chatMessage)
            dialog.dismiss()
        }, getString(R.string.chat_message_context_menu_forward))

        dialog.show()
    }

    private fun showPopupMenu(chatRoom: ChatRoom) {
        val builder = MenuBuilder(requireContext())
        val popupMenu = MenuPopupHelper(requireContext(), builder, binding.menu)
        popupMenu.setForceShowIcon(true)

        MenuInflater(requireContext()).inflate(R.menu.chat_room_menu, builder)
        if (viewModel.oneToOneChatRoom) {
            builder.removeItem(R.id.chat_room_group_info)

            // If one participant one device, a click on security badge
            // will directly start a call or show the dialog, so don't show this menu
            if (viewModel.oneParticipantOneDevice) {
                builder.removeItem(R.id.chat_room_participants_devices)
            }
        }

        if (!viewModel.encryptedChatRoom) {
            builder.removeItem(R.id.chat_room_participants_devices)
            builder.removeItem(R.id.chat_room_ephemeral_messages)
        }

        builder.setCallback(object : MenuBuilder.Callback {
            override fun onMenuModeChange(menu: MenuBuilder) {}

            override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.chat_room_group_info -> {
                        showGroupInfo(chatRoom)
                        true
                    }
                    R.id.chat_room_participants_devices -> {
                        showParticipantsDevices()
                        true
                    }
                    R.id.chat_room_ephemeral_messages -> {
                        showEphemeralMessages()
                        true
                    }
                    R.id.chat_room_delete_messages -> {
                        enterEditionMode()
                        true
                    }
                    else -> false
                }
            }
        })

        popupMenu.show()
    }

    private fun scrollToBottom() {
        if (_adapter != null && adapter.itemCount > 0) {
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

    private fun showDialogToSuggestOpeningFileAsText() {
        val dialogViewModel = DialogViewModel(
            getString(R.string.dialog_try_open_file_as_text_body),
            getString(R.string.dialog_try_open_file_as_text_title)
        )
        val dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

        dialogViewModel.showCancelButton {
            dialog.dismiss()
        }

        dialogViewModel.showOkButton({
            dialog.dismiss()
            navigateToTextFileViewer(true)
        })

        dialog.show()
    }

    private fun showDialogForUserConsentBeforeExportingFileInThirdPartyApp(content: Content) {
        val dialogViewModel = DialogViewModel(
            getString(R.string.chat_message_cant_open_file_in_app_dialog_message),
            getString(R.string.chat_message_cant_open_file_in_app_dialog_title)
        )
        val dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

        dialogViewModel.showDeleteButton({
            dialog.dismiss()
            lifecycleScope.launch {
                val plainFilePath = content.plainFilePath
                Log.i("[Cht Room] Making a copy of [$plainFilePath] to the cache directory before exporting it")
                val cacheCopyPath = FileUtils.copyFileToCache(plainFilePath)
                if (cacheCopyPath != null) {
                    Log.i("[Cht Room] Cache copy has been made: $cacheCopyPath")
                    if (!FileUtils.openFileInThirdPartyApp(requireActivity(), cacheCopyPath)) {
                        showDialogToSuggestOpeningFileAsText()
                    }
                }
            }
        }, getString(R.string.chat_message_cant_open_file_in_app_dialog_export_button))

        dialogViewModel.showOkButton({
            dialog.dismiss()
            navigateToTextFileViewer(true)
        }, getString(R.string.chat_message_cant_open_file_in_app_dialog_open_as_text_button))

        dialogViewModel.showCancelButton {
            dialog.dismiss()
        }

        dialog.show()
    }
}
