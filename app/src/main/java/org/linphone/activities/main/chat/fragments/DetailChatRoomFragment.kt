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
import android.content.*
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            adapter.notifyItemChanged(positionStart - 1) // For grouping purposes

            if (positionStart == 0 && adapter.itemCount == itemCount) {
                // First time we fill the list with messages
                Log.i("[Chat Room] History first $itemCount messages loaded")
            } else {
                // Scroll to newly added messages automatically only if user hasn't initiated a scroll up in the messages history
                if (viewModel.isUserScrollingUp.value == false) {
                    scrollToFirstUnreadMessageOrBottom(false)
                } else {
                    Log.d("[Chat Room] User has scrolled up manually in the messages history, don't scroll to the newly added message at the bottom & don't mark the chat room as read")
                }
            }
        }
    }

    private lateinit var chatScrollListener: ChatScrollListener

    override fun getLayoutId(): Int {
        return R.layout.chat_room_detail_fragment
    }

    override fun onDestroyView() {
        binding.chatMessagesList.adapter = null

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (isSharedViewModelInitialized()) {
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
            goBack()
            return
        }

        view.doOnPreDraw {
            // Notifies fragment is ready to be drawn
            sharedViewModel.chatRoomFragmentOpenedEvent.value = Event(true)
        }

        Compatibility.setLocusIdInContentCaptureSession(binding.root, chatRoom)

        isSecure = chatRoom.currentParams.isEncryptionEnabled

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

        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        binding.chatMessagesList.layoutManager = layoutManager

        // Displays unread messages header
        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.chatMessagesList.addItemDecoration(headerItemDecoration)

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
                val index = viewHolder.bindingAdapterPosition
                if (index < 0 || index >= adapter.currentList.size) {
                    Log.e("[Chat Room] Index is out of bound, can't reply to chat message")
                } else {
                    adapter.notifyItemChanged(index)

                    val chatMessageEventLog = adapter.currentList[index]
                    val chatMessage = chatMessageEventLog.eventLog.chatMessage
                    if (chatMessage != null) {
                        chatSendingViewModel.pendingChatMessageToReplyTo.value?.destroy()
                        chatSendingViewModel.pendingChatMessageToReplyTo.value =
                            ChatMessageData(chatMessage)
                        chatSendingViewModel.isPendingAnswer.value = true
                    }
                }
            }

            override fun onRightToLeftSwipe(viewHolder: RecyclerView.ViewHolder) {
                val index = viewHolder.bindingAdapterPosition
                if (index < 0 || index >= adapter.currentList.size) {
                    Log.e("[Chat Room] Index is out of bound, can't delete chat message")
                } else {
                    // adapter.notifyItemRemoved(index)
                    val eventLog = adapter.currentList[index]
                    addDeleteMessageTaskToQueue(eventLog, index)
                }
            }
        }
        RecyclerViewSwipeUtils(ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT, swipeConfiguration, swipeListener)
            .attachToRecyclerView(binding.chatMessagesList)

        chatScrollListener = object : ChatScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int) {
                Log.i("[Chat Room] User has scrolled up far enough, load more items from history (currently there are $totalItemsCount messages displayed)")
                listViewModel.loadMoreData(totalItemsCount)
            }

            override fun onScrolledUp() {
                viewModel.isUserScrollingUp.value = true
            }

            override fun onScrolledToEnd() {
                viewModel.isUserScrollingUp.value = false

                val peerAddress = viewModel.chatRoom.peerAddress.asStringUriOnly()
                if (viewModel.unreadMessagesCount.value != 0 &&
                    coreContext.notificationsManager.currentlyDisplayedChatRoomAddress == peerAddress
                ) {
                    Log.i("[Chat Room] User has scrolled to the latest message, mark chat room as read")
                    viewModel.chatRoom.markAsRead()
                }
            }
        }

        chatSendingViewModel.textToSend.observe(
            viewLifecycleOwner
        ) {
            chatSendingViewModel.onTextToSendChanged(it)
        }

        chatSendingViewModel.isVoiceRecording.observe(
            viewLifecycleOwner
        ) { voiceRecording ->
            // Keep screen on while recording voice message
            if (voiceRecording) {
                requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        chatSendingViewModel.requestRecordAudioPermissionEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("[Chat Room] Asking for RECORD_AUDIO permission")
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 2)
            }
        }

        chatSendingViewModel.messageSentEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                Log.i("[Chat Room] Message sent")
                // Reset this to ensure sent message will be visible
                viewModel.isUserScrollingUp.value = false
            }
        }

        listViewModel.events.observe(
            viewLifecycleOwner
        ) { events ->
            adapter.setUnreadMessageCount(
                viewModel.chatRoom.unreadMessagesCount,
                viewModel.isUserScrollingUp.value == true
            )
            adapter.submitList(events)
        }

        listViewModel.messageUpdatedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { position ->
                adapter.notifyItemChanged(position)
            }
        }

        listViewModel.requestWriteExternalStoragePermissionEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }

        adapter.deleteMessageEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatMessage ->
                listViewModel.deleteMessage(chatMessage)
                sharedViewModel.refreshChatRoomInListEvent.value = Event(true)
            }
        }

        adapter.resendMessageEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatMessage ->
                listViewModel.resendMessage(chatMessage)
            }
        }

        adapter.forwardMessageEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatMessage ->
                // Remove observer before setting the message to forward
                // as we don't want to forward it in this chat room
                sharedViewModel.messageToForwardEvent.removeObservers(viewLifecycleOwner)
                sharedViewModel.messageToForwardEvent.value = Event(chatMessage)
                sharedViewModel.isPendingMessageForward.value = true

                if (sharedViewModel.isSlidingPaneSlideable.value == true) {
                    Log.i("[Chat Room] Forwarding message, going to chat rooms list")
                    goBack()
                } else {
                    navigateToEmptyChatRoom()
                }
            }
        }

        adapter.replyMessageEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatMessage ->
                chatSendingViewModel.pendingChatMessageToReplyTo.value?.destroy()
                chatSendingViewModel.pendingChatMessageToReplyTo.value =
                    ChatMessageData(chatMessage)
                chatSendingViewModel.isPendingAnswer.value = true
            }
        }

        adapter.showImdnForMessageEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatMessage ->
                val args = Bundle()
                args.putString("MessageId", chatMessage.messageId)
                navigateToImdn(args)
            }
        }

        adapter.addSipUriToContactEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { sipUri ->
                Log.i("[Chat Room] Going to contacts list with SIP URI to add: $sipUri")
                sharedViewModel.updateContactsAnimationsBasedOnDestination.value =
                    Event(R.id.masterChatRoomsFragment)
                navigateToContacts(sipUri)
            }
        }

        adapter.openContentEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { content ->
                var path = content.filePath.orEmpty()

                if (path.isNotEmpty() && !File(path).exists()) {
                    Log.e("[Chat Room] File not found: $path")
                    (requireActivity() as MainActivity).showSnackBar(R.string.chat_room_file_not_found)
                } else {
                    if (path.isEmpty()) {
                        val name = content.name
                        if (name != null && name.isNotEmpty()) {
                            val file = FileUtils.getFileStoragePath(name)
                            FileUtils.writeIntoFile(content.buffer, file)
                            path = file.absolutePath
                            content.filePath = path
                            Log.i("[Chat Room] Content file path was empty, created file from buffer at $path")
                        } else if (content.isIcalendar) {
                            val name = "conference.ics"
                            val file = FileUtils.getFileStoragePath(name)
                            FileUtils.writeIntoFile(content.buffer, file)
                            path = file.absolutePath
                            content.filePath = path
                            Log.i("[Chat Room] Content file path was empty, created conference.ics from buffer at $path")
                        }
                    }

                    Log.i("[Chat Room] Opening file: $path")
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
                                    Log.w("[Chat Room] File is encrypted and can't be opened in one of our viewers...")
                                    showDialogForUserConsentBeforeExportingFileInThirdPartyApp(
                                        content
                                    )
                                } else if (!FileUtils.openFileInThirdPartyApp(
                                        requireActivity(),
                                        path
                                    )
                                ) {
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

        adapter.sipUriClickedEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { sipUri ->
                val args = Bundle()
                args.putString("URI", sipUri)
                args.putBoolean("Transfer", false)
                // If auto start call setting is enabled, ignore it
                args.putBoolean("SkipAutoCallStart", true)
                navigateToDialer(args)
            }
        }

        adapter.callConferenceEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { pair ->
                navigateToConferenceWaitingRoom(pair.first, pair.second)
            }
        }

        adapter.scrollToChatMessageEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatMessage ->
                var index = 0
                var retryCount = 0
                var expectedChildCount = 0
                do {
                    val events = listViewModel.events.value.orEmpty()
                    expectedChildCount = events.size
                    Log.e("[Chat Room] expectedChildCount : $expectedChildCount")
                    val eventLog = events.find { eventLog ->
                        if (eventLog.eventLog.type == EventLog.Type.ConferenceChatMessage) {
                            (eventLog.data as ChatMessageData).chatMessage.messageId == chatMessage.messageId
                        } else false
                    }
                    index = events.indexOf(eventLog)
                    if (index == -1) {
                        retryCount += 1
                        listViewModel.loadMoreData(events.size)
                    }
                } while (index == -1 && retryCount < 5)

                if (index != -1) {
                    if (retryCount == 0) {
                        scrollTo(index, true)
                    } else {
                        lifecycleScope.launch {
                            withContext(Dispatchers.Default) {
                                val layoutManager = binding.chatMessagesList.layoutManager as LinearLayoutManager
                                var retryCount = 0
                                do {
                                    // We have to wait for newly loaded items to be added to list before being able to scroll
                                    delay(500)
                                    retryCount += 1
                                } while (layoutManager.itemCount != expectedChildCount && retryCount < 5)

                                withContext(Dispatchers.Main) {
                                    scrollTo(index, true)
                                }
                            }
                        }
                    }
                } else {
                    Log.w("[Chat Room] Failed to find matching event!")
                }
            }
        }

        adapter.errorEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { message ->
                (requireActivity() as MainActivity).showSnackBar(message)
            }
        }

        binding.setTitleClickListener {
            binding.sipUri.visibility = if (!viewModel.oneToOneChatRoom ||
                binding.sipUri.visibility == View.VISIBLE
            ) View.GONE else View.VISIBLE
        }

        binding.setMenuClickListener {
            showPopupMenu(chatRoom)
        }

        binding.setMenuLongClickListener {
            // Only show debug infos if debug mode is enabled
            if (corePreferences.debugLogs) {
                val alertDialog = MaterialAlertDialogBuilder(requireContext())

                val messageBuilder = StringBuilder()
                messageBuilder.append("Chat room id:\n")
                messageBuilder.append(viewModel.chatRoom.peerAddress.asString())
                messageBuilder.append("\n")
                messageBuilder.append("Local account:\n")
                messageBuilder.append(viewModel.chatRoom.localAddress.asString())
                val message = messageBuilder.toString()
                alertDialog.setMessage(message)

                alertDialog.setNeutralButton(R.string.chat_message_context_menu_copy_text) {
                    _, _ ->
                    val clipboard: ClipboardManager =
                        coreContext.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Chat room info", message)
                    clipboard.setPrimaryClip(clip)
                }

                alertDialog.show()
                true
            }
            false
        }

        binding.setSecurityIconClickListener {
            showParticipantsDevices()
        }

        binding.setAttachFileClickListener {
            if (PermissionHelper.get().hasReadExternalStoragePermission() || PermissionHelper.get().hasCameraPermission()) {
                pickFile()
            } else {
                Log.i("[Chat Room] Asking for READ_EXTERNAL_STORAGE and CAMERA permissions")
                Compatibility.requestReadExternalStorageAndCameraPermissions(this, 0)
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
                            (activity as MainActivity).showSnackBar(R.string.chat_message_voice_recording_hold_to_record)
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

        binding.setGroupCallListener {
            showGroupCallDialog()
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
            viewLifecycleOwner
        ) {
            it.consume { uri ->
                Log.i("[Chat Room] Found rich content URI: $uri")
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        val path = FileUtils.getFilePath(requireContext(), uri)
                        Log.i("[Chat Room] Rich content URI: $uri matching path is: $path")
                        if (path != null) {
                            chatSendingViewModel.addAttachment(path)
                        }
                    }
                }
            }
        }

        sharedViewModel.messageToForwardEvent.observe(
            viewLifecycleOwner
        ) {
            it.consume { chatMessage ->
                Log.i("[Chat Room] Found message to transfer")
                showForwardConfirmationDialog(chatMessage)
                sharedViewModel.isPendingMessageForward.value = false
            }
        }
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<EventLogData>()
        for (index in indexesOfItemToDelete) {
            val eventLog = adapter.currentList[index]
            list.add(eventLog)
        }
        listViewModel.deleteEventLogs(list)
        sharedViewModel.refreshChatRoomInListEvent.value = Event(true)
    }

    @Deprecated("Deprecated in Java")
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

            if (_adapter != null) {
                try {
                    adapter.registerAdapterDataObserver(observer)
                } catch (ise: IllegalStateException) {}
            }

            // Wait for items to be displayed
            binding.chatMessagesList
                .viewTreeObserver
                .addOnGlobalLayoutListener(
                    object : OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            binding.chatMessagesList
                                .viewTreeObserver
                                .removeOnGlobalLayoutListener(this)

                            if (isBindingAvailable()) {
                                if (::chatScrollListener.isInitialized) {
                                    binding.chatMessagesList.addOnScrollListener(chatScrollListener)
                                }

                                if (viewModel.chatRoom.unreadMessagesCount > 0) {
                                    Log.i("[Chat Room] Messages have been displayed, scrolling to first unread")
                                    val notAllMessagesDisplayed = scrollToFirstUnreadMessageOrBottom(false)
                                    if (notAllMessagesDisplayed) {
                                        Log.w("[Chat Room] More unread messages than the screen can display, do not mark chat room as read now, wait for user to scroll to bottom")
                                    } else {
                                        // Consider user as scrolled to the end when marking chat room as read
                                        viewModel.isUserScrollingUp.value = false
                                        Log.i("[Chat Room] Marking chat room as read")
                                        viewModel.chatRoom.markAsRead()
                                    }
                                }
                            } else {
                                Log.e("[Chat Room] Binding not available in onGlobalLayout callback!")
                            }
                        }
                    }
                )
        } else {
            Log.e("[Chat Room] Fragment resuming but viewModel lateinit property isn't initialized!")
        }
    }

    override fun onPause() {
        if (::chatScrollListener.isInitialized) {
            binding.chatMessagesList.removeOnScrollListener(chatScrollListener)
        }

        if (_adapter != null) {
            try {
                adapter.unregisterAdapterDataObserver(observer)
            } catch (ise: IllegalStateException) {}
        }

        // Conversation isn't visible anymore, any new message received in it will trigger a notification
        coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null

        super.onPause()
    }

    @Deprecated("Deprecated in Java")
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
                            coreContext.startCall(address, forceZRTP = true)
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
                    coreContext.startCall(address, forceZRTP = true)
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

    private fun scheduleMeeting(chatRoom: ChatRoom) {
        val participants = arrayListOf<Address>()
        for (participant in chatRoom.participants) {
            participants.add(participant.address)
        }
        sharedViewModel.participantsListForNextScheduledMeeting.value = Event(participants)
        navigateToConferenceScheduling()
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
        var totalSize = itemSize * 8

        val notificationsTurnedOff = viewModel.areNotificationsMuted()
        if (notificationsTurnedOff) {
            popupView.muteHidden = true
            totalSize -= itemSize
        } else {
            popupView.unmuteHidden = true
            totalSize -= itemSize
        }

        if (viewModel.basicChatRoom || viewModel.oneToOneChatRoom) {
            if (viewModel.contact.value != null) {
                popupView.addToContactsHidden = true
            } else {
                popupView.goToContactHidden = true
            }

            popupView.meetingHidden = true
            totalSize -= itemSize
        } else {
            popupView.addToContactsHidden = true
            popupView.goToContactHidden = true
            totalSize -= itemSize
        }

        if (viewModel.basicChatRoom) {
            popupView.groupInfoHidden = true
            totalSize -= itemSize
            popupView.devicesHidden = true
            totalSize -= itemSize
            popupView.ephemeralHidden = true
            totalSize -= itemSize
        } else {
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
        popupView.setMeetingListener {
            scheduleMeeting(chatRoom)
            popupWindow.dismiss()
        }
        popupView.setEditionModeListener {
            enterEditionMode()
            popupWindow.dismiss()
        }
        popupView.setMuteListener {
            viewModel.muteNotifications(true)
            sharedViewModel.refreshChatRoomInListEvent.value = Event(true)
            popupWindow.dismiss()
        }
        popupView.setUnmuteListener {
            viewModel.muteNotifications(false)
            sharedViewModel.refreshChatRoomInListEvent.value = Event(true)
            popupWindow.dismiss()
        }
        popupView.setAddToContactsListener {
            popupWindow.dismiss()
            val copy = viewModel.getRemoteAddress()?.clone()
            if (copy != null) {
                copy.clean()
                val address = copy.asStringUriOnly()
                Log.i("[Chat Room] Creating contact with SIP URI: $address")
                navigateToContacts(address)
            }
        }
        popupView.setGoToContactListener {
            popupWindow.dismiss()
            val contactId = viewModel.contact.value?.refKey
            if (contactId != null) {
                Log.i("[Chat Room] Displaying contact $contactId")
                navigateToContact(contactId)
            } else {
                val copy = viewModel.getRemoteAddress()?.clone()
                if (copy != null) {
                    copy.clean()
                    val address = copy.asStringUriOnly()
                    Log.i("[Chat Room] Displaying friend with address $address")
                    navigateToContact(address)
                }
            }
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
                    sharedViewModel.refreshChatRoomInListEvent.value = Event(true)
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

    private fun scrollToFirstUnreadMessageOrBottom(smooth: Boolean): Boolean {
        if (_adapter != null && adapter.itemCount > 0) {
            val recyclerView = binding.chatMessagesList

            // Scroll to first unread message if any, unless we are already on it
            val firstUnreadMessagePosition = adapter.getFirstUnreadMessagePosition()
            val currentPosition = (recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
            val indexToScrollTo = if (firstUnreadMessagePosition != -1 && firstUnreadMessagePosition != currentPosition) {
                firstUnreadMessagePosition
            } else {
                adapter.itemCount - 1
            }

            Log.i("[Chat Room] Scrolling to position $indexToScrollTo, first unread message is at $firstUnreadMessagePosition")
            scrollTo(indexToScrollTo, smooth)

            if (firstUnreadMessagePosition == 0) {
                // Return true only if all unread messages don't fit in the recyclerview height
                return recyclerView.computeVerticalScrollRange() > recyclerView.height
            }
        }
        return false
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
            try {
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
            } catch (e: Exception) {
                Log.e("[Chat Room] Failed to pick file: $e")
            }
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
                    val plainFilePath = content.exportPlainFile()
                    Log.i("[Chat Room] Making a copy of [$plainFilePath] to the cache directory before exporting it")
                    val cacheCopyPath = FileUtils.copyFileToCache(plainFilePath)
                    if (cacheCopyPath != null) {
                        Log.i("[Chat Room] Cache copy has been made: $cacheCopyPath")
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

    private fun showGroupCallDialog() {
        val dialogViewModel = DialogViewModel(getString(R.string.conference_start_group_call_dialog_message), getString(R.string.conference_start_group_call_dialog_title))
        val dialog: Dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

        dialogViewModel.iconResource = R.drawable.icon_video_conf_incoming
        dialogViewModel.showIcon = true

        dialogViewModel.showCancelButton {
            dialog.dismiss()
        }

        dialogViewModel.showOkButton(
            {
                dialog.dismiss()
                viewModel.startGroupCall()
            },
            getString(R.string.conference_start_group_call_dialog_ok_button)
        )

        dialog.show()
    }

    private fun scrollTo(position: Int, smooth: Boolean = true) {
        try {
            if (smooth && corePreferences.enableAnimations) {
                binding.chatMessagesList.smoothScrollToPosition(position)
            } else {
                binding.chatMessagesList.scrollToPosition(position)
            }
        } catch (iae: IllegalArgumentException) {
            Log.e("[Chat Room] Can't scroll to position $position")
        }
    }
}
