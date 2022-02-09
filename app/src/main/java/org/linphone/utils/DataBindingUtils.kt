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
package org.linphone.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.databinding.*
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.switchmaterial.SwitchMaterial
import org.linphone.BR
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.main.settings.SettingListener
import org.linphone.contact.ContactAvatarView
import org.linphone.core.tools.Log
import org.linphone.views.VoiceRecordProgressBar

/**
 * This file contains all the data binding necessary for the app
 */

@BindingAdapter("android:src")
fun ImageView.setSourceImageResource(resource: Int) {
    this.setImageResource(resource)
}

@BindingAdapter("android:src")
fun ImageView.setSourceImageBitmap(bitmap: Bitmap?) {
    if (bitmap != null) this.setImageBitmap(bitmap)
}

@BindingAdapter("android:contentDescription")
fun ImageView.setContentDescription(resource: Int) {
    if (resource == 0) {
        Log.w("Can't set content description with resource id 0")
        return
    }
    this.contentDescription = context.getString(resource)
}

@BindingAdapter("android:textStyle")
fun TextView.setTypeface(typeface: Int) {
    this.setTypeface(null, typeface)
}

@BindingAdapter("android:layout_height")
fun View.setLayoutHeight(dimension: Float) {
    this.layoutParams.height = dimension.toInt()
}

@BindingAdapter("android:maxHeight")
fun ImageView.setImageMaxHeight(dimension: Float) {
    this.maxHeight = dimension.toInt()
}

@BindingAdapter("android:layout_size")
fun View.setLayoutSize(dimension: Float) {
    if (dimension == 0f) return
    this.layoutParams.height = dimension.toInt()
    this.layoutParams.width = dimension.toInt()
}

@BindingAdapter("android:background")
fun LinearLayout.setBackground(resource: Int) {
    this.setBackgroundResource(resource)
}

@Suppress("DEPRECATION")
@BindingAdapter("style")
fun TextView.setStyle(resource: Int) {
    this.setTextAppearance(context, resource)
}

@BindingAdapter("android:layout_marginLeft")
fun setLeftMargin(view: View, margin: Float) {
    val layoutParams = view.layoutParams as RelativeLayout.LayoutParams
    layoutParams.leftMargin = margin.toInt()
    view.layoutParams = layoutParams
}

@BindingAdapter("android:layout_marginRight")
fun setRightMargin(view: View, margin: Float) {
    val layoutParams = view.layoutParams as RelativeLayout.LayoutParams
    layoutParams.rightMargin = margin.toInt()
    view.layoutParams = layoutParams
}

@BindingAdapter("android:layout_weight")
fun setLayoutWeight(view: View, weight: Float) {
    val layoutParams = view.layoutParams as LinearLayout.LayoutParams
    layoutParams.weight = weight
    view.layoutParams = layoutParams
}

@BindingAdapter("android:layout_alignLeft")
fun setLayoutLeftAlign(view: View, oldTargetId: Int, newTargetId: Int) {
    val layoutParams = view.layoutParams as RelativeLayout.LayoutParams
    if (oldTargetId != 0) layoutParams.removeRule(RelativeLayout.ALIGN_LEFT)
    if (newTargetId != 0) layoutParams.addRule(RelativeLayout.ALIGN_LEFT, newTargetId)
    view.layoutParams = layoutParams
}

@BindingAdapter("android:layout_alignRight")
fun setLayoutRightAlign(view: View, oldTargetId: Int, newTargetId: Int) {
    val layoutParams = view.layoutParams as RelativeLayout.LayoutParams
    if (oldTargetId != 0) layoutParams.removeRule(RelativeLayout.ALIGN_RIGHT)
    if (newTargetId != 0) layoutParams.addRule(RelativeLayout.ALIGN_RIGHT, newTargetId)
    view.layoutParams = layoutParams
}

@BindingAdapter("android:layout_toLeftOf")
fun setLayoutToLeftOf(view: View, oldTargetId: Int, newTargetId: Int) {
    val layoutParams = view.layoutParams as RelativeLayout.LayoutParams
    if (oldTargetId != 0) layoutParams.removeRule(RelativeLayout.LEFT_OF)
    if (newTargetId != 0) layoutParams.addRule(RelativeLayout.LEFT_OF, newTargetId)
    view.layoutParams = layoutParams
}

@BindingAdapter("android:layout_gravity")
fun setLayoutGravity(view: View, gravity: Int) {
    val layoutParams = view.layoutParams as LinearLayout.LayoutParams
    layoutParams.gravity = gravity
    view.layoutParams = layoutParams
}

@BindingAdapter("layout_constraintGuide_percent")
fun setLayoutConstraintGuidePercent(guideline: Guideline, percent: Float) {
    val params = guideline.layoutParams as ConstraintLayout.LayoutParams
    params.guidePercent = percent
    guideline.layoutParams = params
}

