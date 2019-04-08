package org.linphone.recording;

/*
RecordingsFragment.java
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.utils.FileUtils;
import org.linphone.utils.SelectableHelper;

public class RecordingsFragment extends Fragment
        implements AdapterView.OnItemClickListener,
                RecordingViewHolder.ClickListener,
                SelectableHelper.DeleteListener {
    private RecyclerView mRecordingList;
    private List<Recording> mRecordings;
    private TextView mNoRecordings;
    private RecordingsAdapter mRecordingsAdapter;
    private LinearLayoutManager mLayoutManager;
    private Context mContext;
    private SelectableHelper mSelectableHelper;
    private ImageView mBackButton;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.recordings_list, container, false);

        mContext = getActivity().getApplicationContext();
        mSelectableHelper = new SelectableHelper(view, this);

        mRecordingList = view.findViewById(R.id.recording_list);
        mNoRecordings = view.findViewById(R.id.no_recordings);

        mBackButton = view.findViewById(R.id.back);
        if (getResources().getBoolean(R.bool.isTablet)) {
            mBackButton.setVisibility(View.INVISIBLE);
        } else {
            mBackButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            LinphoneActivity.instance().popBackStack();
                        }
                    });
        }

        mLayoutManager = new LinearLayoutManager(mContext);
        mRecordingList.setLayoutManager(mLayoutManager);

        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mRecordingList.getContext(), mLayoutManager.getOrientation());
        dividerItemDecoration.setDrawable(mContext.getResources().getDrawable(R.drawable.divider));
        mRecordingList.addItemDecoration(dividerItemDecoration);

        mRecordings = new ArrayList<>();

        return view;
    }

    private void hideRecordingListAndDisplayMessageIfEmpty() {
        if (mRecordings == null || mRecordings.isEmpty()) {
            mNoRecordings.setVisibility(View.VISIBLE);
            mRecordingList.setVisibility(View.GONE);
        } else {
            mNoRecordings.setVisibility(View.GONE);
            mRecordingList.setVisibility(View.VISIBLE);
        }
    }

    private void removeDeletedRecordings() {
        String recordingsDirectory = FileUtils.getRecordingsDirectory(mContext);
        File directory = new File(recordingsDirectory);

        if (directory.exists() && directory.isDirectory()) {
            File[] existingRecordings = directory.listFiles();

            for (Recording r : mRecordings) {
                boolean exists = false;
                for (File f : existingRecordings) {
                    if (f.getPath().equals(r.getRecordPath())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) mRecordings.remove(r);
            }

            Collections.sort(mRecordings);
        }
        hideRecordingListAndDisplayMessageIfEmpty();
    }

    private void searchForRecordings() {
        String recordingsDirectory = FileUtils.getRecordingsDirectory(mContext);
        File directory = new File(recordingsDirectory);

        if (directory.exists() && directory.isDirectory()) {
            File[] existingRecordings = directory.listFiles();

            for (File f : existingRecordings) {
                boolean exists = false;
                for (Recording r : mRecordings) {
                    if (r.getRecordPath().equals(f.getPath())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    if (Recording.RECORD_PATTERN.matcher(f.getPath()).matches()) {
                        mRecordings.add(new Recording(mContext, f.getPath()));
                    }
                }
            }

            Collections.sort(mRecordings);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // This is necessary, without it you won't be able to remove mRecordings as you won't be
        // allowed to.
        LinphoneActivity.instance().checkAndRequestExternalStoragePermission();

        LinphoneManager.getInstance().setAudioManagerModeNormal();
        LinphoneManager.getInstance().routeAudioToSpeaker();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.RECORDING_LIST);
        }

        removeDeletedRecordings();
        searchForRecordings();

        hideRecordingListAndDisplayMessageIfEmpty();
        mRecordingsAdapter =
                new RecordingsAdapter(
                        getActivity().getApplicationContext(),
                        mRecordings,
                        this,
                        mSelectableHelper);
        mRecordingList.setAdapter(mRecordingsAdapter);
        mSelectableHelper.setAdapter(mRecordingsAdapter);
        mSelectableHelper.setDialogMessage(R.string.recordings_delete_dialog);
    }

    @Override
    public void onPause() {
        super.onPause();

        LinphoneManager.getInstance().routeAudioToReceiver();

        // Close all opened mRecordings
        for (Recording r : mRecordings) {
            if (!r.isClosed()) {
                if (r.isPlaying()) r.pause();
                r.close();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mRecordingsAdapter.isEditionEnabled()) {
            Recording record = mRecordings.get(position);

            if (record.isPlaying()) record.pause();
            record.close();

            File recordingFile = new File(record.getRecordPath());
            if (recordingFile.delete()) {
                mRecordings.remove(record);
            }
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (mRecordingsAdapter.isEditionEnabled()) {
            mRecordingsAdapter.toggleSelection(position);
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mRecordingsAdapter.isEditionEnabled()) {
            mSelectableHelper.enterEditionMode();
        }
        mRecordingsAdapter.toggleSelection(position);
        return true;
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        int size = mRecordingsAdapter.getSelectedItemCount();
        for (int i = 0; i < size; i++) {
            Recording record = (Recording) objectsToDelete[i];

            if (record.isPlaying()) record.pause();
            record.close();

            File recordingFile = new File(record.getRecordPath());
            if (recordingFile.delete()) {
                mRecordings.remove(record);
            }
        }
        hideRecordingListAndDisplayMessageIfEmpty();
    }
}
