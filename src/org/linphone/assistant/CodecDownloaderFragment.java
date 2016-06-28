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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.OpenH264DownloadHelperListener;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactoryImpl;
import org.linphone.core.PayloadType;
import org.linphone.tools.OpenH264DownloadHelper;

/**
 * @author Erwan CROZE
 */
public class CodecDownloaderFragment extends Fragment {
		private Handler mHandler = new Handler();

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.assistant_codec_downloader, container, false);

		final TextView question = (TextView) view.findViewById(R.id.question);
		final TextView downloading = (TextView) view.findViewById(R.id.downloading);
		final TextView downloaded = (TextView) view.findViewById(R.id.downloaded);
		final Button yes = (Button) view.findViewById(R.id.answerYes);
		final Button no = (Button) view.findViewById(R.id.answerNo);
		final Button ok = (Button) view.findViewById(R.id.answerOk);
		final ProgressBar bar = (ProgressBar) view.findViewById(R.id.progressBar);
		final TextView downloadingInfo = (TextView) view.findViewById(R.id.downloadingInfo);

		final OpenH264DownloadHelper codecDownloader = new OpenH264DownloadHelper();
		codecDownloader.setFileDirection(LinphoneManager.getInstance().getContext().getFilesDir().toString());
		final OpenH264DownloadHelperListener codecListener = new OpenH264DownloadHelperListener() {

			@Override
			public void OnProgress(final int current, final int max) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						if (current <= max) {
							downloadingInfo.setText(current + " / " + max);
							downloadingInfo.setVisibility(View.VISIBLE);
							bar.setMax(max);
							bar.setProgress(current);
							bar.setVisibility(View.VISIBLE);
						} else {
							downloadingInfo.setVisibility(View.INVISIBLE);
							bar.setVisibility(View.INVISIBLE);
							LinphoneCoreFactoryImpl.loadOptionalLibraryWithPath(LinphoneManager.getInstance().getContext().getFilesDir() + "/" + codecDownloader.getNameLib());
							LinphoneManager.getLc().reloadMsPlugins(null);
							downloading.setVisibility(View.INVISIBLE);
							downloaded.setVisibility(View.VISIBLE);
							enabledH264(true);
							AssistantActivity.instance().endDownloadCodec();
						}
					}
				});
			}

			@Override
			public void OnError (final String error){
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						question.setVisibility(View.INVISIBLE);
						downloading.setVisibility(View.INVISIBLE);
						yes.setVisibility(View.INVISIBLE);
						no.setVisibility(View.INVISIBLE);
						bar.setVisibility(View.INVISIBLE);
						downloadingInfo.setVisibility(View.INVISIBLE);
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
		downloading.setVisibility(View.INVISIBLE);
		downloaded.setVisibility(View.INVISIBLE);
		bar.setVisibility(View.INVISIBLE);
		downloadingInfo.setVisibility(View.INVISIBLE);
		ok.setVisibility(View.INVISIBLE);

		yes.setOnClickListener(new View.OnClickListener() {
								   @Override
								   public void onClick(View v) {
									   question.setVisibility(View.INVISIBLE);
									   yes.setVisibility(View.INVISIBLE);
									   no.setVisibility(View.INVISIBLE);
									   downloading.setVisibility(View.VISIBLE);
									   ok.setVisibility(View.INVISIBLE);
									   bar.setVisibility(View.VISIBLE);
									   downloadingInfo.setVisibility(View.INVISIBLE);
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

		return view;
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
