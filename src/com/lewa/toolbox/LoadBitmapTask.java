package com.lewa.toolbox;

import java.io.File;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Handler;

import com.lewa.launcher.LauncherApplication;

public class LoadBitmapTask implements Runnable {
    private static final String TAG = "LoadBitmapTask";
    private String mPath;
    private Config mConfig;
    private int maxWidth;
    private int maxHeight;
    private Handler mHandler;
    private IIoadImageListener loadImageListener;
    private Resources res;
    private int mResId;

    public LoadBitmapTask(Context context, String path, int resId, Config config, int maxWidth, int maxHeight, Handler handler, IIoadImageListener loadImageListener) {
        mPath = path;
        mConfig = config;
        mHandler = handler;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.loadImageListener = loadImageListener;
        res = context.getResources();
        mResId = resId;
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        Bitmap b = null;
        File flie = new File(mPath);
        if (flie.exists()) {
            b = EditModeUtils.parseBitmap(mPath, mConfig, maxWidth, maxHeight);
        } else {
            b = EditModeUtils.parseBitmap(res, mResId, mConfig, maxWidth, maxHeight);
        }
        final Bitmap reflectBitmap = EditModeUtils.createReflectedBitmap(res, b, true);
        String key = LauncherApplication.getCacheKey(mPath, maxWidth, maxHeight);
        LauncherApplication.cacheBitmap(key, reflectBitmap);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                loadImageListener.onLoadComplete(reflectBitmap);
            }
        });
    }

}
