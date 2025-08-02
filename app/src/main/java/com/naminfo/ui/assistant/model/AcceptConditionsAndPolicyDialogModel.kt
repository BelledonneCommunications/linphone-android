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
package com.naminfo.ui.assistant.model

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import java.util.regex.Pattern
import com.naminfo.LinphoneApplication.Companion.coreContext
import com.naminfo.R
import org.linphone.core.tools.Log
import com.naminfo.utils.AppUtils
import com.naminfo.utils.Event

class AcceptConditionsAndPolicyDialogModel
    @UiThread
    constructor() {
    companion object {
        private const val TAG = "[Accept Terms & Policy Dialog Model]"
    }

    val message = MutableLiveData<SpannableString>()

    val dismissEvent = MutableLiveData<Event<Boolean>>()

    val conditionsAcceptedEvent = MutableLiveData<Event<Boolean>>()

    val generalTermsClickedEvent = MutableLiveData<Event<Boolean>>()

    val privacyPolicyClickedEvent = MutableLiveData<Event<Boolean>>()

    init {
        val generalTerms = AppUtils.getString(R.string.assistant_dialog_general_terms_label)
        val privacyPolicy = AppUtils.getString(R.string.assistant_dialog_privacy_policy_label)
        val label = coreContext.context.getString(
            R.string.assistant_dialog_general_terms_and_privacy_policy_message,
            generalTerms,
            privacyPolicy
        )
        val spannable = SpannableString(label)

        val termsMatcher = Pattern.compile(generalTerms).matcher(label)
        if (termsMatcher.find()) {
            val clickableSpan: ClickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    Log.i("$TAG Clicked on general terms link")
                    generalTermsClickedEvent.value = Event(true)
                }
            }
            spannable.setSpan(
                clickableSpan,
                termsMatcher.start(0),
                termsMatcher.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val policyMatcher = Pattern.compile(privacyPolicy).matcher(label)
        if (policyMatcher.find()) {
            val clickableSpan: ClickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    Log.i("$TAG Clicked on privacy policy link")
                    privacyPolicyClickedEvent.value = Event(true)
                }
            }
            spannable.setSpan(
                clickableSpan,
                policyMatcher.start(0),
                policyMatcher.end(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        message.value = spannable
    }

    @UiThread
    fun dismiss() {
        dismissEvent.value = Event(true)
    }

    @UiThread
    fun acceptConditions() {
        conditionsAcceptedEvent.value = Event(true)
    }
}
