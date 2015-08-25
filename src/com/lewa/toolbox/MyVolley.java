/**
 * Copyright 2013 Ognyan Bankov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lewa.toolbox;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ImageView;

import com.android.volley.ExecutorDelivery;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;
import com.lewa.launcher.LauncherApplication;
import com.lewa.launcher.LoadPreviewTask;
import com.lewa.launcher.PendingAddItemInfo;
import com.lewa.launcher.EditGridAdapter.VIEWTYPE;
import com.lewa.launcher.LoadPreviewTask.PREVIEWTYPE;
import com.lewa.launcher.bean.EditBaseObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lewa.content.res.IconCustomizer;

/**
 * Helper class that is used to provide references to initialized RequestQueue(s) and ImageLoader(s)
 */
public class MyVolley {
    private static final String mRootDir = "/LEWA/AppRecommend/Icon";
    private static final int mCacheSize = 20 * 1024 * 1024;
    private static RequestQueue mRequestQueue;
    private static ImageLoader mImageLoader;
    private static BitmapLruCache mBitmapLruCache;

    private static ExecutorService exec;
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static List<Future<?>> futrues = new ArrayList<Future<?>>();

    private MyVolley() {
        // no instances
    }


    public static void init(Context context, BitmapLruCache cache) {
        mRequestQueue = newRequestQueue();

        mImageLoader = new ImageLoader(mRequestQueue, cache);

//        int  cpuCoreNumber = Runtime.getRuntime().availableProcessors();  

        exec = Executors.newFixedThreadPool(1);
    }


    public static RequestQueue getRequestQueue() {
        if (mRequestQueue != null) {
            return mRequestQueue;
        } else {
            throw new IllegalStateException("RequestQueue not initialized");
        }
    }

    public static void queue(Request request) {
        getRequestQueue().add(request);
    }


    /**
     * Returns instance of ImageLoader initialized with {@see FakeImageCache} which effectively means
     * that no memory caching is used. This is useful for images that you know that will be show
     * only once.
     *
     * @return
     */
    public static ImageLoader getImageLoader() {
        if (mImageLoader != null) {
            return mImageLoader;
        } else {
            throw new IllegalStateException("ImageLoader not initialized");
        }
    }

    /**
     * Creates a default instance of the worker pool and calls {@link RequestQueue#start()} on it.
     *
     * @return A started {@link RequestQueue} instance.
     */
    public static RequestQueue newRequestQueue() {
        File cacheDir = new File(Environment.getExternalStorageDirectory() + mRootDir);

        Network network = new BasicNetwork(new HurlStack());
        RequestQueue queue = new RequestQueue(new DiskBasedCache(cacheDir, mCacheSize), network);
        queue.start();

        return queue;
    }

    public static void displayImage(Context context, String path, int resId, Config config, int maxWidth, int maxHeight, IIoadImageListener loadImageListener) {
        if (TextUtils.isEmpty(path))
            return;
        loadImageListener.onLoadStart();
        String key = LauncherApplication.getCacheKey(path, maxWidth, maxHeight);
        Bitmap b = LauncherApplication.getCacheBitmap(key);
        if (b != null) {
            loadImageListener.onLoadComplete(b);
            return;
        }
        LoadBitmapTask loadBitmapTask = new LoadBitmapTask(context, path, resId, config, maxWidth, maxHeight, handler, loadImageListener);
        execRunnable(loadBitmapTask);
    }

    public static void displayPreview(Context context, PREVIEWTYPE type, VIEWTYPE viewType, EditBaseObject object, PendingAddItemInfo itemInfo, IIoadImageListener loadImageListener) {
        if (type == PREVIEWTYPE.GROUP && object != null) {
            String pkgName = object.getPkgName();
            if (cacheHitBitmap(loadImageListener, pkgName, IconCustomizer.sCustomizedIconWidth, IconCustomizer.sCustomizedIconHeight))
                return;
        } else if (type == PREVIEWTYPE.WIDGET && itemInfo != null) {
            if (cacheHitBitmap(loadImageListener, itemInfo.getComponentName().getClassName(), itemInfo.spanX, itemInfo.spanY))//use classname and spanX spanY as key
                return;
        }
        LoadPreviewTask loadPreviewTask = new LoadPreviewTask(context, type, viewType, object, itemInfo, handler, loadImageListener);
        execRunnable(loadPreviewTask);
    }


    private static boolean cacheHitBitmap(IIoadImageListener loadImageListener,
                                          String pkgName, int maxWidth, int maxHeight) {
        boolean hit = false;
        String key = LauncherApplication.getCacheKey(pkgName, maxWidth, maxHeight);
        Bitmap b = LauncherApplication.getCacheBitmap(key);
        if (b != null) {
            loadImageListener.onLoadComplete(b);
            hit = true;
        }
        return hit;
    }

    public static void execRunnable(Runnable r) {
        if (r != null && !exec.isShutdown()) {
            Future<?> future = exec.submit(r);
            futrues.add(future);
        }
    }

    public static void clearFetrues() {
        for (Future futrue : futrues) {
            if (!futrue.isDone())
                futrue.cancel(false);
        }
        futrues.clear();
    }
}
