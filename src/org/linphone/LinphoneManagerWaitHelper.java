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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Handler;
import android.view.View;

/**
 * Activity requiring access to LinphoneManager should use this helper class.
 * 
 * @author Guillaume Beraudo
 *
 */
public final class LinphoneManagerWaitHelper {

	public static final int DIALOG_ID = 314159265;
	private Handler mHandler = new Handler();
	private LinphoneManagerReadyListener listener;
	private Activity activity;
	private boolean dialogIsShowing;
	private boolean notifyOnCreate;
	private boolean notifyOnResume;
	static boolean disabled;

	public LinphoneManagerWaitHelper(Activity activity, LinphoneManagerReadyListener listener) {
		this.listener = listener;
		this.activity = activity;
	}

	private ServiceWaitThread thread;

	public Dialog createWaitDialog() {
		View v = activity.getLayoutInflater().inflate((R.layout.wait_service_dialog), null);
		return new AlertDialog.Builder(activity).setView(v).setCancelable(false).create();
	}


	public synchronized void doManagerDependentOnCreate() {
		if (disabled || LinphoneService.isReady()) {
			listener.onCreateWhenManagerReady();
			return;
		}
		if (thread != null) {
			throw new RuntimeException("already waiting for Manager");
		}

		notifyOnCreate = true;

		thread = new ServiceWaitThread();
		thread.start();

		if (!dialogIsShowing) {
			activity.showDialog(LinphoneManagerWaitHelper.DIALOG_ID);
		}
	}

	public synchronized void doManagerDependentOnResume() {
		if (disabled || LinphoneService.isReady()) {
			listener.onResumeWhenManagerReady();
			return;
		}
		notifyOnResume = true;
		if (thread == null) {
			thread = new ServiceWaitThread();
			thread.start();
		}
		if (!dialogIsShowing) {
			activity.showDialog(LinphoneManagerWaitHelper.DIALOG_ID);
		}
	}

	private void dismissDialogFromThread(final int id) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					activity.dismissDialog(id);
				} catch (Throwable e) {
					// Discarding exception which may be thrown if the dialog wasn't showing.
				}
			}
		});
	}

	private class ServiceWaitThread extends Thread {
		private void onCreateWhenManagerReady() {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					listener.onCreateWhenManagerReady();
				}
			});
		}
		private void onResumeWhenManagerReady() {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					listener.onResumeWhenManagerReady();
				}
			});
		}
		@Override
		public void run() {
			while (!LinphoneService.isReady()) {
				try {
					sleep(30);
				} catch (InterruptedException e) {
					Log.e("waiting thread sleep() has been interrupted, exiting as requested");
					dismissDialogFromThread(DIALOG_ID); // FIXME, may not be the best thing to do
					thread = null;
					return;
				}
			}
			if (notifyOnCreate) {
				onCreateWhenManagerReady();
				notifyOnCreate=false;
			}
			if (notifyOnResume) {
				onResumeWhenManagerReady();
				notifyOnResume=false;
			}
			thread = null;
			dismissDialogFromThread(DIALOG_ID);
			super.run();
		}
	}

	public interface LinphoneManagerReadyListener {
		void onCreateWhenManagerReady();
		void onResumeWhenManagerReady();
	}

}
