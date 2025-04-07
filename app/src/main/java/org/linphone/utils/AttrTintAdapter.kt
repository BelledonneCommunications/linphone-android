package org.linphone.utils

import android.util.TypedValue
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.databinding.BindingAdapter

@BindingAdapter("attrTint")
fun setAttrTint(imageView: ImageView, attrResId: Int) {
    if (attrResId != 0) { // Ensure a valid attribute resource ID is provided
        val typedValue = TypedValue()
        val theme = imageView.context.theme
        if (theme.resolveAttribute(attrResId, typedValue, true)) {
            val color = typedValue.data
            ImageViewCompat.setImageTintList(
                imageView,
                android.content.res.ColorStateList.valueOf(color)
            )
        }
    } else {
        // Clear the tint if no attribute is provided
        ImageViewCompat.setImageTintList(imageView, null)
    }
}
