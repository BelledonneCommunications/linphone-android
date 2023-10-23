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

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.ChatMessage
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatConversationFragmentBinding
import org.linphone.databinding.ChatConversationLongPressMenuBinding
import org.linphone.ui.main.chat.adapter.ChatMessageDeliveryAdapter
import org.linphone.ui.main.chat.adapter.ConversationEventAdapter
import org.linphone.ui.main.chat.model.ChatMessageDeliveryModel
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.hideKeyboard
import org.linphone.utils.setKeyboardInsetListener
import org.linphone.utils.showKeyboard

@UiThread
class ConversationFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Conversation Fragment]"
    }

    private lateinit var binding: ChatConversationFragmentBinding

    private lateinit var viewModel: ConversationViewModel

    private val args: ConversationFragmentArgs by navArgs()

    private lateinit var adapter: ConversationEventAdapter

    private lateinit var deliveryAdapter: ChatMessageDeliveryAdapter

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { list ->
        if (!list.isNullOrEmpty()) {
            for (file in list) {
                Log.i("$TAG Picked file [$file]")
            }
        } else {
            Log.w("$TAG No file picked")
        }
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
        // If not done, when going back to ConversationsFragment this fragment will be created again
        return findNavController().popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // This fragment is displayed in a SlidingPane "child" area
        isSlidingPaneChild = true

        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]
        binding.viewModel = viewModel

        binding.setBackClickListener {
            goBack()
        }

        sharedViewModel.isSlidingPaneSlideable.observe(viewLifecycleOwner) { slideable ->
            viewModel.showBackButton.value = slideable
        }

        val localSipUri = args.localSipUri
        val remoteSipUri = args.remoteSipUri
        Log.i(
            "$TAG Looking up for conversation with local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
        )
        viewModel.findChatRoom(localSipUri, remoteSipUri)

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    Log.i(
                        "$TAG Found matching chat room for local SIP URI [$localSipUri] and remote SIP URI [$remoteSipUri]"
                    )
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                        sharedViewModel.openSlidingPaneEvent.value = Event(true)
                    }
                } else {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        Log.e("$TAG Failed to find chat room, going back")
                        goBack()
                    }
                }
            }
        }

        adapter = ConversationEventAdapter(viewLifecycleOwner)
        binding.eventsList.setHasFixedSize(true)
        binding.eventsList.adapter = adapter

        deliveryAdapter = ChatMessageDeliveryAdapter(viewLifecycleOwner)
        binding.messageDelivery.deliveryList.setHasFixedSize(true)
        binding.messageDelivery.deliveryList.adapter = deliveryAdapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.eventsList.layoutManager = layoutManager

        val deliveryLayoutManager = LinearLayoutManager(requireContext())
        binding.messageDelivery.deliveryList.layoutManager = deliveryLayoutManager

        adapter.chatMessageLongPressEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                showChatMessageLongPressMenu(model)
            }
        }

        viewModel.events.observe(viewLifecycleOwner) { items ->
            val currentCount = adapter.itemCount
            adapter.submitList(items)
            Log.i("$TAG Events (messages) list updated with [${items.size}] items")

            if (currentCount < items.size) {
                binding.eventsList.scrollToPosition(items.size - 1)
            }
        }

        val emojisBottomSheetBehavior = BottomSheetBehavior.from(binding.sendArea.root)
        emojisBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        emojisBottomSheetBehavior.isDraggable = false // To allow scrolling through the emojis

        adapter.showDeliveryForChatMessageModelEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                if (viewModel.isGroup.value == true) {
                    showDeliveryBottomSheetDialog(model)
                } else {
                    Log.w("$TAG Conversation is not a group, not showing delivery bottom sheet")
                }
            }
        }

        binding.setOpenFilePickerClickListener {
            Log.i("$TAG Opening media picker")
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }

        binding.setGoToInfoClickListener {
            val action = ConversationFragmentDirections.actionConversationFragmentToConversationInfoFragment(
                localSipUri,
                remoteSipUri
            )
            findNavController().navigate(action)
        }

        viewModel.searchFilter.observe(viewLifecycleOwner) { filter ->
            viewModel.applyFilter(filter.trim())
        }

        viewModel.requestKeyboardHidingEvent.observe(viewLifecycleOwner) {
            it.consume {
                binding.search.hideKeyboard()
            }
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

        binding.root.setKeyboardInsetListener { keyboardVisible ->
            if (keyboardVisible) {
                viewModel.isEmojiPickerOpen.value = false
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val id = LinphoneUtils.getChatRoomId(args.localSipUri, args.remoteSipUri)
        Log.i("$TAG Asking notifications manager not to notify chat messages for chat room [$id]")
        coreContext.notificationsManager.setCurrentlyDisplayedChatRoomId(id)
    }

    override fun onPause() {
        coreContext.notificationsManager.resetCurrentlyDisplayedChatRoomId()

        super.onPause()
    }

    private fun showChatMessageLongPressMenu(chatMessageModel: ChatMessageModel) {
        // TODO: handle backward compat for blurring
        val blurEffect = RenderEffect.createBlurEffect(16F, 16F, Shader.TileMode.MIRROR)
        binding.root.setRenderEffect(blurEffect)

        val dialog = Dialog(requireContext(), R.style.Theme_LinphoneDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val layout: ChatConversationLongPressMenuBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.chat_conversation_long_press_menu,
            null,
            false
        )

        layout.root.setOnClickListener {
            dialog.dismiss()
        }

        layout.setDeleteClickListener {
            viewModel.deleteChatMessage(chatMessageModel)
            dialog.dismiss()
        }

        layout.setCopyClickListener {
            val text = chatMessageModel.text
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val label = "Message"
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

            dialog.dismiss()
        }

        layout.setPickEmojiClickListener {
            layout.emojiPicker.visibility = View.VISIBLE
        }

        layout.model = chatMessageModel
        chatMessageModel.dismissLongPressMenuEvent.observe(viewLifecycleOwner) {
            dialog.dismiss()
        }

        dialog.setContentView(layout.root)
        dialog.setOnDismissListener {
            binding.root.setRenderEffect(null)
        }

        dialog.window
            ?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        val d: Drawable = ColorDrawable(
            AppUtils.getColor(R.color.gray_300)
        )
        d.alpha = 102
        dialog.window?.setBackgroundDrawable(d)
        dialog.show()
    }

    @UiThread
    private fun showDeliveryBottomSheetDialog(chatMessageModel: ChatMessageModel) {
        val deliveryBottomSheetBehavior = BottomSheetBehavior.from(binding.messageDelivery.root)
        deliveryBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.messageDelivery.setHandleClickedListener {
            deliveryBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                delay(200)

                withContext(Dispatchers.Main) {
                    coreContext.postOnCoreThread {
                        val model = ChatMessageDeliveryModel(chatMessageModel.chatMessage)

                        coreContext.postOnMainThread {
                            model.deliveryModels.observe(viewLifecycleOwner) {
                                deliveryAdapter.submitList(it)
                            }

                            binding.messageDelivery.tabs.removeAllTabs()
                            binding.messageDelivery.tabs.addTab(
                                binding.messageDelivery.tabs.newTab().setText(model.readLabel.value).setId(
                                    ChatMessage.State.Displayed.toInt()
                                )
                            )
                            binding.messageDelivery.tabs.addTab(
                                binding.messageDelivery.tabs.newTab().setText(
                                    model.receivedLabel.value
                                ).setId(
                                    ChatMessage.State.DeliveredToUser.toInt()
                                )
                            )
                            binding.messageDelivery.tabs.addTab(
                                binding.messageDelivery.tabs.newTab().setText(model.sentLabel.value).setId(
                                    ChatMessage.State.Delivered.toInt()
                                )
                            )
                            binding.messageDelivery.tabs.addTab(
                                binding.messageDelivery.tabs.newTab().setText(
                                    model.errorLabel.value
                                ).setId(
                                    ChatMessage.State.NotDelivered.toInt()
                                )
                            )

                            binding.messageDelivery.tabs.setOnTabSelectedListener(object : OnTabSelectedListener {
                                override fun onTabSelected(tab: TabLayout.Tab?) {
                                    val state = tab?.id ?: ChatMessage.State.Displayed.toInt()
                                    model.computeListForState(ChatMessage.State.fromInt(state))
                                }

                                override fun onTabUnselected(tab: TabLayout.Tab?) {
                                }

                                override fun onTabReselected(tab: TabLayout.Tab?) {
                                }
                            })

                            binding.messageDelivery.model = model

                            binding.messageDelivery.root.visibility = View.VISIBLE
                            deliveryBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    }
                }
            }
        }
    }
}
