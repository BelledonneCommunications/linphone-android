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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.*
import android.webkit.MimeTypeMap
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.main.MainActivity
import org.linphone.activities.main.chat.ChatScrollListener
import org.linphone.activities.main.chat.adapters.ChatMessagesListAdapter
import org.linphone.activities.main.chat.viewmodels.*
import org.linphone.activities.main.fragments.MasterFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatRoomDetailFragmentBinding
import org.linphone.utils.*
import org.linphone.utils.Event

class DetailChatRoomFragment : MasterFragment() {
    private lateinit var binding: ChatRoomDetailFragmentBinding
    private lateinit var viewModel: ChatRoomViewModel
    private lateinit var chatSendingViewModel: ChatMessageSendingViewModel
    private lateinit var listViewModel: ChatMessagesListViewModel
    private lateinit var adapter: ChatMessagesListAdapter
    private lateinit var sharedViewModel: SharedMainViewModel
    private var chatRoomAddress: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatRoomDetailFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val chatRoom = sharedViewModel.selectedChatRoom.value
        chatRoom ?: return
        chatRoomAddress = chatRoom.peerAddress.asStringUriOnly()

        viewModel = ViewModelProvider(
            this,
            ChatRoomViewModelFactory(chatRoom)
        )[ChatRoomViewModel::class.java]
        binding.viewModel = viewModel
        val activity = requireActivity()
        viewModel.isInWindowedMode.value = activity as? MainActivity == null

        chatSendingViewModel = ViewModelProvider(
            this,
            ChatMessageSendingViewModelFactory(chatRoom)
        )[ChatMessageSendingViewModel::class.java]
        binding.chatSendingViewModel = chatSendingViewModel

        listViewModel = ViewModelProvider(
            this,
            ChatMessagesListViewModelFactory(chatRoom)
        )[ChatMessagesListViewModel::class.java]

