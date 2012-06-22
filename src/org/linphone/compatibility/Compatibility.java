package org.linphone.compatibility;
/*
Compatibility.java
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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import java.io.InputStream;
import java.util.List;

import org.linphone.mediastream.Version;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
/**
 * @author Sylvain Berfini
 */
public class Compatibility {
	public static void overridePendingTransition(int idAnimIn, int idAnimOut) {
		if (Version.sdkAboveOrEqual(5)) {
			ApiFivePlus.overridePendingTransition(idAnimIn, idAnimOut);
		}
	}
	
	public static Intent prepareAddContactIntent(String displayName, String sipUri) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.prepareAddContactIntent(displayName, sipUri);
		} else {
			//TODO
		}
		return null;
	}
	
	public static List<String> extractContactNumbersAndAddresses(String id, ContentResolver cr) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.extractContactNumbersAndAddresses(id, cr);
		} else {
			//TODO
		}
		return null;
	}
	
	public static Cursor getContactsCursor(ContentResolver cr) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getContactsCursor(cr);
		} else {
			//TODO
		}
		return null;
	}
	
	public static String getContactDisplayName(Cursor cursor) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getContactDisplayName(cursor);
		} else {
			//TODO
		}
		return null;
	}
	
	public static Uri getContactPictureUri(Cursor cursor, String id) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getContactPictureUri(cursor, id);
		} else {
			//TODO
		}
		return null;
	}
	
	public static InputStream getContactPictureInputStream(ContentResolver cr, String id) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getContactPictureInputStream(cr, id);
		} else {
			//TODO
		}
		return null;
	}
	
	public static int getCursorDisplayNameColumnIndex(Cursor cursor) {
		if (Version.sdkAboveOrEqual(5)) {
			return ApiFivePlus.getCursorDisplayNameColumnIndex(cursor);
		} else {
			//TODO
		}
		return -1;
	}
}
