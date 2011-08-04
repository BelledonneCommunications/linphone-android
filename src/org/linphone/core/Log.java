/*
Log.java
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
package org.linphone.core;

import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.WARN;

/**
 * Convenient wrapper for Android logs.
 *
 * @author Guillaume Beraudo
 */
public final class Log {

	public static final String TAG = "Linphone";
	private static final boolean useIsLoggable = false;

	@SuppressWarnings(value="all")
	private static boolean isLoggable(int level) {
		return !useIsLoggable || android.util.Log.isLoggable(TAG, level);
	}

	public static void i(Object...objects) {
		if (isLoggable(INFO)) {
			android.util.Log.i(TAG, toString(objects));
		}
	}
	public static void i(Throwable t, Object...objects) {
		if (isLoggable(INFO)) {
			android.util.Log.i(TAG, toString(objects), t);
		}
	}

	
	public static void d(Object...objects) {
		if (isLoggable(DEBUG)) {
			android.util.Log.d(TAG, toString(objects));
		}
	}
	public static void d(Throwable t, Object...objects) {
		if (isLoggable(DEBUG)) {
			android.util.Log.d(TAG, toString(objects), t);
		}
	}
	
	public static void w(Object...objects) {
		if (isLoggable(WARN)) {
			android.util.Log.w(TAG, toString(objects));
		}
	}
	public static void w(Throwable t, Object...objects) {
		if (isLoggable(WARN)) {
			android.util.Log.w(TAG, toString(objects), t);
		}
	}
	
	public static void e(Object...objects) {
		if (isLoggable(ERROR)) {
			android.util.Log.e(TAG, toString(objects));
		}
	}
	public static void e(Throwable t, Object...objects) {
		if (isLoggable(ERROR)) {
			android.util.Log.e(TAG, toString(objects), t);
		}
	}

	/**
	 * @throws RuntimeException always throw after logging the error message.
	 */
	public static void f(Object...objects) {
		if (isLoggable(ERROR)) {
			android.util.Log.e(TAG, toString(objects));
			throw new RuntimeException("Fatal error : " + toString(objects));
		}
	}
	/**
	 * @throws RuntimeException always throw after logging the error message.
	 */
	public static void f(Throwable t, Object...objects) {
		if (isLoggable(ERROR)) {
			android.util.Log.e(TAG, toString(objects), t);
			throw new RuntimeException("Fatal error : " + toString(objects), t);
		}
	}

	private static String toString(Object...objects) {
		StringBuilder sb = new StringBuilder();
		for (Object o : objects) {
			sb.append(o);
		}
		return sb.toString();
	}
}
