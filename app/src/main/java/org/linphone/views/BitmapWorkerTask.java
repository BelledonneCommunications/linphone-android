package org.linphone.views;

/*
BitmapWorkerTask.java
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.widget.ImageView;

import org.linphone.mediastream.Log;
import org.linphone.utils.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
    private Context mContext;
    private Bitmap mDefaultBitmap;

    private final WeakReference<ImageView> imageViewReference;
    public String path;

    public BitmapWorkerTask(Context context, ImageView imageView, Bitmap defaultBitmap) {
        mContext = context;
        mDefaultBitmap = defaultBitmap;
        path = null;
        // Use a WeakReference to ensure the ImageView can be garbage collected
        imageViewReference = new WeakReference<>(imageView);
    }

    private Bitmap scaleToFitHeight(Bitmap b, int height) {
        float factor = height / (float) b.getHeight();
        int dstWidth = (int) (b.getWidth() * factor);
        if (dstWidth > 0 && height > 0) {
            return Bitmap.createScaledBitmap(b, dstWidth, height, true);
        }
        return b;
    }

    // Decode image in background.
    @Override
    protected Bitmap doInBackground(String... params) {
        path = params[0];
        Bitmap bm = null;
        Bitmap thumbnail = null;
        if (FileUtils.isExtensionImage(path)) {
            if (path.startsWith("content")) {
                try {
                    bm = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(path));
                } catch (FileNotFoundException e) {
                    Log.e(e);
                } catch (IOException e) {
                    Log.e(e);
                }
            } else {
                bm = BitmapFactory.decodeFile(path);
            }

            ImageView imageView = imageViewReference.get();

            try {
                // Rotate the bitmap if possible/needed, using EXIF data
                Matrix matrix = new Matrix();
                ExifInterface exif = new ExifInterface(path);
                int width = bm.getWidth();
                int height = bm.getHeight();

                int pictureOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
                if (pictureOrientation == 6 || pictureOrientation == 3 || pictureOrientation == 8) {
                    if (imageView != null) {
                        float factor = (float) imageView.getMeasuredHeight() / height;
                        matrix.postScale(factor, factor);
                    }
                    if (pictureOrientation == 6) {
                        matrix.preRotate(90);
                    } else if (pictureOrientation == 3) {
                        matrix.preRotate(180);
                    } else {
                        matrix.preRotate(270);
                    }
                    thumbnail = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
                    if (thumbnail != bm) {
                        bm.recycle();
                        bm = null;
                    }
                }
            } catch (Exception e) {
                Log.e(e);
            }

            if (thumbnail == null && bm != null) {
                if (imageView == null) return bm;
                thumbnail = scaleToFitHeight(bm, imageView.getMeasuredHeight());
                if (thumbnail != bm) {
                    bm.recycle();
                }
            }
            return thumbnail;
        } else {
            return mDefaultBitmap;
        }
    }

    // Once complete, see if ImageView is still around and set bitmap.
    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (isCancelled()) {
            bitmap.recycle();
            bitmap = null;
        }
        if (imageViewReference != null && bitmap != null) {
            final ImageView imageView = imageViewReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
            if (this == bitmapWorkerTask && imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    public static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncBitmap) {
                final AsyncBitmap asyncDrawable = (AsyncBitmap) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }
}
