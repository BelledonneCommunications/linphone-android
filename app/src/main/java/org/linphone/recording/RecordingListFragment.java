package org.linphone.recording;

/*
RecordingListFragment.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.mediastream.Log;
import org.linphone.utils.FileUtils;
import org.linphone.utils.SelectableHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingListFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener, RecordingViewHolder.ClickListener, SelectableHelper.DeleteListener {
    private RecyclerView recordingList;
    private List<Recording> recordings;
    private TextView noRecordings;
    private RecordingAdapter recordingAdapter;
    private LinearLayoutManager layoutManager;
    private Context context;
    private SelectableHelper selectableHelper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.recordings, container, false);

        context = getActivity().getApplicationContext();
        selectableHelper = new SelectableHelper(view, this);

        recordingList = view.findViewById(R.id.recording_list);
        noRecordings = view.findViewById(R.id.no_recordings);

        layoutManager = new LinearLayoutManager(context);
        recordingList.setLayoutManager(layoutManager);

        //Divider between items
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recordingList.getContext(),
                layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(context.getResources().getDrawable(R.drawable.divider));
        recordingList.addItemDecoration(dividerItemDecoration);

        recordings = new ArrayList<>();

        return view;
    }

    private void hideRecordingListAndDisplayMessageIfEmpty() {
        if (recordings == null || recordings.isEmpty()) {
            noRecordings.setVisibility(View.VISIBLE);
            recordingList.setVisibility(View.GONE);
        } else {
            noRecordings.setVisibility(View.GONE);
            recordingList.setVisibility(View.VISIBLE);
        }
    }

    public void removeDeletedRecordings() {
        String recordingsDirectory = FileUtils.getRecordingsDirectory(context);
        File directory = new File(recordingsDirectory);

        if (directory.exists() && directory.isDirectory()) {
            File[] existingRecordings = directory.listFiles();

            for(Recording r : recordings) {
                boolean exists = false;
                for(File f : existingRecordings) {
                    if (f.getPath().equals(r.getRecordPath())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) recordings.remove(r);
            }

            Collections.sort(recordings);
        }
    }

    public void searchForRecordings() {
        String recordingsDirectory = FileUtils.getRecordingsDirectory(context);
        File directory = new File(recordingsDirectory);

        if (directory.exists() && directory.isDirectory()) {
            File[] existingRecordings = directory.listFiles();

            for(File f : existingRecordings) {
                boolean exists = false;
                for(Recording r : recordings) {
                    if (r.getRecordPath().equals(f.getPath())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    if (Recording.RECORD_PATTERN.matcher(f.getPath()).matches()) {
                        recordings.add(new Recording(context, f.getPath()));
                    }
                }
            }

            Collections.sort(recordings);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        LinphoneManager.getInstance().setAudioManagerModeNormal();
        LinphoneManager.getInstance().routeAudioToSpeaker();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.RECORDING_LIST);
        }

        removeDeletedRecordings();
        searchForRecordings();

        hideRecordingListAndDisplayMessageIfEmpty();
        recordingAdapter = new RecordingAdapter(getActivity().getApplicationContext(), recordings, this, selectableHelper);
        recordingList.setAdapter(recordingAdapter);
        selectableHelper.setAdapter(recordingAdapter);
        selectableHelper.setDialogMessage(R.string.recordings_delete_dialog);
    }

    @Override
    public void onPause() {
        super.onPause();

        LinphoneManager.getInstance().routeAudioToReceiver();

        // Close all opened recordings
        for (Recording r : recordings) {
            if (!r.isClosed()) {
                if (r.isPlaying()) r.pause();
                r.close();
            }
        }
    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (recordingAdapter.isEditionEnabled()) {
            Recording record = recordings.get(position);

            if (record.isPlaying()) record.pause();
            record.close();

            File recordingFile = new File(record.getRecordPath());
            if (recordingFile.delete()) {
                recordings.remove(record);
            }
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (recordingAdapter.isEditionEnabled()) {
            recordingAdapter.toggleSelection(position);
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!recordingAdapter.isEditionEnabled()) {
            selectableHelper.enterEditionMode();
        }
        recordingAdapter.toggleSelection(position);
        return true;
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        int size = recordingAdapter.getSelectedItemCount();
        for (int i = 0; i < size; i++) {
            Recording record = (Recording) objectsToDelete[i];

            if (record.isPlaying()) record.pause();
            record.close();

            File recordingFile = new File(record.getRecordPath());
            if (recordingFile.delete()) {
                recordings.remove(record);
            }
        }
    }
}
