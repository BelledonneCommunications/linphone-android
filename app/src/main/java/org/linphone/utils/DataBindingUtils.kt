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
package org.linphone.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import coil.load
import coil.transform.CircleCropTransformation
import io.getstream.avatarview.AvatarView
import io.getstream.avatarview.coil.loadImage
import org.linphone.BR
import org.linphone.R
import org.linphone.contacts.ContactData
import org.linphone.core.ConsolidatedPresence
import org.linphone.ui.MainActivity
import org.linphone.ui.contacts.model.ContactModel

/**
 * This file contains all the data binding necessary for the app
 */

@BindingAdapter("entries", "layout")
fun <T> setEntries(
    viewGroup: ViewGroup,
    entries: List<T>?,
    layoutId: Int
) {
    viewGroup.removeAllViews()

    if (entries != null) {
        val inflater = viewGroup.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        for (entry in entries) {
            val binding = DataBindingUtil.inflate<ViewDataBinding>(
                inflater,
                layoutId,
                viewGroup,
                false
            )

            binding.setVariable(BR.model, entry)

            // This is a bit hacky...
            binding.lifecycleOwner = viewGroup.context as MainActivity

            viewGroup.addView(binding.root)
        }
    }
}

fun View.showKeyboard(window: Window) {
    this.requestFocus()
    /*val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)*/
    WindowCompat.getInsetsController(window, this).show(WindowInsetsCompat.Type.ime())
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}

fun View.setKeyboardInsetListener(lambda: (visible: Boolean) -> Unit) {
    doOnLayout {
        var isKeyboardVisible = ViewCompat.getRootWindowInsets(this)?.isVisible(
            WindowInsetsCompat.Type.ime()
        ) == true

        lambda(isKeyboardVisible)

        // See https://issuetracker.google.com/issues/281942480
        ViewCompat.setOnApplyWindowInsetsListener(
            rootView
        ) { view, insets ->
            val keyboardVisibilityChanged = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (keyboardVisibilityChanged != isKeyboardVisible) {
                isKeyboardVisible = keyboardVisibilityChanged
                lambda(isKeyboardVisible)
            }
            ViewCompat.onApplyWindowInsets(view, insets)
        }
    }
}

@BindingAdapter("android:src")
fun ImageView.setSourceImageResource(resource: Int) {
    this.setImageResource(resource)
}

@BindingAdapter("android:textStyle")
fun AppCompatTextView.setTypeface(typeface: Int) {
    this.setTypeface(null, typeface)
}

@BindingAdapter("android:drawableTint")
fun AppCompatTextView.setDrawableTint(color: Int) {
    for (drawable in compoundDrawablesRelative) {
        drawable?.setTint(color)
    }
}

@BindingAdapter("coilContact")
fun loadContactPictureWithCoil2(imageView: ImageView, contact: ContactData?) {
    if (contact == null) {
        imageView.load(R.drawable.contact_avatar)
    } else {
        imageView.load(contact.avatar) {
            transformations(CircleCropTransformation())
            error(R.drawable.contact_avatar)
        }
    }
}

@BindingAdapter("contactAvatar")
fun AvatarView.loadContactPicture(contact: ContactModel?) {
    if (contact == null) {
        loadImage(R.drawable.contact_avatar)
    } else {
        indicatorColor = when (contact.presenceStatus.value) {
            ConsolidatedPresence.Online -> R.color.green_online
            else -> R.color.blue_outgoing_message
        }
        indicatorEnabled = contact.presenceStatus.value != ConsolidatedPresence.Offline

        val uri = contact.getAvatarUri()
        loadImage(
            data = uri,
            onStart = {
                // Use initials as placeholder
                avatarInitials = contact.initials
            },
            onSuccess = { _, _ ->
                // If loading is successful, remove initials otherwise image won't be visible
                avatarInitials = ""
            }
        )
    }
}
