package org.linphone.environment;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.linphone.R;
import org.linphone.models.DimensionsEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads the list of browsers on the device for selection in a list or spinner.
 */
public final class EnvironmentSelectionAdapter extends BaseAdapter {

    private static final int LOADER_ID = 102;

    private final Context mContext;
    private ArrayList<DimensionsEnvironment> mEnvironments;

    /**
     * Creates the adapter, using the loader manager from the specified activity.
     */
    public EnvironmentSelectionAdapter(@NonNull final Activity activity) {
        mContext = activity;
        initializeItemList();
        activity.getLoaderManager().initLoader(
                LOADER_ID,
                null,
                new EnvironmentLoaderCallbacks());
    }

    public int getItemIndex(String itemId) {
        for (var i = 0; i < mEnvironments.size(); i++) {
            var env = mEnvironments.get(i);
            if (env != null && env.getId().equals(itemId)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public int getCount() {
        return mEnvironments.size();
    }

    @Override
    public DimensionsEnvironment getItem(int position) {
        return mEnvironments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext)
                    .inflate(R.layout.browser_selector_layout, parent, false);
        }

        DimensionsEnvironment env = mEnvironments.get(position);

        if (env != null) {
            TextView labelView = (TextView) convertView.findViewById(R.id.browser_label);

            CharSequence label = env.getName();
            labelView.setText(label);
        }

        return convertView;
    }

    // TODO: I don't think this is the correct way to handle rebinding, but
    // it appears to work so leaving this in for now.
    @Override
    public void notifyDataSetChanged() {
        final var svc = DimensionsEnvironmentService.Companion.getInstance(mContext);
        mEnvironments.clear();
        mEnvironments.add(null);
        mEnvironments.addAll(svc.getEnvironmentList());

        super.notifyDataSetChanged();
    }

    private void initializeItemList() {
        mEnvironments = new ArrayList<>();
        mEnvironments.add(null);
    }

    private final class EnvironmentLoaderCallbacks implements LoaderCallbacks<List<DimensionsEnvironment>> {

        @Override
        public Loader<List<DimensionsEnvironment>> onCreateLoader(int id, Bundle args) {
            return new EnvironmentLoader(mContext);
        }

        @Override
        public void onLoadFinished(Loader<List<DimensionsEnvironment>> loader, List<DimensionsEnvironment> data) {
            initializeItemList();
            mEnvironments.addAll(data);
            notifyDataSetChanged();
        }

        @Override
        public void onLoaderReset(Loader<List<DimensionsEnvironment>> loader) {
            initializeItemList();
            notifyDataSetChanged();
        }
    }


    private static class EnvironmentLoader extends AsyncTaskLoader<List<DimensionsEnvironment>> {

        private List<DimensionsEnvironment> mResult;
        private final DimensionsEnvironmentService mEnvironmentService;

        EnvironmentLoader(final Context context) {
            super(context);

            mEnvironmentService = DimensionsEnvironmentService.Companion.getInstance(context);
        }

        @Override
        public List<DimensionsEnvironment> loadInBackground() {
            return mEnvironmentService.getEnvironmentList();
        }

        @Override
        public void deliverResult(List<DimensionsEnvironment> data) {
            if (isReset()) {
                mResult = null;
                return;
            }

            mResult = data;
            super.deliverResult(mResult);
        }

        @Override
        protected void onStartLoading() {
            if (mResult != null) {
                deliverResult(mResult);
            }
            forceLoad();
        }

        @Override
        protected void onReset() {
            mResult = null;
        }

        @Override
        public void onCanceled(List<DimensionsEnvironment> data) {
            mResult = null;
            super.onCanceled(data);
        }
    }
}

