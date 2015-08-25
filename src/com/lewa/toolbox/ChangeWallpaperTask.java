package com.lewa.toolbox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

public class ChangeWallpaperTask extends Thread implements Runnable {
    private Context mContext;
    private String mPath;
    private int mResId;
    private Resources res;

    public ChangeWallpaperTask(Context context, String path, int resId) {
        mPath = path;
        mContext = context;
        mResId = resId;
        res = context.getResources();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            InputStream is = null;
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
            int mDisplayWidth = wallpaperManager.getDesiredMinimumWidth();
            int mDisplayHeight = wallpaperManager.getDesiredMinimumHeight();
            File file = new File(mPath);
            if (file.exists()) {
                is = EditModeUtils.getCalculateStream(mPath, mDisplayWidth, mDisplayHeight);
                wallpaperManager.setStream(is);
            } else if (mResId != 0) {
                wallpaperManager.setResource(mResId);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


}
