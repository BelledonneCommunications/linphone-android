package org.linphone.compatibility;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;

/*ApiNineteenPlus.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
/**
 * @author Sylvain Berfini
 */
@TargetApi(19)
public class ApiNineteenPlus {
	public static void scheduleAlarm(AlarmManager alarmManager, int type, long triggerAtMillis, PendingIntent operation) {
		alarmManager.setExact(type, triggerAtMillis, operation);
	}
}
