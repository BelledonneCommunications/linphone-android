package org.linphone.ui.main.meetings.adapter

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
import org.linphone.databinding.MeetingListCellBinding
import org.linphone.databinding.MeetingsListDecorationBinding
import org.linphone.ui.main.meetings.model.MeetingListItemModel
import org.linphone.ui.main.meetings.model.MeetingModel
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter

class MeetingsListAdapter :
    ListAdapter<MeetingListItemModel, RecyclerView.ViewHolder>(
        MeetingDiffCallback()
    ),
    HeaderAdapter {
    companion object {
        const val MEETING = 1
        const val TODAY_INDICATOR = 2
    }

    var selectedAdapterPosition = -1

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
        return when (viewType) {
            TODAY_INDICATOR -> createTodayIndicatorViewHolder(parent)
            else -> createMeetingViewHolder(parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItem(position)
        if (data.isToday) {
            return TODAY_INDICATOR
        }
        return MEETING
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MeetingViewHolder) {
            holder.bind(getItem(position).model as MeetingModel)
        }
    }

    fun resetSelection() {
        notifyItemChanged(selectedAdapterPosition)
        selectedAdapterPosition = -1
    }

    private fun createMeetingViewHolder(parent: ViewGroup): MeetingViewHolder {
        val binding: MeetingListCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.meeting_list_cell,
            parent,
            false
        )
        val viewHolder = MeetingViewHolder(binding)
        binding.apply {
            lifecycleOwner = parent.findViewTreeLifecycleOwner()

            setOnClickListener {
                meetingClickedEvent.value = Event(model!!)
            }

            setOnLongClickListener {
                selectedAdapterPosition = viewHolder.bindingAdapterPosition
                root.isSelected = true
                meetingLongClickedEvent.value = Event(model!!)
                true
            }
        }
        return viewHolder
    }

    private fun createTodayIndicatorViewHolder(parent: ViewGroup): TodayIndicatorViewHolder {
        return TodayIndicatorViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.meeting_list_today_indicator,
                parent,
                false
            )
        )
    }

    inner class MeetingViewHolder(
        val binding: MeetingListCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @UiThread
        fun bind(meetingModel: MeetingModel) {
            with(binding) {
                model = meetingModel

                binding.root.isSelected = bindingAdapterPosition == selectedAdapterPosition

                executePendingBindings()
            }
        }
    }

    inner class TodayIndicatorViewHolder(
        val view: View
    ) : RecyclerView.ViewHolder(view)

    private class MeetingDiffCallback : DiffUtil.ItemCallback<MeetingListItemModel>() {
        override fun areItemsTheSame(oldItem: MeetingListItemModel, newItem: MeetingListItemModel): Boolean {
            if (oldItem.model is MeetingModel && newItem.model is MeetingModel) {
                return oldItem.model.id == newItem.model.id
            }
            return false
        }

        override fun areContentsTheSame(
            oldItem: MeetingListItemModel,
            newItem: MeetingListItemModel
        ): Boolean {
            if (oldItem.model is MeetingModel && newItem.model is MeetingModel) {
                return oldItem.model.subject.value == newItem.model.subject.value && oldItem.model.time == newItem.model.time
            }
            return false
        }
    }
}
