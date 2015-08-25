/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.lewa.launcher;
import com.lewa.themes.ThemeClientApplication;
import java.lang.ref.WeakReference;

import android.app.Application;
import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.Display;
import android.view.WindowManager;

import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.reflection.ReflUtils;
import com.lewa.toolbox.BitmapLruCache;
import com.lewa.toolbox.EditModeUtils;
import com.lewa.toolbox.LewaUtils;
import com.lewa.toolbox.MyVolley;
public class LauncherApplication extends ThemeClientApplication {
    private static final String TAG = "LauncherApplication";
    public LauncherModel mModel;
    public IconCache mIconCache;
    private static boolean sIsSystemApp;
    private static float sScreenDensity;
    private static boolean sIsScreenLarge;
    WeakReference<LauncherProvider> mLauncherProvider;

    public MessageModel mMessageModel;
    private MissedCallObserver mMissedCallObserver;
    private UnreadMsgObserver mUnreadMsgObserver;
    public AppRecommendHelper mAppRecommendHelper;
    public boolean LoadingProccessShowed = false;

    private static BitmapLruCache sBitmapCache = null;
    static final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    final static int cacheSize = maxMemory / 16;
    Handler handler = new Handler(Looper.getMainLooper());
    private AppRecommendHelper.SortAppsListener sortAppsListener = new AppRecommendHelper.SortAppsListener() {
        @Override
        public void sortApps() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mModel.startSortTask(LauncherApplication.this, 0x1);
                }
            });
        }
    };
    private boolean waitToSort;

    @Override
    public void onCreate() {
        super.onCreate();        
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
            sIsSystemApp = true;
        }
        ThemeHelper.checkTheme(this);

        // set sIsScreenXLarge and sScreenDensity *before* creating icon cache
        sIsScreenLarge = getResources().getBoolean(R.bool.is_large_screen);
        sScreenDensity = getResources().getDisplayMetrics().density;

        mIconCache = new IconCache(this);
        mModel = new LauncherModel(this, mIconCache);
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(mModel, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mModel, filter);
        /// API dependents start
        if (Build.VERSION.SDK_INT >= 17) {
            filter = new IntentFilter();
            filter.addAction(ReflUtils.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
            registerReceiver(mModel, filter);
        }
        /// API dependents end
        filter = new IntentFilter();
        filter.addAction(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED);
        registerReceiver(mModel, filter);

        // Register unread change broadcast.
        mMessageModel = new MessageModel(this);
        filter = new IntentFilter();
        filter.addAction(MessageModel.UPDATE_REQUEST);
        registerReceiver(mMessageModel, filter);

        // Register for changes to the favorites
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherSettings.Favorites.CONTENT_URI, true, mFavoritesObserver);

        HandlerThread thread = new HandlerThread("unread");
        thread.start();
        mMissedCallObserver = new MissedCallObserver(this, new Handler(thread.getLooper()));
        resolver.registerContentObserver(Uri.parse("content://call_log/calls"), false, mMissedCallObserver);
        mUnreadMsgObserver = new UnreadMsgObserver(this, new Handler(thread.getLooper()));
        resolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, mUnreadMsgObserver);
        EditModeUtils.logE(TAG, "cacheSize: " + cacheSize);
        sBitmapCache = new BitmapLruCache(cacheSize);
        MyVolley.init(this, sBitmapCache);
        LewaUtils.getUserAgent(getApplicationContext());
        //lqwang - remove smart sort and recommend - modify begin
        if(PreferencesProvider.isRecommendOrSmartSortOn(getApplicationContext())){
            mAppRecommendHelper = new AppRecommendHelper(getApplicationContext());
            filter = new IntentFilter();
            filter.addAction(mAppRecommendHelper.NETWORK_ACTION);
            registerReceiver(mAppRecommendHelper, filter);
        }
        //lqwang - remove smart sort and recommend- modify end
    }

    /**
     * There's no guarantee that this function is ever called.
     */
    @Override
    public void onTerminate() {
        super.onTerminate();

        unregisterReceiver(mModel);
        unregisterReceiver(mMessageModel);
        //lqwang - remove smart sort and recommend - modify begin
        if(PreferencesProvider.isRecommendOrSmartSortOn(getApplicationContext()) && mAppRecommendHelper != null){
            unregisterReceiver(mAppRecommendHelper);
        }
        //lqwang - remove smart sort and recommend - modify end

        ContentResolver resolver = getContentResolver();
        resolver.unregisterContentObserver(mFavoritesObserver);
        resolver.unregisterContentObserver(mMissedCallObserver);
        resolver.unregisterContentObserver(mUnreadMsgObserver);
    }

    /**
     * Receives notifications whenever the user favorites have changed.
     */
    private final ContentObserver mFavoritesObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            // If the database has ever changed, then we really need to force a reload of the
            // workspace on the next load
            mModel.resetLoadedState(false, true);
            mModel.startLoaderFromBackground();
        }
    };

    LauncherModel setLauncher(Launcher launcher) {
        mModel.initialize(launcher);
        mMessageModel.initialize(launcher);
        return mModel;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    LauncherModel getModel() {
        return mModel;
    }

    AppRecommendHelper getRecommendHelper() {
        return mAppRecommendHelper;
    }

    BitmapLruCache getBitmapCache() {
        return sBitmapCache;
    }

    void setLauncherProvider(LauncherProvider provider) {
        mLauncherProvider = new WeakReference<LauncherProvider>(provider);
    }

    LauncherProvider getLauncherProvider() {
        if (mLauncherProvider == null || mLauncherProvider.get() == null) {
            ContentProviderClient cpc = getContentResolver().acquireContentProviderClient(getPackageName() + ".settings");
            if (cpc != null) {
                ContentProvider cp = cpc.getLocalContentProvider();
                if (cp instanceof LauncherProvider) {
                    setLauncherProvider((LauncherProvider) cp);
                    return (LauncherProvider) cp;
                }
            }
            return null;
        }
        return mLauncherProvider.get();
    }

    public MessageModel getMessageModel() {
        return mMessageModel;
    }

    public static boolean isTablet(Context context) { // equals isScreenLarge()
        return context.getResources().getBoolean(R.bool.is_large_screen);
    }

    public static boolean isScreenLarge() {
        return sIsScreenLarge;
    }

    // Screen Size Category:
    // 1. Normal : height < 960
    // 2. Large :  height >= 960 && height < 1920
    // 3. Ex-Large: height >= 1920

    public static boolean isNormalScreen(Context context) {
        return getScreenHeight(context) < 960 ? true : false;
    }

    public static boolean isExLargeScreen(Context context) {
        return getScreenHeight(context) >= 1920 ? true : false;
    }

    public static boolean isScreenLandscape(Context context) {
        return context.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
    }

    public static float getScreenDensity() {
        return sScreenDensity;
    }

    public static boolean isSystemApp() {
        //return false;
        return sIsSystemApp;
    }

    // get real screen height , includes window decorations (statusbar bar/menu bar)
    private static int getScreenHeight(Context context) {
        int screenHeight = 0;
        int version = Build.VERSION.SDK_INT;
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display d = wm.getDefaultDisplay();
        try {
            if (version < 14) {
                DisplayMetrics metrics = new DisplayMetrics();
                d.getMetrics(metrics);
                screenHeight = metrics.heightPixels;
            } else if (version >= 14 && version < 17) {
                screenHeight = (Integer) Display.class.getMethod("getRawHeight").invoke(d);
            } else if (version >= 17) {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(d, realSize);
                screenHeight = realSize.y;
            }
        } catch (Exception ignored) {

        }
        return screenHeight;
    }
    
   /* public static Bitmap getLocalImage(String path,Config decodeConfig,int maxWidth,int maxHeight){
           if (TextUtils.isEmpty(path)) {
               return null;
           }

           String key = getCacheKey(path,maxWidth,maxHeight);

           Bitmap cachedBitmap = sBitmapCache.getBitmap(key);
           if (cachedBitmap != null) {
               return cachedBitmap;
           } else {
               Bitmap bitmap = EditModeUtils.parseBitmap(path, decodeConfig, maxWidth, maxHeight);
               if (bitmap != null) {
                   sBitmapCache.putBitmap(key, bitmap);
               }
               return bitmap;
           }
    }*/

    /**
     * Creates a cache key for use with the L1 cache.
     *
     * @param path      The path of the image.
     * @param maxWidth  The max-width of the output.
     * @param maxHeight The max-height of the output.
     */
    public static String getCacheKey(String path, int maxWidth, int maxHeight) {
        return new StringBuilder(path.length() + 12).append("#W").append(maxWidth)
                .append("#H").append(maxHeight).append(path).toString();
    }

    public static Bitmap getCacheBitmap(String key) {
        return sBitmapCache.getBitmap(key);
    }

    public static void removeCacheBitmap(String key) {
        if (!TextUtils.isEmpty(key) && sBitmapCache.get(key) != null)
            sBitmapCache.remove(key);
    }

    public static void cacheBitmap(String key, Bitmap b) {
        if (b != null && !TextUtils.isEmpty(key)) {
            sBitmapCache.putBitmap(key, b);
        }
    }

    public static void clearCache() {
        sBitmapCache.evictAll();
    }
    //lqwang - PR65523 - add begin
    public int getUnreadMsgCnt(){
        if(mUnreadMsgObserver != null){
            return mUnreadMsgObserver.getMmsUnreadCnt() + mUnreadMsgObserver.getSmsUnreadCnt();
        }
        return 0;
    }

    public int getMissedCallCnt(){
        if(mMissedCallObserver != null){
            return mMissedCallObserver.getMissedCallCount();
        }
        return 0;
    }
    //lqwang - PR65523 - add end

    //lqwang - pr962182 - add begin
    public void sortApps(){
        if(waitToSort){
           sortAppsListener.sortApps();
        }
    }

    public void setWaitToSort(boolean waitToSort) {
        this.waitToSort = waitToSort;
    }

    //lqwang - pr962182 - add end
}
