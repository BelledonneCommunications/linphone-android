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

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatEditText
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LifecycleOwner
import org.linphone.R
import org.linphone.databinding.DialogAssistantAcceptConditionsAndPolicyBinding
import org.linphone.databinding.DialogAssistantCreateAccountConfirmPhoneNumberBinding
import org.linphone.databinding.DialogCallConfirmTransferBinding
import org.linphone.databinding.DialogCancelContactChangesBinding
import org.linphone.databinding.DialogCancelMeetingBinding
import org.linphone.databinding.DialogContactConfirmTrustCallBinding
import org.linphone.databinding.DialogContactTrustProcessBinding
import org.linphone.databinding.DialogDeleteContactBinding
import org.linphone.databinding.DialogKickFromConferenceBinding
import org.linphone.databinding.DialogManageAccountInternationalPrefixHelpBinding
import org.linphone.databinding.DialogMergeCallsIntoConferenceBinding
import org.linphone.databinding.DialogOpenExportFileBinding
import org.linphone.databinding.DialogPickNumberOrAddressBinding
import org.linphone.databinding.DialogRemoveAccountBinding
import org.linphone.databinding.DialogRemoveAllCallLogsBinding
import org.linphone.databinding.DialogRemoveCallLogsBinding
import org.linphone.databinding.DialogRemoveConversationHistoryBinding
import org.linphone.databinding.DialogSetOrEditGroupSubjectBindingImpl
import org.linphone.databinding.DialogStartGroupCallFromConversationBinding
import org.linphone.databinding.DialogUpdateAccountPasswordAfterRegisterFailureBinding
import org.linphone.databinding.DialogUpdateAccountPasswordBinding
import org.linphone.databinding.DialogUpdateAvailableBinding
import org.linphone.databinding.DialogZrtpSasValidationBinding
import org.linphone.databinding.DialogZrtpSecurityAlertBinding
import org.linphone.ui.assistant.model.AcceptConditionsAndPolicyDialogModel
import org.linphone.ui.assistant.model.ConfirmPhoneNumberDialogModel
import org.linphone.ui.call.model.ConfirmCallTransferDialogModel
import org.linphone.ui.call.model.ZrtpAlertDialogModel
import org.linphone.ui.call.model.ZrtpSasConfirmationDialogModel
import org.linphone.ui.main.contacts.model.ContactTrustDialogModel
import org.linphone.ui.main.contacts.model.NumberOrAddressPickerDialogModel
import org.linphone.ui.main.contacts.model.TrustCallDialogModel
import org.linphone.ui.main.history.model.ConfirmationDialogModel
import org.linphone.ui.main.model.AuthRequestedDialogModel
import org.linphone.ui.main.model.GroupSetOrEditSubjectDialogModel
import org.linphone.ui.main.settings.model.UpdatePasswordDialogModel

class DialogUtils {
    companion object {
        @UiThread
        fun getAcceptConditionsAndPrivacyDialog(
            context: Context,
            viewModel: AcceptConditionsAndPolicyDialogModel
        ): Dialog {
            val binding: DialogAssistantAcceptConditionsAndPolicyBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_assistant_accept_conditions_and_policy,
                null,
                false
            )
            binding.viewModel = viewModel
            binding.message.movementMethod = LinkMovementMethod.getInstance()

            return getDialog(context, binding)
        }

        @UiThread
        fun getAccountCreationPhoneNumberConfirmationDialog(
            context: Context,
            viewModel: ConfirmPhoneNumberDialogModel
        ): Dialog {
            val binding: DialogAssistantCreateAccountConfirmPhoneNumberBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_assistant_create_account_confirm_phone_number,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getAccountInternationalPrefixHelpDialog(context: Context): Dialog {
            val binding: DialogManageAccountInternationalPrefixHelpBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_manage_account_international_prefix_help,
                null,
                false
            )
            val dialog = getDialog(context, binding)

            binding.setDismissClickListener {
                dialog.dismiss()
            }

            return dialog
        }

        @UiThread
        fun getConfirmAccountRemovalDialog(
            context: Context,
            viewModel: ConfirmationDialogModel,
            showDeleteAccountLink: Boolean
        ): Dialog {
            val binding: DialogRemoveAccountBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_remove_account,
                null,
                false
            )
            binding.viewModel = viewModel
            binding.message.visibility = if (showDeleteAccountLink) View.VISIBLE else View.GONE