@BindingAdapter("onClickToggleSwitch")
fun switchSetting(view: View, switchId: Int) {
    val switch: SwitchMaterial = view.findViewById(switchId)
    view.setOnClickListener { switch.isChecked = !switch.isChecked }
}

@BindingAdapter("onValueChanged")
fun editTextSetting(view: EditText, lambda: () -> Unit) {
    view.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            lambda()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

@BindingAdapter("onSettingImeDone")
fun editTextImeDone(view: EditText, lambda: () -> Unit) {
    view.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            lambda()

            view.clearFocus()

            val imm = view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            return@setOnEditorActionListener true
        }
        false
    }
}

@BindingAdapter("onFocusChangeVisibilityOf")
fun setEditTextOnFocusChangeVisibilityOf(editText: EditText, view: View) {
    editText.setOnFocusChangeListener { _, hasFocus ->
        view.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
    }
}

@BindingAdapter("selectedIndex", "settingListener")
fun spinnerSetting(spinner: Spinner, selectedIndex: Int, listener: SettingListener) {
    spinner.setSelection(selectedIndex, true)

    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            listener.onListValueChanged(position)
        }
    }
}

@BindingAdapter("onProgressChanged")
fun setListener(view: SeekBar, lambda: (Any) -> Unit) {
    view.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) lambda(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })
}

@BindingAdapter("entries")
fun setEntries(
    viewGroup: ViewGroup,
    entries: List<ViewDataBinding>?
) {
    viewGroup.removeAllViews()
    if (entries != null) {
        for (i in entries) {
            viewGroup.addView(i.root)
        }
    }
}

private fun <T> setEntries(
    viewGroup: ViewGroup,
    entries: List<T>?,
    layoutId: Int,
    onLongClick: View.OnLongClickListener?,
    parent: Any?
) {
    viewGroup.removeAllViews()
    if (entries != null) {
        val inflater = viewGroup.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        for (i in entries.indices) {
            val entry = entries[i]
            val binding = DataBindingUtil.inflate<ViewDataBinding>(
                inflater,
                layoutId,
                viewGroup,
                false
            )
            binding.setVariable(BR.data, entry)
            binding.setVariable(BR.longClickListener, onLongClick)
            binding.setVariable(BR.parent, parent)

            // This is a bit hacky...
            binding.lifecycleOwner = viewGroup.context as GenericActivity

            viewGroup.addView(binding.root)
        }
    }
}

@BindingAdapter("entries", "layout")
fun <T> setEntries(
    viewGroup: ViewGroup,
    entries: List<T>?,
    layoutId: Int
) {
    setEntries(viewGroup, entries, layoutId, null, null)
}

@BindingAdapter("entries", "layout", "onLongClick")
fun <T> setEntries(
    viewGroup: ViewGroup,
    entries: List<T>?,
    layoutId: Int,
    onLongClick: View.OnLongClickListener?
) {
    setEntries(viewGroup, entries, layoutId, onLongClick, null)
}

@BindingAdapter("entries", "layout", "parent")
fun <T> setEntries(
    viewGroup: ViewGroup,
    entries: List<T>?,
    layoutId: Int,
    parent: Any?
) {
    setEntries(viewGroup, entries, layoutId, null, parent)
}

@BindingAdapter("android:scaleType")
fun setImageViewScaleType(imageView: ImageView, scaleType: ImageView.ScaleType) {
    imageView.scaleType = scaleType
}

@BindingAdapter("glideAvatarFallback")
fun loadAvatarWithGlideFallback(imageView: ImageView, path: String?) {
    if (path != null && path.isNotEmpty() && FileUtils.isExtensionImage(path)) {
        GlideApp.with(imageView)
            .load(path)
            .signature(ObjectKey(coreContext.contactsManager.latestContactFetch))
            .apply(RequestOptions.circleCropTransform())
            .into(imageView)
    } else {
        Log.w("[Data Binding] [Glide] Can't load $path")
        imageView.setImageResource(R.drawable.avatar)
    }
}

@BindingAdapter("glidePath")
fun loadImageWithGlide(imageView: ImageView, path: String) {
    if (path.isNotEmpty() && FileUtils.isExtensionImage(path)) {
        if (corePreferences.vfsEnabled && path.endsWith(FileUtils.VFS_PLAIN_FILE_EXTENSION)) {
            GlideApp.with(imageView)
                .load(path)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(imageView)
        } else {
            GlideApp.with(imageView).load(path).into(imageView)
        }
    } else {
        Log.w("[Data Binding] [Glide] Can't load $path")
    }
}

@BindingAdapter("glideAvatar")
fun loadAvatarWithGlide(imageView: ImageView, path: Uri?) {
    loadAvatarWithGlide(imageView, path?.toString())
}

