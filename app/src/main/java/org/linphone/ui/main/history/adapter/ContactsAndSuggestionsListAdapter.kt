package org.linphone.ui.main.history.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.ContactListCellBinding
import org.linphone.databinding.StartCallSuggestionListCellBinding
import org.linphone.databinding.StartCallSuggestionListDecorationBinding
import org.linphone.ui.main.history.model.ContactOrSuggestionModel
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter

class ContactsAndSuggestionsListAdapter :
    ListAdapter<ContactOrSuggestionModel, RecyclerView.ViewHolder>(
        ContactOrSuggestionDiffCallback()
    ),
    HeaderAdapter {
    companion object {
        private const val CONTACT_TYPE = 0
        private const val SUGGESTION_TYPE = 1
    }

    val contactClickedEvent: MutableLiveData<Event<ContactOrSuggestionModel>> by lazy {
        MutableLiveData<Event<ContactOrSuggestionModel>>()
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        val model = getItem(position)
        if (model.friend == null) {
            if (position == 0) {
                return true
            }
            val previousModel = getItem(position - 1)
            return previousModel.friend != null
        } else if (position == 0) return true
        return false
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding = StartCallSuggestionListDecorationBinding.inflate(LayoutInflater.from(context))
        binding.header.text = if (position == 0) {
            if (getItemViewType(0) == SUGGESTION_TYPE) {
                AppUtils.getString(R.string.history_call_start_suggestions_list_title)
            } else {
                AppUtils.getString(R.string.history_call_start_contacts_list_title)
            }
        } else {
            AppUtils.getString(R.string.history_call_start_suggestions_list_title)
        }
        return binding.root
    }

    override fun getItemViewType(position: Int): Int {
        val model = getItem(position)
        return if (model.friend == null) SUGGESTION_TYPE else CONTACT_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            CONTACT_TYPE -> {
                val binding: ContactListCellBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.contact_list_cell,
                    parent,
                    false
                )
                binding.lifecycleOwner = parent.findViewTreeLifecycleOwner()
                ContactViewHolder(binding)
            }
            else -> {
                val binding: StartCallSuggestionListCellBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.start_call_suggestion_list_cell,
                    parent,
                    false
                )
                binding.apply {
                    lifecycleOwner = parent.findViewTreeLifecycleOwner()

                    setOnClickListener {
                        contactClickedEvent.value = Event(model!!)
                    }
                }
                SuggestionViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            CONTACT_TYPE -> (holder as ContactViewHolder).bind(getItem(position))
            else -> (holder as SuggestionViewHolder).bind(getItem(position))
        }
    }

    inner class ContactViewHolder(
        val binding: ContactListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(contactOrSuggestionModel: ContactOrSuggestionModel) {
            with(binding) {
                model = contactOrSuggestionModel.avatarModel.value
                setOnClickListener {
                    contactClickedEvent.value = Event(contactOrSuggestionModel)
                }
                executePendingBindings()
            }
        }
    }

    inner class SuggestionViewHolder(
        val binding: StartCallSuggestionListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(contactOrSuggestionModel: ContactOrSuggestionModel) {
            with(binding) {
                model = contactOrSuggestionModel
                executePendingBindings()
            }
        }
    }

    private class ContactOrSuggestionDiffCallback : DiffUtil.ItemCallback<ContactOrSuggestionModel>() {
        override fun areItemsTheSame(
            oldItem: ContactOrSuggestionModel,
            newItem: ContactOrSuggestionModel
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: ContactOrSuggestionModel,
            newItem: ContactOrSuggestionModel
        ): Boolean {
            return false
        }
    }
}
