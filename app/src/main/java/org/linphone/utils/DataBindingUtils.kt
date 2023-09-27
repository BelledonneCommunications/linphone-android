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
import android.graphics.PorterDuff
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import coil.load
import coil.transform.CircleCropTransformation
import io.getstream.avatarview.AvatarView
import io.getstream.avatarview.coil.loadImage
import org.linphone.BR
import org.linphone.R
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.tools.Log
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.model.AccountModel

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
    if (!entries.isNullOrEmpty()) {
        viewGroup.removeAllViews()
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
            if (viewGroup.context as? LifecycleOwner != null) {
                binding.lifecycleOwner = viewGroup.context as LifecycleOwner
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
fun AppCompatEditText.removeCharacterAtPosition() {
    val start = selectionStart
    val end = selectionEnd
    if (start > 0) {
        text =
            text?.delete(
                start - 1,
                end
            )
        setSelection(start - 1)
    }
}

@UiThread
fun AppCompatEditText.addCharacterAtPosition(character: String) {
    val newValue = "${text}$character"
    setText(newValue)
    setSelection(newValue.length)
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
                "[Data Binding Utils] Failed to called lambda after keyboard visibility changed: $ise"
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
                        "[Data Binding Utils] Failed to called lambda after keyboard visibility changed: $ise"
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
    Log.i("[Data Binding Utils] Loading file [$file] with coil")
    if (file != null) {
        imageView.load(file) {
            transformations(CircleCropTransformation())
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

@BindingAdapter("tint")
fun ImageView.setTintColor(@ColorRes color: Int) {
    setColorFilter(ContextCompat.getColor(context, color), PorterDuff.Mode.SRC_IN)
}

@BindingAdapter("textColor")
fun AppCompatTextView.setColor(@ColorRes color: Int) {
    setTextColor(ContextCompat.getColor(context, color))
}

@UiThread
@BindingAdapter("avatarInitials")
fun AvatarView.loadInitials(initials: String?) {
    Log.i("[Data Binding Utils] Displaying initials [$initials] on AvatarView")
    if (initials.orEmpty() != "+") {
        avatarInitials = initials.orEmpty()
    }
}

@UiThread
@BindingAdapter("accountAvatar")
fun AvatarView.loadAccountAvatar(account: AccountModel?) {
    Log.i("[Data Binding Utils] Loading account picture [${account?.avatar?.value}] with coil")
    if (account == null) {
        loadImage(R.drawable.user_circle)
    } else {
        val lifecycleOwner = findViewTreeLifecycleOwner()
        if (lifecycleOwner != null) {
            account.avatar.observe(lifecycleOwner) { uri ->
                loadImage(
                    data = uri,
                    onStart = {
                        // Use initials as placeholder
                        val initials = account.initials.value.orEmpty()
                        if (initials != "+") {
                            avatarInitials = initials
                        }

                        if (account.showTrust.value == true) {
                            avatarBorderColor =
                                resources.getColor(R.color.blue_info_500, context.theme)
                            avatarBorderWidth =
                                AppUtils.getDimension(R.dimen.avatar_trust_border_width).toInt()
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
        } else {
            loadImage(
                data = account.avatar.value,
                onStart = {
                    // Use initials as placeholder
                    val initials = account.initials.value.orEmpty()
                    if (initials != "+") {
                        avatarInitials = initials
                    }

                    if (account.showTrust.value == true) {
                        avatarBorderColor = resources.getColor(R.color.blue_info_500, context.theme)
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
}

@UiThread
@BindingAdapter("contactAvatar")
fun AvatarView.loadContactAvatar(contact: ContactAvatarModel?) {
    if (contact == null) {
        loadImage(R.drawable.user_circle)
    } else {
        val uri = contact.avatar.value
        loadImage(
            data = uri,
            onStart = {
                // Use initials as placeholder
                val initials = contact.initials
                if (initials != "+") {
                    avatarInitials = initials
                }

                if (contact.showTrust.value == true) {
                    avatarBorderColor =
                        resources.getColor(R.color.blue_info_500, context.theme)
                    avatarBorderWidth =
                        AppUtils.getDimension(R.dimen.avatar_trust_border_width).toInt()
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

@BindingAdapter("android:layout_marginTop")
fun setConstraintLayoutTopMargin(view: View, margins: Float) {
    val params = view.layoutParams as ConstraintLayout.LayoutParams
    val m = margins.toInt()
    params.setMargins(params.leftMargin, m, params.rightMargin, params.bottomMargin)
    view.layoutParams = params
}

@BindingAdapter("focusNextOnInput")
fun focusNextOnInput(editText: EditText, enabled: Boolean) {
    if (!enabled) return

    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            if (!s.isNullOrEmpty()) {
                editText.onEditorAction(EditorInfo.IME_ACTION_NEXT)
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

@BindingAdapter("validateOnInput")
fun validateOnInput(editText: EditText, onValidate: () -> (Unit)) {
    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            if (!s.isNullOrEmpty()) {
                editText.onEditorAction(EditorInfo.IME_ACTION_DONE)
                onValidate.invoke()
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}
