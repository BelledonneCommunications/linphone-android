package org.linphone.utils

import android.widget.TextView
import androidx.databinding.BindingAdapter

@BindingAdapter("textStyle")
fun setTextStyle(textView: TextView, styleResId: Int) {
    if (styleResId > 0) { // Ensure a valid style resource ID is provided
        textView.setTextAppearance(styleResId) // Apply the style programmatically
    }
}
