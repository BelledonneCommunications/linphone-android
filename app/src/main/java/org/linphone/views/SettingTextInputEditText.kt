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
package org.linphone.views

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.TextInputEditText
import org.linphone.activities.main.settings.SettingListener

class SettingTextInputEditText : TextInputEditText {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun fakeImeDone(listener: SettingListener) {
        listener.onTextValueChanged(text.toString())

        // Send IME action DONE to trigger onSettingImeDone binding adapter, but that doesn't work...
        // val inputConnection = BaseInputConnection(this, true)
        // inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)

        // Will make check icon to disappear thanks to onFocusChangeVisibilityOf binding adapter
        clearFocus()

        // Hide keyboard
        val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }
}
