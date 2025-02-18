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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.core.tools.Log
import org.linphone.databinding.ChatDocumentsFragmentBinding
import org.linphone.ui.main.chat.adapter.ConversationsFilesAdapter
import org.linphone.ui.main.chat.model.FileModel
import org.linphone.ui.main.chat.viewmodel.ConversationDocumentsListViewModel
import org.linphone.ui.main.fragment.SlidingPaneChildFragment
import org.linphone.utils.ConfirmationDialogModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.RecyclerViewHeaderDecoration

@UiThread
class ConversationDocumentsListFragment : SlidingPaneChildFragment() {
    companion object {
        private const val TAG = "[Conversation Documents List Fragment]"
    }

    private lateinit var binding: ChatDocumentsFragmentBinding

    private lateinit var viewModel: ConversationDocumentsListViewModel

    private lateinit var adapter: ConversationsFilesAdapter

    private val args: ConversationMediaListFragmentArgs by navArgs()

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConversationsFilesAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatDocumentsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[ConversationDocumentsListViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val conversationId = args.conversationId
        Log.i("$TAG Looking up for conversation with conversation ID [$conversationId]")
        val chatRoom = sharedViewModel.displayedChatRoom
        viewModel.findChatRoom(chatRoom, conversationId)

        val headerItemDecoration = RecyclerViewHeaderDecoration(requireContext(), adapter)
        binding.documentsList.addItemDecoration(headerItemDecoration)

        binding.documentsList.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(requireContext())
        binding.documentsList.layoutManager = layoutManager

        if (binding.documentsList.adapter != adapter) {
            binding.documentsList.adapter = adapter
        }

        binding.setBackClickListener {
            goBack()
        }

        viewModel.chatRoomFoundEvent.observe(viewLifecycleOwner) {
            it.consume {
                startPostponedEnterTransition()
            }
        }

        viewModel.documentsList.observe(viewLifecycleOwner) { items ->
            if (items != adapter.currentList || items.size != adapter.itemCount) {
                adapter.submitList(items)
                Log.i("$TAG Documents list updated with [${items.size}] items")
            }
        }

        viewModel.openDocumentEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                Log.i("$TAG User clicked on file [${model.path}], let's display it in file viewer")
                goToFileViewer(model)
            }
        }
    }

    private fun goToFileViewer(fileModel: FileModel) {
        val path = fileModel.path
        Log.i("$TAG Navigating to file viewer fragment with path [$path]")
        val extension = FileUtils.getExtensionFromFileName(path)
        val mime = FileUtils.getMimeTypeFromExtension(extension)

        val bundle = Bundle()
        bundle.apply {
            putString("conversationId", viewModel.conversationId)
            putString("path", path)
            putBoolean("isEncrypted", fileModel.isEncrypted)
            putLong("timestamp", fileModel.fileCreationTimestamp)
            putString("originalPath", fileModel.originalPath)
            putBoolean("isFromEphemeralMessage", fileModel.isFromEphemeralMessage)
            putBoolean("isMedia", false)
        }
        when (FileUtils.getMimeType(mime)) {
            FileUtils.MimeType.Pdf, FileUtils.MimeType.PlainText -> {
                sharedViewModel.displayFileEvent.value = Event(bundle)
            }
            else -> {
                val intent = Intent(Intent.ACTION_VIEW)
                val contentUri: Uri =
                    FileUtils.getPublicFilePath(requireContext(), path)
                intent.setDataAndType(contentUri, mime)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    requireContext().startActivity(intent)
                } catch (anfe: ActivityNotFoundException) {
                    Log.e("$TAG Can't open file [$path] in third party app: $anfe")
                    showOpenAsPlainTextDialog(bundle)
                }
            }
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

        model.cancelEvent.observe(viewLifecycleOwner) {
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
}
