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
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.*
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.ChatScrollListener
import org.linphone.activities.main.chat.adapters.ChatMessagesListAdapter
import org.linphone.activities.main.chat.viewmodels.*
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.activities.navigateToChatRooms
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
            if (itemCount == 1 && positionStart > 0) {
                adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            goBack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        }

        val localSipUri = arguments?.getString("LocalSipUri")
        val remoteSipUri = arguments?.getString("RemoteSipUri")

        val textToShare = arguments?.getString("TextToShare")
        val filestoShare = arguments?.getStringArrayList("FilesToShare")

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
            (activity as MainActivity).showSnackBar(R.string.error)
            findNavController().navigateUp()
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

        val chatScrollListener: ChatScrollListener = object : ChatScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int) {
                listViewModel.loadMoreData(totalItemsCount)
            }
        }
        binding.chatMessagesList.addOnScrollListener(chatScrollListener)

        chatSendingViewModel.textToSend.observe(viewLifecycleOwner, {
            chatSendingViewModel.onTextToSendChanged(it)
        })

        listViewModel.events.observe(viewLifecycleOwner, { events ->
            adapter.submitList(events)
        })

        listViewModel.messageUpdatedEvent.observe(viewLifecycleOwner, {
            it.consume { position ->
                adapter.notifyItemChanged(position)
            }
        })

        listViewModel.scrollToBottomOnMessageReceivedEvent.observe(viewLifecycleOwner, {
            it.consume {
                scrollToBottom()
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
                Log.i("[Chat Room] Forwarding message, going to chat rooms list")
                navigateToChatRooms()
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
                                    showDialogForUserConsentBeforeExportingFileInThirdPartyApp(path)
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

        binding.setBackClickListener {
            goBack()
        }
        binding.back.visibility = if (resources.getBoolean(R.bool.isTablet)) View.INVISIBLE else View.VISIBLE

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

        binding.setSendMessageClickListener {
            chatSendingViewModel.sendMessage()
            binding.message.text?.clear()
        }

        binding.setStartCallClickListener {
            coreContext.startCall(viewModel.addressToCall)
        }

        if (textToShare?.isNotEmpty() == true) {
            Log.i("[Chat Room] Found text to share")
            chatSendingViewModel.textToSend.value = textToShare
        }
        if (filestoShare?.isNotEmpty() == true) {
            for (path in filestoShare) {
                Log.i("[Chat Room] Found $path file to share")
                chatSendingViewModel.addAttachment(path)
            }
        }

        sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner, {
            it.consume { chatMessage ->
                Log.i("[Chat Room] Found message to transfer")
                showForwardConfirmationDialog(chatMessage)
            }
        })
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<EventLog>()
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
        if (requestCode == 0) {
            var atLeastOneGranted = false
            for (result in grantResults) {
                atLeastOneGranted = atLeastOneGranted || result == PackageManager.PERMISSION_GRANTED
            }
            if (atLeastOneGranted) {
                pickFile()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (this::viewModel.isInitialized) {
            // Prevent notifications for this chat room to be displayed
            val peerAddress = viewModel.chatRoom.peerAddress.asStringUriOnly()
            coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = peerAddress
            coreContext.notificationsManager.cancelChatNotificationIdForSipUri(peerAddress)
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
                for (fileToUploadPath in ImageUtils.getFilesPathFromPickerIntent(
                    data,
                    chatSendingViewModel.temporaryFileUploadPath
                )) {
                    chatSendingViewModel.addAttachment(fileToUploadPath)
                }
            }
        }
    }

    private fun goBack() {
        if (!findNavController().popBackStack(R.id.masterChatRoomsFragment, false)) {
            Log.w("[Chat Room] No MasterChatRoomsFragment found in back stack")
            navigateToChatRooms()
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

                if (viewModel.oneParticipantOneDevice) {
                    coreContext.startCall(viewModel.onlyParticipantOnlyDeviceAddress, true)
                } else {
                    navigateToDevices()
                }

                dialog.dismiss()
            }, okLabel)

            dialog.show()
        } else {
            if (viewModel.oneParticipantOneDevice) {
                coreContext.startCall(viewModel.onlyParticipantOnlyDeviceAddress, true)
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
        viewModel.iconResource = R.drawable.forward_message_dialog_default
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
        // TODO: hide ephemeral menu if not all participants support the feature

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
        val cameraIntents = ArrayList<Intent>()

        // Handles image & video picking
        val galleryIntent = Intent(Intent.ACTION_PICK)
        galleryIntent.type = "*/*"
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        if (PermissionHelper.get().hasCameraPermission()) {
            // Allows to capture directly from the camera
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val tempFileName = System.currentTimeMillis().toString() + ".jpeg"
            val file = FileUtils.getFileStoragePath(tempFileName)
            chatSendingViewModel.temporaryFileUploadPath = file
            val publicUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getString(R.string.file_provider),
                file
            )
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, publicUri)
            captureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cameraIntents.add(captureIntent)
        }

        if (PermissionHelper.get().hasReadExternalStorage()) {
            // Finally allow any kind of file
            val fileIntent = Intent(Intent.ACTION_GET_CONTENT)
            fileIntent.type = "*/*"
            fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            cameraIntents.add(fileIntent)
        }

        val chooserIntent =
            Intent.createChooser(galleryIntent, getString(R.string.chat_message_pick_file_dialog))
        chooserIntent.putExtra(
            Intent.EXTRA_INITIAL_INTENTS,
            cameraIntents.toArray(arrayOf<Parcelable>())
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

    private fun showDialogForUserConsentBeforeExportingFileInThirdPartyApp(path: String) {
        val dialogViewModel = DialogViewModel(
            getString(R.string.chat_message_cant_open_file_in_app_dialog_message),
            getString(R.string.chat_message_cant_open_file_in_app_dialog_title)
        )
        val dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

        dialogViewModel.showDeleteButton({
            dialog.dismiss()
            if (!FileUtils.openFileInThirdPartyApp(requireActivity(), path)) {
                showDialogToSuggestOpeningFileAsText()
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
