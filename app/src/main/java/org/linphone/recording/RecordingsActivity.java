package org.linphone.recording;

/*
RecordingsActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.utils.FileUtils;
import org.linphone.utils.SelectableHelper;
import org.linphone.utils.ThemableActivity;

public class RecordingsActivity extends ThemableActivity
        implements SelectableHelper.DeleteListener, RecordingViewHolder.ClickListener {
    private RecyclerView mRecordingList;
    private List<Recording> mRecordings;
    private TextView mNoRecordings;
    private ImageView mBackButton;
    private RecordingsAdapter mRecordingsAdapter;
    private LinearLayoutManager mLayoutManager;
    private SelectableHelper mSelectableHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.recordings_list);

        mBackButton = findViewById(R.id.back);
        mBackButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });

        mSelectableHelper = new SelectableHelper(findViewById(R.id.root_layout), this);

        mRecordingList = findViewById(R.id.recording_list);
        mNoRecordings = findViewById(R.id.no_recordings);

        mLayoutManager = new LinearLayoutManager(this);
        mRecordingList.setLayoutManager(mLayoutManager);

        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(this, mLayoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.divider));
        mRecordingList.addItemDecoration(dividerItemDecoration);

        mRecordings = new ArrayList<>();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO: permission check for external storage

        LinphoneManager.getInstance().setAudioManagerModeNormal();
        LinphoneManager.getInstance().routeAudioToSpeaker();

        removeDeletedRecordings();
        searchForRecordings();

        hideRecordingListAndDisplayMessageIfEmpty();
        mRecordingsAdapter = new RecordingsAdapter(this, mRecordings, this, mSelectableHelper);
        mRecordingList.setAdapter(mRecordingsAdapter);
        mSelectableHelper.setAdapter(mRecordingsAdapter);
        mSelectableHelper.setDialogMessage(R.string.recordings_delete_dialog);
    }

    @Override
    protected void onPause() {
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
        String recordingsDirectory = FileUtils.getRecordingsDirectory(this);
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
        String recordingsDirectory = FileUtils.getRecordingsDirectory(this);
        File directory = new File(recordingsDirectory);

        if (directory.exists() && directory.isDirectory()) {
            File[] existingRecordings = directory.listFiles();
            if (existingRecordings == null) return;

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
                        mRecordings.add(new Recording(this, f.getPath()));
                    }
                }
            }

            Collections.sort(mRecordings);
        }
    }
}
