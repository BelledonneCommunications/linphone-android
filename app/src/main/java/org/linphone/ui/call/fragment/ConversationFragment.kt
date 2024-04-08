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
package org.linphone.ui.call.fragment

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
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
import org.linphone.ui.call.CallActivity
import org.linphone.ui.main.chat.ConversationScrollListener
import org.linphone.ui.main.chat.adapter.ConversationEventAdapter
import org.linphone.ui.main.chat.adapter.MessageBottomSheetAdapter
import org.linphone.ui.main.chat.fragment.ConversationFragmentArgs
import org.linphone.ui.main.chat.fragment.EndToEndEncryptionDetailsDialogFragment
import org.linphone.ui.main.chat.model.MessageDeliveryModel
import org.linphone.ui.main.chat.model.MessageModel
import org.linphone.ui.main.chat.model.MessageReactionsModel
import org.linphone.ui.main.chat.view.RichEditText
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel
import org.linphone.ui.main.chat.viewmodel.SendMessageInConversationViewModel
import org.linphone.utils.RecyclerViewHeaderDecoration
import org.linphone.utils.RecyclerViewSwipeUtils
import org.linphone.utils.RecyclerViewSwipeUtilsCallback
import org.linphone.utils.addCharacterAtPosition
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard

class ConversationFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[In-call Conversation Fragment]"
    }

    private lateinit var binding: ChatConversationFragmentBinding

    private lateinit var viewModel: ConversationViewModel

    private lateinit var sendMessageViewModel: SendMessageInConversationViewModel

    private lateinit var adapter: ConversationEventAdapter

    private lateinit var bottomSheetAdapter: MessageBottomSheetAdapter

    private var messageLongPressDialog: Dialog? = null

    private val args: ConversationFragmentArgs by navArgs()

    private var bottomSheetDialog: BottomSheetDialogFragment? = null

    private val dataObserver = object : RecyclerView.AdapterDataObserver() {
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
                    "$TAG [$itemCount] events have been loaded"
                )
            } else {
                Log.i(
                    "$TAG [$itemCount] new events have been loaded, scrolling to first unread message"
                )
                scrollToFirstUnreadMessageOrBottom()
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
                if (e.action == MotionEvent.ACTION_UP) {
                    if ((rv.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() == 0) {
                        if (e.y >= 0 && e.y <= headerItemDecoration.getDecorationHeight(0)) {
                            showEndToEndEncryptionDetailsBottomSheet()
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

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]
        sendMessageViewModel =
            ViewModelProvider(this)[SendMessageInConversationViewModel::class.java]

        viewModel.isInCallConversation.value = true
        binding.viewModel = viewModel

        sendMessageViewModel.isInCallConversation.value = true
        binding.sendMessageViewModel = sendMessageViewModel

        binding.setBackClickListener {
            findNavController().popBackStack()
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
        viewModel.findChatRoom(null, localSipUri, remoteSipUri)

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (!found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find conversation, going back")
                        findNavController().popBackStack()
                        val message = getString(R.string.toast_cant_find_conversation_to_display)
                        (requireActivity() as CallActivity).showRedToast(message, R.drawable.x)
                    }
                } else {
                    sendMessageViewModel.configureChatRoom(viewModel.chatRoom)
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                }
            }
        }

        viewModel.updateEvents.observe(viewLifecycleOwner) {
            val items = viewModel.eventsList
            adapter.submitList(items)
            Log.i("$TAG Events (messages) list updated, contains [${items.size}] items")
        }

        viewModel.isEndToEndEncrypted.observe(viewLifecycleOwner) { encrypted ->
            if (encrypted) {
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

        binding.setScrollToBottomClickListener {
            scrollToFirstUnreadMessageOrBottom()
        }

        binding.setEndToEndEncryptedEventClickListener {
            showEndToEndEncryptionDetailsBottomSheet()
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

        sendMessageViewModel.showRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as CallActivity).showRedToast(message, icon)
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

        viewModel.showRedToastEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                (requireActivity() as CallActivity).showRedToast(message, icon)
            }
        }

        viewModel.messageDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                val message = getString(R.string.conversation_message_deleted_toast)
                val icon = R.drawable.x
                (requireActivity() as CallActivity).showGreenToast(message, icon)
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
                if (viewModel.isUserScrollingUp.value == true) {
                    viewModel.isUserScrollingUp.value = false
                    Log.i("$TAG Last message is visible, considering conversation as read")
                    viewModel.markAsRead()
                }
            }
        }
        binding.eventsList.addOnScrollListener(scrollListener)
    }

    override fun onResume() {
        super.onResume()

        viewModel.updateCurrentlyDisplayedConversation()

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

        val bottomSheetBehavior = BottomSheetBehavior.from(binding.messageBottomSheet.root)
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        currentChatMessageModelForBottomSheet = null
    }

    private fun scrollToFirstUnreadMessageOrBottom() {
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
        recyclerView.scrollToPosition(indexToScrollTo)

        if (indexToScrollTo == adapter.itemCount - 1) {
            viewModel.isUserScrollingUp.postValue(false)
            viewModel.markAsRead()
        }
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
        layout.hideForward = true

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

        tabs.setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
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

        tabs.setOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
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
