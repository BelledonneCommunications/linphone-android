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
package org.linphone.ui.main.chat.fragment

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.ChatMessage
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatConversationFragmentBinding
import org.linphone.databinding.ChatConversationPopupMenuBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.chat.RecyclerViewScrollListener
import org.linphone.ui.main.chat.adapter.ConversationEventAdapter
import org.linphone.ui.main.chat.adapter.MessageBottomSheetAdapter
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.ui.main.chat.model.MessageDeliveryModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.chat.model.MessageReactionsModel
import org.linphone.ui.main.chat.view.RichEditText
import org.linphone.ui.main.chat.viewmodel.ChatMessageLongPressViewModel
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel
import org.linphone.ui.main.chat.viewmodel.SendMessageInConversationViewModel
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.RecyclerViewHeaderDecoration
import org.linphone.utils.RecyclerViewSwipeUtils
import org.linphone.utils.RecyclerViewSwipeUtilsCallback
import org.linphone.utils.TimestampUtils
import org.linphone.utils.addCharacterAtPosition
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard
import androidx.core.net.toUri
import org.linphone.ui.main.chat.adapter.ConversationParticipantsAdapter
import org.linphone.ui.main.chat.model.MessageDeleteDialogModel

@UiThread
open class ConversationFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Conversation Fragment]"

        private const val EXPORT_FILE_AS_DOCUMENT = 10
    }

    protected lateinit var binding: ChatConversationFragmentBinding

    protected lateinit var viewModel: ConversationViewModel

    protected lateinit var sendMessageViewModel: SendMessageInConversationViewModel

    private lateinit var messageLongPressViewModel: ChatMessageLongPressViewModel

    private lateinit var adapter: ConversationEventAdapter

    private lateinit var participantsAdapter: ConversationParticipantsAdapter

    private lateinit var bottomSheetAdapter: MessageBottomSheetAdapter

    private val args: ConversationFragmentArgs by navArgs()

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private var filePathToExport: String? = null

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = SendMessageInConversationViewModel.MAX_FILES_TO_ATTACH
        )
    ) { list ->
        sendMessageViewModel.closeFilePickerBottomSheet()
        val filesToAttach = arrayListOf<String>()
        lifecycleScope.launch {
            for (uri in list) {
                withContext(Dispatchers.IO) {
                    val path = FileUtils.getFilePath(requireContext(), uri, false)
                    Log.i("$TAG Picked file [$uri] matching path is [$path]")
                    if (path != null) {
                        filesToAttach.add(path)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                sendMessageViewModel.addAttachments(filesToAttach)
            }
        }
    }

    private var pendingImageCaptureFile: File? = null

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { files ->
        sendMessageViewModel.closeFilePickerBottomSheet()
        val filesToAttach = arrayListOf<String>()
        lifecycleScope.launch {
            for (fileUri in files) {
                val path = FileUtils.getFilePath(requireContext(), fileUri, false).orEmpty()
                if (path.isNotEmpty()) {
                    Log.i("$TAG Picked file [$path]")
                    filesToAttach.add(path)
                } else {
                    Log.e("$TAG Failed to pick file [$fileUri]")
                }
            }
            withContext(Dispatchers.Main) {
                sendMessageViewModel.addAttachments(filesToAttach)
            }
        }
    }

    private val startCameraCapture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        sendMessageViewModel.closeFilePickerBottomSheet()
        val path = pendingImageCaptureFile?.absolutePath
        if (path != null) {
            if (captured) {
                Log.i("$TAG Image was captured and saved in [$path]")
                sendMessageViewModel.addAttachments(arrayListOf(path))
            } else {
                Log.w("$TAG Image capture was aborted")
                lifecycleScope.launch {
                    FileUtils.deleteFile(path)
                }
            }
            pendingImageCaptureFile = null
        } else {
            Log.e("$TAG No pending captured image file!")
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG CAMERA permission has been granted")
        } else {
            Log.e("$TAG CAMERA permission has been denied")
        }
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG RECORD_AUDIO permission has been granted, starting voice message recording")
            sendMessageViewModel.startVoiceMessageRecording()
        } else {
            Log.e("$TAG RECORD_AUDIO permission has been denied")
        }
    }

    private val globalLayoutObserver = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            binding.eventsList
                .viewTreeObserver
                .removeOnGlobalLayoutListener(this)

            binding.root.setKeyboardInsetListener { keyboardVisible ->
                sendMessageViewModel.isKeyboardOpen.value = keyboardVisible
                if (keyboardVisible) {
                    sendMessageViewModel.isEmojiPickerOpen.value = false
                }
            }

            val unreadCount = viewModel.unreadMessagesCount.value ?: 0
            if (unreadCount > 0) {
                Log.i(
                    "$TAG Messages have been displayed and [$unreadCount] of them are unread, scrolling to first unread"
                )
                scrollToFirstUnreadMessageOrBottom()
            }
        }
    }

    private val dataObserver = object : AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart > 0) {
                adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
            } else if (adapter.itemCount != itemCount) {
                if (viewModel.searchInProgress.value == true) {
                    val recyclerView = binding.eventsList
                    var indexToScrollTo = viewModel.itemToScrollTo.value ?: 0
                    if (indexToScrollTo < 0) indexToScrollTo = 0
                    Log.i(
                        "$TAG User has loaded more history to go to a specific message, scrolling to index [$indexToScrollTo]"
                    )
                    recyclerView.scrollToPosition(indexToScrollTo)
                    viewModel.searchInProgress.postValue(false)
                }
            }

            if (viewModel.isUserScrollingUp.value == true) {
                Log.i(
                    "$TAG [$itemCount] events have been loaded but user was scrolling up in conversation, do not scroll"
                )
                return
            }

            if (positionStart == 0 && adapter.itemCount == itemCount) {
                // First time we fill the list with messages
                Log.i("$TAG [$itemCount] events have been loaded")
                val unreadCount = viewModel.unreadMessagesCount.value ?: 0
                if (unreadCount > 0) {
                    Log.i("$TAG [$unreadCount] unread messages, scrolling to first one")
                    scrollToFirstUnreadMessageOrBottom()
                }
            } else {
                Log.i(
                    "$TAG [$itemCount] new events have been loaded, scrolling to first unread message"
                )
                scrollToFirstUnreadMessageOrBottom()
            }
        }
    }

    private val textObserver = object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun afterTextChanged(editable: Editable?) {
            if (viewModel.isGroup.value == true) {
                val split = editable.toString().split(" ")
                if (split.isNotEmpty()) {
                    val lastPart = split.last()
                    if (lastPart.isNotEmpty() && lastPart.startsWith("@")) {
                        coreContext.postOnCoreThread {
                            val filter = if (lastPart.length > 1) lastPart.substring(1) else ""
                            sendMessageViewModel.filterParticipantsList(filter)
                        }

                        if (sendMessageViewModel.isParticipantsListOpen.value == false) {
                            Log.i("$TAG '@' found, opening participants list")
                            sendMessageViewModel.openParticipantsList()
                        }
                    } else if (sendMessageViewModel.isParticipantsListOpen.value == true) {
                        Log.i("$TAG Closing participants list")
                        sendMessageViewModel.closeParticipantsList()
                    }
                }
            }

            sendMessageViewModel.notifyComposing(editable.toString().isNotEmpty())
        }
    }

    private lateinit var scrollListener: RecyclerViewScrollListener

    private lateinit var headerItemDecoration: RecyclerViewHeaderDecoration

    private val listItemTouchListener = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(
            rv: RecyclerView,
            e: MotionEvent
        ): Boolean {
            // Following code is only to detect click on header at position 0
            if (::headerItemDecoration.isInitialized) {
                if (e.action == MotionEvent.ACTION_UP) {
                    if ((rv.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() == 0) {
                        if (e.y >= 0 && e.y <= headerItemDecoration.getDecorationHeight(0)) {
                            if (viewModel.isEndToEndEncrypted.value == true) {
                                showEndToEndEncryptionDetailsBottomSheet()
                            } else {
                                showUnsafeConversationDisabledDetailsBottomSheet()
                            }
                            return true
                        }
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) { }

        override fun onRequestDisallowInterceptTouchEvent(
            disallowIntercept: Boolean
        ) { }
    }

    private var currentChatMessageModelForBottomSheet: MessageModel? = null

    private val bottomSheetCallback = object : BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                currentChatMessageModelForBottomSheet?.isSelected?.value = false
                backPressedCallback.isEnabled = false
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) { }
    }

    private var bottomSheetDeliveryModel: MessageDeliveryModel? = null

    private var bottomSheetReactionsModel: MessageReactionsModel? = null

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (viewModel.searchBarVisible.value == true) {
                Log.i("$TAG Search bar is visible, closing it instead of going back")
                viewModel.closeSearchBar()
                return
            }

            val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN && bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
                Log.i(
                    "$TAG Bottom sheet isn't hidden nor collapsed, hiding it instead of going back"
                )
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                return
            }

            if (messageLongPressViewModel.visible.value == true) {
                Log.i("$TAG Message long press menu is visible, hiding it instead of going back")
                messageLongPressViewModel.dismiss()
                return
            }

            Log.i("$TAG Search bar is closed & no bottom sheet is opened, going back")
            isEnabled = false
            try {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            } catch (ise: IllegalStateException) {
                Log.w("$TAG Can't go back: $ise")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationEventAdapter()
        participantsAdapter = ConversationParticipantsAdapter()
        headerItemDecoration = RecyclerViewHeaderDecoration(
            requireContext(),
            adapter,
            false
        )
        bottomSheetAdapter = MessageBottomSheetAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatConversationFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun goBack(): Boolean {
        if (viewModel.isCallConversation.value == true) {
            Log.i("$TAG Conversation is call related, going back to previous fragment")
            return findNavController().popBackStack()
        }

        sharedViewModel.closeSlidingPaneEvent.value = Event(true)
        sharedViewModel.displayedChatRoom = null

        if (findNavController().currentDestination?.id == R.id.conversationFragment) {
            // If not done this fragment won't be paused, which will cause us issues
            val action =
                ConversationFragmentDirections.actionConversationFragmentToEmptyFragment()
            findNavController().navigate(action)
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        // The following prevents re-computing conversation history
        // when going back from a sub-fragment such as media grid or info
        if (!::viewModel.isInitialized) {
            viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]
        }
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        sendMessageViewModel = ViewModelProvider(this)[SendMessageInConversationViewModel::class.java]
        binding.sendMessageViewModel = sendMessageViewModel
        observeToastEvents(sendMessageViewModel)

        messageLongPressViewModel = ViewModelProvider(this)[ChatMessageLongPressViewModel::class.java]
        binding.messageLongPressViewModel = messageLongPressViewModel
        observeToastEvents(messageLongPressViewModel)
        messageLongPressViewModel.setupEmojiPicker(binding.longPressMenu.emojiPickerBottomSheet)

        binding.setBackClickListener {
            goBack()
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        binding.eventsList.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        binding.eventsList.layoutManager = layoutManager

        binding.sendArea.participants.participantsList.setHasFixedSize(true)
        val participantsLayoutManager = LinearLayoutManager(requireContext())
        binding.sendArea.participants.participantsList.layoutManager = participantsLayoutManager

        val callbacks = RecyclerViewSwipeUtilsCallback(
            R.drawable.reply,
            ConversationEventAdapter.EventViewHolder::class.java
        ) { viewHolder ->
            val index = viewHolder.bindingAdapterPosition
            if (index < 0 || index >= adapter.currentList.size) {
                Log.e("$TAG Swipe viewHolder index [$index] is out of bounds!")
            } else {
                adapter.notifyItemChanged(index)
                if (viewModel.isReadOnly.value == true || viewModel.isDisabledBecauseNotSecured.value == true) {
                    Log.w("$TAG Do not handle swipe action because conversation is read only")
                    return@RecyclerViewSwipeUtilsCallback
                }

                val chatMessageEventLog = adapter.currentList[index]
                val chatMessageModel = (chatMessageEventLog.model as? MessageModel)
                if (chatMessageModel != null) {
                    if (chatMessageModel.hasBeenRetracted.value == true) { // Don't allow to reply to retracted messages
                        // TODO: notify user?
                    } else {
                        viewModel.closeSearchBar()
                        sendMessageViewModel.replyToMessage(chatMessageModel)
                        // Open keyboard & focus edit text
                        binding.sendArea.messageToSend.showKeyboard()
                    }
                } else {
                    Log.e(
                        "$TAG Can't reply, failed to get a ChatMessageModel from adapter item #[$index]"
                    )
                }
            }
        }
        RecyclerViewSwipeUtils(callbacks).attachToRecyclerView(binding.eventsList)

        val conversationId = args.conversationId
        Log.i("$TAG Looking up for conversation with conversation ID [$conversationId]")
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, conversationId)
        Compatibility.setLocusIdInContentCaptureSession(binding.root, conversationId)

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (!found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find conversation, going back")
                        goBack()
                        val message = getString(R.string.conversation_to_display_no_found_toast)
                        (requireActivity() as GenericActivity).showRedToast(
                            message,
                            R.drawable.warning_circle
                        )
                    }
                } else {
                    sharedViewModel.displayedChatRoom = viewModel.chatRoom

                    sendMessageViewModel.configureChatRoom(viewModel.chatRoom)
                    adapter.setIsConversationSecured(viewModel.isEndToEndEncrypted.value == true)

                    // Wait for chat room to be ready before trying to forward a message in it
                    sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner) { event ->
                        event.consume { toForward ->
                            Log.i("$TAG Found message to forward")
                            if (viewModel.isReadOnly.value == true || viewModel.isDisabledBecauseNotSecured.value == true) {
                                Log.w(
                                    "$TAG Can't forward message in this conversation as it is read only, keeping it in memory until conversation is joined just in case"
                                )
                                viewModel.pendingForwardMessage = toForward
                            } else {
                                sendMessageViewModel.forwardMessage(toForward)
                            }
                        }
                    }
                }
            }
        }

        viewModel.forwardMessageEvent.observe(viewLifecycleOwner) {
            it.consume { toForward ->
                Log.i("$TAG Found pending message to forward")
                if (viewModel.isReadOnly.value == true || viewModel.isDisabledBecauseNotSecured.value == true) {
                    Log.w(
                        "$TAG Can't forward message in this conversation as it is still read only"
                    )
                } else {
                    sendMessageViewModel.forwardMessage(toForward)
                }
            }
        }

        viewModel.voiceRecordPlaybackEndedEvent.observe(viewLifecycleOwner) {
            it.consume { id ->
                Log.i(
                    "$TAG Voice record playback finished, looking for voice record in next message"
                )
                val list = viewModel.eventsList
                val model = list.find { eventLogModel ->
                    (eventLogModel.model as? MessageModel)?.id == id
                }
                if (model != null) {
                    val index = list.indexOf(model)
                    if (index < list.size - 1) {
                        val nextModel = list[index + 1].model as? MessageModel
                        if (nextModel?.isVoiceRecord?.value == true) {
                            Log.i(
                                "$TAG Next message model is also a voice record, start playing it"
                            )
                            nextModel.togglePlayPauseVoiceRecord()
                        }
                    }
                }
            }
        }

        viewModel.updateEvents.observe(viewLifecycleOwner) {
            it.consume {
                val items = viewModel.eventsList
                Log.i("$TAG Events (messages) list submitted, contains [${items.size}] items")
                adapter.submitList(items)

                // Wait for adapter to have items before setting it in the RecyclerView,
                // otherwise scroll position isn't retained
                if (binding.eventsList.adapter != adapter) {
                    binding.eventsList.adapter = adapter
                }

                (view.parent as? ViewGroup)?.doOnPreDraw {
                    sharedViewModel.openSlidingPaneEvent.value = Event(true)
                }
            }
        }

        viewModel.confirmGroupCallEvent.observe(viewLifecycleOwner) {
            it.consume {
                showConfirmGroupCallPopup()
            }
        }

        viewModel.isEndToEndEncrypted.observe(viewLifecycleOwner) { encrypted ->
            adapter.setIsConversationSecured(encrypted)
            if (encrypted || (!encrypted && viewModel.isEndToEndEncryptionAvailable.value == true)) {
                binding.eventsList.addItemDecoration(headerItemDecoration)
                binding.eventsList.addOnItemTouchListener(listItemTouchListener)
            }
        }

        binding.messageBottomSheet.bottomSheetList.setHasFixedSize(true)
        val bottomSheetLayoutManager = LinearLayoutManager(requireContext())
        binding.messageBottomSheet.bottomSheetList.layoutManager = bottomSheetLayoutManager

        adapter.chatMessageLongPressEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showChatMessageLongPressMenu(model)
            }
        }

        adapter.showDeliveryForChatMessageModelEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showBottomSheetDialog(model, showDelivery = true)
            }
        }

        adapter.showReactionForChatMessageModelEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showBottomSheetDialog(model, showReactions = true)
            }
        }

        adapter.scrollToRepliedMessageEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val repliedMessageId = model.replyToMessageId
                if (repliedMessageId.isNullOrEmpty()) {
                    Log.w("$TAG Message [${model.id}] doesn't have a reply to ID!")
                    return@consume
                }

                val originalMessage = adapter.currentList.find { eventLog ->
                    !eventLog.isEvent && (eventLog.model as MessageModel).id == repliedMessageId
                }
                if (originalMessage != null) {
                    val position = adapter.currentList.indexOf(originalMessage)
                    Log.i("$TAG Scrolling to position [$position]")
                    binding.eventsList.scrollToPosition(position)
                } else {
                    Log.w(
                        "$TAG Failed to find message with ID [$repliedMessageId] in already loaded history, loading missing items"
                    )
                    viewModel.loadDataUpUntilToMessageId(model.replyToMessageId)
                }
            }
        }

        binding.setShowMenuClickListener {
            showPopupMenu(binding.showMenu)
        }

        binding.setOpenFilePickerClickListener {
            Log.i("$TAG Opening file picker")
            pickDocument.launch(arrayOf("*/*"))
        }

        binding.setOpenMediaPickerClickListener {
            Log.i("$TAG Opening media picker")
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }

        binding.setOpenCameraClickListener {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("$TAG Asking for CAMERA permission")
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                val timeStamp = TimestampUtils.toFullString(
                    System.currentTimeMillis(),
                    timestampInSecs = false
                )
                val tempFileName = "$timeStamp.jpg"
                Log.i(
                    "$TAG Opening camera to take a picture, will be stored in file [$tempFileName]"
                )
                val file = FileUtils.getFileStoragePath(tempFileName)
                try {
                    val publicUri = FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getString(R.string.file_provider),
                        file
                    )
                    pendingImageCaptureFile = file
                    startCameraCapture.launch(publicUri)
                } catch (e: Exception) {
                    Log.e(
                        "$TAG Failed to get public URI for file in which to store captured image: $e"
                    )
                }
            }
        }

        binding.setGoToInfoClickListener {
            goToInfoFragment()
        }

        binding.setScrollToBottomClickListener {
            scrollToFirstUnreadMessageOrBottom()
        }

        binding.setEndToEndEncryptedEventClickListener {
            showEndToEndEncryptionDetailsBottomSheet()
        }

        binding.setWarningConversationDisabledClickListener {
            showUnsafeConversationDisabledDetailsBottomSheet()
        }

        binding.searchField.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                view.hideKeyboard()
                viewModel.searchUp()
                return@setOnEditorActionListener true
            }
            false
        }

        sendMessageViewModel.messageSentEvent.observe(viewLifecycleOwner) {
            it.consume { message ->
                viewModel.addSentMessageToEventsList(message)
            }
        }

        sendMessageViewModel.emojiToAddEvent.observe(viewLifecycleOwner) {
            it.consume { emoji ->
                binding.sendArea.messageToSend.addCharacterAtPosition(emoji)
            }
        }

        sendMessageViewModel.participantUsernameToAddEvent.observe(viewLifecycleOwner) {
            it.consume { username ->
                Log.i("$TAG Adding username [$username] after '@'")
                // Also add a space for convenience
                binding.sendArea.messageToSend.addCharacterAtPosition("$username ")
            }
        }

        sendMessageViewModel.requestKeyboardHidingEvent.observe(viewLifecycleOwner) {
            it.consume {
                binding.search.hideKeyboard()
            }
        }

        sendMessageViewModel.askRecordAudioPermissionEvent.observe(viewLifecycleOwner) {
            it.consume {
                requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.RECORD_AUDIO)) {
                    Log.w("$TAG Asking for RECORD_AUDIO permission")
                    requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    Log.i("$TAG Permission request for RECORD_AUDIO will be automatically denied, go to android app settings instead")
                    (requireActivity() as GenericActivity).goToAndroidPermissionSettings()
                }
            }
        }

        sendMessageViewModel.participants.observe(viewLifecycleOwner) {
            participantsAdapter.submitList(it)

            if (binding.sendArea.participants.participantsList.adapter != participantsAdapter) {
                binding.sendArea.participants.participantsList.adapter = participantsAdapter
            }
        }

        viewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                    // To automatically open keyboard
                    binding.search.showKeyboard()
                } else {
                    binding.search.hideKeyboard()
                }
            }
        }

        viewModel.fileToDisplayEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                if (messageLongPressViewModel.visible.value == true) return@consume
                Log.i("$TAG User clicked on file [${model.path}], let's display it in file viewer")
                goToFileViewer(model)
            }
        }

        viewModel.sipUriToCallEvent.observe(viewLifecycleOwner) {
            it.consume { sipUri ->
                coreContext.postOnCoreThread {
                    if (messageLongPressViewModel.visible.value == true) return@postOnCoreThread
                    val address = coreContext.core.interpretUrl(sipUri, false)
                    if (address != null) {
                        Log.i("$TAG Starting audio call to parsed SIP URI [${address.asStringUriOnly()}]")
                        coreContext.startAudioCall(address)
                    } else {
                        Log.w("$TAG Failed to parse [$sipUri] as SIP URI")
                    }
                }
            }
        }

        viewModel.conferenceToJoinEvent.observe(viewLifecycleOwner) {
            it.consume { conferenceUri ->
                if (messageLongPressViewModel.visible.value == true) return@consume
                Log.i("$TAG Requesting to go to waiting room for conference URI [$conferenceUri]")
                sharedViewModel.goToMeetingWaitingRoomEvent.value = Event(conferenceUri)
            }
        }

        viewModel.openWebBrowserEvent.observe(viewLifecycleOwner) {
            it.consume { url ->
                if (messageLongPressViewModel.visible.value == true) return@consume
                Log.i("$TAG Requesting to open web browser on page [$url]")
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(browserIntent)
                } catch (ise: IllegalStateException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url], IllegalStateException: $ise"
                    )
                } catch (anfe: ActivityNotFoundException) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url], ActivityNotFoundException: $anfe"
                    )
                } catch (e: Exception) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                    )
                }
            }
        }

        viewModel.contactToDisplayEvent.observe(viewLifecycleOwner) {
            it.consume { friendRefKey ->
                if (messageLongPressViewModel.visible.value == true) return@consume
                Log.i("$TAG Navigating to contact with ref key [$friendRefKey]")
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showContactEvent.value = Event(friendRefKey)
            }
        }

        viewModel.messageDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                val message = getString(R.string.conversation_message_deleted_toast)
                val icon = R.drawable.trash_simple
                (requireActivity() as GenericActivity).showGreenToast(message, icon)
                sharedViewModel.updateConversationLastMessageEvent.value = Event(viewModel.conversationId)
            }
        }

        viewModel.itemToScrollTo.observe(viewLifecycleOwner) { position ->
            if (position >= 0) {
                val recyclerView = binding.eventsList
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstDisplayedItemPosition = layoutManager.findFirstVisibleItemPosition()
                val lastDisplayedItemPosition = layoutManager.findLastVisibleItemPosition()
                Log.i(
                    "$TAG Scrolling to message/event at position [$position], " +
                        "display show events between positions [$firstDisplayedItemPosition] and [$lastDisplayedItemPosition]"
                )
                if (firstDisplayedItemPosition > position && position > 0) {
                    recyclerView.scrollToPosition(position - 1)
                } else if (lastDisplayedItemPosition < position && position < layoutManager.itemCount - 1) {
                    recyclerView.scrollToPosition(position + 1)
                } else {
                    recyclerView.scrollToPosition(position)
                }
            }
        }

        messageLongPressViewModel.showImdnInfoEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = messageLongPressViewModel.messageModel.value
                if (model != null) {
                    showBottomSheetDialog(model, showDelivery = true)
                }
            }
        }

        messageLongPressViewModel.editMessageEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = messageLongPressViewModel.messageModel.value
                if (model != null) {
                    viewModel.closeSearchBar()
                    sendMessageViewModel.editMessage(model)

                    // Open keyboard & focus edit text
                    binding.sendArea.messageToSend.showKeyboard()
                    // Put cursor at the end
                    coreContext.postOnMainThread {
                        binding.sendArea.messageToSend.setSelection(binding.sendArea.messageToSend.length())
                    }
                }
            }
        }

        messageLongPressViewModel.replyToMessageEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = messageLongPressViewModel.messageModel.value
                if (model != null) {
                    viewModel.closeSearchBar()
                    sendMessageViewModel.replyToMessage(model)
                    // Open keyboard & focus edit text
                    binding.sendArea.messageToSend.showKeyboard()
                }
            }
        }

        messageLongPressViewModel.deleteMessageEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = messageLongPressViewModel.messageModel.value
                if (model != null) {
                    if (model.isOutgoing && !(model.hasBeenRetracted.value ?: false)) {
                        // For sent messages let user choose between delete locally / delete for everyone
                        showHowToDeleteMessageDialog(model)
                    } else {
                        // For received messages or retracted sent ones you can only delete locally
                        viewModel.deleteChatMessage(model)
                    }
                }
            }
        }

        messageLongPressViewModel.forwardMessageEvent.observe(viewLifecycleOwner) {
            it.consume {
                val model = messageLongPressViewModel.messageModel.value
                if (model != null) {
                    viewModel.closeSearchBar()
                    sendMessageViewModel.cancelReply()

                    // Remove observer before setting the message to forward
                    // as we don't want to forward it in this chat room
                    sharedViewModel.messageToForwardEvent.removeObservers(viewLifecycleOwner)
                    sharedViewModel.messageToForwardEvent.postValue(Event(model))

                    if (findNavController().currentDestination?.id == R.id.conversationFragment) {
                        val action = ConversationFragmentDirections.actionConversationFragmentToConversationForwardMessageFragment()
                        findNavController().navigate(action)
                    }
                }
            }
        }

        messageLongPressViewModel.onDismissedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Compatibility.removeBlurRenderEffect(binding.coordinatorLayout)
            }
        }

        sharedViewModel.richContentUri.observe(
            viewLifecycleOwner
        ) {
            it.consume { uri ->
                Log.i("$TAG Found rich content URI: $uri")
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val path = FileUtils.getFilePath(requireContext(), uri, false)
                        Log.i("$TAG Rich content URI [$uri] matching path is [$path]")
                        if (path != null) {
                            withContext(Dispatchers.Main) {
                                sendMessageViewModel.addAttachments(arrayListOf(path))
                            }
                        }
                    }
                }
            }
        }

        sharedViewModel.hideConversationEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.w("$TAG We were asked to close conversation, going back")
                goBack()
            }
        }

        sharedViewModel.textToShareFromIntent.observe(viewLifecycleOwner) { text ->
            if (text.isNotEmpty()) {
                Log.i("$TAG Found text to share from intent")
                sendMessageViewModel.textToSend.value = text

                sharedViewModel.textToShareFromIntent.value = ""
            }
        }

        sharedViewModel.filesToShareFromIntent.observe(viewLifecycleOwner) { files ->
            if (files.isNotEmpty()) {
                Log.i("$TAG Found [${files.size}] files to share from intent")
                for (path in files) {
                    sendMessageViewModel.addAttachments(arrayListOf(path))
                }

                sharedViewModel.filesToShareFromIntent.value = arrayListOf()
            }
        }

        sharedViewModel.forceRefreshConversationInfoEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Force refreshing conversation info")
                viewModel.refresh()
            }
        }

        sharedViewModel.forceRefreshConversationEvents.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Force refreshing messages list")
                viewModel.applyFilter()
            }
        }

        sharedViewModel.newChatMessageEphemeralLifetimeToSetEvent.observe(viewLifecycleOwner) {
            it.consume { ephemeralLifetime ->
                Log.i(
                    "$TAG Setting [$ephemeralLifetime] as new ephemeral lifetime for messages"
                )
                viewModel.updateEphemeralLifetime(ephemeralLifetime)
            }
        }

        binding.sendArea.messageToSend.setControlEnterListener(object :
                RichEditText.RichEditTextSendListener {
                override fun onControlEnterPressedAndReleased() {
                    Log.i("$TAG Detected left control + enter key presses, sending message")
                    sendMessageViewModel.sendMessage()
                }
            })

        binding.sendArea.messageToSend.addTextChangedListener(textObserver)

        scrollListener = object : RecyclerViewScrollListener(layoutManager, 5, false) {
            @UiThread
            override fun onLoadMore(totalItemsCount: Int) {
                if (viewModel.searchInProgress.value == false) {
                    viewModel.loadMoreData(totalItemsCount)
                }
            }

            @UiThread
            override fun onScrolledUp() {
                viewModel.isUserScrollingUp.value = true
            }

            @UiThread
            override fun onScrolledToEnd() {
                if (viewModel.isUserScrollingUp.value == true) {
                    viewModel.isUserScrollingUp.value = false
                    Log.i("$TAG Last message is visible, considering conversation as read")
                    viewModel.markAsRead()
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
    }

    override fun onResume() {
        super.onResume()

        viewModel.updateCurrentlyDisplayedConversation()

        // Wait for items to be displayed
        binding.eventsList
            .viewTreeObserver
            .addOnGlobalLayoutListener(globalLayoutObserver)

        if (::scrollListener.isInitialized) {
            binding.eventsList.addOnScrollListener(scrollListener)
        }

        try {
            adapter.registerAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to register data observer to adapter: $e")
        }

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback)
    }

    override fun onPause() {
        super.onPause()

        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null

        if (::scrollListener.isInitialized) {
            binding.eventsList.removeOnScrollListener(scrollListener)
        }
        binding.eventsList.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutObserver)

        coreContext.postOnCoreThread {
            bottomSheetReactionsModel?.destroy()
            bottomSheetDeliveryModel?.destroy()
            coreContext.notificationsManager.resetCurrentlyDisplayedChatRoomId()
        }

        try {
            adapter.unregisterAdapterDataObserver(dataObserver)
        } catch (e: IllegalStateException) {
            Log.e("$TAG Failed to unregister data observer to adapter: $e")
        }

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        currentChatMessageModelForBottomSheet?.isSelected?.value = false
        currentChatMessageModelForBottomSheet = null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EXPORT_FILE_AS_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK) {
                val filePath = filePathToExport
                if (filePath != null) {
                    data?.data?.also { documentUri ->
                        Log.i(
                            "$TAG Exported file [$filePath] should be stored in URI [$documentUri]"
                        )
                        viewModel.copyFileToUri(filePath, documentUri)
                        filePathToExport = null
                    }
                } else {
                    Log.e("$TAG No file path waiting to be exported!")
                }
            } else {
                Log.w("$TAG Export file activity result is [$resultCode], aborting")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun scrollToFirstUnreadMessageOrBottom() {
        if (adapter.itemCount == 0) {
            Log.w("$TAG No item in adapter yet, do not scroll")
            return
        }

        val recyclerView = binding.eventsList
        // Scroll to first unread message if any, unless we are already on it
        val firstUnreadMessagePosition = adapter.getFirstUnreadMessagePosition()
        val currentPosition = (recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        val indexToScrollTo = if (firstUnreadMessagePosition != -1 && firstUnreadMessagePosition != currentPosition) {
            firstUnreadMessagePosition
        } else {
            adapter.itemCount - 1
        }

        Log.i(
            "$TAG Scrolling to position $indexToScrollTo, first unread message is at $firstUnreadMessagePosition"
        )
        recyclerView.scrollToPosition(indexToScrollTo)

        val bottomReached = indexToScrollTo == adapter.itemCount - 1
        viewModel.isUserScrollingUp.value = !bottomReached
        if (bottomReached) {
            viewModel.markAsRead()
        } else {
            val firstUnread = adapter.currentList[firstUnreadMessagePosition]
            if (firstUnread.model is MessageModel) {
                Log.i("$TAG Marking only first message (to which user scrolled to) as read")
                firstUnread.model.markAsRead()
                viewModel.updateUnreadMessageCount()
                sharedViewModel.updateUnreadMessageCountForCurrentConversationEvent.postValue(
                    Event(true)
                )
            }
        }
    }

    private fun goToInfoFragment() {
        Log.i("TAG Navigating to info fragment")
        if (findNavController().currentDestination?.id == R.id.conversationFragment) {
            val action =
                ConversationFragmentDirections.actionConversationFragmentToConversationInfoFragment(
                    viewModel.conversationId,
                )
            findNavController().navigate(action)
        }
    }

    private fun goToFileViewer(fileModel: FileModel) {
        val path = fileModel.path
        Log.i("$TAG Navigating to file viewer fragment with path [$path]")
        val extension = FileUtils.getExtensionFromFileName(path)
        val mime = FileUtils.getMimeTypeFromExtension(extension)
        val mimeType = FileUtils.getMimeType(mime)
        if (mimeType == FileUtils.MimeType.Unknown && extension.contains("/")) {
            Log.w("$TAG Slash character found in 'extension' [$extension] deduced from file path [$path]; MIME type will be Unknown")
        } else {
            Log.i("$TAG Extension for file [$path] is [$extension], associated MIME type is [$mimeType]")
        }

        val bundle = Bundle()
        bundle.apply {
            putString("conversationId", viewModel.conversationId)
            putString("path", path)
            putBoolean("isEncrypted", fileModel.isEncrypted)
            putLong("timestamp", fileModel.fileCreationTimestamp)
            putString("originalPath", fileModel.originalPath)
            putBoolean("isFromEphemeralMessage", fileModel.isFromEphemeralMessage)
        }
        when (mimeType) {
            FileUtils.MimeType.Image, FileUtils.MimeType.Video, FileUtils.MimeType.Audio -> {
                bundle.putBoolean("isMedia", true)
                sharedViewModel.displayFileEvent.value = Event(bundle)
            }
            FileUtils.MimeType.Pdf, FileUtils.MimeType.PlainText -> {
                bundle.putBoolean("isMedia", false)
                sharedViewModel.displayFileEvent.value = Event(bundle)
            }
            else -> {
                bundle.putBoolean("isMedia", false)
                showOpenOrExportFileDialog(path, mime, bundle)
            }
        }
    }

    private fun showPopupMenu(view: View) {
        val popupView: ChatConversationPopupMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.chat_conversation_popup_menu,
            null,
            false
        )

        val popupWindow = PopupWindow(
            popupView.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.conversationMuted = viewModel.isMuted.value == true
        popupView.ephemeralMessagesAvailable = viewModel.isEndToEndEncrypted.value == true
        popupView.readOnlyConversation = viewModel.isReadOnly.value == true

        popupView.setGoToInfoClickListener {
            goToInfoFragment()
            popupWindow.dismiss()
        }

        popupView.setSearchClickListener {
            Log.i("$TAG Opening search bar")
            viewModel.openSearchBar()
            backPressedCallback.isEnabled = true

            popupWindow.dismiss()
        }

        popupView.setMuteClickListener {
            Log.i("$TAG Muting conversation")
            viewModel.mute()
            popupWindow.dismiss()
            sharedViewModel.forceRefreshDisplayedConversationEvent.value = Event(true)
        }

        popupView.setUnmuteClickListener {
            Log.i("$TAG Un-muting conversation")
            viewModel.unMute()
            popupWindow.dismiss()
            sharedViewModel.forceRefreshDisplayedConversationEvent.value = Event(true)
        }

        popupView.setConfigureEphemeralMessagesClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationFragment) {
                val currentValue = viewModel.ephemeralLifetime.value ?: 0L
                Log.i("$TAG Going to ephemeral lifetime fragment (currently [$currentValue])")
                val action =
                    ConversationFragmentDirections.actionConversationFragmentToConversationEphemeralLifetimeFragment(
                        currentValue
                    )
                findNavController().navigate(action)
            }
            popupWindow.dismiss()
        }

        popupView.setMediaClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationFragment) {
                val action =
                    ConversationFragmentDirections.actionConversationFragmentToConversationMediaListFragment(
                        viewModel.conversationId
                    )
                findNavController().navigate(action)
            }
            popupWindow.dismiss()
        }

        popupView.setDocumentsClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationFragment) {
                val action =
                    ConversationFragmentDirections.actionConversationFragmentToConversationDocumentsListFragment(
                        viewModel.conversationId
                    )
                findNavController().navigate(action)
            }
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }

    @UiThread
    private fun showChatMessageLongPressMenu(chatMessageModel: MessageModel) {
        binding.sendArea.messageToSend.hideKeyboard()
        Compatibility.setBlurRenderEffect(binding.coordinatorLayout)
        messageLongPressViewModel.setMessage(chatMessageModel)
        chatMessageModel.dismissLongPressMenuEvent.observe(viewLifecycleOwner) {
            it.consume {
                messageLongPressViewModel.dismiss()
            }
        }
        messageLongPressViewModel.visible.value = true
        backPressedCallback.isEnabled = true
    }

    @UiThread
    private fun showBottomSheetDialog(
        chatMessageModel: MessageModel,
        showDelivery: Boolean = false,
        showReactions: Boolean = false
    ) {
        viewModel.closeSearchBar()
        binding.sendArea.messageToSend.hideKeyboard()
        backPressedCallback.isEnabled = true

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.messageBottomSheet.setHandleClickedListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        if (binding.messageBottomSheet.bottomSheetList.adapter != bottomSheetAdapter) {
            binding.messageBottomSheet.bottomSheetList.adapter = bottomSheetAdapter
        }

        currentChatMessageModelForBottomSheet?.isSelected?.value = false
        currentChatMessageModelForBottomSheet = chatMessageModel
        chatMessageModel.isSelected.value = true

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Wait for previous bottom sheet to go away
                delay(200)

                withContext(Dispatchers.Main) {
                    if (showDelivery) {
                        prepareBottomSheetForDeliveryStatus(chatMessageModel)
                    } else if (showReactions) {
                        prepareBottomSheetForReactions(chatMessageModel)
                    }

                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    @UiThread
    private fun prepareBottomSheetForDeliveryStatus(chatMessageModel: MessageModel) {
        coreContext.postOnCoreThread {
            bottomSheetDeliveryModel?.destroy()

            val model = MessageDeliveryModel(chatMessageModel.chatMessage) { deliveryModel ->
                coreContext.postOnMainThread {
                    displayDeliveryStatuses(deliveryModel)
                }
            }
            bottomSheetDeliveryModel = model
        }
    }

    @UiThread
    private fun prepareBottomSheetForReactions(chatMessageModel: MessageModel) {
        coreContext.postOnCoreThread {
            bottomSheetReactionsModel?.destroy()

            val model = MessageReactionsModel(chatMessageModel.chatMessage) { reactionsModel ->
                coreContext.postOnMainThread {
                    if (reactionsModel.allReactions.value.orEmpty().isEmpty()) {
                        Log.i("$TAG No reaction to display, closing bottom sheet")
                        val bottomSheetBehavior = BottomSheetBehavior.from(
                            binding.messageBottomSheet.root
                        )
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    } else {
                        displayReactions(reactionsModel)
                    }
                }
            }
            bottomSheetReactionsModel = model
        }
    }

    @UiThread
    private fun displayDeliveryStatuses(model: MessageDeliveryModel) {
        val tabs = binding.messageBottomSheet.tabs
        tabs.removeAllTabs()
        tabs.addTab(
            tabs.newTab().setText(model.readLabel.value).setId(
                ChatMessage.State.Displayed.toInt()
            )
        )
        tabs.addTab(
            tabs.newTab().setText(
                model.receivedLabel.value
            ).setId(
                ChatMessage.State.DeliveredToUser.toInt()
            )
        )
        tabs.addTab(
            tabs.newTab().setText(model.sentLabel.value).setId(
                ChatMessage.State.Delivered.toInt()
            )
        )
        tabs.addTab(
            tabs.newTab().setText(
                model.errorLabel.value
            ).setId(
                ChatMessage.State.NotDelivered.toInt()
            )
        )

        tabs.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val state = tab?.id ?: ChatMessage.State.Displayed.toInt()
                bottomSheetAdapter.submitList(
                    model.computeListForState(ChatMessage.State.fromInt(state))
                )
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        val initialList = model.displayedModels
        bottomSheetAdapter.submitList(initialList)
        Log.i("$TAG Submitted [${initialList.size}] items for default delivery status list")
    }

    @UiThread
    private fun displayReactions(model: MessageReactionsModel) {
        val totalCount = model.allReactions.value.orEmpty().size
        val label = getString(R.string.message_reactions_info_all_title, totalCount.toString())

        val tabs = binding.messageBottomSheet.tabs
        tabs.removeAllTabs()
        tabs.addTab(
            tabs.newTab().setText(label).setId(0).setTag("")
        )

        var index = 1
        for (reaction in model.differentReactions) {
            val count = model.reactionsMap[reaction]
            val tabLabel = getString(
                R.string.message_reactions_info_emoji_title,
                reaction,
                count.toString()
            )
            tabs.addTab(
                tabs.newTab().setText(tabLabel).setId(index).setTag(reaction)
            )
            index += 1
        }

        tabs.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val filter = tab?.tag.toString()
                if (filter.isEmpty()) {
                    bottomSheetAdapter.submitList(model.allReactions.value.orEmpty())
                } else {
                    bottomSheetAdapter.submitList(model.filterReactions(filter))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        val initialList = model.allReactions.value.orEmpty()
        bottomSheetAdapter.submitList(initialList)
        Log.i("$TAG Submitted [${initialList.size}] items for default reactions list")
    }

    private fun showEndToEndEncryptionDetailsBottomSheet() {
        val e2eEncryptionDetailsBottomSheet = EndToEndEncryptionDetailsDialogFragment()
        e2eEncryptionDetailsBottomSheet.show(
            requireActivity().supportFragmentManager,
            EndToEndEncryptionDetailsDialogFragment.TAG
        )
        bottomSheetDialog = e2eEncryptionDetailsBottomSheet
    }

    private fun showUnsafeConversationDisabledDetailsBottomSheet() {
        val unsafeConversationDisabledDetailsBottomSheet = UnsafeConversationDisabledDetailsDialogFragment()
        unsafeConversationDisabledDetailsBottomSheet.show(
            requireActivity().supportFragmentManager,
            UnsafeConversationDisabledDetailsDialogFragment.TAG
        )
        bottomSheetDialog = unsafeConversationDisabledDetailsBottomSheet
    }

    private fun showOpenOrExportFileDialog(path: String, mime: String, bundle: Bundle) {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getOpenOrExportFileDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.alternativeChoiceEvent.observe(viewLifecycleOwner) {
            it.consume {
                openFileInAnotherApp(path, mime, bundle)
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                exportFile(path, mime)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showConfirmGroupCallPopup() {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getConfirmGroupCallDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.startGroupCall()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun openFileInAnotherApp(path: String, mime: String, bundle: Bundle) {
        val intent = Intent(Intent.ACTION_VIEW)
        val contentUri: Uri =
            FileUtils.getPublicFilePath(requireContext(), path)
        intent.setDataAndType(contentUri, mime)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            Log.i("$TAG Trying to start ACTION_VIEW intent for file [$path]")
            requireContext().startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
            Log.e("$TAG Can't open file [$path] in third party app: $anfe")
            showOpenAsPlainTextDialog(bundle)
        }
    }

    private fun showOpenAsPlainTextDialog(bundle: Bundle) {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getOpenAsPlainTextDialog(
            requireActivity(),
            model
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                sharedViewModel.displayFileEvent.value = Event(bundle)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun exportFile(path: String, mime: String) {
        filePathToExport = path

        Log.i("$TAG Asking where to save file [$filePathToExport] on device")
        val name = FileUtils.getNameFromFilePath(path)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        try {
            startActivityForResult(intent, EXPORT_FILE_AS_DOCUMENT)
        } catch (exception: ActivityNotFoundException) {
            Log.e("$TAG No activity found to handle intent ACTION_CREATE_DOCUMENT: $exception")
        }
    }

    private fun showHowToDeleteMessageDialog(model: MessageModel) {
        val canBeRetracted = messageLongPressViewModel.canBeRemotelyDeleted.value == true
        val dialogModel = MessageDeleteDialogModel(canBeRetracted)

        val dialog = DialogUtils.getHowToDeleteMessageDialog(
            requireActivity(),
            dialogModel
        )

        dialogModel.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        dialogModel.cancelEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        dialogModel.deleteLocallyEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Deleting chat message locally")
                viewModel.deleteChatMessage(model)
                dialog.dismiss()
            }
        }

        dialogModel.deleteForEveryoneEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Deleting chat message (content) for everyone")
                viewModel.deleteChatMessageForEveryone(model)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
