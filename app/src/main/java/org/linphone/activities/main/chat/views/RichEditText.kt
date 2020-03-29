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
package org.linphone.activities.main.chat.views

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/**
 * Allows for image input inside an EditText, usefull for keyboards with gif support for example.
 */
class RichEditText : AppCompatEditText {
    private var mListener: RichInputListener? = null
    private var mSupportedMimeTypes: Array<String>? = null

    interface RichInputListener {
        fun onCommitContent(
            inputContentInfo: InputContentInfoCompat,
            flags: Int,
            opts: Bundle,
            contentMimeTypes: Array<String>?
        ): Boolean
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun setListener(listener: RichInputListener) {
        mListener = listener
        mSupportedMimeTypes = arrayOf("image/png", "image/gif", "image/jpeg")
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(editorInfo)
        EditorInfoCompat.setContentMimeTypes(editorInfo, mSupportedMimeTypes)

        val callback =
            InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, opts ->
                val listener = mListener
                listener?.onCommitContent(
                    inputContentInfo, flags, opts, mSupportedMimeTypes
                ) ?: false
            }

        return if (ic != null) {
            InputConnectionCompat.createWrapper(ic, editorInfo, callback)
        } else null
    }
}
