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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
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
import org.linphone.core.tools.Log
import org.linphone.ui.main.MainActivity
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.model.AccountModel
import org.linphone.ui.voip.VoipActivity

/**
 * This file contains all the data binding necessary for the app
 */

@UiThread
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
            if (viewGroup.context as? MainActivity != null) {
                binding.lifecycleOwner = viewGroup.context as MainActivity
            } else if (viewGroup.context as? VoipActivity != null) {
                binding.lifecycleOwner = viewGroup.context as VoipActivity
            } else {
                Log.e(
                    "[Data Binding Utils] Failed to cast viewGroup's context as an Activity, lifecycle owner hasn't be set!"
                )
            }

            viewGroup.addView(binding.root)
        }
    }
}

@UiThread
fun View.showKeyboard(window: Window) {
    this.requestFocus()
    /*val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)*/
    WindowCompat.getInsetsController(window, this).show(WindowInsetsCompat.Type.ime())
}

@UiThread
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}

@UiThread
fun View.setKeyboardInsetListener(lambda: (visible: Boolean) -> Unit) {
    doOnLayout {
        var isKeyboardVisible = ViewCompat.getRootWindowInsets(this)?.isVisible(
            WindowInsetsCompat.Type.ime()
        ) == true

        try {
            lambda(isKeyboardVisible)
        } catch (ise: IllegalStateException) {
            Log.e(
                "[Databinding Utils] Failed to called lambda after keyboard visibility changed: $ise"
            )
        }

        // See https://issuetracker.google.com/issues/281942480
        ViewCompat.setOnApplyWindowInsetsListener(
            rootView
        ) { view, insets ->
            val keyboardVisibilityChanged = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (keyboardVisibilityChanged != isKeyboardVisible) {
                isKeyboardVisible = keyboardVisibilityChanged

                try {
                    lambda(isKeyboardVisible)
                } catch (ise: IllegalStateException) {
                    Log.e(
                        "[Databinding Utils] Failed to called lambda after keyboard visibility changed: $ise"
                    )
                }
            }
            ViewCompat.onApplyWindowInsets(view, insets)
        }
    }
}

@UiThread
@BindingAdapter("android:src")
fun ImageView.setSourceImageResource(resource: Int) {
    this.setImageResource(resource)
}

@UiThread
@BindingAdapter("android:textStyle")
fun AppCompatTextView.setTypeface(typeface: Int) {
    this.setTypeface(null, typeface)
}

@UiThread
@BindingAdapter("android:drawableTint")
fun AppCompatTextView.setDrawableTint(@ColorInt color: Int) {
    for (drawable in compoundDrawablesRelative) {
        drawable?.setTint(color)
    }
}

@UiThread
@BindingAdapter("coil")
fun loadPictureWithCoil(imageView: ImageView, file: String?) {
    if (file != null) {
        imageView.load(file) {
            transformations(CircleCropTransformation())
        }
    }
}

@UiThread
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

@UiThread
@BindingAdapter("presenceIcon")
fun ImageView.setPresenceIcon(presence: ConsolidatedPresence?) {
    val icon = when (presence) {
        ConsolidatedPresence.Online -> R.drawable.led_online
        ConsolidatedPresence.DoNotDisturb -> R.drawable.led_do_not_disturb
        ConsolidatedPresence.Busy -> R.drawable.led_away
        else -> R.drawable.led_not_registered
    }
    setImageResource(icon)
}

@UiThread
@BindingAdapter("accountAvatar")
fun AvatarView.loadAccountAvatar(account: AccountModel?) {
    if (account == null) {
        loadImage(R.drawable.contact_avatar)
    } else {
        val uri = account.avatar.value
        loadImage(
            data = uri,
            onStart = {
                // Use initials as placeholder
                avatarInitials = account.initials.value.orEmpty()

                if (account.showTrust.value == true) {
                    avatarBorderColor = resources.getColor(R.color.trusted_blue, context.theme)
                    avatarBorderWidth = AppUtils.getDimension(R.dimen.avatar_trust_border_width).toInt()
                } else {
                    avatarBorderWidth = AppUtils.getDimension(R.dimen.zero).toInt()
                }
            },
            onSuccess = { _, _ ->
                // If loading is successful, remove initials otherwise image won't be visible
                avatarInitials = ""
            }
        )
    }
}

@UiThread
@BindingAdapter("contactAvatar")
fun AvatarView.loadContactAvatar(contact: ContactAvatarModel?) {
    if (contact == null) {
        loadImage(R.drawable.contact_avatar)
    } else {
        val uri = contact.avatar.value
        loadImage(
            data = uri,
            onStart = {
                // Use initials as placeholder
                avatarInitials = contact.initials

                if (contact.showTrust.value == true) {
                    avatarBorderColor = resources.getColor(R.color.trusted_blue, context.theme)
                    avatarBorderWidth = AppUtils.getDimension(R.dimen.avatar_trust_border_width).toInt()
                } else {
                    avatarBorderWidth = AppUtils.getDimension(R.dimen.zero).toInt()
                }
            },
            onSuccess = { _, _ ->
                // If loading is successful, remove initials otherwise image won't be visible
                avatarInitials = ""
            }
        )
    }
}

@UiThread
@BindingAdapter("onValueChanged")
fun AppCompatEditText.editTextSetting(lambda: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            lambda()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

@BindingAdapter("android:layout_marginBottom")
fun setConstraintLayoutBottomMargin(view: View, margins: Float) {
    val params = view.layoutParams as ConstraintLayout.LayoutParams
    val m = margins.toInt()
    params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, m)
    view.layoutParams = params
}
