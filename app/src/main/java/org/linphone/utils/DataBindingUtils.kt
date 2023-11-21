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
import android.graphics.drawable.AnimatedVectorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.TextureView
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
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import coil.dispose
import coil.load
import coil.request.videoFrameMillis
import coil.size.Dimension
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import org.linphone.BR
import org.linphone.R
import org.linphone.contacts.AbstractAvatarModel
import org.linphone.contacts.AvatarGenerator
import org.linphone.core.ChatRoom
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.tools.Log
import org.linphone.ui.call.model.ConferenceParticipantDeviceModel

/**
 * This file contains all the data binding necessary for the app
 */

private const val TAG = "[Data Binding Utils]"

@BindingAdapter("inflatedLifecycleOwner")
fun setInflatedViewStubLifecycleOwner(view: View, enable: Boolean) {
    val binding = DataBindingUtil.bind<ViewDataBinding>(view)
    // This is a bit hacky...
    binding?.lifecycleOwner = view.context as AppCompatActivity
}

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
                    "$TAG Failed to cast viewGroup's context as an Activity, lifecycle owner hasn't be set!"
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
                "$TAG Failed to called lambda after keyboard visibility changed: $ise"
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
                        "$TAG Failed to called lambda after keyboard visibility changed: $ise"
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
@BindingAdapter("coilFile")
fun ImageView.loadFileImage(file: String?) {
    if (!file.isNullOrEmpty()) {
        load(file)
    }
}

@UiThread
@BindingAdapter("coilBubble")
fun ImageView.loadImageForChatBubble(file: String?) {
    loadImageForChatBubble(this, file, false)
}

@UiThread
@BindingAdapter("coilBubbleGrid")
fun ImageView.loadImageForChatBubbleGrid(file: String?) {
    loadImageForChatBubble(this, file, true)
}

private fun loadImageForChatBubble(imageView: ImageView, file: String?, grid: Boolean) {
    if (!file.isNullOrEmpty()) {
        val dimen = if (grid) {
            imageView.resources.getDimension(R.dimen.chat_bubble_grid_image_size).toInt()
        } else {
            imageView.resources.getDimension(R.dimen.chat_bubble_big_image_max_size).toInt()
        }
        val width = if (grid) Dimension(dimen) else Dimension.Undefined
        val height = Dimension(dimen)
        /* val radius = imageView.resources.getDimension(
            R.dimen.chat_bubble_images_rounded_corner_radius
        ) */
        if (FileUtils.isExtensionVideo(file)) {
            imageView.load(file) {
                videoFrameMillis(0)
                // transformations(RoundedCornersTransformation(radius))
                size(width, height)
                listener(
                    onError = { _, result ->
                        Log.e(
                            "$TAG Error getting preview picture from video? [$file]: ${result.throwable}"
                        )
                        imageView.visibility = View.GONE
                    }
                )
            }
        } else {
            imageView.load(file) {
                // Can't have a transformation for gif file, breaks animation
                // transformations(RoundedCornersTransformation(radius))
                size(width, height)
                listener(
                    onError = { _, result ->
                        Log.e(
                            "$TAG Error getting picture from file [$file]: ${result.throwable}"
                        )
                        imageView.visibility = View.GONE
                    }
                )
            }
        }
    }
}

@UiThread
@BindingAdapter("animatedDrawable")
fun ImageView.startAnimatedDrawable(start: Boolean = true) {
    drawable.apply {
        when (this) {
            is AnimatedVectorDrawableCompat -> start()
            is AnimatedVectorDrawable -> start()
            else -> { /* not an animated icon */ }
        }
    }
}

@UiThread
@BindingAdapter("coil")
fun ShapeableImageView.loadCircleFileWithCoil(file: String?) {
    if (file != null) {
        load(file)
    }
}

@UiThread
@BindingAdapter("coilAvatar")
fun ShapeableImageView.loadAvatarWithCoil(model: AbstractAvatarModel?) {
    loadContactPictureWithCoil(this, model)
}