        adapter = ChatMessagesListAdapter(listSelectionViewModel)
        // SubmitList is done on a background thread
        // We need this adapter data observer to know when to scroll
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == adapter.itemCount - 1) {
                    adapter.notifyItemChanged(positionStart - 1) // For grouping purposes
                    scrollToBottom()
                }
            }
        })
        binding.chatMessagesList.adapter = adapter

        val layoutManager = LinearLayoutManager(activity)
        layoutManager.stackFromEnd = true
        binding.chatMessagesList.layoutManager = layoutManager

        val chatScrollListener: ChatScrollListener = object : ChatScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int) {
                listViewModel.loadMoreData(totalItemsCount)
            }
        }
        binding.chatMessagesList.addOnScrollListener(chatScrollListener)

        chatSendingViewModel.textToSend.observe(viewLifecycleOwner, Observer {
            chatSendingViewModel.onTextToSendChanged(it)
        })

        listViewModel.events.observe(viewLifecycleOwner, Observer { events ->
            adapter.submitList(events)
        })

        listViewModel.messageUpdatedEvent.observe(viewLifecycleOwner, Observer {
            it.consume { position ->
                adapter.notifyItemChanged(position)
            }
        })

        listViewModel.requestWriteExternalStoragePermissionEvent.observe(viewLifecycleOwner, Observer {
            it.consume {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        })

        adapter.deleteMessageEvent.observe(viewLifecycleOwner, Observer {
            it.consume { chatMessage ->
                listViewModel.deleteMessage(chatMessage)
            }
        })

        adapter.resendMessageEvent.observe(viewLifecycleOwner, Observer {
            it.consume { chatMessage ->
                listViewModel.resendMessage(chatMessage)
            }
        })

        adapter.forwardMessageEvent.observe(viewLifecycleOwner, Observer {
            it.consume { chatMessage ->
                // Remove observer before setting the message to forward
                // as we don't want to forward it in this chat room
                sharedViewModel.messageToForwardEvent.removeObservers(viewLifecycleOwner)
                sharedViewModel.messageToForwardEvent.value = Event(chatMessage)

                val deepLink = "linphone-android://chat/"
                Log.i("[Chat Room] Forwarding message, starting deep link: $deepLink")
                findNavController().navigate(Uri.parse(deepLink))
            }
        })

        adapter.showImdnForMessageEvent.observe(viewLifecycleOwner, Observer {
            it.consume { chatMessage ->
                val args = Bundle()
                args.putString("MessageId", chatMessage.messageId)
                Navigation.findNavController(binding.root).navigate(R.id.action_detailChatRoomFragment_to_imdnFragment, args)
            }
        })

        adapter.addSipUriToContactEvent.observe(viewLifecycleOwner, Observer {
            it.consume { sipUri ->
                val deepLink = "linphone-android://contact/new/$sipUri"
                Log.i("[Chat Room] Creating contact, starting deep link: $deepLink")
                findNavController().navigate(Uri.parse(deepLink))
            }
        })

        adapter.openContentEvent.observe(viewLifecycleOwner, Observer {
            it.consume { path ->
                openFile(path)
            }
        })

        binding.setBackClickListener {
            findNavController().popBackStack()
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
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.CAMERA), 0)
            }
        }

        binding.setSendMessageClickListener {
            chatSendingViewModel.sendMessage()
            binding.message.text?.clear()
        }

        binding.setStartCallClickListener {
            coreContext.startCall(viewModel.addressToCall)
        }

        sharedViewModel.filesToShare.observe(viewLifecycleOwner, Observer {
            if (it.isNotEmpty()) {
                for (path in it) {
                    Log.i("[Chat Room] Found $path file to share")
                    chatSendingViewModel.addAttachment(path)
                }
                sharedViewModel.filesToShare.value = arrayListOf()
            }
        })

        sharedViewModel.messageToForwardEvent.observe(viewLifecycleOwner, Observer {
            it.consume { chatMessage ->
                Log.i("[Chat Room] Found message to transfer")
                showForwardConfirmationDialog(chatMessage)
            }
        })
    }

    override fun getItemCount(): Int {
        return adapter.itemCount
    }

    override fun deleteItems(indexesOfItemToDelete: ArrayList<Int>) {
        val list = ArrayList<EventLog>()
        for (index in indexesOfItemToDelete) {
            val eventLog = adapter.getItemAt(index)
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

        // Prevent notifications for this chat room to be displayed
        if (viewModel.isInWindowedMode.value == false) {
            coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = chatRoomAddress
        }
        scrollToBottom()
    }

    override fun onPause() {
        coreContext.notificationsManager.currentlyDisplayedChatRoomAddress = null

        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            var fileToUploadPath: String? = null

            val temporaryFileUploadPath = chatSendingViewModel.temporaryFileUploadPath
            if (temporaryFileUploadPath != null) {
                if (data != null) {
                    val dataUri = data.data
                    if (dataUri != null) {
                        fileToUploadPath = dataUri.toString()
                        Log.i("[Chat Room] Using data URI $fileToUploadPath")
                    } else if (temporaryFileUploadPath.exists()) {
                        fileToUploadPath = temporaryFileUploadPath.absolutePath
                        Log.i("[Chat Room] Data URI is null, using $fileToUploadPath")
                    }
                } else if (temporaryFileUploadPath.exists()) {
                    fileToUploadPath = temporaryFileUploadPath.absolutePath
                    Log.i("[Chat Room] Data is null, using $fileToUploadPath")
                }
            }

            if (fileToUploadPath != null) {
                if (fileToUploadPath.startsWith("content://") ||
                    fileToUploadPath.startsWith("file://")
                ) {
                    val uriToParse = Uri.parse(fileToUploadPath)
                    fileToUploadPath = FileUtils.getFilePath(requireContext(), uriToParse)
                    Log.i("[Chat] Path was using a content or file scheme, real path is: $fileToUploadPath")
                    if (fileToUploadPath == null) {
                        Log.e("[Chat] Failed to get access to file $uriToParse")
                    }
                }
            }

            if (fileToUploadPath != null) {
                chatSendingViewModel.addAttachment(fileToUploadPath)
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

            val okLabel = if (viewModel.oneParticipantOneDevice) getString(R.string.dialog_call) else getString(R.string.dialog_ok)
            dialogViewModel.showOkButton({ doNotAskAgain ->
                if (doNotAskAgain) corePreferences.limeSecurityPopupEnabled = false

                if (viewModel.oneParticipantOneDevice) {
                    coreContext.startCall(viewModel.onlyParticipantOnlyDeviceAddress, true)
                } else {
                    if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
                        findNavController().navigate(R.id.action_detailChatRoomFragment_to_devicesFragment)
                    }
                }

                dialog.dismiss()
            }, okLabel)

            dialog.show()
        } else {
            if (viewModel.oneParticipantOneDevice) {
                coreContext.startCall(viewModel.onlyParticipantOnlyDeviceAddress, true)
            } else {
                if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
                    findNavController().navigate(R.id.action_detailChatRoomFragment_to_devicesFragment)
                }
            }
        }
    }

    private fun showGroupInfo(chatRoom: ChatRoom) {
        sharedViewModel.selectedGroupChatRoom.value = chatRoom
        if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
            findNavController().navigate(R.id.action_detailChatRoomFragment_to_groupInfoFragment)
        }
    }

    private fun showEphemeralMessages() {
        if (findNavController().currentDestination?.id == R.id.detailChatRoomFragment) {
            findNavController().navigate(R.id.action_detailChatRoomFragment_to_ephemeralFragment)
        }
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
            override fun onMenuModeChange(menu: MenuBuilder?) {}

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
        if (adapter.itemCount > 0) {
            binding.chatMessagesList.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun pickFile() {
        val cameraIntents = ArrayList<Intent>()

        // Handles image & video picking
        val galleryIntent = Intent(Intent.ACTION_PICK)
        galleryIntent.type = "*/*"
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))

        if (PermissionHelper.get().hasCameraPermission()) {
            // Allows to capture directly from the camera
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val tempFileName = System.currentTimeMillis().toString() + ".jpeg"
            chatSendingViewModel.temporaryFileUploadPath =
                FileUtils.getFileStoragePath(tempFileName)
            val uri = Uri.fromFile(chatSendingViewModel.temporaryFileUploadPath)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            cameraIntents.add(captureIntent)
        }

        if (PermissionHelper.get().hasReadExternalStorage()) {
            // Finally allow any kind of file
            val fileIntent = Intent(Intent.ACTION_GET_CONTENT)
            fileIntent.type = "*/*"
            cameraIntents.add(fileIntent)
        }

        val chooserIntent =
            Intent.createChooser(galleryIntent, getString(R.string.chat_message_pick_file_dialog))
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(arrayOf<Parcelable>()))

        startActivityForResult(chooserIntent, 0)
    }

    private fun openFile(contentFilePath: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        val contentUri: Uri
        var path = contentFilePath

        when {
            path.startsWith("file://") -> {
                path = path.substring("file://".length)
                val file = File(path)
                contentUri = FileProvider.getUriForFile(
                    requireContext(),
                    getString(R.string.file_provider),
                    file
                )
            }
            path.startsWith("content://") -> {
                contentUri = Uri.parse(path)
            }
            else -> {
                val file = File(path)
                contentUri = try {
                    FileProvider.getUriForFile(
                        requireContext(),
                        getString(R.string.file_provider),
                        file
                    )
                } catch (e: Exception) {
                    Log.e(
                        "[Chat Message] Couldn't get URI for file $file using file provider ${getString(R.string.file_provider)}"
                    )
                    Uri.parse(path)
                }
            }
        }

        val filePath: String = contentUri.toString()
        Log.i("[Chat Message] Trying to open file: $filePath")
        var type: String? = null
        val extension = FileUtils.getExtensionFromFileName(filePath)

        if (extension.isNotEmpty()) {
            Log.i("[Chat Message] Found extension $extension")
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            Log.e("[Chat Message] Couldn't find extension")
        }

        if (type != null) {
            Log.i("[Chat Message] Found matching MIME type $type")
        } else {
            type = FileUtils.getMimeFromFile(filePath)
            Log.e("[Chat Message] Can't get MIME type from extension: $extension, will use $type")
        }

        intent.setDataAndType(contentUri, type)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
            Log.e("[Chat Message] Couldn't find an activity to handle MIME type: $type")
            val activity = requireActivity() as SnackBarActivity
            activity.showSnackBar(R.string.chat_room_cant_open_file_no_app_found)
        }
    }
}