            return getDialog(context, binding)
        }

        @UiThread
        fun getNumberOrAddressPickerDialog(
            context: Context,
            viewModel: NumberOrAddressPickerDialogModel
        ): Dialog {
            val binding: DialogPickNumberOrAddressBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_pick_number_or_address,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getContactTrustCallConfirmationDialog(
            context: Context,
            viewModel: TrustCallDialogModel
        ): Dialog {
            val binding: DialogContactConfirmTrustCallBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_contact_confirm_trust_call,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getContactTrustProcessExplanationDialog(
            context: Context,
            viewModel: ContactTrustDialogModel
        ): Dialog {
            val binding: DialogContactTrustProcessBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_contact_trust_process,
                null,
                false
            )
            binding.viewModel = viewModel

            val dialog = getDialog(context, binding)

            binding.setDismissClickListener {
                dialog.dismiss()
            }

            return dialog
        }

        @UiThread
        fun getDeleteContactConfirmationDialog(
            context: Context,
            viewModel: ConfirmationDialogModel,
            contactName: String
        ): Dialog {
            val binding: DialogDeleteContactBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_delete_contact,
                null,
                false
            )
            binding.viewModel = viewModel
            binding.title.text = context.getString(
                R.string.contact_dialog_delete_title,
                contactName
            )

            return getDialog(context, binding)
        }

        @UiThread
        fun getRemoveCallLogsConfirmationDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogRemoveCallLogsBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_remove_call_logs,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getRemoveAllCallLogsConfirmationDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogRemoveAllCallLogsBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_remove_all_call_logs,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getCancelContactChangesConfirmationDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogCancelContactChangesBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_cancel_contact_changes,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getSetOrEditGroupSubjectDialog(
            context: Context,
            lifecycleOwner: LifecycleOwner,
            viewModel: GroupSetOrEditSubjectDialogModel
        ): Dialog {
            val binding: DialogSetOrEditGroupSubjectBindingImpl = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_set_or_edit_group_subject,
                null,
                false
            )
            binding.lifecycleOwner = lifecycleOwner
            binding.viewModel = viewModel
            // For some reason, binding.subject triggers an error on Android Studio...
            binding.root.findViewById<AppCompatEditText>(R.id.subject)?.requestFocus()

            return getDialog(context, binding)
        }

        @UiThread
        fun getConfirmGroupCallDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogStartGroupCallFromConversationBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_start_group_call_from_conversation,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getDeleteConversationHistoryConfirmationDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogRemoveConversationHistoryBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_remove_conversation_history,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getOpenOrExportFileDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogOpenExportFileBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_open_export_file,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getUpdateAvailableDialog(
            context: Context,
            viewModel: ConfirmationDialogModel,
            message: String
        ): Dialog {
            val binding: DialogUpdateAvailableBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_update_available,
                null,
                false
            )
            binding.viewModel = viewModel
            binding.message.text = message

            return getDialog(context, binding)
        }

        @UiThread
        fun getAuthRequestedDialog(
            context: Context,
            viewModel: AuthRequestedDialogModel
        ): Dialog {
            val binding: DialogUpdateAccountPasswordAfterRegisterFailureBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_update_account_password_after_register_failure,
                null,
                false
            )
            binding.viewModel = viewModel
            binding.lifecycleOwner = context as LifecycleOwner

            return getDialog(context, binding)
        }

        @UiThread
        fun getUpdatePasswordDialog(
            context: Context,
            viewModel: UpdatePasswordDialogModel
        ): Dialog {
            val binding: DialogUpdateAccountPasswordBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_update_account_password,
                null,
                false
            )
            binding.viewModel = viewModel
            binding.lifecycleOwner = context as LifecycleOwner

            return getDialog(context, binding)
        }

        @UiThread
        fun getZrtpSasConfirmationDialog(
            context: Context,
            viewModel: ZrtpSasConfirmationDialogModel
        ): Dialog {
            val binding: DialogZrtpSasValidationBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_zrtp_sas_validation,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getZrtpAlertDialog(
            context: Context,
            viewModel: ZrtpAlertDialogModel
        ): Dialog {
            val binding: DialogZrtpSecurityAlertBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_zrtp_security_alert,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getConfirmMergeCallsDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogMergeCallsIntoConferenceBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_merge_calls_into_conference,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getConfirmCallTransferCallDialog(
            context: Context,
            viewModel: ConfirmCallTransferDialogModel
        ): Dialog {
            val binding: DialogCallConfirmTransferBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_call_confirm_transfer,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        fun getKickConferenceParticipantConfirmationDialog(
            context: Context,
            viewModel: ConfirmationDialogModel,
            displayName: String
        ): Dialog {
            val binding: DialogKickFromConferenceBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_kick_from_conference,
                null,
                false
            )
            binding.viewModel = viewModel
            binding.title.text = context.getString(
                R.string.conference_confirm_removing_participant_dialog_title,
                displayName
            )

            return getDialog(context, binding)
        }

        @UiThread
        fun getCancelMeetingDialog(
            context: Context,
            viewModel: ConfirmationDialogModel
        ): Dialog {
            val binding: DialogCancelMeetingBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.dialog_cancel_meeting,
                null,
                false
            )
            binding.viewModel = viewModel

            return getDialog(context, binding)
        }

        @UiThread
        private fun getDialog(context: Context, binding: ViewDataBinding): Dialog {
            val dialog = Dialog(context, R.style.Theme_LinphoneDialog)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(binding.root)

            dialog.window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

                val d: Drawable = ColorDrawable(
                    context.getColor(R.color.bc_black)
                )
                d.alpha = 153 // 60% opacity
                setBackgroundDrawable(d)
            }

            return dialog
        }
    }
}