@UiThread
@BindingAdapter("coilAvatarNoTrust")
fun ShapeableImageView.loadAvatarWithCoilWithoutTrust(model: AbstractAvatarModel?) {
    loadContactPictureWithCoil(this, model, skipTrust = true)
}

@UiThread
@BindingAdapter("coilBubbleAvatar")
fun ShapeableImageView.loadBubbleAvatarWithCoil(model: AbstractAvatarModel?) {
    val size = R.dimen.avatar_bubble_size
    val initialsSize = R.dimen.avatar_initials_bubble_text_size
    loadContactPictureWithCoil(this, model, size = size, textSize = initialsSize)
}

@UiThread
@BindingAdapter("coilBigAvatar")
fun ShapeableImageView.loadBigAvatarWithCoil(model: AbstractAvatarModel?) {
    val size = R.dimen.avatar_big_size
    val initialsSize = R.dimen.avatar_initials_big_text_size
    loadContactPictureWithCoil(this, model, size = size, textSize = initialsSize)
}

@UiThread
@BindingAdapter("coilCallAvatar")
fun ShapeableImageView.loadCallAvatarWithCoil(model: AbstractAvatarModel?) {
    val size = R.dimen.avatar_in_call_size
    val initialsSize = R.dimen.avatar_initials_call_text_size
    loadContactPictureWithCoil(this, model, size = size, textSize = initialsSize)
}

@UiThread
@BindingAdapter("coilInitials")
fun ShapeableImageView.loadInitialsAvatarWithCoil(initials: String?) {
    val builder = AvatarGenerator(context)
    builder.setInitials(initials.orEmpty())
    load(builder.build())
}

@SuppressLint("ResourceType")
private fun loadContactPictureWithCoil(
    imageView: ShapeableImageView,
    model: AbstractAvatarModel?,
    @DimenRes size: Int = 0,
    @DimenRes textSize: Int = 0,
    skipTrust: Boolean = false
) {
    imageView.dispose()

    val context = imageView.context
    if (model != null) {
        if (model.forceConferenceIcon.value == true) {
            imageView.load(R.drawable.inset_meeting)
            return
        } else if (model.forceConversationIcon.value == true) {
            imageView.load(R.drawable.inset_users_three)
            return
        }

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
            if (image != null) {
                imageView.load(image) {
                    listener(
                        onError = { _, _ ->
                            imageView.load(getErrorImageLoader(context, model, size, textSize))
                        }
                    )
                }
            } else {
                imageView.load(getErrorImageLoader(context, model, size, textSize))
            }
        } else {
            val w = if (size > 0) {
                AppUtils.getDimension(size).toInt()
            } else {
                AppUtils.getDimension(R.dimen.avatar_list_cell_size).toInt()
            }
            (context as AppCompatActivity).lifecycleScope.launch {
                val bitmap = ImageUtils.getBitmapFromMultipleAvatars(imageView.context, w, images)
                imageView.load(bitmap)
            }
        }
    } else {
        imageView.load(R.drawable.smiley)
    }
}

private fun getErrorImageLoader(
    context: Context,
    model: AbstractAvatarModel,
    size: Int,
    textSize: Int
): Any {
    val initials = model.initials.value.orEmpty()
    return if (initials.isEmpty() || initials == "+" || model.skipInitials.value == true) {
        if (model.defaultToConferenceIcon.value == true) {
            R.drawable.inset_meeting
        } else if (model.defaultToConversationIcon.value == true) {
            R.drawable.inset_users_three
        } else {
            R.drawable.inset_user_circle
        }
    } else {
        ImageUtils.getGeneratedAvatar(context, size, textSize, initials)
    }
}

@UiThread
@BindingAdapter("participantTextureView")
fun setParticipantTextureView(
    textureView: TextureView,
    model: ConferenceParticipantDeviceModel
) {
    model.setTextureView(textureView)
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

@BindingAdapter("android:layout_marginStart")
fun setConstraintLayoutStartMargin(view: View, margins: Float) {
    val params = view.layoutParams as ViewGroup.MarginLayoutParams
    val m = margins.toInt()
    params.marginStart = m
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
