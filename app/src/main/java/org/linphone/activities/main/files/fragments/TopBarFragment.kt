/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
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
package org.linphone.activities.main.files.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.navigation.fragment.findNavController
import org.linphone.LinphoneApplication
import org.linphone.R
import org.linphone.activities.GenericFragment
import org.linphone.activities.main.viewmodels.DialogViewModel
import org.linphone.core.tools.Log
import org.linphone.databinding.FileViewerTopBarFragmentBinding
import org.linphone.utils.DialogUtils
import org.linphone.utils.FileUtils

class TopBarFragment : GenericFragment<FileViewerTopBarFragmentBinding>() {
    private var filePath: String = ""

    override fun getLayoutId(): Int = R.layout.file_viewer_top_bar_fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setExportClickListener {
            openFile(filePath)
        }
    }

    fun setFilePath(newFilePath: String) {
        Log.i("[File Viewer] File path is: $newFilePath")
        filePath = newFilePath
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("FilePath", filePath)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        filePath = savedInstanceState?.getString("FilePath") ?: filePath
    }

    private fun openFile(contentFilePath: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        val contentUri: Uri = FileUtils.getPublicFilePath(requireContext(), contentFilePath)
        val filePath: String = contentUri.toString()
        Log.i("[File Viewer] Trying to open file: $filePath")
        var type: String? = null
        val extension = FileUtils.getExtensionFromFileName(filePath)

        if (extension.isNotEmpty()) {
            Log.i("[File Viewer] Found extension $extension")
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            Log.e("[File Viewer] Couldn't find extension")
        }

        if (type != null) {
            Log.i("[File Viewer] Found matching MIME type $type")
        } else {
            type = "file/$extension"
            Log.e("[File Viewer] Can't get MIME type from extension: $extension, will use $type")
        }

        intent.setDataAndType(contentUri, type)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(intent)

            if (LinphoneApplication.corePreferences.enableAnimations) {
                requireActivity().overridePendingTransition(R.anim.enter_right, R.anim.exit_left)
            }
        } catch (anfe: ActivityNotFoundException) {
            Log.e("[File Viewer] Couldn't find an activity to handle MIME type: $type")

            val dialogViewModel = DialogViewModel(
                getString(R.string.dialog_try_open_file_as_text_body), getString(
                    R.string.dialog_try_open_file_as_text_title
                )
            )
            val dialog = DialogUtils.getDialog(requireContext(), dialogViewModel)

            dialogViewModel.showCancelButton {
                dialog.dismiss()
            }

            dialogViewModel.showOkButton({
                dialog.dismiss()
            })

            dialog.show()
        }
    }
}
