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
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.PopupWindow
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
import org.linphone.databinding.ChatBubbleLongPressMenuBinding
import org.linphone.databinding.ChatConversationFragmentBinding
import org.linphone.databinding.ChatConversationPopupMenuBinding
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.chat.ConversationScrollListener
import org.linphone.ui.main.chat.adapter.ConversationEventAdapter
import org.linphone.ui.main.chat.adapter.MessageBottomSheetAdapter
import org.linphone.ui.main.chat.model.MessageDeliveryModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.chat.model.MessageReactionsModel
import org.linphone.ui.main.chat.view.RichEditText
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel.Companion.SCROLLING_POSITION_NOT_SET
import org.linphone.ui.main.chat.viewmodel.SendMessageInConversationViewModel
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
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

@UiThread
class ConversationFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Conversation Fragment]"
    }

    private lateinit var binding: ChatConversationFragmentBinding

    private lateinit var viewModel: ConversationViewModel

    private lateinit var sendMessageViewModel: SendMessageInConversationViewModel

    private lateinit var adapter: ConversationEventAdapter

    private lateinit var bottomSheetAdapter: MessageBottomSheetAdapter

    private var messageLongPressDialog: Dialog? = null

    private val args: ConversationFragmentArgs by navArgs()

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { list ->
        if (!list.isNullOrEmpty()) {
            for (uri in list) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val path = FileUtils.getFilePath(requireContext(), uri, false)
                        Log.i("$TAG Picked file [$uri] matching path is [$path]")
                        if (path != null) {
                            withContext(Dispatchers.Main) {
                                sendMessageViewModel.addAttachment(path)
                            }
                        }
                    }
                }
            }
        } else {
            Log.w("$TAG No file picked")
        }
    }

    private var pendingImageCaptureFile: File? = null

    private val startCameraCapture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val path = pendingImageCaptureFile?.absolutePath
        if (path != null) {
            if (captured) {
                Log.i("$TAG Image was captured and saved in [$path]")
                sendMessageViewModel.addAttachment(path)
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

    private val dataObserver = object : AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (positionStart > 0) {
                adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
            }

            if (viewModel.isUserScrollingUp.value == true) {
                Log.i(
                    "$TAG [$itemCount] events have been loaded but user was scrolling up in conversation, do not scroll"
                )
                return
            }

            if (positionStart == 0 && adapter.itemCount == itemCount) {
                // First time we fill the list with messages
                Log.i(
                    "$TAG [$itemCount] events have been loaded, scrolling to first unread message"
                )
            } else {
                Log.i(
                    "$TAG [$itemCount] new events have been loaded, scrolling to first unread message"
                )
            }
            scrollToFirstUnreadMessageOrBottom(false)
        }
    }

    private val textObserver = object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        }

        override fun afterTextChanged(p0: Editable?) {
            sendMessageViewModel.isParticipantsListOpen.value = false

            val split = p0.toString().split(" ")
            for (part in split) {
                if (part == "@") {
                    Log.i("$TAG '@' found, opening participants list")
                    sendMessageViewModel.isParticipantsListOpen.value = true
                }
            }
        }
    }

    private lateinit var scrollListener: ConversationScrollListener

    private lateinit var headerItemDecoration: RecyclerViewHeaderDecoration

    private val listItemTouchListener = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(
            rv: RecyclerView,
            e: MotionEvent
        ): Boolean {
            // Following code is only to detect click on header at position 0
            if (::headerItemDecoration.isInitialized) {
                if ((rv.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() == 0) {
                    if (e.y >= 0 && e.y <= headerItemDecoration.getDecorationHeight(0)) {
                        showEndToEndEncryptionDetailsBottomSheet()
                        return true
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
            Log.i("$TAG Bottom sheet state is [$newState]")
            if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                currentChatMessageModelForBottomSheet?.isSelected?.value = false
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) { }
    }

    private var bottomSheetDeliveryModel: MessageDeliveryModel? = null

    private var bottomSheetReactionsModel: MessageReactionsModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationEventAdapter()
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
        sharedViewModel.closeSlidingPaneEvent.value = Event(true)

        // If not done this fragment won't be paused, which will cause us issues
        val action = ConversationFragmentDirections.actionConversationFragmentToEmptyFragment()
        findNavController().navigate(action)
        return true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]
        sendMessageViewModel = ViewModelProvider(this)[SendMessageInConversationViewModel::class.java]

        binding.viewModel = viewModel
        binding.sendMessageViewModel = sendMessageViewModel

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

        if (binding.eventsList.adapter != adapter) {
            binding.eventsList.adapter = adapter
        }

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
                    sendMessageViewModel.replyToMessage(chatMessageModel)
                    // Open keyboard & focus edit text
                    binding.sendArea.messageToSend.showKeyboard()
                } else {
                    Log.e(
                        "$TAG Can't reply, failed to get a ChatMessageModel from adapter item #[$index]"
                    )
                }
            }
        }
        RecyclerViewSwipeUtils(callbacks).attachToRecyclerView(binding.eventsList)

        val localSipUri = args.localSipUri
        val remoteSipUri = args.remoteSipUri
        Log.i(
            "$TAG Looking up for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
        )
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, localSipUri, remoteSipUri)

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (!found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find conversation, going back")
                        goBack()
                        val message = getString(R.string.toast_cant_find_conversation_to_display)
                        (requireActivity() as MainActivity).showRedToast(message, R.drawable.x)
                    }
                } else {
                    sendMessageViewModel.configureChatRoom(viewModel.chatRoom)

                    if (viewModel.isEndToEndEncrypted.value == true) {
                        headerItemDecoration = RecyclerViewHeaderDecoration(
                            requireContext(),
                            adapter,
                            false
                        )
                        binding.eventsList.addItemDecoration(headerItemDecoration)
                        binding.eventsList.addOnItemTouchListener(listItemTouchListener)
                    }

                    // Wait for chat room to be ready before trying to forward a message in it
                    sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner) { event ->
                        event.consume { toForward ->
                            Log.i("$TAG Found message to forward")
                            if (viewModel.isReadOnly.value == true || viewModel.isDisabledBecauseNotSecured.value == true) {
                                Log.w(
                                    "$TAG Can't forward message in this conversation as it is read only"
                                )
                            } else {
                                sendMessageViewModel.forwardMessage(toForward)
                            }
                        }
                    }
                }
            }
        }

        viewModel.events.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            Log.i("$TAG Events (messages) list updated with [${items.size}] items")

            (view.parent as? ViewGroup)?.doOnPreDraw {
                sharedViewModel.openSlidingPaneEvent.value = Event(true)
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
                } else {
                    val originalMessage = adapter.currentList.find { eventLog ->
                        !eventLog.isEvent && (eventLog.model as MessageModel).id == repliedMessageId
                    }
                    if (originalMessage != null) {
                        val position = adapter.currentList.indexOf(originalMessage)
                        Log.i("$TAG Scrolling to position [$position]")
                        binding.eventsList.scrollToPosition(position)
                    } else {
                        Log.w("$TAG Failed to find matching message in adapter's items!")
                    }
                }
            }
        }

        binding.setShowMenuClickListener {
            showPopupMenu(binding.showMenu)
        }

        binding.setOpenFilePickerClickListener {
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
            scrollToFirstUnreadMessageOrBottom(true)
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
                Log.w("$TAG Asking for RECORD_AUDIO permission")
                requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        sendMessageViewModel.showRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as MainActivity).showRedToast(message, icon)
            }
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            viewModel.applyFilter(filter.trim())
        }

        viewModel.focusSearchBarEvent.observe(viewLifecycleOwner) {
            it.consume { show ->
                if (show) {
                    // To automatically open keyboard
                    binding.search.showKeyboard()
                } else {
                    binding.search.hideKeyboard()
                }
            }
        }

        viewModel.fileToDisplayEvent.observe(viewLifecycleOwner) {
            it.consume { file ->
                if (messageLongPressDialog != null) return@consume
                Log.i("$TAG User clicked on file [$file], let's display it in file viewer")
                goToFileViewer(file)
            }
        }

        viewModel.conferenceToJoinEvent.observe(viewLifecycleOwner) {
            it.consume { conferenceUri ->
                if (messageLongPressDialog != null) return@consume
                Log.i("$TAG Requesting to go to waiting room for conference URI [$conferenceUri]")
                sharedViewModel.goToMeetingWaitingRoomEvent.value = Event(conferenceUri)
            }
        }

        viewModel.openWebBrowserEvent.observe(viewLifecycleOwner) {
            it.consume { url ->
                if (messageLongPressDialog != null) return@consume
                Log.i("$TAG Requesting to open web browser on page [$url]")
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Log.e(
                        "$TAG Can't start ACTION_VIEW intent for URL [$url]: $e"
                    )
                }
            }
        }

        viewModel.contactToDisplayEvent.observe(viewLifecycleOwner) {
            it.consume { friendRefKey ->
                if (messageLongPressDialog != null) return@consume
                Log.i("$TAG Navigating to contact with ref key [$friendRefKey]")
                sharedViewModel.navigateToContactsEvent.value = Event(true)
                sharedViewModel.showContactEvent.value = Event(friendRefKey)
            }
        }

        viewModel.isGroup.observe(viewLifecycleOwner) { group ->
            if (group) {
                Log.i("$TAG Adding text observer to message sending area")
                binding.sendArea.messageToSend.addTextChangedListener(textObserver)
            }
        }

        viewModel.showRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as MainActivity).showRedToast(message, icon)
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
                                sendMessageViewModel.addAttachment(path)
                            }
                        }
                    }
                }
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
                    sendMessageViewModel.addAttachment(path)
                }

                sharedViewModel.filesToShareFromIntent.value = arrayListOf()
            }
        }

        sharedViewModel.forceRefreshConversationInfo.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Force refreshing conversation info")
                viewModel.refresh()
            }
        }

        sharedViewModel.forceRefreshConversationEvents.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Force refreshing messages list")
                viewModel.applyFilter("")
            }
        }

        binding.sendArea.messageToSend.setControlEnterListener(object :
                RichEditText.RichEditTextSendListener {
                override fun onControlEnterPressedAndReleased() {
                    Log.i("$TAG Detected left control + enter key presses, sending message")
                    sendMessageViewModel.sendMessage()
                }
            })

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            sendMessageViewModel.isKeyboardOpen.value = keyboardVisible
            if (keyboardVisible) {
                sendMessageViewModel.isEmojiPickerOpen.value = false
            }
        }

        scrollListener = object : ConversationScrollListener(layoutManager) {
            @UiThread
            override fun onLoadMore(totalItemsCount: Int) {
                viewModel.loadMoreData(totalItemsCount)
            }

            @UiThread
            override fun onScrolledUp() {
                viewModel.isUserScrollingUp.value = true
            }

            @UiThread
            override fun onScrolledToEnd() {
                viewModel.isUserScrollingUp.value = false
                Log.i("$TAG Last message is visible, considering conversation as read")
                viewModel.markAsRead()
            }
        }
        binding.eventsList.addOnScrollListener(scrollListener)
    }

    override fun onResume() {
        super.onResume()

        viewModel.updateCurrentlyDisplayedConversation()

        if (viewModel.scrollingPosition != SCROLLING_POSITION_NOT_SET) {
            Log.d("$TAG Restoring previous scrolling position: ${viewModel.scrollingPosition}")
            binding.eventsList.scrollToPosition(viewModel.scrollingPosition)
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

        if (viewModel.isUserScrollingUp.value == true) {
            val layoutManager = binding.eventsList.layoutManager as LinearLayoutManager
            viewModel.scrollingPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            Log.d("$TAG Storing current scrolling position: ${viewModel.scrollingPosition}")
        } else {
            viewModel.scrollingPosition = SCROLLING_POSITION_NOT_SET
        }

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        currentChatMessageModelForBottomSheet = null
    }

    private fun scrollToFirstUnreadMessageOrBottom(smooth: Boolean) {
        if (adapter.itemCount == 0) return

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
        if (smooth) {
            recyclerView.smoothScrollToPosition(indexToScrollTo)
        } else {
            recyclerView.scrollToPosition(indexToScrollTo)
        }
    }

    private fun goToInfoFragment() {
        Log.i("TAG Navigating to info fragment")
        if (findNavController().currentDestination?.id == R.id.conversationFragment) {
            val action =
                ConversationFragmentDirections.actionConversationFragmentToConversationInfoFragment(
                    viewModel.localSipUri,
                    viewModel.remoteSipUri
                )
            findNavController().navigate(action)
        }
    }

    private fun goToFileViewer(path: String) {
        Log.i("$TAG Navigating to file viewer fragment with path [$path]")
        val extension = FileUtils.getExtensionFromFileName(path)
        val mime = FileUtils.getMimeTypeFromExtension(extension)

        val bundle = Bundle()
        bundle.apply {
            putString("localSipUri", viewModel.localSipUri)
            putString("remoteSipUri", viewModel.remoteSipUri)
            putString("path", path)
        }
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Image, FileUtils.MimeType.Video -> {
                bundle.putBoolean("isMedia", true)
                sharedViewModel.displayFileEvent.value = Event(bundle)
            }
            FileUtils.MimeType.Pdf, FileUtils.MimeType.PlainText -> {
                bundle.putBoolean("isMedia", false)
                sharedViewModel.displayFileEvent.value = Event(bundle)
            }
            else -> {
                val intent = Intent(Intent.ACTION_VIEW)
                val contentUri: Uri =
                    FileUtils.getPublicFilePath(requireContext(), path)
                intent.setDataAndType(contentUri, "file/$mime")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    requireContext().startActivity(intent)
                } catch (anfe: ActivityNotFoundException) {
                    Log.e("$TAG Can't open file [$path] in third party app: $anfe")
                    val message = getString(
                        R.string.toast_no_app_registered_to_handle_content_type_error
                    )
                    val icon = R.drawable.file
                    (requireActivity() as MainActivity).showRedToast(message, icon)
                }
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
        popupView.ephemeralMessagesAvailable = viewModel.isEndToEndEncrypted.value == true || viewModel.isGroup.value == true
        popupView.readOnlyConversation = viewModel.isReadOnly.value == true

        popupView.setGoToInfoClickListener {
            goToInfoFragment()
            popupWindow.dismiss()
        }

        popupView.setSearchClickListener {
            Log.i("$TAG Opening search bar")
            viewModel.openSearchBar()
            popupWindow.dismiss()
        }

        popupView.setMuteClickListener {
            Log.i("$TAG Muting conversation")
            viewModel.mute()
            popupWindow.dismiss()
        }

        popupView.setUnmuteClickListener {
            Log.i("$TAG Un-muting conversation")
            viewModel.unmute()
            popupWindow.dismiss()
        }

        popupView.setConfigureEphemeralMessagesClickListener {
            // TODO: go to configure ephemeral messages
            popupWindow.dismiss()
        }

        popupView.setMediasClickListener {
            if (findNavController().currentDestination?.id == R.id.conversationFragment) {
                val action =
                    ConversationFragmentDirections.actionConversationFragmentToConversationMediaListFragment(
                        localSipUri = viewModel.localSipUri,
                        remoteSipUri = viewModel.remoteSipUri
                    )
                findNavController().navigate(action)
            }
            popupWindow.dismiss()
        }

        // Elevation is for showing a shadow around the popup
        popupWindow.elevation = 20f
        popupWindow.showAsDropDown(view, 0, 0, Gravity.BOTTOM)
    }

    private fun dismissDialog() {
        messageLongPressDialog?.dismiss()
        messageLongPressDialog = null
    }

    private fun showChatMessageLongPressMenu(chatMessageModel: MessageModel) {
        Compatibility.setBlurRenderEffect(binding.root)

        val dialog = Dialog(requireContext(), R.style.Theme_LinphoneDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout: ChatBubbleLongPressMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.chat_bubble_long_press_menu,
            null,
            false
        )

        layout.root.setOnClickListener {
            dismissDialog()
        }

        layout.setDeleteClickListener {
            Log.i("$TAG Deleting message")
            viewModel.deleteChatMessage(chatMessageModel)
            dismissDialog()
        }

        layout.setCopyClickListener {
            Log.i("$TAG Copying message text into clipboard")
            val text = chatMessageModel.text.value?.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val label = "Message"
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

            dismissDialog()
        }

        layout.setPickEmojiClickListener {
            Log.i("$TAG Opening emoji-picker for reaction")
            val emojiSheetBehavior = BottomSheetBehavior.from(layout.emojiPickerBottomSheet.root)
            emojiSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        layout.setResendClickListener {
            Log.i("$TAG Re-sending message in error state")
            chatMessageModel.resend()
            dismissDialog()
        }

        layout.setForwardClickListener {
            Log.i("$TAG Forwarding message, going back to conversations list")
            // Remove observer before setting the message to forward
            // as we don't want to forward it in this chat room
            sharedViewModel.messageToForwardEvent.removeObservers(viewLifecycleOwner)
            sharedViewModel.messageToForwardEvent.postValue(Event(chatMessageModel))
            dismissDialog()
            goBack()
        }

        layout.setReplyClickListener {
            Log.i("$TAG Updating sending area to reply to selected message")
            sendMessageViewModel.replyToMessage(chatMessageModel)
            dismissDialog()

            // Open keyboard & focus edit text
            binding.sendArea.messageToSend.showKeyboard()
        }

        layout.model = chatMessageModel
        chatMessageModel.dismissLongPressMenuEvent.observe(viewLifecycleOwner) {
            dismissDialog()
        }

        dialog.setContentView(layout.root)
        dialog.setOnDismissListener {
            Compatibility.removeBlurRenderEffect(binding.root)
        }

        dialog.window
            ?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        val d: Drawable = ColorDrawable(
            requireContext().getColor(R.color.grey_300)
        )
        d.alpha = 102
        dialog.window?.setBackgroundDrawable(d)
        dialog.show()
        messageLongPressDialog = dialog
    }

    @UiThread
    private fun showBottomSheetDialog(
        chatMessageModel: MessageModel,
        showDelivery: Boolean = false,
        showReactions: Boolean = false
    ) {
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
                    if (reactionsModel.allReactions.isEmpty()) {
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
        val totalCount = model.allReactions.size
        val label = getString(R.string.message_reactions_info_all_title, totalCount.toString())

        val tabs = binding.messageBottomSheet.tabs
        tabs.removeAllTabs()
        tabs.addTab(
            tabs.newTab().setText(label).setId(0).setTag("")
        )

        var index = 1
        for (reaction in model.differentReactions.value.orEmpty()) {
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
                    bottomSheetAdapter.submitList(model.allReactions)
                } else {
                    bottomSheetAdapter.submitList(model.filterReactions(filter))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        val initialList = model.allReactions
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
}
