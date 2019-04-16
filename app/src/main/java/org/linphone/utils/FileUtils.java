package org.linphone.utils;

/*
FileUtils.java
Copyright (C) 2018  Belledonne Communications, Grenoble, France

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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.linphone.LinphoneManager;
import org.linphone.core.Address;
import org.linphone.core.ChatMessage;
import org.linphone.core.Content;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.tools.Log;

public class FileUtils {
    public static String getNameFromFilePath(String filePath) {
        String name = filePath;
        int i = filePath.lastIndexOf('/');
        if (i > 0) {
            name = filePath.substring(i + 1);
        }
        return name;
    }

    public static String getExtensionFromFileName(String fileName) {
        String extension = null;
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    public static Boolean isExtensionImage(String path) {
        String extension = getExtensionFromFileName(path);
        if (extension != null) extension = extension.toLowerCase();
        return (extension != null && extension.matches("(png|jpg|jpeg|bmp|gif)"));
    }

    public static void recursiveFileRemoval(File root) {
        if (!root.delete()) {
            if (root.isDirectory()) {
                File[] files = root.listFiles();
                if (files != null) {
                    for (File f : files) {
                        recursiveFileRemoval(f);
                    }
                }
            }
        }
    }

    public static String getFilePath(final Context context, final Uri uri) {
        if (uri == null) return null;

        String result = null;
        String name = getNameFromUri(uri, context);

        try {
            File localFile = createFile(context, name);
            InputStream remoteFile = context.getContentResolver().openInputStream(uri);

            if (copyToFile(remoteFile, localFile)) {
                result = localFile.getAbsolutePath();
            }

            remoteFile.close();
        } catch (IOException e) {
            Log.e("Enable to get sharing file", e);
        }

        return result;
    }

    private static String getNameFromUri(Uri uri, Context context) {
        String name = null;
        if (uri.getScheme().equals("content")) {
            Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
            if (returnCursor != null) {
                returnCursor.moveToFirst();
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                name = returnCursor.getString(nameIndex);
                returnCursor.close();
            }
        } else if (uri.getScheme().equals("file")) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    /**
     * Copy data from a source stream to destFile. Return true if succeed, return false if failed.
     */
    private static boolean copyToFile(InputStream inputStream, File destFile) {
        if (inputStream == null || destFile == null) return false;
        try {
            try (OutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static File createFile(Context context, String fileName) {
        if (TextUtils.isEmpty(fileName)) fileName = getStartDate();

        if (!fileName.contains(".")) {
            fileName = fileName + ".unknown";
        }

        final File root;
        root = context.getExternalCacheDir();

        if (root != null && !root.exists()) root.mkdirs();
        return new File(root, fileName);
    }

    public static Uri getCVSPathFromLookupUri(String content) {
        String contactId = getNameFromFilePath(content);
        FriendList[] friendList = LinphoneManager.getLc().getFriendsLists();
        for (FriendList list : friendList) {
            for (Friend friend : list.getFriends()) {
                if (friend.getRefKey().equals(contactId)) {
                    String contactVcard = friend.getVcard().asVcard4String();
                    return createCvsFromString(contactVcard);
                }
            }
        }
        return null;
    }

    public static String getRealPathFromURI(Context context, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            String result = cursor.getString(column_index);
            cursor.close();
            return result;
        }
        return null;
    }

    public static String getStorageDirectory(Context mContext) {
        String storageDir =
                Environment.getExternalStorageDirectory()
                        + "/"
                        + mContext.getString(
                                mContext.getResources()
                                        .getIdentifier(
                                                "app_name", "string", mContext.getPackageName()));
        File file = new File(storageDir);
        if (!file.isDirectory() || !file.exists()) {
            Log.w("Directory " + file + " doesn't seem to exists yet, let's create it");
            file.mkdirs();
            LinphoneManager.getInstance().getMediaScanner().scanFile(file, null);
        }
        return storageDir;
    }

    public static String getRecordingsDirectory(Context mContext) {
        String recordingsDir =
                Environment.getExternalStorageDirectory()
                        + "/"
                        + mContext.getString(
                                mContext.getResources()
                                        .getIdentifier(
                                                "app_name", "string", mContext.getPackageName()))
                        + "/recordings";
        File file = new File(recordingsDir);
        if (!file.isDirectory() || !file.exists()) {
            Log.w("Directory " + file + " doesn't seem to exists yet, let's create it");
            file.mkdirs();
            LinphoneManager.getInstance().getMediaScanner().scanFile(file, null);
        }
        return recordingsDir;
    }

    @SuppressLint("SimpleDateFormat")
    public static String getCallRecordingFilename(Context context, Address address) {
        String fileName = getRecordingsDirectory(context) + "/";

        String name =
                address.getDisplayName() == null ? address.getUsername() : address.getDisplayName();
        fileName += name + "_";

        DateFormat format = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss");
        fileName += format.format(new Date()) + ".mkv";

        return fileName;
    }

    public static void scanFile(ChatMessage message) {
        String appData = message.getAppdata();
        if (appData == null) {
            for (Content c : message.getContents()) {
                if (c.isFile()) {
                    appData = c.getFilePath();
                }
            }
        }
        LinphoneManager.getInstance().getMediaScanner().scanFile(new File(appData), null);
    }

    private static Uri createCvsFromString(String vcardString) {
        String contactName = getContactNameFromVcard(vcardString);
        File vcfFile = new File(Environment.getExternalStorageDirectory(), contactName + ".cvs");
        try {
            FileWriter fw = new FileWriter(vcfFile);
            fw.write(vcardString);
            fw.close();
            return Uri.fromFile(vcfFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getContactNameFromVcard(String vcard) {
        if (vcard != null) {
            String contactName = vcard.substring(vcard.indexOf("FN:") + 3);
            contactName = contactName.substring(0, contactName.indexOf("\n") - 1);
            contactName = contactName.replace(";", "");
            contactName = contactName.replace(" ", "");
            return contactName;
        }
        return null;
    }

    private static String getStartDate() {
        try {
            return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        } catch (RuntimeException e) {
            return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        }
    }

    public static String getMimeFromFile(String path) {
        if (isExtensionImage(path)) {
            return "image/" + getExtensionFromFileName(path);
        }
        return "file/" + getExtensionFromFileName(path);
    }
}
