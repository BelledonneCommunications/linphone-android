package org.linphone.activities.main.history.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.GsonBuilder
import org.linphone.R
import org.linphone.activities.main.history.viewmodels.RecordingInfoViewModel
import org.linphone.databinding.GenericListHeaderBinding
import org.linphone.databinding.RecordingInfoCellBinding
import org.linphone.utils.Event
import org.linphone.utils.HeaderAdapter

class RecordingInfoListAdapter(
    private val viewLifecycleOwner: LifecycleOwner
) : ListAdapter<RecordingInfoViewModel, RecyclerView.ViewHolder>(
    CallRecordingInfoDiffCallback()
),
    HeaderAdapter {

    val recordingSelected: MutableLiveData<Event<RecordingInfoViewModel>> by lazy {
        MutableLiveData<Event<RecordingInfoViewModel>>()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding: RecordingInfoCellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.recording_info_cell,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(getItem(position))
    }

    inner class ViewHolder(
        val binding: RecordingInfoCellBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recInfo: RecordingInfoViewModel) {
            with(binding) {
                viewModel = recInfo

                lifecycleOwner = viewLifecycleOwner

                setClickListener {
                    recordingSelected.postValue(Event(viewModel!!))
                }

                executePendingBindings()
            }
        }
    }

    private fun setClickListener(function: () -> Unit) { }

    override fun displayHeaderForPosition(position: Int): Boolean {
        return false
        /*
        if (position >= itemCount) return false

        val recInfo = getItem(position)
        val previousPosition = position - 1
        return if (previousPosition >= 0) {
            //val previousItemDate = getItem(previousPosition).calleeName
            //!TimestampUtils.isSameDay(date, previousItemDate)
        } else {
            true
        }
        */
    }

    override fun getHeaderViewForPosition(context: Context, position: Int): View {
        val recInfo = getItem(position)
        val binding: GenericListHeaderBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.generic_list_header,
            null,
            false
        )
        binding.title = recInfo.label

        binding.executePendingBindings()
        return binding.root
    }
}

private class CallRecordingInfoDiffCallback : DiffUtil.ItemCallback<RecordingInfoViewModel>() {
    private val gson = GsonBuilder().create()

    override fun areItemsTheSame(
        oldItem: RecordingInfoViewModel,
        newItem: RecordingInfoViewModel
    ): Boolean {
        return oldItem.info.id == newItem.info.id
    }

    override fun areContentsTheSame(
        oldItem: RecordingInfoViewModel,
        newItem: RecordingInfoViewModel
    ): Boolean {
        return gson.toJson(oldItem.info) == gson.toJson(newItem.info)
    }
}
