package org.linphone.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ImageView;

import org.linphone.R;
import org.linphone.contacts.ContactsManager;
import org.linphone.mediastream.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class ImageUtils {

    public static Bitmap downloadBitmap(Uri uri) {
        URL url;
        InputStream is = null;
        try {
            url = new URL(uri.toString());
            is = url.openStream();
            return BitmapFactory.decodeStream(is);
        } catch (MalformedURLException e) {
            Log.e(e, e.getMessage());
        } catch (IOException e) {
            Log.e(e, e.getMessage());
        } finally {
            try {
                is.close();
            } catch (IOException x) {
            }
        }
        return null;
    }


    public static void setImagePictureFromUri(Context c, ImageView view, Uri pictureUri, Uri thumbnailUri) {
        if (pictureUri == null && thumbnailUri == null) {
            view.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            return;
        }
        if (pictureUri.getScheme().startsWith("http")) {
            Bitmap bm = downloadBitmap(pictureUri);
            if (bm == null) view.setImageResource(R.drawable.avatar);
            view.setImageBitmap(bm);
        } else {
            Bitmap bm = null;
            try {
                bm = MediaStore.Images.Media.getBitmap(c.getContentResolver(), pictureUri);
            } catch (IOException e) {
                if (thumbnailUri != null) {
                    try {
                        bm = MediaStore.Images.Media.getBitmap(c.getContentResolver(), thumbnailUri);
                    } catch (IOException ie) {
                    }
                }
            }
            if (bm != null) {
                view.setImageBitmap(bm);
            } else {
                view.setImageBitmap(ContactsManager.getInstance().getDefaultAvatarBitmap());
            }
        }
    }

    public static Bitmap getRoundBitmapFromUri(Context context, Uri fromPictureUri) {
        Bitmap bm;
        Bitmap roundBm;
        if (fromPictureUri != null) {
            try {
                bm = MediaStore.Images.Media.getBitmap(context.getContentResolver(), fromPictureUri);
            } catch (Exception e) {
                bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.topbar_avatar);
            }
        } else {
            bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.topbar_avatar);
        }
        if (bm != null) {
            roundBm = getRoundBitmap(bm);
            if (roundBm != null) {
                bm.recycle();
                bm = roundBm;
            }
        }
        return bm;
    }

    public static Bitmap getRoundBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }
}
