package org.linphone.utils;

/*
PushNotificationUtils.java
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

import android.content.Context;
import java.lang.reflect.Constructor;
import org.linphone.R;
import org.linphone.core.tools.Log;

public class PushNotificationUtils {
    private static PushHelperInterface mHelper;

    public static void init(Context context) {
        mHelper = null;
        String push_type = context.getString(R.string.push_type);

        if (push_type.equals("firebase")) {
            String className = "org.linphone.firebase.FirebasePushHelper";
            try {
                Class pushHelper = Class.forName(className);
                Class[] types = {};
                Constructor constructor = pushHelper.getConstructor(types);
                Object[] parameters = {};
                mHelper = (PushHelperInterface) constructor.newInstance(parameters);
                mHelper.init(context);
            } catch (NoSuchMethodException e) {
                Log.w("[Push Utils] Couldn't get push helper constructor");
            } catch (ClassNotFoundException e) {
                Log.w("[Push Utils] Couldn't find class " + className);
            } catch (Exception e) {
                Log.w("[Push Utils] Couldn't get push helper instance: " + e);
            }
        } else {
            Log.w("[Push Utils] Unknow push type " + push_type);
        }
    }

    public static boolean isAvailable(Context context) {
        if (mHelper == null) return false;
        return mHelper.isAvailable(context);
    }

    public interface PushHelperInterface {
        void init(Context context);

        boolean isAvailable(Context context);
    }
}