@BindingAdapter("glideAvatar")
fun loadAvatarWithGlide(imageView: ImageView, path: String?) {
    if (path != null) {
        GlideApp
            .with(imageView)
            .load(path)
            .signature(ObjectKey(coreContext.contactsManager.latestContactFetch))
            .apply(RequestOptions.circleCropTransform())
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.w("[Data Binding] [Glide] Can't load $path")
                    imageView.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    imageView.visibility = View.VISIBLE
                    return false
                }
            })
            .into(imageView)
    } else {
        imageView.visibility = View.GONE
    }
}

@BindingAdapter("showSecurityLevel")
fun ContactAvatarView.setShowAvatarSecurityLevel(visible: Boolean) {
    this.binding.securityBadgeVisibility = visible
}

@BindingAdapter("showLimeCapability")
fun ContactAvatarView.setShowLimeCapability(limeCapability: Boolean) {
    this.binding.showLimeCapability = limeCapability
}

@BindingAdapter("assistantPhoneNumberValidation")
fun addPhoneNumberEditTextValidation(editText: EditText, enabled: Boolean) {
    if (!enabled) return
    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            when {
                s?.matches(Regex("\\d+")) == false ->
                    editText.error =
                        editText.context.getString(R.string.assistant_error_phone_number_invalid_characters)
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

@BindingAdapter("assistantPhoneNumberPrefixValidation")
fun addPrefixEditTextValidation(editText: EditText, enabled: Boolean) {
    if (!enabled) return
    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (s == null || s.isEmpty() || !s.startsWith("+")) {
                editText.setText("+$s")
            }
        }
    })
}

@BindingAdapter("assistantUsernameValidation")
fun addUsernameEditTextValidation(editText: EditText, enabled: Boolean) {
    if (!enabled) return
    val usernameRegexp = corePreferences.config.getString(
        "assistant",
        "username_regex",
        "^[a-z0-9+_.\\-]*\$"
    )!!
    val usernameMaxLength = corePreferences.config.getInt("assistant", "username_max_length", 64)
    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            when {
                s?.matches(Regex(usernameRegexp)) == false ->
                    editText.error =
                        editText.context.getString(R.string.assistant_error_username_invalid_characters)
                s?.length ?: 0 > usernameMaxLength -> {
                    editText.error =
                        editText.context.getString(R.string.assistant_error_username_too_long)
                }
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

@BindingAdapter("emailConfirmationValidation")
fun addEmailEditTextValidation(editText: EditText, enabled: Boolean) {
    if (!enabled) return
    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (!Patterns.EMAIL_ADDRESS.matcher(s).matches()) {
                editText.error =
                    editText.context.getString(R.string.assistant_error_invalid_email_address)
            }
        }
    })
}

@BindingAdapter("urlConfirmationValidation")
fun addUrlEditTextValidation(editText: EditText, enabled: Boolean) {
    if (!enabled) return
    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (!Patterns.WEB_URL.matcher(s).matches()) {
                editText.error =
                    editText.context.getString(R.string.assistant_remote_provisioning_wrong_format)
            }
        }
    })
}

@BindingAdapter("passwordConfirmationValidation")
fun addPasswordConfirmationEditTextValidation(password: EditText, passwordConfirmation: EditText) {
    password.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (passwordConfirmation.text == null || s == null || passwordConfirmation.text.toString() != s.toString()) {
                passwordConfirmation.error =
                    passwordConfirmation.context.getString(R.string.assistant_error_passwords_dont_match)
            } else {
                passwordConfirmation.error = null // To clear other edit text field error
            }
        }
    })

    passwordConfirmation.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {}

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (password.text == null || s == null || password.text.toString() != s.toString()) {
                passwordConfirmation.error =
                    passwordConfirmation.context.getString(R.string.assistant_error_passwords_dont_match)
            }
        }
    })
}

@BindingAdapter("errorMessage")
fun setEditTextError(editText: EditText, error: String?) {
    if (error != editText.error) {
        editText.error = error
    }
}

@InverseBindingAdapter(attribute = "errorMessage")
fun getEditTextError(editText: EditText): String? {
    return editText.error?.toString()
}

@BindingAdapter("errorMessageAttrChanged")
fun setEditTextErrorListener(editText: EditText, attrChange: InverseBindingListener) {
    editText.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            attrChange.onChange()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            editText.error = null
            attrChange.onChange()
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

@BindingAdapter("app:max")
fun VoiceRecordProgressBar.setProgressMax(max: Int) {
    setMax(max)
}

@BindingAdapter("android:progress")
fun VoiceRecordProgressBar.setPrimaryProgress(progress: Int) {
    setProgress(progress)
}

@BindingAdapter("android:secondaryProgress")
fun VoiceRecordProgressBar.setSecProgress(progress: Int) {
    setSecondaryProgress(progress)
}

@BindingAdapter("app:secondaryProgressTint")
fun VoiceRecordProgressBar.setSecProgressTint(color: Int) {
    setSecondaryProgressTint(color)
}
