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
import android.graphics.Rect
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
import androidx.annotation.UiThread
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.math.max
import kotlin.math.min
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatConversationFragmentBinding
import org.linphone.databinding.ChatConversationLongPressMenuBinding
import org.linphone.ui.main.chat.adapter.ConversationEventAdapter
import org.linphone.ui.main.chat.model.ChatMessageModel
import org.linphone.ui.main.chat.viewmodel.ConversationViewModel
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.utils.AppUtils
import org.linphone.utils.Event

@UiThread
class ConversationFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Conversation Fragment]"
    }

    private lateinit var binding: ChatConversationFragmentBinding

    private lateinit var viewModel: ConversationViewModel

    private val args: ConversationFragmentArgs by navArgs()

    private lateinit var adapter: ConversationEventAdapter

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

        val layoutManager = LinearLayoutManager(requireContext())
        binding.eventsList.layoutManager = layoutManager

        adapter.chatMessageLongPressEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                showChatMessageLongPressMenu(pair.first, pair.second)
            }
        }

        viewModel.events.observe(viewLifecycleOwner) { items ->
            val currentCount = adapter.itemCount
            adapter.submitList(items)
            Log.i("$TAG Events (messages) list updated with [${items.size}] items")

            if (currentCount == 0 && items.isNotEmpty()) {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                    sharedViewModel.openSlidingPaneEvent.value = Event(true)
                }
            }

            if (currentCount < items.size) {
                binding.eventsList.scrollToPosition(items.size - 1)
            }
        }

        val emojisBottomSheetBehavior = BottomSheetBehavior.from(binding.emojiPicker)
        emojisBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        emojisBottomSheetBehavior.isDraggable = false // To allow scrolling through the emojis

        binding.setOpenEmojiPickerClickListener {
            /*val state = emojisBottomSheetBehavior.state
            if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                emojisBottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                if (binding.emojiPicker.visibility == View.GONE) {
                    binding.emojiPicker.visibility = View.VISIBLE
                }
            } else {
                emojisBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }*/
        }
    }

    private fun showChatMessageLongPressMenu(chatMessageModel: ChatMessageModel, yPosition: Int) {
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

        layout.model = chatMessageModel
        chatMessageModel.dismissLongPressMenuEvent.observe(viewLifecycleOwner) {
            dialog.dismiss()
        }

        val screenY = yPosition - AppUtils.getDimension(
            R.dimen.chat_bubble_long_press_menu_bubble_offset
        )
        val rect = Rect()
        binding.root.getGlobalVisibleRect(rect)
        val height = rect.height()
        val percent = ((screenY * 100) / height)
        // To prevent bubble from being behind the bottom actions or the emojis to be out of the screen
        val guideline = min(max(0.1f, (percent / 100)), 0.4f) // value must be between 0 and 1

        val constraintLayout = layout.constraintLayout
        val set = ConstraintSet()
        set.clone(constraintLayout)
        set.setGuidelinePercent(R.id.guideline, guideline)
        set.applyTo(constraintLayout)

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
}
