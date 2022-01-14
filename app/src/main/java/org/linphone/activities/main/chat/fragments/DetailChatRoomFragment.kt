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
import android.view.*
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.lang.IllegalArgumentException
import kotlinx.coroutines.*
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
import org.linphone.activities.main.chat.views.RichEditTextSendListener
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
import org.linphone.databinding.ChatRoomMenuBindingImpl
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

            if (positionStart == 0 && adapter.itemCount == itemCount) {
                // First time we fill the list with messages
                Log.i("[Chat Room] History first $itemCount messages loaded")
            } else {
                // Scroll to newly added messages automatically
                if (positionStart == adapter.itemCount - itemCount) {
                    // But only if user hasn't initiated a scroll up in the messages history
                    if (viewModel.isUserScrollingUp.value == false) {
                        scrollToFirstUnreadMessageOrBottom(false)
                    } else {
                        Log.w("[Chat Room] User has scrolled up manually in the messages history, don't scroll to the newly added message at the bottom & don't mark the chat room as read")
                    }
                }
            }
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.chat_room_detail_fragment
    }

    override fun onDestroyView() {
        if (_adapter != null) {
            try {
                adapter.unregisterAdapterDataObserver(observer)
            } catch (ise: IllegalStateException) {}
        }
        binding.chatMessagesList.adapter = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::sharedViewModel.isInitialized) {
            val chatRoom = sharedViewModel.selectedChatRoom.value
            if (chatRoom != null) {
                outState.putString("LocalSipUri", chatRoom.localAddress.asStringUriOnly())
                outState.putString("RemoteSipUri", chatRoom.peerAddress.asStringUriOnly())
                Log.i("[Chat Room] Saving current chat room local & remote addresses in save instance state")
            }
        } else {
            Log.w("[Chat Room] Can't save instance state, sharedViewModel hasn't been initialized yet")
        }
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        sharedViewModel = requireActivity().run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }
        binding.sharedMainViewModel = sharedViewModel

        useMaterialSharedAxisXForwardAnimation = sharedViewModel.isSlidingPaneSlideable.value == false

        val localSipUri = arguments?.getString("LocalSipUri") ?: savedInstanceState?.getString("LocalSipUri")
        val remoteSipUri = arguments?.getString("RemoteSipUri") ?: savedInstanceState?.getString("RemoteSipUri")

        val textToShare = arguments?.getString("TextToShare")
        val filesToShare = arguments?.getStringArrayList("FilesToShare")

        if (remoteSipUri != null && arguments?.getString("RemoteSipUri") == null) {
            Log.w("[Chat Room] Chat room will be restored from saved instance state")
        }
        arguments?.clear()
        if (localSipUri != null && remoteSipUri != null) {
            Log.i("[Chat Room] Found local [$localSipUri] & remote [$remoteSipUri] addresses in arguments or saved instance state")

            val localAddress = Factory.instance().createAddress(localSipUri)
            val remoteSipAddress = Factory.instance().createAddress(remoteSipUri)
            sharedViewModel.selectedChatRoom.value = coreContext.core.searchChatRoom(
                null, localAddress, remoteSipAddress,
                arrayOfNulls(
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

        view.doOnPreDraw {
            // Notifies fragment is ready to be drawn
            sharedViewModel.chatRoomFragmentOpenedEvent.value = Event(true)
        }

        Compatibility.setLocusIdInContentCaptureSession(binding.root, chatRoom)

        isSecure = chatRoom.currentParams.isEncryptionEnabled

        val chatRoomsListViewModel: ChatRoomsListViewModel = requireActivity().run {
            ViewModelProvider(this)[ChatRoomsListViewModel::class.java]
        }
        val chatRoomViewModel = chatRoomsListViewModel.chatRooms.value.orEmpty().find {
            it.chatRoom == chatRoom
        }
        if (chatRoomViewModel == null) {
            Log.w("[Chat Room] Couldn't find existing view model, will create a new one!")
        }
        viewModel = chatRoomViewModel ?: ViewModelProvider(
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
        adapter.registerAdapterDataObserver(observer)
        binding.chatMessagesList.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
        layoutManager.stackFromEnd = true
        binding.chatMessagesList.layoutManager = layoutManager

        // Displays unread messages header
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.chatMessagesList.addItemDecoration(headerItemDecoration)

        // Wait for items to be displayed before scrolling for the first time
        binding.chatMessagesList
            .viewTreeObserver
            .addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (isBindingAvailable()) {
                            binding.chatMessagesList
                                .viewTreeObserver
                                .removeOnGlobalLayoutListener(this)
                            Log.i("[Chat Room] Messages have been displayed, scrolling to first unread message if any")
                            scrollToFirstUnreadMessageOrBottom(false)
                        } else {
                            Log.e("[Chat Room] Binding not available in onGlobalLayout callback!")
                        }
                    }
                }
            )

        // Swipe action
        val swipeConfiguration = RecyclerViewSwipeConfiguration()
        // Reply action can only be done on a ChatMessageEventLog
        swipeConfiguration.leftToRightAction = RecyclerViewSwipeConfiguration.Action(
            text = requireContext().getString(R.string.chat_message_context_menu_reply),
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.light_grey_color),
            preventFor = ChatMessagesListAdapter.EventViewHolder::class.java
        )
        // Delete action can be done on any EventLog
        swipeConfiguration.rightToLeftAction = RecyclerViewSwipeConfiguration.Action(
            text = requireContext().getString(R.string.chat_message_context_menu_delete),
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.red_color)
        )
        val swipeListener = object : RecyclerViewSwipeListener {
            override fun onLeftToRightSwipe(viewHolder: RecyclerView.ViewHolder) {
                adapter.notifyItemChanged(viewHolder.bindingAdapterPosition)

                val chatMessageEventLog = adapter.currentList[viewHolder.bindingAdapterPosition]
                val chatMessage = chatMessageEventLog.eventLog.chatMessage
                if (chatMessage != null) {
                    chatSendingViewModel.pendingChatMessageToReplyTo.value?.destroy()
                    chatSendingViewModel.pendingChatMessageToReplyTo.value =
                        ChatMessageData(chatMessage)
                    chatSendingViewModel.isPendingAnswer.value = true
                }
            }

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val position = viewHolder.bindingAdapterPosition
                // adapter.notifyItemRemoved(viewHolder.bindingAdapterPosition)

                val eventLog = adapter.currentList[position]
                addDeleteMessageTaskToQueue(eventLog, position)
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.chatMessagesList)

        val chatScrollListener = object : ChatScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int) {
                Log.i("[Chat Room] User has scrolled up far enough, load more items from history (currently there are $totalItemsCount messages displayed)")
                listViewModel.loadMoreData(totalItemsCount)
            }

            override fun onScrolledUp() {
                viewModel.isUserScrollingUp.value = true
            }

            override fun onScrolledToEnd() {
                viewModel.isUserScrollingUp.value = false
                if (viewModel.unreadMessagesCount.value != 0 && coreContext.notificationsManager.currentlyDisplayedChatRoomAddress != null) {
                    Log.i("[Chat Room] User has scrolled to the latest message, mark chat room as read")
                    viewModel.chatRoom.markAsRead()
                }
            }
        }
        binding.chatMessagesList.addOnScrollListener(chatScrollListener)

        chatSendingViewModel.textToSend.observe(
            viewLifecycleOwner,
            {
                chatSendingViewModel.onTextToSendChanged(it)
            }
        )

        chatSendingViewModel.requestRecordAudioPermissionEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    Log.i("[Chat Room] Asking for RECORD_AUDIO permission")
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 2)
                }
            }
        )

        listViewModel.events.observe(
            viewLifecycleOwner,
            { events ->
                adapter.setUnreadMessageCount(viewModel.chatRoom.unreadMessagesCount, viewModel.isUserScrollingUp.value == true)
                adapter.submitList(events)
            }
        )

        listViewModel.messageUpdatedEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { position ->
                    adapter.notifyItemChanged(position)
                }
            }
        )

        listViewModel.requestWriteExternalStoragePermissionEvent.observe(
            viewLifecycleOwner,
            {
                it.consume {
                    requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                }
            }
        )

        adapter.deleteMessageEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { chatMessage ->
                    listViewModel.deleteMessage(chatMessage)
                    viewModel.updateLastMessageToDisplay()
                }
            }
        )

        adapter.resendMessageEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { chatMessage ->
                    listViewModel.resendMessage(chatMessage)
                }
            }
        )

        adapter.forwardMessageEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { chatMessage ->
                    // Remove observer before setting the message to forward
                    // as we don't want to forward it in this chat room
                    sharedViewModel.messageToForwardEvent.removeObservers(viewLifecycleOwner)
                    sharedViewModel.messageToForwardEvent.value = Event(chatMessage)
                    sharedViewModel.isPendingMessageForward.value = true

                    if (sharedViewModel.isSlidingPaneSlideable.value == true) {
                        Log.i("[Chat Room] Forwarding message, going to chat rooms list")
                        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
                    } else {
                        navigateToEmptyChatRoom()
                    }
                }
            }
        )

        adapter.replyMessageEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { chatMessage ->
                    chatSendingViewModel.pendingChatMessageToReplyTo.value?.destroy()
                    chatSendingViewModel.pendingChatMessageToReplyTo.value = ChatMessageData(chatMessage)
                    chatSendingViewModel.isPendingAnswer.value = true
                }
            }
        )

        adapter.showImdnForMessageEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { chatMessage ->
                    val args = Bundle()
                    args.putString("MessageId", chatMessage.messageId)
                    navigateToImdn(args)
                }
            }
        )

        adapter.addSipUriToContactEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { sipUri ->
                    Log.i("[Chat Room] Going to contacts list with SIP URI to add: $sipUri")
                    sharedViewModel.updateContactsAnimationsBasedOnDestination.value = Event(R.id.masterChatRoomsFragment)
                    navigateToContacts(sipUri)
                }
            }
        )

        adapter.openContentEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { content ->
                    val path = content.filePath.orEmpty()

                    if (!File(path).exists()) {
                        (requireActivity() as MainActivity).showSnackBar(R.string.chat_room_file_not_found)
                    } else {
                        Log.i("[Chat Message] Opening file: $path")
                        sharedViewModel.contentToOpen.value = content

                        if (corePreferences.useInAppFileViewerForNonEncryptedFiles || content.isFileEncrypted) {
                            val preventScreenshots =
                                viewModel.chatRoom.currentParams.isEncryptionEnabled
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
            }
        )

        adapter.scrollToChatMessageEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { chatMessage ->
                    val events = listViewModel.events.value.orEmpty()
                    val eventLog = events.find { eventLog ->
                        if (eventLog.eventLog.type == EventLog.Type.ConferenceChatMessage) {
                            (eventLog.data as ChatMessageData).chatMessage.messageId == chatMessage.messageId
                        } else false
                    }
                    val index = events.indexOf(eventLog)
                    try {
                        if (corePreferences.enableAnimations) {
                            binding.chatMessagesList.smoothScrollToPosition(index)
                        } else {
                            binding.chatMessagesList.scrollToPosition(index)
                        }
                    } catch (iae: IllegalArgumentException) {
                        Log.e("[Chat Room] Can't scroll to position $index")
                    }
                }
            }
        )

        binding.setBackClickListener {
            goBack()
        }

        binding.setTitleClickListener {
            binding.sipUri.visibility = if (!viewModel.oneToOneChatRoom ||
                binding.sipUri.visibility == View.VISIBLE
            ) View.GONE else View.VISIBLE
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
            if (PermissionHelper.get().hasReadExternalStoragePermission() && PermissionHelper.get().hasCameraPermission()) {
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

        binding.message.setControlEnterListener(object : RichEditTextSendListener {
            override fun onControlEnterPressedAndReleased() {
                Log.i("[Chat Room] Detected left control + enter key presses, sending message")
                chatSendingViewModel.sendMessage()
            }
        })

        binding.setCancelReplyToClickListener {
            chatSendingViewModel.cancelReply()
        }

        binding.setScrollToBottomClickListener {
            scrollToFirstUnreadMessageOrBottom(true)
            viewModel.isUserScrollingUp.value = false
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

        sharedViewModel.richContentUri.observe(
            viewLifecycleOwner,
            {
                it.consume { uri ->
                    Log.i("[Chat] Found rich content URI: $uri")
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            val path = FileUtils.getFilePath(requireContext(), uri)
                            Log.i("[Chat] Rich content URI: $uri matching path is: $path")
                            if (path != null) {
                                chatSendingViewModel.addAttachment(path)
                            }
                        }
                    }
                }
            }
        )

        sharedViewModel.messageToForwardEvent.observe(
            viewLifecycleOwner,
            {
                it.consume { chatMessage ->
                    Log.i("[Chat Room] Found message to transfer")
                    showForwardConfirmationDialog(chatMessage)
                    sharedViewModel.isPendingMessageForward.value = false
                }
            }
        )

        binding.stubbedMessageToReplyTo.setOnInflateListener { _, inflated ->
            Log.i("[Chat Room] Replying to message layout inflated")
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }

        binding.stubbedVoiceRecording.setOnInflateListener { _, inflated ->
            Log.i("[Chat Room] Voice recording layout inflated")
            val binding = DataBindingUtil.bind<ViewDataBinding>(inflated)
            binding?.lifecycleOwner = viewLifecycleOwner
        }
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<EventLogData>()
        for (index in indexesOfItemToDelete) {
            val eventLog = adapter.currentList[index]
            list.add(eventLog)
        }
        listViewModel.deleteEventLogs(list)
        viewModel.updateLastMessageToDisplay()
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
            Log.i("[Chat Room] Fragment resuming, mark chat room as read")
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

    override fun goBack() {
        coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null
        if (!findNavController().popBackStack()) {
            if (sharedViewModel.isSlidingPaneSlideable.value == true) {
                if (_adapter != null) {
                    try {
                        adapter.unregisterAdapterDataObserver(observer)
                    } catch (ise: IllegalStateException) {}
                }
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
            dialogViewModel.showOkButton(
                { doNotAskAgain ->
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
                },
                okLabel
            )

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

        viewModel.showOkButton(
            {
                Log.i("[Chat Room] Transfer confirmed")
                chatSendingViewModel.transferMessage(chatMessage)
                dialog.dismiss()
            },
            getString(R.string.chat_message_context_menu_forward)
        )

        dialog.show()
    }

    private fun showPopupMenu(chatRoom: ChatRoom) {
        val popupView: ChatRoomMenuBindingImpl = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.chat_room_menu, null, false
        )

        val itemSize = AppUtils.getDimension(R.dimen.chat_room_popup_item_height).toInt()
        var totalSize = itemSize * 4

        if (!viewModel.encryptedChatRoom) {
            popupView.devicesHidden = true
            totalSize -= itemSize
            popupView.ephemeralHidden = true
            totalSize -= itemSize
        } else {
            if (viewModel.oneToOneChatRoom) {
                popupView.groupInfoHidden = true
                totalSize -= itemSize
            }

            // If one participant one device, a click on security badge
            // will directly start a call or show the dialog, so don't show this menu
            if (viewModel.oneParticipantOneDevice) {
                popupView.devicesHidden = true
                totalSize -= itemSize
            }

            if (viewModel.ephemeralChatRoom) {
                if (chatRoom.currentParams.ephemeralMode == ChatRoomEphemeralMode.AdminManaged) {
                    if (chatRoom.me?.isAdmin == false) {
                        Log.w("[Chat Room] Hiding ephemeral menu as mode is admin managed and we aren't admin")
                        popupView.ephemeralHidden = true
                        totalSize -= itemSize
                    }
                }
            }
        }

        // When using WRAP_CONTENT instead of real size, fails to place the
        // popup window above if not enough space is available below
        val popupWindow = PopupWindow(
            popupView.root,
            AppUtils.getDimension(R.dimen.chat_room_popup_width).toInt(),
            totalSize,
            true
        )
        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f

        popupView.setGroupInfoListener {
            showGroupInfo(chatRoom)
            popupWindow.dismiss()
        }
        popupView.setDevicesListener {
            showParticipantsDevices()
            popupWindow.dismiss()
        }
        popupView.setEphemeralListener {
            showEphemeralMessages()
            popupWindow.dismiss()
        }
        popupView.setEditionModeListener {
            enterEditionMode()
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(binding.menu, 0, 0, Gravity.BOTTOM)
    }

    private fun addDeleteMessageTaskToQueue(eventLog: EventLogData, position: Int) {
        val task = lifecycleScope.launch {
            delay(2800) // Duration of Snackbar.LENGTH_LONG
            withContext(Dispatchers.Main) {
                if (isActive) {
                    Log.i("[Chat Room] Message/event deletion task is still active, proceed")
                    val chatMessage = eventLog.eventLog.chatMessage
                    if (chatMessage != null) {
                        Log.i("[Chat Room] Deleting message $chatMessage at position $position")
                        listViewModel.deleteMessage(chatMessage)
                    } else {
                        Log.i("[Chat Room] Deleting event $eventLog at position $position")
                        listViewModel.deleteEventLogs(arrayListOf(eventLog))
                    }
                    viewModel.updateLastMessageToDisplay()
                }
            }
        }

        (requireActivity() as MainActivity).showSnackBar(
            R.string.chat_message_removal_info,
            R.string.chat_message_abort_removal
        ) {
            Log.i("[Chat Room] Canceled message/event deletion task: $task for message/event at position $position")
            adapter.notifyItemRangeChanged(position, adapter.itemCount - position)
            task.cancel()
        }
    }

    private fun scrollToFirstUnreadMessageOrBottom(smooth: Boolean) {
        if (_adapter != null && adapter.itemCount > 0) {
            // Scroll to first unread message if any
            val firstUnreadMessagePosition = adapter.getFirstUnreadMessagePosition()
            val indexToScrollTo = if (firstUnreadMessagePosition != -1) {
                firstUnreadMessagePosition
            } else {
                adapter.itemCount - 1
            }

            Log.i("[Chat Room] Scrolling to position $indexToScrollTo, first unread message is at $firstUnreadMessagePosition")
            if (smooth && corePreferences.enableAnimations) {
                binding.chatMessagesList.smoothScrollToPosition(indexToScrollTo)
            } else {
                binding.chatMessagesList.scrollToPosition(indexToScrollTo)
            }
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

        dialogViewModel.showDeleteButton(
            {
                dialog.dismiss()
                lifecycleScope.launch {
                    Log.w("[Chat Room] Content is encrypted, requesting plain file path")
                    val plainFilePath = content.plainFilePath
                    Log.i("[Cht Room] Making a copy of [$plainFilePath] to the cache directory before exporting it")
                    val cacheCopyPath = FileUtils.copyFileToCache(plainFilePath)
                    if (cacheCopyPath != null) {
                        Log.i("[Cht Room] Cache copy has been made: $cacheCopyPath")
                        FileUtils.deleteFile(plainFilePath)
                        if (!FileUtils.openFileInThirdPartyApp(requireActivity(), cacheCopyPath)) {
                            showDialogToSuggestOpeningFileAsText()
                        }
                    }
                }
            },
            getString(R.string.chat_message_cant_open_file_in_app_dialog_export_button)
        )

        dialogViewModel.showOkButton(
            {
                dialog.dismiss()
                navigateToTextFileViewer(true)
            },
            getString(R.string.chat_message_cant_open_file_in_app_dialog_open_as_text_button)
        )

        dialogViewModel.showCancelButton {
            dialog.dismiss()
        }

        dialog.show()
    }
}
