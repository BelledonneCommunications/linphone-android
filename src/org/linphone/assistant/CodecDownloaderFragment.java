package org.linphone.assistant;

/*
CodecDownloaderFragment.java
Copyright (C) 2016  Belledonne Communications, Grenoble, France

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

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.OpenH264DownloadHelperListener;
import org.linphone.core.PayloadType;
import org.linphone.tools.OpenH264DownloadHelper;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * @author Erwan CROZE
 */
public class CodecDownloaderFragment extends Fragment {
	private Handler mHandler = new Handler();
	private TextView question;
	private TextView downloading;
	private TextView downloaded;
	private Button yes;
	private Button no;
	private Button ok;
	private ProgressBar bar;
	private TextView downloadingInfo;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.assistant_codec_downloader, container, false);

		question = (TextView) view.findViewById(R.id.question);
		downloading = (TextView) view.findViewById(R.id.downloading);
		downloaded = (TextView) view.findViewById(R.id.downloaded);
		yes = (Button) view.findViewById(R.id.answerYes);
		no = (Button) view.findViewById(R.id.answerNo);
		ok = (Button) view.findViewById(R.id.answerOk);
		bar = (ProgressBar) view.findViewById(R.id.progressBar);
		downloadingInfo = (TextView) view.findViewById(R.id.downloadingInfo);

		final OpenH264DownloadHelper codecDownloader = LinphoneManager.getInstance().getOpenH264DownloadHelper();
		final OpenH264DownloadHelperListener codecListener = new OpenH264DownloadHelperListener() {

			@Override
			public void OnProgress(final int current, final int max) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						if (current <= max) {
							hideAllItems();
							downloadingInfo.setText(current + " / " + max);
							downloadingInfo.setVisibility(View.VISIBLE);
							downloading.setVisibility(View.VISIBLE);
							bar.setMax(max);
							bar.setProgress(current);
							bar.setVisibility(View.VISIBLE);
						} else {
							hideAllItems();
							LinphoneManager.getLc().reloadMsPlugins(null);
							downloaded.setVisibility(View.VISIBLE);
							enabledH264(true);
							AssistantActivity.instance().endDownloadCodec();
						}
					}
				});
			}

			@Override
			public void OnError(final String error) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						hideAllItems();
						downloaded.setText("Sorry an error has occurred.");
						downloaded.setVisibility(View.VISIBLE);
						ok.setVisibility(View.VISIBLE);
						enabledH264(false);
						AssistantActivity.instance().endDownloadCodec();
					}
				});
			}
		};

		codecDownloader.setOpenH264HelperListener(codecListener);

		yes.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				hideAllItems();
				bar.setVisibility(View.VISIBLE);
				codecDownloader.downloadCodec();
			}
		});

		no.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				enabledH264(false);
				AssistantActivity.instance().endDownloadCodec();
			}
		});
		hideAllItems();

		if (savedInstanceState != null) {
			if (savedInstanceState.containsKey("question"))
				question.setVisibility((Integer) savedInstanceState.getSerializable("question"));
			else
				question.setVisibility(View.VISIBLE);

			if (savedInstanceState.containsKey("yes"))
				yes.setVisibility((Integer) savedInstanceState.getSerializable("yes"));
			else
				yes.setVisibility(View.VISIBLE);

			if (savedInstanceState.containsKey("no"))
				no.setVisibility((Integer) savedInstanceState.getSerializable("no"));
			else
				no.setVisibility(View.VISIBLE);

			if (savedInstanceState.containsKey("downloading"))
				downloading.setVisibility((Integer) savedInstanceState.getSerializable("downloading"));

			if (savedInstanceState.containsKey("downloaded"))
				downloaded.setVisibility((Integer) savedInstanceState.getSerializable("downloaded"));

			if (savedInstanceState.containsKey("bar"))
				bar.setVisibility((Integer) savedInstanceState.getSerializable("bar"));

			if (savedInstanceState.containsKey("downloadingInfo"))
				downloadingInfo.setVisibility((Integer) savedInstanceState.getSerializable("downloadingInfo"));

			if (savedInstanceState.containsKey("ok"))
				ok.setVisibility((Integer) savedInstanceState.getSerializable("ok"));
		} else {
			yes.setVisibility(View.VISIBLE);
			question.setVisibility(View.VISIBLE);
			no.setVisibility(View.VISIBLE);
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (question != null) outState.putSerializable("question", question.getVisibility());
		if (downloading != null) outState.putSerializable("downloading", downloading.getVisibility());
		if (downloaded != null) outState.putSerializable("downloaded", downloaded.getVisibility());
		if (yes != null) outState.putSerializable("yes", yes.getVisibility());
		if (no != null) outState.putSerializable("no", no.getVisibility());
		if (ok != null) outState.putSerializable("ok", ok.getVisibility());
		if (bar != null) outState.putSerializable("bar", bar.getVisibility());
		if (downloadingInfo != null) outState.putSerializable("downloadingInfo", downloadingInfo.getVisibility());
		super.onSaveInstanceState(outState);
	}

	private void hideAllItems() {
		if (question != null) question.setVisibility(View.INVISIBLE);
		if (downloading != null) downloading.setVisibility(View.INVISIBLE);
		if (downloaded != null) downloaded.setVisibility(View.INVISIBLE);
		if (yes != null) yes.setVisibility(View.INVISIBLE);
		if (no != null) no.setVisibility(View.INVISIBLE);
		if (ok != null) ok.setVisibility(View.INVISIBLE);
		if (bar != null) bar.setVisibility(View.INVISIBLE);
		if (downloadingInfo != null) downloadingInfo.setVisibility(View.INVISIBLE);
	}

	private void enabledH264(boolean enable) {
		PayloadType h264 = null;
		for (PayloadType pt : LinphoneManager.getLc().getVideoCodecs()) {
			if (pt.getMime().equals("H264")) h264 = pt;
		}

		if (h264 != null) {
			try {
				LinphoneManager.getLc().enablePayloadType(h264, enable);
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
			}
		}
	}
}
