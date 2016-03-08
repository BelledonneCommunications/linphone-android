package org.linphone.tutorials;
/*
TutorialLauncherActivity.java
Copyright (C) 2013  Belledonne Communications, Grenoble, France

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

import org.linphone.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * @author Sylvain Berfini
 */
public class TutorialLauncherActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tutorials);
	}
	
	public void startHelloWorldTutorial(View v) {
		startActivity(new Intent().setClass(TutorialLauncherActivity.this, TutorialHelloWorldActivity.class));
	}
	
	public void startRegistrationTutorial(View v) {
		startActivity(new Intent().setClass(TutorialLauncherActivity.this, TutorialRegistrationActivity.class));
	}
	
	public void startChatRoomTutorial(View v) {
		startActivity(new Intent().setClass(TutorialLauncherActivity.this, TutorialChatRoomActivity.class));
	}
	
	public void startBuddyStatusTutorial(View v) {
		startActivity(new Intent().setClass(TutorialLauncherActivity.this, TutorialBuddyStatusActivity.class));
	}
	
	public void startCardDavSyncTutorial(View v) {
		startActivity(new Intent().setClass(TutorialLauncherActivity.this, TutorialCardDavSync.class));
	}
}
