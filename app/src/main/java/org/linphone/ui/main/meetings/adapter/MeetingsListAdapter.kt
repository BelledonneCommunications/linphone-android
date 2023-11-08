package org.linphone.ui.main.meetings.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.linphone.R
import org.linphone.databinding.MeetingListCellBinding
import org.linphone.databinding.MeetingsListDecorationBinding
import org.linphone.ui.main.meetings.model.MeetingModel
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter

class MeetingsListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<MeetingModel, RecyclerView.ViewHolder>(MeetingDiffCallback()), HeaderAdapter {
    val meetingClickedEvent: MutableLiveData<Event<MeetingModel>> by lazy {
        MutableLiveData<Event<MeetingModel>>()
    }

    val meetingLongClickedEvent: MutableLiveData<Event<MeetingModel>> by lazy {
        MutableLiveData<Event<MeetingModel>>()
    }

    override fun displayHeaderForPosition(position: Int): Boolean {
        if (position == 0) return true

        val previous = getItem(position - 1)
        val item = getItem(position)
        return previous.month != item.month
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val binding = MeetingsListDecorationBinding.inflate(LayoutInflater.from(context))
        val item = getItem(position)
        binding.header.text = item.month
        return binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: MeetingListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.meeting_list_cell,
            parent,
            false
        )
        binding.apply {
            lifecycleOwner = viewLifecycleOwner

            setOnClickListener {
                meetingClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                root.isSelected = true
                meetingLongClickedEvent.value = Event(model!!)
                true
            }
        }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        val binding: MeetingListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(meetingModel: MeetingModel) {
            with(binding) {
                model = meetingModel
                executePendingBindings()
            }
        }
    }

    private class MeetingDiffCallback : DiffUtil.ItemCallback<MeetingModel>() {
        override fun areItemsTheSame(oldItem: MeetingModel, newItem: MeetingModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MeetingModel, newItem: MeetingModel): Boolean {
            return oldItem.subject.value == newItem.subject.value && oldItem.time == newItem.time
        }
    }
}
