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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.EmojiViewItem
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.dispose
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.BR
import org.linphone.R
import org.linphone.contacts.AbstractAvatarModel
import org.linphone.contacts.AvatarGenerator
import org.linphone.core.ChatRoom
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.tools.Log

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
    if (!entries.isNullOrEmpty()) {
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

@BindingAdapter("tint", "disableTint")
fun ImageView.setTintColor(@ColorRes color: Int, disable: Boolean) {
    if (!disable) {
        setColorFilter(AppUtils.getColor(color), PorterDuff.Mode.SRC_IN)
    }
}

@BindingAdapter("textColor")
fun AppCompatTextView.setColor(@ColorRes color: Int) {
    setTextColor(AppUtils.getColor(color))
}

@UiThread
@BindingAdapter("coil")
fun ShapeableImageView.loadCircleFileWithCoil(file: String?) {
    Log.i("[Data Binding Utils] Loading file [$file] with coil")
    if (file != null) {
        load(file)
    }
}

@UiThread
@BindingAdapter("coilAvatar")
fun ShapeableImageView.loadAvatarWithCoil(model: AbstractAvatarModel?) {
    val imageView = this
    (context as AppCompatActivity).lifecycleScope.launch {
        loadContactPictureWithCoil(imageView, model)
    }
}

@UiThread
@BindingAdapter("coilAvatarNoTrust")
fun ShapeableImageView.loadAvatarWithCoilWithoutTrust(model: AbstractAvatarModel?) {
    val imageView = this
    (context as AppCompatActivity).lifecycleScope.launch {
        loadContactPictureWithCoil(imageView, model, skipTrust = true)
    }
}

@UiThread
@BindingAdapter("coilBubbleAvatar")
fun ShapeableImageView.loadBubbleAvatarWithCoil(model: AbstractAvatarModel?) {
    val imageView = this
    (context as AppCompatActivity).lifecycleScope.launch {
        val size = R.dimen.avatar_bubble_size
        val initialsSize = R.dimen.avatar_initials_bubble_text_size
        loadContactPictureWithCoil(imageView, model, size = size, textSize = initialsSize)
    }
}

@UiThread
@BindingAdapter("coilBigAvatar")
fun ShapeableImageView.loadBigAvatarWithCoil(model: AbstractAvatarModel?) {
    val imageView = this
    (context as AppCompatActivity).lifecycleScope.launch {
        val size = R.dimen.avatar_big_size
        val initialsSize = R.dimen.avatar_initials_big_text_size
        loadContactPictureWithCoil(imageView, model, size = size, textSize = initialsSize)
    }
}

@UiThread
@BindingAdapter("coilCallAvatar")
fun ShapeableImageView.loadCallAvatarWithCoil(model: AbstractAvatarModel?) {
    val imageView = this
    (context as AppCompatActivity).lifecycleScope.launch {
        val size = R.dimen.avatar_in_call_size
        val initialsSize = R.dimen.avatar_initials_call_text_size
        loadContactPictureWithCoil(imageView, model, size = size, textSize = initialsSize)
    }
}

@UiThread
@BindingAdapter("coilInitials")
fun ShapeableImageView.loadInitialsAvatarWithCoil(initials: String?) {
    Log.i("[Data Binding Utils] Displaying initials [$initials] on ImageView")
    val imageView = this
    (context as AppCompatActivity).lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            val builder = AvatarGenerator(context)
            builder.setInitials(initials.orEmpty())
            load(builder.build())
        }
    }
}

@SuppressLint("ResourceType")
private suspend fun loadContactPictureWithCoil(
    imageView: ShapeableImageView,
    model: AbstractAvatarModel?,
    @DimenRes size: Int = 0,
    @DimenRes textSize: Int = 0,
    skipTrust: Boolean = false
) {
    withContext(Dispatchers.IO) {
        imageView.dispose()

        val context = imageView.context
        if (model != null) {
            if (!skipTrust) {
                if (model.showTrust.value == true) {
                    when (model.trust.value) {
                        ChatRoom.SecurityLevel.Safe -> {
                            imageView.setStrokeColorResource(R.color.blue_info_500)
                            imageView.setStrokeWidthResource(R.dimen.avatar_trust_border_width)
                        }

                        ChatRoom.SecurityLevel.Unsafe -> {
                            imageView.setStrokeColorResource(R.color.red_danger_500)
                            imageView.setStrokeWidthResource(R.dimen.avatar_trust_border_width)
                        }

                        else -> {
                            imageView.setStrokeColorResource(R.color.transparent_color)
                            imageView.setStrokeWidthResource(R.dimen.zero)
                        }
                    }
                } else {
                    imageView.setStrokeColorResource(R.color.transparent_color)
                    imageView.setStrokeWidthResource(R.dimen.zero)
                }
            }

            val images = model.images.value.orEmpty()
            val count = images.size
            if (count == 1) {
                val image = images.firstOrNull()
                imageView.load(image) {
                    error(
                        coroutineScope {
                            withContext(Dispatchers.IO) {
                                val initials = model.initials.value.orEmpty()
                                if (initials.isEmpty() || initials == "+") {
                                    ResourcesCompat.getDrawable(
                                        context.resources,
                                        R.drawable.user_circle,
                                        context.theme
                                    )
                                } else {
                                    val builder = AvatarGenerator(context)
                                    builder.setInitials(model.initials.value.orEmpty())
                                    if (size > 0) {
                                        builder.setAvatarSize(AppUtils.getDimension(size).toInt())
                                    }
                                    if (textSize > 0) {
                                        builder.setTextSize(AppUtils.getDimension(textSize))
                                    }
                                    builder.build()
                                }
                            }
                        }
                    )
                }
            } else {
                val w = if (size > 0) {
                    AppUtils.getDimension(size).toInt()
                } else {
                    AppUtils.getDimension(R.dimen.avatar_list_cell_size).toInt()
                }
                val bitmap = ImageUtils.getBitmapFromMultipleAvatars(imageView.context, w, images)
                imageView.load(bitmap)
            }
        } else {
            imageView.load(
                ResourcesCompat.getDrawable(
                    context.resources,
                    R.drawable.user_circle,
                    context.theme
                )
            )
        }
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
    val params = view.layoutParams as ViewGroup.MarginLayoutParams
    val m = margins.toInt()
    params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, m)
    view.layoutParams = params
}

@BindingAdapter("android:layout_marginTop")
fun setConstraintLayoutTopMargin(view: View, margins: Float) {
    val params = view.layoutParams as ViewGroup.MarginLayoutParams
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

@BindingAdapter("emojiPickedListener")
fun EmojiPickerView.setEmojiPickedListener(listener: EmojiPickedListener) {
    setOnEmojiPickedListener { emoji ->
        listener.onEmojiPicked(emoji)
    }
}

interface EmojiPickedListener {
    fun onEmojiPicked(item: EmojiViewItem)
}
