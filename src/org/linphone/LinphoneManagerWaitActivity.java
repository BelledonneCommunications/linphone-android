/*
LinphoneManagerWaitActivity.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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
package org.linphone;

import org.linphone.core.Log;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

/**
 * Activity requiring access to LinphoneManager should inherit from this class.
 * 
 * @author Guillaume Beraudo
 *
 */
public abstract class LinphoneManagerWaitActivity extends SoftVolumeActivity {

	private final int waitServiceDialogId = 314159265;
	private Handler mHandler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (LinphoneService.isReady()) {
			onLinphoneManagerAvailable(LinphoneManager.getInstance());
		} else {
			showDialog(waitServiceDialogId);
			thread = new ServiceWaitThread();
			thread.start();
		}
	}

	private ServiceWaitThread thread;

	@Override
	protected void onDestroy() {
		if (thread != null) thread.interrupt();
		super.onDestroy();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == waitServiceDialogId) {
			View v = getLayoutInflater().inflate((R.layout.wait_service_dialog), null);
			return new AlertDialog.Builder(this).setView(v).setCancelable(false).create();
		}
		return super.onCreateDialog(id);
	}

	protected abstract void onLinphoneManagerAvailable(LinphoneManager m);

	private void dismissDialogFromThread(final int id) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					dismissDialog(id);
				} catch (Throwable e) {
					// Discarding exception which may be thrown if the dialog wasn't showing.
				}
			}
		});
	}

	private class ServiceWaitThread extends Thread {
		@Override
		public void run() {
			while (!LinphoneService.isReady()) {
				try {
					sleep(30);
				} catch (InterruptedException e) {
					Log.e("waiting thread sleep() has been interrupted, exiting as requested");
					dismissDialogFromThread(waitServiceDialogId); // FIXME, may not be the best thing to do
					return;
				}
			}
			onLinphoneManagerAvailable(LinphoneManager.getInstance());
			dismissDialogFromThread(waitServiceDialogId);
			super.run();
		}
	}

}
