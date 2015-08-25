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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.R.bool;
import android.R.id;
import android.R.string;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.widget.Toast;

import com.lewa.launcher.InstallWidgetReceiver.WidgetMimeTypeHandlerData;
import com.lewa.launcher.LauncherSettings.Favorites;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.launcher.R;
import com.lewa.toolbox.EditModeUtils;

import lewa.content.res.IconCustomizer;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel extends BroadcastReceiver {
    static final boolean DEBUG_LOADERS = false;
    static final boolean DEBUG_SCREENS = true;
    static final String TAG = "Launcher.Model";

    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons
    private final boolean mAppsCanBeOnExternalStorage;
    private int mBatchSize; // 0 is all apps at once
    private int mAllAppsLoadDelay; // milliseconds between batches

    private final LauncherApplication mApp;
    private final Object mLock = new Object();
    private DeferredHandler mHandler = new DeferredHandler();
    private LoaderTask mLoaderTask;
    //yixiao@lewatek.com add 2015.1.8
	protected boolean mIsLoaderTaskRunning;
	public static final long CUSTROM_SCREENID = -401;
    private boolean mMissedChecked = false;
    private boolean mRemovedChecked = false;

    // Specific runnable types that are run on the main thread deferred handler, this allows us to
    // clear all queued binding runnables when the Launcher activity is destroyed.
    private static final int MAIN_THREAD_NORMAL_RUNNABLE = 0;
    private static final int MAIN_THREAD_BINDING_RUNNABLE = 1;


    private static final HandlerThread sWorkerThread = new HandlerThread("launcher-loader");
    static {
        sWorkerThread.start();
    }
    private static final Handler sWorker = new Handler(sWorkerThread.getLooper());

    // We start off with everything not loaded.  After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery.  These are only ever touched from the loader thread.
    private boolean mWorkspaceLoaded;
    private boolean mAllAppsLoaded;

    // When we are loading pages synchronously, we can't just post the binding of items on the side
    // pages as this delays the rotation process.  Instead, we wait for a callback from the first
    // draw (in Workspace) to initiate the binding of the remaining side pages.  Any time we start
    // a normal load, we also clear this set of Runnables.
    static final ArrayList<Runnable> mDeferredBindRunnables = new ArrayList<Runnable>();

    private WeakReference<Callbacks> mCallbacks;

    // < only access in worker thread >
    private AllAppsList mBgAllAppsList;

    // The lock that must be acquired before referencing any static bg data structures.  Unlike
    // other locks, this one can generally be held long-term because we never expect any of these
    // static data structures to be referenced outside of the worker thread except on the first
    // load after configuration change.
    static final Object sBgLock = new Object();
    

    // sBgItemsIdMap maps *all* the ItemInfos (shortcuts, folders, and widgets) created by
    // LauncherModel to their ids
    static final HashMap<Long, ItemInfo> sBgItemsIdMap = new HashMap<Long, ItemInfo>();

    // sBgWorkspaceItems is passed to bindItems, which expects a list of all folders and shortcuts
    //       created by LauncherModel that are directly on the home screen (however, no widgets or
    //       shortcuts within folders).
    static final ArrayList<ItemInfo> sBgWorkspaceItems = new ArrayList<ItemInfo>();

    // sBgAppWidgets is all LauncherAppWidgetInfo created by LauncherModel. Passed to bindAppWidget()
    static final ArrayList<LauncherAppWidgetInfo> sBgAppWidgets =
        new ArrayList<LauncherAppWidgetInfo>();

    // sBgFolders is all FolderInfos created by LauncherModel. Passed to bindFolders()
    static final HashMap<Long, FolderInfo> sBgFolders = new HashMap<Long, FolderInfo>();

    // sBgDbIconCache is the set of ItemInfos that need to have their icons updated in the database
    static final HashMap<Object, byte[]> sBgDbIconCache = new HashMap<Object, byte[]>();
    // </ only access in worker thread >
    
    static  List<String> newAddApps = null;

    private IconCache mIconCache;
    private Bitmap mDefaultIcon;

    private static int mCellCountX;
    private static int mCellCountY;

    protected int mPreviousConfigMcc;

    public interface Callbacks {
        public boolean setLoadOnResume();
        public int getCurrentWorkspaceScreen();
        public void startBinding();
        public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end);
        public void bindFolders(HashMap<Long,FolderInfo> folders);
        public void finishBindingItems();
        public void bindAppWidget(LauncherAppWidgetInfo info);
        public void bindAllApplications(ArrayList<ApplicationInfo> apps);
        public void bindAppsAdded(ArrayList<ApplicationInfo> apps);
        public void bindAppsUpdated(ArrayList<ApplicationInfo> apps);
        public void bindAppsRemoved(ArrayList<ApplicationInfo> apps, boolean permanent);
        public void bindPackagesUpdated();
        public boolean isAllAppsVisible();
        public void onPageBoundSynchronously(int page);
    }
    LauncherModel(final LauncherApplication app, IconCache iconCache) {
        mAppsCanBeOnExternalStorage = !Environment.isExternalStorageEmulated();
        mApp = app;
        //lqwang-PR58643-modify begin
        String newAppsString = PreferencesProvider.getStringValue(app,Constants.NEWAPPSKEY,"");
        if(!TextUtils.isEmpty(newAppsString)){
            newAddApps = new ArrayList<String>(Arrays.asList(newAppsString.replaceAll("\\s","").split("[,\\[\\]]")));
        }else{
            newAddApps = new ArrayList<String>();
        }
        //lqwang-PR58643-modify end
        mBgAllAppsList = new AllAppsList(app, this, iconCache);
        mIconCache = iconCache;

        mDefaultIcon = Utilities.createIconBitmap(
                mIconCache.getFullResDefaultActivityIcon(), app);

        final Resources res = app.getResources();
        mAllAppsLoadDelay = res.getInteger(R.integer.config_allAppsBatchLoadDelay);
        mBatchSize = res.getInteger(R.integer.config_allAppsBatchSize);
        Configuration config = res.getConfiguration();
        mPreviousConfigMcc = config.mcc;
    }

    /** Runs the specified runnable immediately if called from the main thread, otherwise it is
     * posted on the main thread handler. */
    private void runOnMainThread(Runnable r) {
        runOnMainThread(r, 0);
    }
    private void runOnMainThread(Runnable r, int type) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            // If we are on the worker thread, post onto the main handler
            mHandler.post(r);
        } else {
            r.run();
        }
    }

    /** Runs the specified runnable immediately if called from the worker thread, otherwise it is
     * posted on the worker thread handler. */
    public static void runOnWorkerThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            r.run();
        } else {
            // If we are not on the worker thread, then post to the worker handler
            sWorker.post(r);
        }
    }

    public Bitmap getFallbackIcon() {
        return Bitmap.createBitmap(mDefaultIcon);
    }

    public void unbindItemInfosAndClearQueuedBindRunnables() {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            throw new RuntimeException("Expected unbindLauncherItemInfos() to be called from the " +
                    "main thread");
        }

        // Clear any deferred bind runnables
        mDeferredBindRunnables.clear();
        // Remove any queued bind runnables
        mHandler.cancelAllRunnablesOfType(MAIN_THREAD_BINDING_RUNNABLE);
        // Unbind all the workspace items
        unbindWorkspaceItemsOnMainThread();
    }

    /** Unbinds all the sBgWorkspaceItems and sBgAppWidgets on the main thread */
    void unbindWorkspaceItemsOnMainThread() {
        // Ensure that we don't use the same workspace items data structure on the main thread
        // by making a copy of workspace items first.
        final ArrayList<ItemInfo> tmpWorkspaceItems = new ArrayList<ItemInfo>();
        final ArrayList<ItemInfo> tmpAppWidgets = new ArrayList<ItemInfo>();
        synchronized (sBgLock) {
            tmpWorkspaceItems.addAll(sBgWorkspaceItems);
            tmpAppWidgets.addAll(sBgAppWidgets);
        }
        Runnable r = new Runnable() {
                @Override
                public void run() {
                   for (ItemInfo item : tmpWorkspaceItems) {
                       item.unbind();
                   }
                   for (ItemInfo item : tmpAppWidgets) {
                       item.unbind();
                   }
                }
            };
        runOnMainThread(r);
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    static void addOrMoveItemInDatabase(Context context, ItemInfo item, long container,
            int screen, int cellX, int cellY) {
        if (item.container == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(context, item, container, screen, cellX, cellY, false);
        } else {
            // From somewhere else
            moveItemInDatabase(context, item, container, screen, cellX, cellY);
        }
    }

    static void checkItemInfoLocked(
            final long itemId, final ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = sBgItemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (modelItem instanceof ShortcutInfo && item instanceof ShortcutInfo) {
                ShortcutInfo modelShortcut = (ShortcutInfo) modelItem;
                ShortcutInfo shortcut = (ShortcutInfo) item;
                // sometimes the shortcut.title was not copied yet.
                // like ItemInfo constructor will call checkItemInfoLocked()
                if(shortcut.title == null){
                	Log.d(TAG, "shortcut.title == null and quit checkItemInfoLocked()");
                	return;
                }
                if (modelShortcut.title.toString().equals(shortcut.title.toString()) &&
                        modelShortcut.intent.filterEquals(shortcut.intent) &&
                        modelShortcut.id == shortcut.id &&
                        modelShortcut.itemType == shortcut.itemType &&
                        modelShortcut.container == shortcut.container &&
                        modelShortcut.screen == shortcut.screen &&
                        modelShortcut.cellX == shortcut.cellX &&
                        modelShortcut.cellY == shortcut.cellY &&
                        modelShortcut.spanX == shortcut.spanX &&
                        modelShortcut.spanY == shortcut.spanY &&
                        ((modelShortcut.dropPos == null && shortcut.dropPos == null) ||
                        (modelShortcut.dropPos != null &&
                                shortcut.dropPos != null &&
                                modelShortcut.dropPos[0] == shortcut.dropPos[0] &&
                        modelShortcut.dropPos[1] == shortcut.dropPos[1]))) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }

    static void updateItemInDatabaseHelper(final Context context, final ContentValues values,
            final ItemInfo item, final String callingFunction) {
        final long itemId = item.id;
        final Uri uri = LauncherSettings.Favorites.getContentUri(itemId, false);
        final ContentResolver cr = context.getContentResolver();

        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        Runnable r = new Runnable() {
            public void run() {
                /// delete overlap item.
                ShortcutInfo shortcut = getShortcutByLocation(context, values.getAsLong(LauncherSettings.Favorites.CONTAINER), 
                        values.getAsInteger(LauncherSettings.Favorites.SCREEN), 
                        values.getAsInteger(LauncherSettings.Favorites.CELLX), 
                        values.getAsInteger(LauncherSettings.Favorites.CELLY));
                if (shortcut != null && shortcut.state != ShortcutInfo.STATE_OK && itemId != shortcut.id) {
                    deleteItemFromDatabase(context, shortcut);
                }
                /// end
                cr.update(uri, values, null, null);

                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    checkItemInfoLocked(itemId, item, stackTrace);

                    if (item.container != LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                            item.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                        // Item is in a folder, make sure this folder exists
                        if (!sBgFolders.containsKey(item.container)) {
                            // An items container is being set to a that of an item which is not in
                            // the list of Folders.
                            String msg = "item: " + item + " container being set to: " +
                                    item.container + ", not in the list of folders";
                            Log.e(TAG, msg);
                            Launcher.dumpDebugLogsToConsole();
                        }
                    }

                    // Items are added/removed from the corresponding FolderInfo elsewhere, such
                    // as in Workspace.onDrop. Here, we just add/remove them from the list of items
                    // that are on the desktop, as appropriate
                    ItemInfo modelItem = sBgItemsIdMap.get(itemId);
                    if (modelItem == null) {
                        Log.e(TAG, "get item from map failed, modelItem == null:"+itemId + " item:"+item);
                        return;
                    }
                    if (modelItem.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                            modelItem.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                        switch (modelItem.itemType) {
                            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                if (!sBgWorkspaceItems.contains(modelItem)) {
                                    sBgWorkspaceItems.add(modelItem);
                                }
                                break;
                            default:
                                break;
                        }
                    } else {
                        sBgWorkspaceItems.remove(modelItem);
                    }
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    static void moveItemInDatabase(Context context, final ItemInfo item, final long container,
            final int screen, final int cellX, final int cellY) {
        String transaction = "DbDebug    Modify item (" + item.title + ") in db, id: " + item.id +
                " (" + item.container + ", " + item.screen + ", " + item.cellX + ", " + item.cellY +
                ") --> " + "(" + container + ", " + screen + ", " + cellX + ", " + cellY + ")";
        Launcher.sDumpLogs.add(transaction);
        Log.d(TAG, transaction);
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;
        item.screen = screen;
        final ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, item.container);
        values.put(LauncherSettings.Favorites.CELLX, item.cellX);
        values.put(LauncherSettings.Favorites.CELLY, item.cellY);
        values.put(LauncherSettings.Favorites.SCREEN, LauncherModel.getScreenIdByIndex(screen));
        values.put(LauncherSettings.Favorites.IS_HIDDEN, item.isHidden);

        updateItemInDatabaseHelper(context, values, item, "moveItemInDatabase");
    }

    /**
     * Move and/or resize item in the DB to a new <container, screen, cellX, cellY, spanX, spanY>
     */
    static void modifyItemInDatabase(Context context, final ItemInfo item, final long container,
            final int screen, final int cellX, final int cellY, final int spanX, final int spanY) {
        String transaction = "DbDebug    Modify item (" + item.title + ") in db, id: " + item.id +
                " (" + item.container + ", " + item.screen + ", " + item.cellX + ", " + item.cellY +
                ") --> " + "(" + container + ", " + screen + ", " + cellX + ", " + cellY + ")";
        Launcher.sDumpLogs.add(transaction);
        Log.d(TAG, transaction);
        item.cellX = cellX;
        item.cellY = cellY;
        item.spanX = spanX;
        item.spanY = spanY;
        item.screen = screen;

        final ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, item.container);
        values.put(LauncherSettings.Favorites.CELLX, item.cellX);
        values.put(LauncherSettings.Favorites.CELLY, item.cellY);
        values.put(LauncherSettings.Favorites.SPANX, item.spanX);
        values.put(LauncherSettings.Favorites.SPANY, item.spanY);
        values.put(LauncherSettings.Favorites.IS_HIDDEN, item.isHidden);
        values.put(LauncherSettings.Favorites.SCREEN, LauncherModel.getScreenIdByIndex(screen));

        updateItemInDatabaseHelper(context, values, item, "modifyItemInDatabase");
    }

    /**
     * Update an item to the database in a specified container.
     */
    static void updateItemInDatabase(Context context, final ItemInfo item) {
        final ContentValues values = new ContentValues();
        item.onAddToDatabase(values);
        item.updateValuesWithCoordinates(values, item.cellX, item.cellY);
        updateItemInDatabaseHelper(context, values, item, "updateItemInDatabase");
    }
    
    // Begin, added by zhumeiquan, for the screen id and index map, 20130821
    private static final ArrayList<Long> sScreenInfoMap = new ArrayList<Long>();

    public HashMap<ComponentName, ShortcutInfo> getWorkspaceAppItems() {
        return mBgAllAppsList.workspaceAppItems;
    }

    public static ArrayList<Long> getScreenInfoMap() {
        return sScreenInfoMap;
    }

    public static int getScreenCnt() {
        return sScreenInfoMap.size();
    }
    
    public static long getScreenIdByIndex(int index) {
        if (index >= 0 && index < sScreenInfoMap.size()) {
            return sScreenInfoMap.get(index);
        } else {
            return -1;
        }
    }
    
    public static int getScreenIndexById(long id) {
        if (id != -1) {
            for (int idx = 0; idx < sScreenInfoMap.size(); idx++) {
                if (sScreenInfoMap.get(idx) == id) {
                    return idx;
                }
            }
        }
        return -1;
    }
   
    public static void updateScreenItem(Context context) {
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues();
        for (int i = 0; i < sScreenInfoMap.size(); i++) {
            values.clear();
            values.put("screenOrder", i);
            if (cr.update(LauncherSettings.Screens.CONTENT_URI, values, "_id=?",
                    new String[] { String.valueOf(sScreenInfoMap.get(i))}) <= 0) {
                Log.e(TAG, "Failed to update screens table for reorder, aborting");
            }
        }
    }
    //zwsun@letek.com 20150108 start
    public static void deleteScreenItem(Context context, int position) {
        final ContentResolver cr = context.getContentResolver();
        sScreenInfoMap.remove(position);
        logScreenMap("LauncherModel.deleteScreenItem pos,"+position);
        cr.delete(LauncherSettings.Screens.CONTENT_URI, "screenOrder="+position, null);
        updateScreenItem(context);
    }
    public static long addScreenItem(Context context, int position) {
        final ContentResolver cr = context.getContentResolver();
        final ContentValues values = new ContentValues();
        values.put("screenOrder", position);
        if(sScreenInfoMap.size() == 1 && sScreenInfoMap.get(0) == CUSTROM_SCREENID){
            values.put("_id",1);
        }
        long scrId = Long.valueOf(cr.insert(LauncherSettings.Screens.CONTENT_URI, values).getLastPathSegment());
        //yixiao add #972732
        if(position > sScreenInfoMap.size()){
            position = sScreenInfoMap.size();
        }
        sScreenInfoMap.add(position, scrId);
        logScreenMap("LauncherModel.addScreenItem pos,"+position+"  id,"+scrId);
        return scrId;
    }
	//zwsun@letek.com 20150108 end
    // End
    //yixiao@lewatek.com add for custrom screen 2015.1.11 begin
    public static void deleteCustromScreen(Context context, int position) {
        final ContentResolver cr = context.getContentResolver();
        if(cr.delete(LauncherSettings.Screens.CONTENT_URI, "_id="+CUSTROM_SCREENID, null)>0){
           sScreenInfoMap.remove(position);
            logScreenMap("LauncherModel.deleteCustromScreen pos,"+position);
        }
        updateScreenItem(context);
    }
    
    public static long addCustromScreen(Context context, int position) {
        final ContentResolver cr = context.getContentResolver();
        final ContentValues values = new ContentValues();
        values.put("screenOrder", position);
        values.put("_id", CUSTROM_SCREENID);
        cr.insert(LauncherSettings.Screens.CONTENT_URI, values);
        sScreenInfoMap.add(position, CUSTROM_SCREENID);
        logScreenMap("LauncherModel.addCustromScreen pos,"+position+"  id,"+CUSTROM_SCREENID);
        return CUSTROM_SCREENID;
    }
    //yixiao@lewatek.com add for custrom screen 2015.1.11 end 
    
    /**
     * Returns true if the shortcuts already exists in the database.
     * we identify a shortcut by its title and intent.
     */
    static boolean shortcutExists(Context context, String title, Intent intent) {
//        final ContentResolver cr = context.getContentResolver();
//        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI,
//            new String[] { "title", "intent" }, "title=? and intent=?",
//            new String[] { title, intent.toUri(0) }, null);
//        boolean result = false;
//        try {
//            result = c.moveToFirst();
//        } finally {
//            c.close();
//        }
//        return result;
        // just query by intent, because sometime there is same icon with different name, by luoyongxing
        final ContentResolver cr = context.getContentResolver();
        ComponentName cn = intent.getComponent();
        if (cn == null) {
            return false;
        }

        String queryStr = "itemType=0 AND intent Like '%" + "component=" + cn.flattenToShortString() + "%'";
        String[] selection = {
                LauncherSettings.Favorites.TITLE, 
                LauncherSettings.Favorites.INTENT
        };

        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI,
                selection, queryStr,
                null, null);
        boolean result = false;
        try {
            result = c.moveToFirst();
        } finally {
            c.close();
        }
        return result;
    }

    //lqwang - pr938336 - modify begin
    /**
     * Returns true if the shortcuts already exists in the database.
     * we identify a shortcut by its title and intent.
     */
    static boolean shortcutFromThridAppExists(Context context, String title, Intent intent) {
        final ContentResolver cr = context.getContentResolver();
        final Intent intentWithPkg, intentWithoutPkg;

        if (intent.getComponent() != null) {
            // If component is not null, an intent with null package will produce
            // the same result and should also be a match.
            if (intent.getPackage() != null) {
                intentWithPkg = intent;
                intentWithoutPkg = new Intent(intent).setPackage(null);
            } else {
                intentWithPkg = new Intent(intent).setPackage(
                        intent.getComponent().getPackageName());
                intentWithoutPkg = intent;
            }
        } else {
            intentWithPkg = intent;
            intentWithoutPkg = intent;
        }
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI,
                new String[] { "title", "intent" }, "title=? and (intent=? or intent=?)",
                new String[] { title, intentWithPkg.toUri(0), intentWithoutPkg.toUri(0) }, null);
        boolean result = false;
        try {
            result = c.moveToFirst();
        } finally {
            c.close();
        }
        return result;
    }
    //lqwang - pr938336 - modify end


    /**
     * Returns true if the shortcuts already exists in the database.
     * we identify a shortcut by its ComponentName.
     */
    static boolean shortcutExists(Context context, ComponentName cn) {
        // just query by intent, because sometime there is same icon with different name, by luoyongxing
        final ContentResolver cr = context.getContentResolver();

        if (cn == null) {
            return false;
        }
    
        String queryStr = "itemType=0 AND intent Like '%" + "component=" + cn.flattenToShortString() + ";end'";
        String[] selection = {
                LauncherSettings.Favorites.TITLE, 
                LauncherSettings.Favorites.INTENT
        };

        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI,
                selection, queryStr,
                null, null);
        boolean result = false;
        try {
            result = c.moveToFirst();
        } finally {
            c.close();
        }
        return result;
    }
    
    static ShortcutInfo getShortcutByLocation(Context context, long container, int screen, int cellX, int cellY) {
        // never check item in folder.
        if (container > 0) {
            return null;
        }
        final ContentResolver cr = context.getContentResolver();
        String[] selection = new String[] {
                LauncherSettings.Favorites._ID, LauncherSettings.Favorites.INTENT,
                LauncherSettings.Favorites.ITEM_TYPE
        };
        String[] selectionArg = new String[] {
                String.valueOf(container), String.valueOf(screen), String.valueOf(cellX),
                String.valueOf(cellY)
        };
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI, selection, 
            "container=? AND screen=? AND cellX=? AND cellY=?",
            selectionArg, null);
       
        final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
        final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
        final int intentIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
        
        try{
            if (c.moveToFirst()) {
                long id = c.getLong(idIndex);
                int itemType = c.getInt(itemTypeIndex);
                String intentStr = c.getString(intentIndex);
                if (itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                        || itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                    Intent intent = intentStr != null ? Intent.parseUri(intentStr, 0) : null;
                    if (sBgItemsIdMap == null) {
                        Log.e(TAG, "ERROR!, sBgItemsIdMap == null when getShortcutByLocation(). for id:"+id);
                        return null;
                    }
                    ItemInfo item = sBgItemsIdMap.get(id);
                    if (item instanceof ShortcutInfo) {
                        return (ShortcutInfo)item;
                    } else {
                        Log.e(TAG, "ERROR!, item isn't a ShortcutInfo. for id:"+id);
                    }
                }
            }
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            c.close();
        }
        return null;
    }

    /**
     * Returns an ItemInfo array containing all the items in the LauncherModel.
     * The ItemInfo.id is not set through this function.
     */
    static ArrayList<ItemInfo> getItemsInLocalCoordinates(Context context) {
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI, new String[] {
        		LauncherSettings.Favorites._ID, LauncherSettings.Favorites.ITEM_TYPE, LauncherSettings.Favorites.CONTAINER,
                LauncherSettings.Favorites.SCREEN, LauncherSettings.Favorites.CELLX, LauncherSettings.Favorites.CELLY,
                LauncherSettings.Favorites.SPANX, LauncherSettings.Favorites.SPANY }, null, null, null);

        final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
        final int containerIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
        final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
        final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
        final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
        final int spanXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANX);
        final int spanYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANY);
        final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
        

        try {
            while (c.moveToNext()) {
                ItemInfo item = new ItemInfo();
                item.cellX = c.getInt(cellXIndex);
                item.cellY = c.getInt(cellYIndex);
                item.spanX = c.getInt(spanXIndex);
                item.spanY = c.getInt(spanYIndex);
                item.container = c.getInt(containerIndex);
                item.itemType = c.getInt(itemTypeIndex);
                item.screen = getScreenIndexById(c.getInt(screenIndex));
                item.id = c.getLong(idIndex);
                items.add(item);
            }
        } catch (Exception e) {
            items.clear();
        } finally {
            c.close();
        }

        return items;
    }

    /**
     * Find a folder in the db, creating the FolderInfo if necessary, and adding it to folderList.
     */
    FolderInfo getFolderById(Context context, HashMap<Long,FolderInfo> folderList, long id) {
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI, null,
                "_id=? and (itemType=? or itemType=?)",
                new String[] { String.valueOf(id),
                        String.valueOf(LauncherSettings.Favorites.ITEM_TYPE_FOLDER)}, null);

        try {
            if (c.moveToFirst()) {
                final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
                final int titleIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
                final int containerIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
                final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
                final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
                final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);

                FolderInfo folderInfo = null;
                switch (c.getInt(itemTypeIndex)) {
                    case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                        folderInfo = findOrMakeFolder(folderList, id);
                        break;
                }

                folderInfo.title = c.getString(titleIndex);
                folderInfo.id = id;
                folderInfo.container = c.getInt(containerIndex);
                folderInfo.screen = getScreenIndexById(c.getInt(screenIndex));
                folderInfo.cellX = c.getInt(cellXIndex);
                folderInfo.cellY = c.getInt(cellYIndex);
                return folderInfo;
            }
        } finally {
            c.close();
        }
        return null;
    }
    //PR937172 zwsun modify begin
    FolderInfo getFolderByTitle(Context context, String title){
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI, null,
                "title=? and (itemType=? or itemType=?)",
                new String[] { String.valueOf(title),
                        String.valueOf(LauncherSettings.Favorites.ITEM_TYPE_FOLDER)}, null);
        try {
            if (c.moveToFirst()) {
                final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
                final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
                final int titleIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
                final int containerIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
                final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
                final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
                final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);

                FolderInfo folderInfo = new FolderInfo();

                folderInfo.id = c.getLong(idIndex);
                folderInfo.title = c.getString(titleIndex);
                folderInfo.container = c.getInt(containerIndex);
                folderInfo.screen = getScreenIndexById(c.getInt(screenIndex));
                folderInfo.cellX = c.getInt(cellXIndex);
                folderInfo.cellY = c.getInt(cellYIndex);
                return folderInfo;
            }
        } finally {
            c.close();
        }
        return null;
    }
    //PR937172 zwsun modify end
    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    static void addItemToDatabase(final Context context, final ItemInfo item, final long container,
            final int screen, final int cellX, final int cellY, final boolean notify) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;
        item.screen = screen;

        final ContentValues values = new ContentValues();
        final ContentResolver cr = context.getContentResolver();
        item.onAddToDatabase(values);

        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        item.id = app.getLauncherProvider().generateNewId();
        values.put(LauncherSettings.Favorites._ID, item.id);
        item.updateValuesWithCoordinates(values, item.cellX, item.cellY);
        
        Runnable r = new Runnable() {
            public void run() {
                String transaction = "DbDebug    Add item (" + item.title + ") to db, id: "
                        + item.id + " (" + container + ", " + screen + ", " + cellX + ", "
                        + cellY + ")";
                Launcher.sDumpLogs.add(transaction);
                Log.d(TAG, transaction);
              /// delete overlap item.
                ShortcutInfo shortcut = getShortcutByLocation(context, values.getAsLong(LauncherSettings.Favorites.CONTAINER), 
                        values.getAsInteger(LauncherSettings.Favorites.SCREEN), 
                        values.getAsInteger(LauncherSettings.Favorites.CELLX), 
                        values.getAsInteger(LauncherSettings.Favorites.CELLY));
                if (shortcut != null && shortcut.state != ShortcutInfo.STATE_OK) {
                    deleteItemFromDatabase(context, shortcut);
                }
                /// end
                cr.insert(notify ? LauncherSettings.Favorites.CONTENT_URI :
                        LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    checkItemInfoLocked(item.id, item, null);
                    sBgItemsIdMap.put(item.id, item);
                    switch (item.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                            sBgFolders.put(item.id, (FolderInfo) item);
                            // Fall through
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                        case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                                    item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                                sBgWorkspaceItems.add(item);
                            } else {
                                if (!sBgFolders.containsKey(item.container)) {
                                    // Adding an item to a folder that doesn't exist.
                                    String msg = "adding item: " + item + " to a folder that " +
                                            " doesn't exist";
                                    Log.e(TAG, msg);
                                    Launcher.dumpDebugLogsToConsole();
                                }
                            }
                            break;
                        case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                            sBgAppWidgets.add((LauncherAppWidgetInfo) item);
                            break;
                    }
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Creates a new unique child id, for a given cell span across all layouts.
     */
    static int getCellLayoutChildId(
            long container, int screen, int localCellX, int localCellY, int spanX, int spanY) {
        return (((int) container & 0xFF) << 24)
                | (screen & 0xFF) << 16 | (localCellX & 0xFF) << 8 | (localCellY & 0xFF);
    }

    static int getCellCountX() {
        return mCellCountX;
    }

    static int getCellCountY() {
        return mCellCountY;
    }

    /**
     * Updates the model orientation helper to take into account the current layout dimensions
     * when performing local/canonical coordinate transformations.
     */
    static void updateWorkspaceLayoutCells(int shortAxisCellCount, int longAxisCellCount) {
        mCellCountX = shortAxisCellCount;
        mCellCountY = longAxisCellCount;
    }

    /**
     * Removes the specified item from the database
     * @param context
     * @param item
     */
    static void deleteItemFromDatabase(Context context, final ItemInfo item) {
        final ContentResolver cr = context.getContentResolver();
        final Uri uriToDelete = LauncherSettings.Favorites.getContentUri(item.id, false);
        if (DEBUG_LOADERS) {
            Log.d(TAG, "deleteItemFromDatabase:"+item.title+", id="+item.id);
            new Throwable().printStackTrace();
        }
        Runnable r = new Runnable() {
            public void run() {
                String transaction = "DbDebug    Delete item (" + item.title + ") from db, id: "
                        + item.id + " (" + item.container + ", " + item.screen + ", " + item.cellX +
                        ", " + item.cellY + ")";
                Launcher.sDumpLogs.add(transaction);
                Log.d(TAG, transaction);

                cr.delete(uriToDelete, null, null);

                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    switch (item.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                            sBgFolders.remove(item.id);
                            for (ItemInfo info: sBgItemsIdMap.values()) {
                                if (info.container == item.id) {
                                    // We are deleting a folder which still contains items that
                                    // think they are contained by that folder.
                                    String msg = "deleting a folder (" + item + ") which still " +
                                            "contains items (" + info + ")";
                                    Log.e(TAG, msg);
                                    Launcher.dumpDebugLogsToConsole();
                                }
                            }
                            sBgWorkspaceItems.remove(item);
                            break;
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                        case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                            sBgWorkspaceItems.remove(item);
                            break;
                        case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                            sBgAppWidgets.remove((LauncherAppWidgetInfo) item);
                            break;
                    }
                    sBgItemsIdMap.remove(item.id);
                    sBgDbIconCache.remove(item);
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Remove the contents of the specified folder from the database
     */
    static void deleteFolderContentsFromDatabase(Context context, final FolderInfo info) {
        final ContentResolver cr = context.getContentResolver();

        Runnable r = new Runnable() {
            public void run() {
                cr.delete(LauncherSettings.Favorites.getContentUri(info.id, false), null, null);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    sBgItemsIdMap.remove(info.id);
                    sBgFolders.remove(info.id);
                    sBgDbIconCache.remove(info);
                    sBgWorkspaceItems.remove(info);
                }

                cr.delete(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION,
                        LauncherSettings.Favorites.CONTAINER + "=" + info.id, null);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    for (ItemInfo childInfo : info.contents) {
                        sBgItemsIdMap.remove(childInfo.id);
                        sBgDbIconCache.remove(childInfo);
                    }
                }
            }
        };
        runOnWorkerThread(r);
    }
    
    /**
     * dismiss the contents of the specified folder from the database
     */
    static void dismissFolderContentsFromDatabase(Context context, final FolderInfo info) {
        final ContentResolver cr = context.getContentResolver();

        Runnable r = new Runnable() {
            public void run() {
                cr.delete(LauncherSettings.Favorites.getContentUri(info.id, false), null, null);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    sBgItemsIdMap.remove(info.id);
                    sBgFolders.remove(info.id);
                    sBgDbIconCache.remove(info);
                    sBgWorkspaceItems.remove(info);
                }

            }
        };
        runOnWorkerThread(r);
    }


    /**
     * Set this as the current Launcher activity object for the loader.
     */
    public void initialize(Callbacks callbacks) {
        synchronized (mLock) {
            mCallbacks = new WeakReference<Callbacks>(callbacks);
        }
    }

    /**
     * Call from the handler for ACTION_PACKAGE_ADDED, ACTION_PACKAGE_REMOVED and
     * ACTION_PACKAGE_CHANGED.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG_LOADERS) Log.d(TAG, "onReceive intent=" + intent);

        final String action = intent.getAction();

        if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            final String packageName = intent.getData().getSchemeSpecificPart();
            final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

            int op = PackageUpdatedTask.OP_NONE;

            if (packageName == null || packageName.length() == 0) {
                // they sent us a bad intent
                return;
            }

            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                op = PackageUpdatedTask.OP_UPDATE;
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                if (!replacing) {
                    op = PackageUpdatedTask.OP_REMOVE;
                }
                // else, we are replacing the package, so a PACKAGE_ADDED will be sent
                // later, we will update the package at this time
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                if (!replacing) {
                    Launcher.isInstallAdd = true;
                    op = PackageUpdatedTask.OP_ADD;
                    EditModeUtils.logE(TAG, "newAddApps add : "+packageName);
                    newAddApps.add(packageName);
                    //lqwang-PR58643-modify begin
                    PreferencesProvider.putStringValue(context, Constants.NEWAPPSKEY,newAddApps.toString());
                    //lqwang-PR58643-modify end
                } else {
                    op = PackageUpdatedTask.OP_UPDATE;
                }
            }

            if (op != PackageUpdatedTask.OP_NONE) {
                enqueuePackageUpdated(new PackageUpdatedTask(op, new String[] { packageName }));
            }

        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
            // First, schedule to add these apps back in.
            String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_AVAILABLE, packages));
            // Then, rebind everything.
            startLoaderFromBackground();
        } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
            String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            enqueuePackageUpdated(new PackageUpdatedTask(
                        PackageUpdatedTask.OP_UNAVAILABLE, packages));
        } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // If we have changed locale we need to clear out the labels in all apps/workspace.
            forceReload();
        } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
             // Check if configuration change was an mcc/mnc change which would affect app resources
             // and we would need to clear out the labels in all apps/workspace. Same handling as
             // above for ACTION_LOCALE_CHANGED
             Configuration currentConfig = context.getResources().getConfiguration();
             if (mPreviousConfigMcc != currentConfig.mcc) {
                   Log.d(TAG, "Reload apps on config change. curr_mcc:"
                       + currentConfig.mcc + " prevmcc:" + mPreviousConfigMcc);
                   forceReload();
             }
             // Update previousConfig
             mPreviousConfigMcc = currentConfig.mcc;
             Launcher.isLocalChanged = true;
        }
    }

    private void forceReload() {
        resetLoadedState(true, true);

        // Do this here because if the launcher activity is running it will be restarted.
        // If it's not running startLoaderFromBackground will merely tell it that it needs
        // to reload.
        startLoaderFromBackground();
    }

    public void resetLoadedState(boolean resetAllAppsLoaded, boolean resetWorkspaceLoaded) {
        synchronized (mLock) {
            // Stop any existing loaders first, so they don't set mAllAppsLoaded or
            // mWorkspaceLoaded to true later
            stopLoaderLocked();
            if (resetAllAppsLoaded) mAllAppsLoaded = false;
            if (resetWorkspaceLoaded) mWorkspaceLoaded = false;
        }
    }

    /**
     * When the launcher is in the background, it's possible for it to miss paired
     * configuration changes.  So whenever we trigger the loader from the background
     * tell the launcher that it needs to re-run the loader when it comes back instead
     * of doing it now.
     */
    public void startLoaderFromBackground() {
        boolean runLoader = false;
        if (mCallbacks != null) {
            Callbacks callbacks = mCallbacks.get();
            if (callbacks != null) {
                // Only actually run the loader if they're not paused.
                if (!callbacks.setLoadOnResume()) {
                    runLoader = true;
                }
            }
        }
        if (runLoader) {
            startLoader(false, -1);
        }
    }

    // If there is already a loader task running, tell it to stop.
    // returns true if isLaunching() was true on the old task
    private boolean stopLoaderLocked() {
        boolean isLaunching = false;
        LoaderTask oldTask = mLoaderTask;
        if (oldTask != null) {
            if (oldTask.isLaunching()) {
                isLaunching = true;
            }
            oldTask.stopLocked();
        }
        return isLaunching;
    }

    public void startLoader(boolean isLaunching, int synchronousBindPage) {
        synchronized (mLock) {
            if (DEBUG_LOADERS) {
                Log.d(TAG, "startLoader isLaunching=" + isLaunching);
            }

            // Clear any deferred bind-runnables from the synchronized load process
            // We must do this before any loading/binding is scheduled below.
            mDeferredBindRunnables.clear();

            // Don't bother to start the thread if we know it's not going to do anything
            if (mCallbacks != null && mCallbacks.get() != null) {
                // If there is already one running, tell it to stop.
                // also, don't downgrade isLaunching if we're already running
                isLaunching = isLaunching || stopLoaderLocked();
                mLoaderTask = new LoaderTask(mApp, isLaunching);
                if (synchronousBindPage > -1 && mAllAppsLoaded && mWorkspaceLoaded) {
                    mLoaderTask.runBindSynchronousPage(synchronousBindPage);
                } else {
                    sWorkerThread.setPriority(Thread.NORM_PRIORITY);
                    sWorker.post(mLoaderTask);
                }
            }
        }
    }

    void bindRemainingSynchronousPages() {
        // Post the remaining side pages to be loaded
        if (!mDeferredBindRunnables.isEmpty()) {
            for (final Runnable r : mDeferredBindRunnables) {
                mHandler.post(r, MAIN_THREAD_BINDING_RUNNABLE);
            }
            mDeferredBindRunnables.clear();
        }
    }

    public void stopLoader() {
        synchronized (mLock) {
            if (mLoaderTask != null) {
                mLoaderTask.stopLocked();
            }
        }
    }

    public boolean isAllAppsLoaded() {
        return mAllAppsLoaded;
    }

    boolean isLoadingWorkspace() {
        synchronized (mLock) {
            if (mLoaderTask != null) {
                return mLoaderTask.isLoadingWorkspace();
            }
        }
        return false;
    }

    /**
     * Runnable for the thread that loads the contents of the launcher:
     *   - workspace icons
     *   - widgets
     *   - all apps icons
     */
    private class LoaderTask implements Runnable {
        private Context mContext;
        private boolean mIsLaunching;
        private boolean mIsLoadingAndBindingWorkspace;
        private boolean mStopped;
        private boolean mLoadAndBindStepFinished;

        private HashMap<Object, CharSequence> mLabelCache;

        LoaderTask(Context context, boolean isLaunching) {
            mContext = context;
            mIsLaunching = isLaunching;
            mLabelCache = new HashMap<Object, CharSequence>();
        }

        boolean isLaunching() {
            return mIsLaunching;
        }

        boolean isLoadingWorkspace() {
            return mIsLoadingAndBindingWorkspace;
        }

        private void loadAndBindWorkspace() {
            mIsLoadingAndBindingWorkspace = true;

            // Load the workspace
            if (DEBUG_LOADERS) {
                Log.d(TAG, "loadAndBindWorkspace mWorkspaceLoaded=" + mWorkspaceLoaded);
            }

            if (!mWorkspaceLoaded) {
                loadWorkspace();
                synchronized (LoaderTask.this) {
                    if (mStopped) {
                        return;
                    }
                    mWorkspaceLoaded = true;
                }
            }

            // Bind the workspace
            bindWorkspace(-1);
        }

        private void waitForIdle() {
            // Wait until the either we're stopped or the other threads are done.
            // This way we don't start loading all apps until the workspace has settled
            // down.
            synchronized (LoaderTask.this) {
                final long workspaceWaitTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

                mHandler.postIdle(new Runnable() {
                        public void run() {
                            synchronized (LoaderTask.this) {
                                mLoadAndBindStepFinished = true;
                                if (DEBUG_LOADERS) {
                                    Log.d(TAG, "done with previous binding step");
                                }
                                LoaderTask.this.notify();
                            }
                        }
                    });

                while (!mStopped && !mLoadAndBindStepFinished) {
                    try {
                        this.wait();
                    } catch (InterruptedException ex) {
                        // Ignore
                    }
                }
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "waited "
                            + (SystemClock.uptimeMillis()-workspaceWaitTime)
                            + "ms for previous step to finish binding");
                }
            }
        }

        void runBindSynchronousPage(int synchronousBindPage) {
            if (synchronousBindPage < 0) {
                // Ensure that we have a valid page index to load synchronously
                throw new RuntimeException("Should not call runBindSynchronousPage() without " +
                        "valid page index");
            }
            if (!mAllAppsLoaded || !mWorkspaceLoaded) {
                // Ensure that we don't try and bind a specified page when the pages have not been
                // loaded already (we should load everything asynchronously in that case)
                throw new RuntimeException("Expecting AllApps and Workspace to be loaded");
            }
            synchronized (mLock) {
                if (mIsLoaderTaskRunning) {
                    // Ensure that we are never running the background loading at this point since
                    // we also touch the background collections
                    Log.e(TAG, "Error! Background loading is already running");
                    return;
//                    throw new RuntimeException("Error! Background loading is already running");
                }
            }

            // XXX: Throw an exception if we are already loading (since we touch the worker thread
            //      data structures, we can't allow any other thread to touch that data, but because
            //      this call is synchronous, we can get away with not locking).

            // The LauncherModel is static in the LauncherApplication and mHandler may have queued
            // operations from the previous activity.  We need to ensure that all queued operations
            // are executed before any synchronous binding work is done.
            mHandler.flush();

            // Divide the set of loaded items into those that we are binding synchronously, and
            // everything else that is to be bound normally (asynchronously).
            bindWorkspace(synchronousBindPage);
            // XXX: For now, continue posting the binding of AllApps as there are other issues that
            //      arise from that.
            onlyBindAllApps();
        }

        public void run() {
            synchronized (mLock) {
                mIsLoaderTaskRunning = true;
            }
            // Optimize for end-user experience: if the Launcher is up and // running with the
            // All Apps interface in the foreground, load All Apps first. Otherwise, load the
            // workspace first (default).
            final boolean loadWorkspaceFirst = true;

            keep_running: {
                // Elevate priority when Home launches for the first time to avoid
                // starving at boot time. Staring at a blank home is not cool.
                synchronized (mLock) {
                    if (DEBUG_LOADERS) Log.d(TAG, "Setting thread priority to " +
                            (mIsLaunching ? "DEFAULT" : "BACKGROUND"));
                    android.os.Process.setThreadPriority(mIsLaunching
                            ? Process.THREAD_PRIORITY_DEFAULT : Process.THREAD_PRIORITY_BACKGROUND);
                }
                if (loadWorkspaceFirst) {
                    if (DEBUG_LOADERS) Log.d(TAG, "step 1: loading workspace");
                    loadAndBindWorkspace();
                } else {
                    if (DEBUG_LOADERS) Log.d(TAG, "step 1: special: loading all apps");
                    loadAndBindAllApps();
                }

                if (mStopped) {
                    break keep_running;
                }

                // Whew! Hard work done.  Slow us down, and wait until the UI thread has
                // settled down.
                synchronized (mLock) {
                    if (mIsLaunching) {
                        if (DEBUG_LOADERS) Log.d(TAG, "Setting thread priority to BACKGROUND");
                        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    }
                }
                waitForIdle();

                // second step
                if (loadWorkspaceFirst) {
                    if (DEBUG_LOADERS) Log.d(TAG, "step 2: loading all apps");
                    loadAndBindAllApps();
                } else {
                    if (DEBUG_LOADERS) Log.d(TAG, "step 2: special: loading workspace");
                    loadAndBindWorkspace();
                }

                // Restore the default thread priority after we are done loading items
                synchronized (mLock) {
                    android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                }
            }


            // Update the saved icons if necessary
            if (DEBUG_LOADERS) Log.d(TAG, "Comparing loaded icons to database icons");
            synchronized (sBgLock) {
                for (Object key : sBgDbIconCache.keySet()) {
                    updateSavedIcon(mContext, (ShortcutInfo) key, sBgDbIconCache.get(key));
                }
                sBgDbIconCache.clear();
            }

            // Clear out this reference, otherwise we end up holding it until all of the
            // callback runnables are done.
            mContext = null;

            synchronized (mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask == this) {
                    mLoaderTask = null;
                }
                mIsLoaderTaskRunning = false;
            }
        }

        public void stopLocked() {
            synchronized (LoaderTask.this) {
                mStopped = true;
                this.notify();
            }
        }

        /**
         * Gets the callbacks object.  If we've been stopped, or if the launcher object
         * has somehow been garbage collected, return null instead.  Pass in the Callbacks
         * object that was around when the deferred message was scheduled, and if there's
         * a new Callbacks object around then also return null.  This will save us from
         * calling onto it with data that will be ignored.
         */
        Callbacks tryGetCallbacks(Callbacks oldCallbacks) {
            synchronized (mLock) {
                if (mStopped) {
                    return null;
                }

                if (mCallbacks == null) {
                    return null;
                }

                final Callbacks callbacks = mCallbacks.get();
                if (callbacks != oldCallbacks) {
                    return null;
                }
                if (callbacks == null) {
                    Log.w(TAG, "no mCallbacks");
                    return null;
                }

                return callbacks;
            }
        }

        // check & update map of what's occupied; used to discard overlapping/invalid items
        private boolean checkItemPlacement(ItemInfo occupied[][][], ItemInfo item) {
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                || item.container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                // Skip further checking if it is not the hotseat or workspace container
                return true;
            }

            int containerIndex = item.screen;
            // Check if any workspace icons overlap with each other
            for (int x = item.cellX; x < (item.cellX+item.spanX); x++) {
                for (int y = item.cellY; y < (item.cellY+item.spanY); y++) {
                    if (occupied[containerIndex][x][y] != null) {
                        Log.e(TAG, "Error loading shortcut " + item
                            + " into cell (" + containerIndex + "-" + item.screen + ":"
                            + x + "," + y
                            + ") occupied by "
                            + occupied[containerIndex][x][y]);
                        return false;
                    }
                }
            }
            for (int x = item.cellX; x < (item.cellX+item.spanX); x++) {
                for (int y = item.cellY; y < (item.cellY+item.spanY); y++) {
                    occupied[containerIndex][x][y] = item;
                }
            }

            return true;
        }

        private void loadWorkspace() {
            final long t = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            final Context context = mContext;
            final ContentResolver contentResolver = context.getContentResolver();
            final PackageManager manager = context.getPackageManager();
            final AppWidgetManager widgets = AppWidgetManager.getInstance(context);
            HashMap<ComponentName, ShortcutInfo> workspaceAppItems = new HashMap<ComponentName, ShortcutInfo>();
            
            final boolean isSafeMode = manager.isSafeMode();
            
            synchronized (sBgLock) {
                sBgWorkspaceItems.clear();
                sBgAppWidgets.clear();
                sBgFolders.clear();
                sBgItemsIdMap.clear();
                sBgDbIconCache.clear();
                mBgAllAppsList.workspaceLoadCompleted = false;

                final ArrayList<Long> itemsToRemove = new ArrayList<Long>();
                final HashMap<ComponentName, ResolveInfo> allActivitiesMap = getAllActivitiesMap(mContext);
                final Cursor c = contentResolver.query(
                        LauncherSettings.Favorites.CONTENT_URI, null, null, null, null);

                // +1 for the hotseat (it can be larger than the workspace)
                // Load workspace in reverse order to ensure that latest items are loaded first (and
                // before any earlier duplicates)
                int workspaceCnt = sScreenInfoMap.size();
                final ItemInfo occupied[][][] =
                        new ItemInfo[workspaceCnt + 1][mCellCountX + 1][mCellCountY + 1];

                try {
                    final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
                    final int intentIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.INTENT);
                    final int titleIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.TITLE);
                    final int iconTypeIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ICON_TYPE);
                    final int iconIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
                    final int iconPackageIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ICON_PACKAGE);
                    final int iconResourceIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ICON_RESOURCE);
                    final int containerIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.CONTAINER);
                    final int itemTypeIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ITEM_TYPE);
                    final int appWidgetIdIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.APPWIDGET_ID);
                    final int screenIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.SCREEN);
                    final int cellXIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.CELLX);
                    final int cellYIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.CELLY);
                    final int spanXIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.SPANX);
                    final int spanYIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.SPANY);
                    final int hiddenIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.IS_HIDDEN);
                    //final int uriIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.URI);
                    //final int displayModeIndex = c.getColumnIndexOrThrow(
                    //        LauncherSettings.Favorites.DISPLAY_MODE);

                    ShortcutInfo info;
                    String intentDescription;
                    LauncherAppWidgetInfo appWidgetInfo;
                    int container;
                    long id;
                    Intent intent;
                    
                    boolean exception = false;
                    
                    while (!mStopped && c.moveToNext()) {
                        try {
                            int itemType = c.getInt(itemTypeIndex);

                            switch (itemType) {
                            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                                intentDescription = c.getString(intentIndex);
                                try {
                                    intent = Intent.parseUri(intentDescription, 0);
                                } catch (URISyntaxException e) {
                                	exception = true;
                                	e.printStackTrace();
                                    continue;
                                }

                                if (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                    info = getShortcutInfo(manager, intent, context, c, iconIndex,
                                            titleIndex, mLabelCache);
                                } else {
                                    info = getShortcutInfo(c, context, iconTypeIndex,
                                            iconPackageIndex, iconResourceIndex, iconIndex,
                                            titleIndex);

                                    // App shortcuts that used to be automatically added to Launcher
                                    // didn't always have the correct intent flags set, so do that
                                    // here
                                    if (intent.getAction() != null &&
                                        intent.getCategories() != null &&
                                        intent.getAction().equals(Intent.ACTION_MAIN) &&
                                        intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                                        // don't add FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, because the QQ shortcut can't enter dialog UI. by luoyongxing
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK /*| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED*/);
                                    }
                                }
                                if (info != null) {
                                    info.intent = intent;
                                    info.id = c.getLong(idIndex);
                                    container = c.getInt(containerIndex);
                                    info.container = container;
                                    info.screen = getScreenIndexById(c.getInt(screenIndex));
                                    info.cellX = c.getInt(cellXIndex);
                                    info.cellY = c.getInt(cellYIndex);
                                    info.isHidden = c.getInt(hiddenIndex);
                                    // Begin ,added by yxluo, for Apps installed on SD card
                                    info.state = (itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT 
                                            || allActivitiesMap.containsKey(intent.getComponent())) ? ShortcutInfo.STATE_OK : ShortcutInfo.STATE_UNAVAILABLE;
                                    if (info.state != ShortcutInfo.STATE_OK) {                                        
                                        Log.i(TAG, "hide " + info+" itemType = "+itemType);
                                    }
                                    
                                    if (!info.isHidden()) {
                                        // check & update map of what's occupied
                                        if (info.state == ShortcutInfo.STATE_OK && !checkItemPlacement(occupied, info)) {
                                            Log.w(TAG, "WARNNING!! checkItemPlacement failed for : " + info);
                                            break;
                                        }
                                    }

                                    switch (container) {
                                    case LauncherSettings.Favorites.CONTAINER_DESKTOP:
                                    case LauncherSettings.Favorites.CONTAINER_HOTSEAT:
                                        sBgWorkspaceItems.add(info);
                                        break;
                                    default:
                                        // Item is in a user folder
                                        FolderInfo folderInfo = findOrMakeFolder(sBgFolders, container);
                                        folderInfo.add(info);
                                        break;
                                    }
                                    /// put all workspace application item to it.
                                    ComponentName cn = intent.getComponent();
                                    if (cn != null && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                        if (workspaceAppItems.containsKey(cn)) {
                                            info.state = ShortcutInfo.STATE_DUPLICATED;
                                            Log.e(TAG, "application shortcut dulicated:" + info);
                                            // TODO: need to removed ?
                                        } else {
                                            workspaceAppItems.put(cn, info);
                                        }
                                    }
                                    /// end
                                    sBgItemsIdMap.put(info.id, info);

                                    // now that we've loaded everthing re-save it with the
                                    // icon in case it disappears somehow.
                                    queueIconToBeChecked(sBgDbIconCache, info, c, iconIndex);
                                } else {
                                    // Failed to load the shortcut, probably because the
                                    // activity manager couldn't resolve it (maybe the app
                                    // was uninstalled), or the db row was somehow screwed up.
                                    // Delete it.
                                    id = c.getLong(idIndex);
                                    Log.e(TAG, "Error loading shortcut " + id + ", removing it");
                                    contentResolver.delete(LauncherSettings.Favorites.getContentUri(
                                                id, false), null, null);
                                }
                                break;

                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                id = c.getLong(idIndex);
                                FolderInfo folderInfo = findOrMakeFolder(sBgFolders, id);

                                folderInfo.title = c.getString(titleIndex);
                                folderInfo.id = id;
                                container = c.getInt(containerIndex);
                                folderInfo.container = container;
                                folderInfo.screen = getScreenIndexById(c.getInt(screenIndex));
                                folderInfo.cellX = c.getInt(cellXIndex);
                                folderInfo.cellY = c.getInt(cellYIndex);

                                // check & update map of what's occupied
                                if (!checkItemPlacement(occupied, folderInfo)) {
                                    break;
                                }
                                switch (container) {
                                    case LauncherSettings.Favorites.CONTAINER_DESKTOP:
                                    case LauncherSettings.Favorites.CONTAINER_HOTSEAT:
                                        sBgWorkspaceItems.add(folderInfo);
                                        break;
                                }

                                sBgItemsIdMap.put(folderInfo.id, folderInfo);
                                sBgFolders.put(folderInfo.id, folderInfo);
                                break;

                            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                                // Read all Launcher-specific widget details
                                int appWidgetId = c.getInt(appWidgetIdIndex);
                                id = c.getLong(idIndex);

                                final AppWidgetProviderInfo provider =
                                        widgets.getAppWidgetInfo(appWidgetId);

                                if (!isSafeMode && (provider == null || provider.provider == null ||
                                        provider.provider.getPackageName() == null)) {
                                    String log = "Deleting widget that isn't installed anymore: id="
                                        + id + " appWidgetId=" + appWidgetId;
                                    Log.e(TAG, log);
                                    Launcher.sDumpLogs.add(log);
                                    itemsToRemove.add(id);
                                } else {
                                    appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId, provider.provider);
                                    appWidgetInfo.id = id;
                                    appWidgetInfo.screen = getScreenIndexById(c.getInt(screenIndex));
                                    appWidgetInfo.cellX = c.getInt(cellXIndex);
                                    appWidgetInfo.cellY = c.getInt(cellYIndex);
                                    appWidgetInfo.spanX = c.getInt(spanXIndex);
                                    appWidgetInfo.spanY = c.getInt(spanYIndex);
                                    int[] minSpan = Launcher.getMinSpanForWidget(context, provider);
                                    appWidgetInfo.minSpanX = minSpan[0];
                                    appWidgetInfo.minSpanY = minSpan[1];

                                    container = c.getInt(containerIndex);
                                    if (container != LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                                        container != LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                                        Log.e(TAG, "Widget found where container != " +
                                            "CONTAINER_DESKTOP nor CONTAINER_HOTSEAT - ignoring!");
                                        continue;
                                    }
                                    appWidgetInfo.container = c.getInt(containerIndex);

                                    // check & update map of what's occupied
                                    if (!checkItemPlacement(occupied, appWidgetInfo)) {
                                        break;
                                    }
                                    sBgItemsIdMap.put(appWidgetInfo.id, appWidgetInfo);
                                    sBgAppWidgets.add(appWidgetInfo);
                                }
                                break;
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Desktop items loading interrupted:", e);
                            exception = true;
                        }
                    }
                    /// upate if that are some items was removed or added when Launcher inactive.
                    if(!mStopped && !exception) {
                        mBgAllAppsList.workspaceLoadCompleted = true;
                        mBgAllAppsList.workspaceAppItems = workspaceAppItems;
   
                        boolean needDeleteRemoved = !mRemovedChecked && AllAppsList.isAllAppsScaned();
                        if (DEBUG_LOADERS) 
                            Log.i(TAG, "start updateRemovedPackages() when loading workspace needDeleteRemoved:"+needDeleteRemoved);
                        if (needDeleteRemoved) {
                            mBgAllAppsList.updateRemovedPackages(workspaceAppItems, allActivitiesMap.keySet());
                            mRemovedChecked = true;
                        }
                        if (!mMissedChecked) {
                            mBgAllAppsList.updateMissedPackages(workspaceAppItems, allActivitiesMap);
                            mMissedChecked = true;
                        }
                    } 
                    /// end
                } finally {
                    c.close();
                }

                if (itemsToRemove.size() > 0) {
                    ContentProviderClient client = contentResolver.acquireContentProviderClient(
                                    LauncherSettings.Favorites.CONTENT_URI);
                    // Remove dead items
                    for (long id : itemsToRemove) {
                        if (DEBUG_LOADERS) {
                            Log.d(TAG, "Removed id = " + id);
                        }
                        // Don't notify content observers
                        try {
                            client.delete(LauncherSettings.Favorites.getContentUri(id, false),
                                    null, null);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Could not remove id = " + id);
                        }
                    }
                }

                if (DEBUG_LOADERS) {
                    Log.d(TAG, "loaded workspace in " + (SystemClock.uptimeMillis()-t) + "ms");
                    Log.d(TAG, "workspace layout: ");
                    for (int y = 0; y < mCellCountY; y++) {
                        String line = "";
                        for (int s = 0; s < LauncherModel.getScreenCnt(); s++) {
                            if (s > 0) {
                                line += " | ";
                            }
                            for (int x = 0; x < mCellCountX; x++) {
                                line += ((occupied[s][x][y] != null) ? "#" : ".");
                            }
                        }
                        Log.d(TAG, "[ " + line + " ]");
                    }
                }
            }
            loadRecommendApps();
        }
        
        /**
         * Load the recommend APP from database
         */
        private void loadRecommendApps() {
            final Context context = mContext;
            final ContentResolver contentResolver = context.getContentResolver();
            synchronized (sBgLock) {
                final Cursor c = contentResolver.query(LauncherSettings.RecommendApps.CONTENT_URI, null, null, null, null);
                try {
                    while (!mStopped && c.moveToNext()) {
                        int container = c.getInt(c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER));
                        FolderInfo folderInfo = findOrMakeFolder(sBgFolders, container);
                        ShortcutInfo info = new ShortcutInfo();
                        info.id = c.getLong(c.getColumnIndexOrThrow(LauncherSettings.RecommendApps._ID));
                        info.title = c.getString(c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE));
                        info.container = container;
                        info.recommendPkgName = c.getString(c.getColumnIndexOrThrow(LauncherSettings.RecommendApps.PKG_NAME));
                        info.iconUrl = c.getString(c.getColumnIndexOrThrow(LauncherSettings.RecommendApps.ICON_URL));
                        info.downloadUrl = c.getString(c.getColumnIndexOrThrow(LauncherSettings.RecommendApps.DOWNLOAD_URL));
                        folderInfo.addRecommendApp(info);
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }
        }

        /** Filters the set of items who are directly or indirectly (via another container) on the
         * specified screen. */
        private void filterCurrentWorkspaceItems(int currentScreen,
                ArrayList<ItemInfo> allWorkspaceItems,
                ArrayList<ItemInfo> currentScreenItems,
                ArrayList<ItemInfo> otherScreenItems) {
            // Purge any null ItemInfos
            Iterator<ItemInfo> iter = allWorkspaceItems.iterator();
            while (iter.hasNext()) {
                ItemInfo i = iter.next();
                if (i == null) {
                    iter.remove();
                }
            }

            // If we aren't filtering on a screen, then the set of items to load is the full set of
            // items given.
            if (currentScreen < 0) {
                currentScreenItems.addAll(allWorkspaceItems);
            }

            // Order the set of items by their containers first, this allows use to walk through the
            // list sequentially, build up a list of containers that are in the specified screen,
            // as well as all items in those containers.
            Set<Long> itemsOnScreen = new HashSet<Long>();
            Collections.sort(allWorkspaceItems, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    return (int) (lhs.container - rhs.container);
                }
            });
            for (ItemInfo info : allWorkspaceItems) {
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    if (info.screen == currentScreen) {
                        currentScreenItems.add(info);
                        itemsOnScreen.add(info.id);
                    } else {
                        otherScreenItems.add(info);
                    }
                } else if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    if (itemsOnScreen.contains(info.container)) {
                        currentScreenItems.add(info);
                        itemsOnScreen.add(info.id);
                    } else {
                        otherScreenItems.add(info);
                    }
                }
            }
        }

        /** Filters the set of widgets which are on the specified screen. */
        private void filterCurrentAppWidgets(int currentScreen,
                ArrayList<LauncherAppWidgetInfo> appWidgets,
                ArrayList<LauncherAppWidgetInfo> currentScreenWidgets,
                ArrayList<LauncherAppWidgetInfo> otherScreenWidgets) {
            // If we aren't filtering on a screen, then the set of items to load is the full set of
            // widgets given.
            if (currentScreen < 0) {
                currentScreenWidgets.addAll(appWidgets);
            }

            for (LauncherAppWidgetInfo widget : appWidgets) {
                if (widget == null) continue;
                if (widget.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                        widget.screen == currentScreen) {
                    currentScreenWidgets.add(widget);
                } else {
                    otherScreenWidgets.add(widget);
                }
            }
        }

        /** Filters the set of folders which are on the specified screen. */
        private void filterCurrentFolders(int currentScreen,
                HashMap<Long, ItemInfo> itemsIdMap,
                HashMap<Long, FolderInfo> folders,
                HashMap<Long, FolderInfo> currentScreenFolders,
                HashMap<Long, FolderInfo> otherScreenFolders) {
            // If we aren't filtering on a screen, then the set of items to load is the full set of
            // widgets given.
            if (currentScreen < 0) {
                currentScreenFolders.putAll(folders);
            }

            for (long id : folders.keySet()) {
                ItemInfo info = itemsIdMap.get(id);
                FolderInfo folder = folders.get(id);
                if (info == null || folder == null) continue;
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                        info.screen == currentScreen) {
                    currentScreenFolders.put(id, folder);
                } else {
                    otherScreenFolders.put(id, folder);
                }
            }
        }

        /** Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to
         * right) */
        private void sortWorkspaceItemsSpatially(ArrayList<ItemInfo> workspaceItems) {
            // XXX: review this
            Collections.sort(workspaceItems, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    int cellCountX = LauncherModel.getCellCountX();
                    int cellCountY = LauncherModel.getCellCountY();
                    int screenOffset = cellCountX * cellCountY;
                    int workspaceCnt = getScreenCnt();
                    int containerOffset = screenOffset * (workspaceCnt + 1); // +1 hotseat
                    long lr = (lhs.container * containerOffset + lhs.screen * screenOffset +
                            lhs.cellY * cellCountX + lhs.cellX);
                    long rr = (rhs.container * containerOffset + rhs.screen * screenOffset +
                            rhs.cellY * cellCountX + rhs.cellX);
                    return (int) (lr - rr);
                }
            });
        }

        private void bindWorkspaceItems(final Callbacks oldCallbacks,
                final ArrayList<ItemInfo> workspaceItems,
                final ArrayList<LauncherAppWidgetInfo> appWidgets,
                final HashMap<Long, FolderInfo> folders,
                ArrayList<Runnable> deferredBindRunnables) {

            final boolean postOnMainThread = (deferredBindRunnables != null);

            //lqwang - PR50940 - modify begin
            // Bind the widgets, one at a time
            int N = appWidgets.size();
            for (int i = 0; i < N; i++) {
                final LauncherAppWidgetInfo widget = appWidgets.get(i);
                final Runnable r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindAppWidget(widget);
                        }
                    }
                };
                if (postOnMainThread) {
                    deferredBindRunnables.add(r);
                } else {
                    runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
                }
            }
            //lqwang - PR50940 - modify end

            // Bind the workspace items
             N = workspaceItems.size();
            for (int i = 0; i < N; i += ITEMS_CHUNK) {
                final int start = i;
                final int chunkSize = (i+ITEMS_CHUNK <= N) ? ITEMS_CHUNK : (N-i);
                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindItems(workspaceItems, start, start+chunkSize);
                        }
                    }
                };
                if (postOnMainThread) {
                    deferredBindRunnables.add(r);
                } else {
                    runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
                }
            }

            // Bind the folders
            if (!folders.isEmpty()) {
                final Runnable r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindFolders(folders);
                        }
                    }
                };
                if (postOnMainThread) {
                    deferredBindRunnables.add(r);
                } else {
                    runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
                }
            }
        }

        /**
         * Binds all loaded data to actual views on the main thread.
         */
        private void bindWorkspace(int synchronizeBindPage) {
            final long t = SystemClock.uptimeMillis();
            Runnable r;

            // Don't use these two variables in any of the callback runnables.
            // Otherwise we hold a reference to them.
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher");
                return;
            }

            final boolean isLoadingSynchronously = (synchronizeBindPage > -1);
            final int currentScreen = isLoadingSynchronously ? synchronizeBindPage :
                oldCallbacks.getCurrentWorkspaceScreen();
            if (currentScreen == -1) {
                Log.e(TAG, "can't find workspace, in error state?");
                return;
            }
            // Load all the items that are on the current page first (and in the process, unbind
            // all the existing workspace items before we call startBinding() below.
            unbindWorkspaceItemsOnMainThread();
            ArrayList<ItemInfo> workspaceItems = new ArrayList<ItemInfo>();
            ArrayList<LauncherAppWidgetInfo> appWidgets =
                    new ArrayList<LauncherAppWidgetInfo>();
            HashMap<Long, FolderInfo> folders = new HashMap<Long, FolderInfo>();
            HashMap<Long, ItemInfo> itemsIdMap = new HashMap<Long, ItemInfo>();
            synchronized (sBgLock) {
                workspaceItems.addAll(sBgWorkspaceItems);
                appWidgets.addAll(sBgAppWidgets);
                folders.putAll(sBgFolders);
                itemsIdMap.putAll(sBgItemsIdMap);
            }

            ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<ItemInfo>();
            ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<ItemInfo>();
            ArrayList<LauncherAppWidgetInfo> currentAppWidgets =
                    new ArrayList<LauncherAppWidgetInfo>();
            ArrayList<LauncherAppWidgetInfo> otherAppWidgets =
                    new ArrayList<LauncherAppWidgetInfo>();
            HashMap<Long, FolderInfo> currentFolders = new HashMap<Long, FolderInfo>();
            HashMap<Long, FolderInfo> otherFolders = new HashMap<Long, FolderInfo>();

            // Separate the items that are on the current screen, and all the other remaining items
            filterCurrentWorkspaceItems(currentScreen, workspaceItems, currentWorkspaceItems,
                    otherWorkspaceItems);
            filterCurrentAppWidgets(currentScreen, appWidgets, currentAppWidgets,
                    otherAppWidgets);
            filterCurrentFolders(currentScreen, itemsIdMap, folders, currentFolders,
                    otherFolders);
            sortWorkspaceItemsSpatially(currentWorkspaceItems);
            sortWorkspaceItemsSpatially(otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.startBinding();
                    }
                }
            };
            runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);

            // Load items on the current page
            bindWorkspaceItems(oldCallbacks, currentWorkspaceItems, currentAppWidgets,
                    currentFolders, null);
            if (isLoadingSynchronously) {
                r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.onPageBoundSynchronously(currentScreen);
                        }
                    }
                };
                runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
            }

            // Load all the remaining pages (if we are loading synchronously, we want to defer this
            // work until after the first render)
            mDeferredBindRunnables.clear();
            bindWorkspaceItems(oldCallbacks, otherWorkspaceItems, otherAppWidgets, otherFolders,
                    (isLoadingSynchronously ? mDeferredBindRunnables : null));

            // Tell the workspace that we're done binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.finishBindingItems();
                    }

                    // If we're profiling, ensure this is the last thing in the queue.
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "bound workspace in "
                            + (SystemClock.uptimeMillis()-t) + "ms");
                    }

                    mIsLoadingAndBindingWorkspace = false;
                }
            };
            if (isLoadingSynchronously) {
                mDeferredBindRunnables.add(r);
            } else {
                runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
            }
        }

        private void loadAndBindAllApps() {
            if (DEBUG_LOADERS) {
                Log.d(TAG, "loadAndBindAllApps mAllAppsLoaded=" + mAllAppsLoaded);
            }
            if (!mAllAppsLoaded) {
                loadAllAppsByBatch();
                synchronized (LoaderTask.this) {
                    if (mStopped) {
                        return;
                    }
                    mAllAppsLoaded = true;
                }
            } else {
                onlyBindAllApps();
            }
        }

        private void onlyBindAllApps() {
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (onlyBindAllApps)");
                return;
            }

            // shallow copy
            @SuppressWarnings("unchecked")
            final ArrayList<ApplicationInfo> list
                    = (ArrayList<ApplicationInfo>) mBgAllAppsList.data.clone();
            Runnable r = new Runnable() {
                public void run() {
                    final long t = SystemClock.uptimeMillis();
                    final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindAllApplications(list);
                    }
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "bound all " + list.size() + " apps from cache in "
                                + (SystemClock.uptimeMillis()-t) + "ms");
                    }
                }
            };
            boolean isRunningOnMainThread = !(sWorkerThread.getThreadId() == Process.myTid());
            if (oldCallbacks.isAllAppsVisible() && isRunningOnMainThread) {
                r.run();
            } else {
                mHandler.post(r);
            }
        }

        private void loadAllAppsByBatch() {
            final long t = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            // Don't use these two variables in any of the callback runnables.
            // Otherwise we hold a reference to them.
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (loadAllAppsByBatch)");
                return;
            }

            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            final PackageManager packageManager = mContext.getPackageManager();
            List<ResolveInfo> apps = null;

            int N = Integer.MAX_VALUE;

            int startIndex;
            int i=0;
            int batchSize = -1;
            while (i < N && !mStopped) {
                if (i == 0) {
                    mBgAllAppsList.clear();
                    final long qiaTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;
                    apps = packageManager.queryIntentActivities(mainIntent, 0);
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "queryIntentActivities took "
                                + (SystemClock.uptimeMillis()-qiaTime) + "ms");
                    }
                    if (apps == null) {
                        return;
                    }
                    N = apps.size();
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "queryIntentActivities got " + N + " apps");
                    }
                    if (N == 0) {
                        // There are no apps?!?
                        return;
                    }
                    if (mBatchSize == 0) {
                        batchSize = N;
                    } else {
                        batchSize = mBatchSize;
                    }

                    final long sortTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;
                    // Don't need to sort any more.
                    //Collections.sort(apps,
                    //        new LauncherModel.ShortcutNameComparator(packageManager, mLabelCache));
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "sort took "
                                + (SystemClock.uptimeMillis()-sortTime) + "ms");
                    }
                }

                final long t2 = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

                startIndex = i;
                for (int j=0; i<N && j<batchSize; j++) {
                    // This builds the icon bitmaps.
                    // Begin, modifieded by zhumeiquan, for some apps always need hidden, 20131225
                    if (!isAppNeedHidden(mContext,apps.get(i))) {
                        mBgAllAppsList.add(new ApplicationInfo(packageManager, apps.get(i),
                                mIconCache, mLabelCache));
                    }
                    // End
                    i++;
                }

                final boolean first = i <= batchSize;
                final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                final ArrayList<ApplicationInfo> added = mBgAllAppsList.added;
                mBgAllAppsList.added = new ArrayList<ApplicationInfo>();

                mHandler.post(new Runnable() {
                    public void run() {
                        final long t = SystemClock.uptimeMillis();
                        if (callbacks != null) {
                            if (first) {
                                callbacks.bindAllApplications(added);
                            } else {
                                callbacks.bindAppsAdded(added);
                            }
                            if (DEBUG_LOADERS) {
                                Log.d(TAG, "bound " + added.size() + " apps in "
                                    + (SystemClock.uptimeMillis() - t) + "ms");
                            }
                        } else {
                            Log.i(TAG, "not binding apps: no Launcher activity");
                        }
                    }
                });

                if (DEBUG_LOADERS) {
                    Log.d(TAG, "batch of " + (i-startIndex) + " icons processed in "
                            + (SystemClock.uptimeMillis()-t2) + "ms");
                }

                if (mAllAppsLoadDelay > 0 && i < N) {
                    try {
                        if (DEBUG_LOADERS) {
                            Log.d(TAG, "sleeping for " + mAllAppsLoadDelay + "ms");
                        }
                        Thread.sleep(mAllAppsLoadDelay);
                    } catch (InterruptedException exc) { }
                }
            }

            if (DEBUG_LOADERS) {
                Log.d(TAG, "cached all " + N + " apps in "
                        + (SystemClock.uptimeMillis()-t) + "ms"
                        + (mAllAppsLoadDelay > 0 ? " (including delay)" : ""));
            }
        }

        public void dumpState() {
            synchronized (sBgLock) {
                Log.d(TAG, "mLoaderTask.mContext=" + mContext);
                Log.d(TAG, "mLoaderTask.mIsLaunching=" + mIsLaunching);
                Log.d(TAG, "mLoaderTask.mStopped=" + mStopped);
                Log.d(TAG, "mLoaderTask.mLoadAndBindStepFinished=" + mLoadAndBindStepFinished);
                Log.d(TAG, "mItems size=" + sBgWorkspaceItems.size());
            }
        }
    }

    void enqueuePackageUpdated(PackageUpdatedTask task) {
        sWorker.post(task);
    }

    private class PackageUpdatedTask implements Runnable {
        int mOp;
        String[] mPackages;

        public static final int OP_NONE = 0;
        public static final int OP_ADD = 1;
        public static final int OP_UPDATE = 2;
        public static final int OP_REMOVE = 3; // uninstlled
        public static final int OP_UNAVAILABLE = 4; // external media unmounted
        public static final int OP_AVAILABLE = 5; // external media mounted

        public PackageUpdatedTask(int op, String[] packages) {
            mOp = op;
            mPackages = packages;
        }

        public void run() {
            final Context context = mApp;

            final String[] packages = mPackages;
            final int N = packages.length;
            switch (mOp) {
                case OP_ADD:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.addPackage " + packages[i]);
                        mBgAllAppsList.addPackage(context, packages[i]);
                    }
                    break;
                    
                case OP_AVAILABLE:
                    if (!mRemovedChecked) {
                        if (DEBUG_LOADERS) 
                            Log.i(TAG, "start updateRemovedPackages() when apps available.");
                        HashMap<ComponentName, ResolveInfo> allActivities = getAllActivitiesMap(context);
                        mBgAllAppsList.updateRemovedPackages(mBgAllAppsList.workspaceAppItems, allActivities.keySet());
                    }
                	for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.packageAvailable " + packages[i]);
                        mBgAllAppsList.packageAvailable(context, packages[i]);
                    }
                	break;
                	
                case OP_UPDATE:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.updatePackage " + packages[i]);
                        mBgAllAppsList.updatePackage(context, packages[i]);
                    }
                    break;
                case OP_REMOVE:
                case OP_UNAVAILABLE:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                        mBgAllAppsList.removePackage(packages[i], mOp == OP_UNAVAILABLE);
                    }
                    break;
            }

            ArrayList<ApplicationInfo> added = null;
            ArrayList<ApplicationInfo> modified = null;
            ArrayList<ItemInfo> missed = null;
            ArrayList<ApplicationInfo> removed = null;
            if (mBgAllAppsList.added.size() > 0) {
                added = new ArrayList<ApplicationInfo>(mBgAllAppsList.added);
                mBgAllAppsList.added.clear();
            }
            if (mBgAllAppsList.modified.size() > 0) {
                modified = new ArrayList<ApplicationInfo>(mBgAllAppsList.modified);
                mBgAllAppsList.modified.clear();
            }
            if (mBgAllAppsList.missed.size() > 0) {
                missed = new ArrayList<ItemInfo>(mBgAllAppsList.missed);
                mBgAllAppsList.missed.clear();
            }
            // We may be removing packages that have no associated launcher application, so we
            // pass through the removed package names directly.
            // NOTE: We flush the icon cache aggressively in removePackage() above.
//            final ArrayList<String> removedPackageNames = new ArrayList<String>();
            if (mBgAllAppsList.removed.size() > 0) {
                removed = new ArrayList<ApplicationInfo>(mBgAllAppsList.removed);
                mBgAllAppsList.removed.clear();
//                for (int i = 0; i < N; ++i) {
//                    removedPackageNames.add(packages[i]);
//                }
            }

            final Callbacks callbacks = mCallbacks != null ? mCallbacks.get() : null;
            if (callbacks == null) {
                Log.w(TAG, "Nobody to tell about the new app.  Launcher is probably loading.");
                return;
            }

            if (added != null) {
                final ArrayList<ApplicationInfo> addedFinal = added;
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = mCallbacks != null ? mCallbacks.get() : null;
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppsAdded(addedFinal);
                        }
                    }
                });
            }
            if (modified != null) {
                final ArrayList<ApplicationInfo> modifiedFinal = modified;
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = mCallbacks != null ? mCallbacks.get() : null;
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppsUpdated(modifiedFinal);
                        }
                    }
                });
            }
            if (missed != null) {
                final ArrayList<ItemInfo> missedFinal = missed;
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = mCallbacks != null ? mCallbacks.get() : null;
                        if (DEBUG_LOADERS) {
                            Log.i(TAG, "find missed, try to bind, missed size = " + missedFinal.size());
                        }
                        if (callbacks == cb && cb != null) {
                            callbacks.bindItems(missedFinal, 0, missedFinal.size());
                        } else {
                            Log.w(TAG, "find missed, but callback == null:" + missedFinal.size());
                        }
                    }
                });
            } 
            if (removed != null) {
                final boolean permanent = mOp == OP_REMOVE;
                final ArrayList<ApplicationInfo> removedFinal = removed;
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = mCallbacks != null ? mCallbacks.get() : null;
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppsRemoved(removedFinal, permanent);
                        }
                    }
                });
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Callbacks cb = mCallbacks != null ? mCallbacks.get() : null;
                    if (callbacks == cb && cb != null) {
                        callbacks.bindPackagesUpdated();
                    }
                }
            });
        }
    }

    /**
     * This is called from the code that adds shortcuts from the intent receiver.  This
     * doesn't have a Cursor, but
     */
    public ShortcutInfo getShortcutInfo(PackageManager manager, Intent intent, Context context) {
        return getShortcutInfo(manager, intent, context, null, -1, -1, null);
    }

    /**
     * Make an ShortcutInfo object for a shortcut that is an application.
     *
     * If c is not null, then it will be used to fill in missing data like the title and icon.
     */
    public ShortcutInfo getShortcutInfo(PackageManager manager, Intent intent, Context context,
            Cursor c, int iconIndex, int titleIndex, HashMap<Object, CharSequence> labelCache) {
        Bitmap icon = null;
        final ShortcutInfo info = new ShortcutInfo();

        ComponentName componentName = intent.getComponent();
        if (componentName == null) {
            return null;
        }

        try {
            PackageInfo pi = manager.getPackageInfo(componentName.getPackageName(), 0);
            if (!pi.applicationInfo.enabled) {
                // If we return null here, the corresponding item will be removed from the launcher
                // db and will not appear in the workspace.
                return null;
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "getPackInfo failed for package " + componentName.getPackageName());
        }

        // TODO: See if the PackageManager knows about this case.  If it doesn't
        // then return null & delete this.

        // the resource -- This may implicitly give us back the fallback icon,
        // but don't worry about that.  All we're doing with usingFallbackIcon is
        // to avoid saving lots of copies of that in the database, and most apps
        // have icons anyway.

        // Attempt to use queryIntentActivities to get the ResolveInfo (with IntentFilter info) and
        // if that fails, or is ambiguious, fallback to the standard way of getting the resolve info
        // via resolveActivity().
        ResolveInfo resolveInfo = null;
        ComponentName oldComponent = intent.getComponent();
        Intent newIntent = new Intent(intent.getAction(), null);
        newIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        newIntent.setPackage(oldComponent.getPackageName());
        List<ResolveInfo> infos = manager.queryIntentActivities(newIntent, 0);
        for (ResolveInfo i : infos) {
            ComponentName cn = new ComponentName(i.activityInfo.packageName,
                    i.activityInfo.name);
            if (cn.equals(oldComponent)) {
                resolveInfo = i;
            }
        }
        if (resolveInfo == null) {
            resolveInfo = manager.resolveActivity(intent, 0);
        }
        if (resolveInfo != null) {
            icon = mIconCache.getIcon(componentName, resolveInfo, labelCache);
        }
        // the db
        if (icon == null) {
            if (c != null) {
                icon = getIconFromCursor(c, iconIndex, context);
            }
        }
        // the fallback icon
        if (icon == null) {
            icon = getFallbackIcon();
            info.usingFallbackIcon = true;
        }
        info.setIcon(icon);

        // from the resource
        if (resolveInfo != null) {
            ComponentName key = LauncherModel.getComponentNameFromResolveInfo(resolveInfo);
            if (labelCache != null && labelCache.containsKey(key)) {
                info.title = labelCache.get(key);
            } else {
                info.title = resolveInfo.activityInfo.loadLabel(manager);
                if (labelCache != null) {
                    labelCache.put(key, info.title);
                }
            }
        }
        // from the db
        if (info.title == null) {
            if (c != null) {
                info.title =  c.getString(titleIndex);
            }
        }
        // fall back to the class name of the activity
        if (info.title == null) {
            info.title = componentName.getClassName();
        }
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.isNewAdded = isNewAdded(componentName.getPackageName());
        return info;
    }

    /**
     * Returns the set of workspace ShortcutInfos with the specified intent.
     */
    static ArrayList<ItemInfo> getWorkspaceShortcutItemInfosWithIntent(Intent intent) {
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        synchronized (sBgLock) {
            for (ItemInfo info : sBgWorkspaceItems) {
                if (info instanceof ShortcutInfo) {
                    ShortcutInfo shortcut = (ShortcutInfo) info;
                    if (shortcut.intent.toUri(0).equals(intent.toUri(0))) {
                        items.add(shortcut);
                    }
                }
            }
        }
        return items;
    }

    /**
     * Make an ShortcutInfo object for a shortcut that isn't an application.
     */
    private ShortcutInfo getShortcutInfo(Cursor c, Context context,
            int iconTypeIndex, int iconPackageIndex, int iconResourceIndex, int iconIndex,
            int titleIndex) {

        Bitmap icon = null;
        final ShortcutInfo info = new ShortcutInfo();
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;

        // TODO: If there's an explicit component and we can't install that, delete it.

        info.title = c.getString(titleIndex);

        int iconType = c.getInt(iconTypeIndex);
        switch (iconType) {
        case LauncherSettings.Favorites.ICON_TYPE_RESOURCE:
            Drawable d = null;
            String packageName = c.getString(iconPackageIndex);
            String resourceName = c.getString(iconResourceIndex);

            String iconResName = null;
            if (resourceName != null && resourceName.endsWith(".png") && context.getPackageName().equals(packageName)) {
            	iconResName = resourceName;
            }

            if (iconResName != null) {
                d = mIconCache.getIcon(iconResName);
                if(d == null){
                	Log.e(TAG, "ERROR! can't get icon from file:"+iconResName);
                }
                info.customIcon = false;
            }

            if (d != null) {
                icon = Utilities.createIconBitmap(d, context);
            } else {
                PackageManager packageManager = context.getPackageManager();
                info.customIcon = false;
                // the resource
                try {
                    Resources resources = packageManager.getResourcesForApplication(packageName);
                    if (resources != null) {
                        final int id = resources.getIdentifier(resourceName, null, null);
                        icon = Utilities.createIconBitmap(IconCustomizer.generateShortcutDrawable(resources, id), context);
                    }
                } catch (Exception e) {
                    // drop this.  we have other places to look for icons
                }
            }
            // the db
            if (icon == null) {
                icon = getIconFromCursor(c, iconIndex, context);
            }
            // the fallback icon
            if (icon == null) {
                icon = getFallbackIcon();
                info.usingFallbackIcon = true;
            }
            break;
        case LauncherSettings.Favorites.ICON_TYPE_BITMAP:
            icon = getIconFromCursor(c, iconIndex, context);
            if (icon == null) {
                icon = getFallbackIcon();
                info.customIcon = false;
                info.usingFallbackIcon = true;
            } else {
                info.customIcon = true;
            }
            break;
        default:
            icon = getFallbackIcon();
            info.usingFallbackIcon = true;
            info.customIcon = false;
            break;
        }
        info.setIcon(icon);
        return info;
    }

    Bitmap getIconFromCursor(Cursor c, int iconIndex, Context context) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean debug = false;
        if (debug) {
            Log.d(TAG, "getIconFromCursor app="
                    + c.getString(c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE)));
        }
        byte[] data = c.getBlob(iconIndex);
        try {
            return Utilities.createIconBitmap(
                    BitmapFactory.decodeByteArray(data, 0, data.length), context);
        } catch (Exception e) {
            return null;
        }
    }

    ShortcutInfo addShortcut(Context context, Intent data, long container, int screen,
            int cellX, int cellY, boolean notify) {
        final ShortcutInfo info = infoFromShortcutIntent(context, data, null);
        if (info == null) {
            return null;
        }
        addItemToDatabase(context, info, container, screen, cellX, cellY, notify);

        return info;
    }

    /**
     * Attempts to find an AppWidgetProviderInfo that matches the given component.
     */
    AppWidgetProviderInfo findAppWidgetProviderInfoWithComponent(Context context,
            ComponentName component) {
        List<AppWidgetProviderInfo> widgets =
            AppWidgetManager.getInstance(context).getInstalledProviders();
        for (AppWidgetProviderInfo info : widgets) {
            if (info.provider.equals(component)) {
                return info;
            }
        }
        return null;
    }

    ShortcutInfo infoFromShortcutIntent(Context context, Intent data, Bitmap fallbackIcon) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        Parcelable bitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);

        if (intent == null) {
            // If the intent is null, we can't construct a valid ShortcutInfo, so we return null
            Log.e(TAG, "Can't construct ShorcutInfo with null intent");
            return null;
        }

        Bitmap icon = null;
        boolean customIcon = false;
        ShortcutIconResource iconResource = null;

        if (bitmap != null && bitmap instanceof Bitmap) {
            Drawable d = new BitmapDrawable(context.getResources(), (Bitmap)bitmap);
            d = IconCustomizer.generateIconDrawable(d);
            icon = Utilities.createIconBitmap(d, context);
            customIcon = true;
        } else {
            Parcelable extra = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            if (extra != null && extra instanceof ShortcutIconResource) {
                try {
                    iconResource = (ShortcutIconResource) extra;
                    final PackageManager packageManager = context.getPackageManager();
                    Resources resources = packageManager.getResourcesForApplication(
                            iconResource.packageName);
                    final int id = resources.getIdentifier(iconResource.resourceName, null, null);
                    icon = Utilities.createIconBitmap(
                            IconCustomizer.generateShortcutDrawable(resources, id), context);
                } catch (Exception e) {
                    Log.w(TAG, "Could not load shortcut icon: " + extra);
                }
            }
        }

        final ShortcutInfo info = new ShortcutInfo();

        if (icon == null) {
            if (fallbackIcon != null) {
                icon = fallbackIcon;
            } else {
                icon = getFallbackIcon();
                info.usingFallbackIcon = true;
            }
        }
        info.setIcon(icon);

        info.title = name;
        info.intent = intent;
        info.customIcon = customIcon;
        info.iconResource = iconResource;

        return info;
    }

    boolean queueIconToBeChecked(HashMap<Object, byte[]> cache, ShortcutInfo info, Cursor c,
            int iconIndex) {
        // If apps can't be on SD, don't even bother.
        if (!mAppsCanBeOnExternalStorage) {
            return false;
        }
        // If this icon doesn't have a custom icon, check to see
        // what's stored in the DB, and if it doesn't match what
        // we're going to show, store what we are going to show back
        // into the DB.  We do this so when we're loading, if the
        // package manager can't find an icon (for example because
        // the app is on SD) then we can use that instead.
        if (!info.customIcon && !info.usingFallbackIcon) {
            cache.put(info, c.getBlob(iconIndex));
            return true;
        }
        return false;
    }
    void updateSavedIcon(Context context, ShortcutInfo info, byte[] data) {
        boolean needSave = false;
        try {
            if (data != null) {
                Bitmap saved = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap loaded = info.getIcon(mIconCache);
                needSave = !saved.sameAs(loaded);
            } else {
                needSave = true;
            }
        } catch (Exception e) {
            needSave = true;
        }
        if (needSave) {
          // Log.d(TAG, "going to save icon bitmap for info=" + info);
            // This is slower than is ideal, but this only happens once
            // or when the app is updated with a new icon.
            updateItemInDatabase(context, info);
        }
    }

    /**
     * Return an existing FolderInfo object if we have encountered this ID previously,
     * or make a new one.
     */
    private static FolderInfo findOrMakeFolder(HashMap<Long, FolderInfo> folders, long id) {
        // See if a placeholder was created for us already
        FolderInfo folderInfo = folders.get(id);
        if (folderInfo == null) {
            // No placeholder -- create a new instance
            folderInfo = new FolderInfo();
            folders.put(id, folderInfo);
        }
        return folderInfo;
    }

    public static final Comparator<ApplicationInfo> getAppNameComparator() {
        final Collator collator = Collator.getInstance();
        return new Comparator<ApplicationInfo>() {
            public final int compare(ApplicationInfo a, ApplicationInfo b) {
                int result = collator.compare(a.title.toString(), b.title.toString());
                if (result == 0) {
                    result = a.componentName.compareTo(b.componentName);
                }
                return result;
            }
        };
    }
    public static final Comparator<ApplicationInfo> APP_INSTALL_TIME_COMPARATOR
            = new Comparator<ApplicationInfo>() {
        public final int compare(ApplicationInfo a, ApplicationInfo b) {
            if (a.firstInstallTime < b.firstInstallTime) return 1;
            if (a.firstInstallTime > b.firstInstallTime) return -1;
            return 0;
        }
    };
    public static final Comparator<AppWidgetProviderInfo> getWidgetNameComparator() {
        final Collator collator = Collator.getInstance();
        return new Comparator<AppWidgetProviderInfo>() {
            public final int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                return collator.compare(a.label.toString(), b.label.toString());
            }
        };
    }
    static ComponentName getComponentNameFromResolveInfo(ResolveInfo info) {
        if (info.activityInfo != null) {
            return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        } else {
            return new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
        }
    }
    public static class ShortcutNameComparator implements Comparator<ResolveInfo> {
        private Collator mCollator;
        private PackageManager mPackageManager;
        private HashMap<Object, CharSequence> mLabelCache;
        ShortcutNameComparator(PackageManager pm) {
            mPackageManager = pm;
            mLabelCache = new HashMap<Object, CharSequence>();
            mCollator = Collator.getInstance();
        }
        ShortcutNameComparator(PackageManager pm, HashMap<Object, CharSequence> labelCache) {
            mPackageManager = pm;
            mLabelCache = labelCache;
            mCollator = Collator.getInstance();
        }
        public final int compare(ResolveInfo a, ResolveInfo b) {
            CharSequence labelA, labelB;
            ComponentName keyA = LauncherModel.getComponentNameFromResolveInfo(a);
            ComponentName keyB = LauncherModel.getComponentNameFromResolveInfo(b);
            if (mLabelCache.containsKey(keyA)) {
                labelA = mLabelCache.get(keyA);
            } else {
                labelA = a.loadLabel(mPackageManager).toString();

                mLabelCache.put(keyA, labelA);
            }
            if (mLabelCache.containsKey(keyB)) {
                labelB = mLabelCache.get(keyB);
            } else {
                labelB = b.loadLabel(mPackageManager).toString();

                mLabelCache.put(keyB, labelB);
            }
            return mCollator.compare(labelA, labelB);
        }
    };

    public static class WidgetAndShortcutNameComparator implements Comparator<Object> {
        private Collator mCollator;
        private PackageManager mPackageManager;
        private HashMap<Object, String> mLabelCache;
        private HashSet<String> mFromLewaSet = new HashSet<String>();
        private HashMap<Object, Boolean> mIsFromLewaCache = new HashMap<Object, Boolean>();

        WidgetAndShortcutNameComparator(PackageManager pm) {
            mPackageManager = pm;
            mLabelCache = new HashMap<Object, String>();
            mCollator = Collator.getInstance();
            //
            mFromLewaSet.add("com.android.settings.CreateShortcut");
            mFromLewaSet.add("com.android.alarmclock.AnalogAppWidgetProvider");

        }

        public final int compare(Object a, Object b) {
            String labelA, labelB;
            if (mLabelCache.containsKey(a)) {
                labelA = mLabelCache.get(a);
            } else {
                labelA = (a instanceof AppWidgetProviderInfo) ?
                    ((AppWidgetProviderInfo) a).label :
                    ((ResolveInfo) a).loadLabel(mPackageManager).toString();
                mLabelCache.put(a, labelA);
            }
            if (mLabelCache.containsKey(b)) {
                labelB = mLabelCache.get(b);
            } else {
                labelB = (b instanceof AppWidgetProviderInfo) ?
                        ((AppWidgetProviderInfo) b).label :
                        ((ResolveInfo) b).loadLabel(mPackageManager).toString();
                mLabelCache.put(b, labelB);
            }
            boolean aFromLewa = isFromLewa(a);
            boolean bFromLewa = isFromLewa(b);
            if (aFromLewa && !bFromLewa) {
                return -1;
            } else if (!aFromLewa && bFromLewa) {
                return 1;
            }
            return mCollator.compare(labelA, labelB);
        }

        private boolean isFromLewa(Object o) {
            String pkgName = null;
            String clsName = null;
            if (o == null) {
                return false;
            }
            Boolean r = mIsFromLewaCache.get(o);
            if (r != null) {
                return r;
            }
            if (o instanceof AppWidgetProviderInfo) {
                ComponentName provider = ((AppWidgetProviderInfo) o).provider;
                pkgName = provider.getPackageName();
                clsName = provider.getClassName();
            } else if (o instanceof ResolveInfo) {
                if (((ResolveInfo) o).activityInfo != null) {
                    pkgName = ((ResolveInfo) o).activityInfo.packageName;
                    clsName = ((ResolveInfo) o).activityInfo.name;
                }
            }
            if (mFromLewaSet.contains(clsName)) {
                r = true;
            } else if (pkgName != null && pkgName.contains("lewa")) {
                r = true;
            } else {
                r = false;
            }
            mIsFromLewaCache.put(o, r);
            return r;
        }
    };
    

    public void dumpState() {
        Log.d(TAG, "mCallbacks=" + mCallbacks);
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.data", mBgAllAppsList.data);
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.added", mBgAllAppsList.added);
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.removed", mBgAllAppsList.removed);
        ApplicationInfo.dumpApplicationInfoList(TAG, "mAllAppsList.modified", mBgAllAppsList.modified);
        if (mLoaderTask != null) {
            mLoaderTask.dumpState();
        } else {
            Log.d(TAG, "mLoaderTask=null");
        }
    }
    
    private static HashMap<ComponentName, ResolveInfo> getAllActivitiesMap(Context context) {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activityList = context.getPackageManager().queryIntentActivities(mainIntent, 0);
        HashMap<ComponentName, ResolveInfo> activityMap = new HashMap<ComponentName, ResolveInfo>();
        for (ResolveInfo activity : activityList) {
            // Begin, modifieded by zhumeiquan, for some apps always need hidden, 20131225
            if (!isAppNeedHidden(context,activity)) {
                ComponentName cn = new ComponentName(activity.activityInfo.packageName, activity.activityInfo.name);
                activityMap.put(cn, activity);
            }
            // End
        }
        return activityMap;
    }
    
    public static boolean isItemVisible(ItemInfo item) {
        if (item instanceof  ShortcutInfo) {
            int state = ((ShortcutInfo)item).state;
            if (state == ShortcutInfo.STATE_DUPLICATED || state == ShortcutInfo.STATE_UNAVAILABLE) {
                return false;
            }
        }
        return true;
    }
    
    // Begin, modifieded by zhumeiquan, for some apps always need hidden, 20131225
    public static boolean isAppNeedHidden(Context context,ResolveInfo info) {
        //if ("***.***.***".equals(info.activityInfo.packageName)
        //     && "***.***.****".equals(info.activityInfo.name)) {
        //    return true;
        //}
        //lqwang - PR62913 - modify begin
        if (info != null && info.activityInfo != null) {
            String pkgName = info.activityInfo.packageName;
            List<String> hiddenApps = Arrays.asList(context.getResources().getStringArray(R.array.hiddenApps));
            if(hiddenApps.contains(pkgName)){
                return true;
            }
        }
        //lqwang - PR62913 - modify end
        return false;
    }
    // End
    
    public static ItemInfo getSyncedItemInfo(ItemInfo info) {
        if (sBgItemsIdMap != null && info != null && info.id > 0) {
            ItemInfo synced = sBgItemsIdMap.get(info.id);
            if (DEBUG_LOADERS) {
                if (synced != null) {
                    Log.i(TAG, "getSyncedItemInfo synced:"+ (synced == info) 
                            + " same:" + (info.id == synced.id) + " postion:" 
                            + (info.screen == synced.screen && info.cellX == synced.cellX 
                            && info.cellY == synced.cellY));
                } else {
                    Log.w(TAG, "WARNING!! getSyncedItemInfo failed for:"+info);
                }
            }
            return synced;
        }
        Log.w(TAG, "WARNING!! getSyncedItemInfo failed for:"+info + " sBgItemsIdMap != null?"+(sBgItemsIdMap != null));
        return null;
    }
    
    public static boolean isItemInfoSynced(Object o) {
        if (!(o instanceof ItemInfo)) {
            return true;
        }
        ItemInfo info = (ItemInfo)o;
        if (sBgItemsIdMap != null && info != null && info.id > 0) {
            ItemInfo synced = sBgItemsIdMap.get(info.id);
            return (synced == info);
        }
        return true;
    }

    private static final int SMART_SORT_TYPE = 0x1;
    private static final int RESTORE_SORT_TYPE = 0x2;
    public static boolean isSortingWorkspace = false;

    public void startSortTask(Context context, int taskType) {
        SortTask sortTask = new SortTask(context, taskType);
        sWorker.post(sortTask);
        resetLoadedState(false, true);
        startLoader(true, -1);
    }

    private class SortTask implements Runnable {
        int taskType = 0;
        Context context;

        SortTask(Context context, int type) {
            taskType = type;
            this.context = context;
        }

        public void run() {
            isSortingWorkspace = true;
            if (taskType == SMART_SORT_TYPE) {
                //lqwang - pr962152 - modify
                PreferencesProvider.getSharedPreferences(context).edit().putBoolean(Constants.APPS_SORTED,true).commit();
                smartSortApps(context);
            } else if (taskType == RESTORE_SORT_TYPE) {
                //lqwang - pr962152 - modify
                PreferencesProvider.getSharedPreferences(context).edit().putBoolean(Constants.APPS_SORTED,false).commit();
                unSmartSortApps(context);
            }
        }
    }

    public void unSmartSortApps(Context context) {
        ContentResolver cr = context.getContentResolver();
        for (Long id : sFolderIdBackup) {
            ItemInfo itemInfo = sBgItemsIdMap.get(id);
            if (itemInfo != null) {
                cr.delete(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, "_id=?", new String[] {
                    String.valueOf(itemInfo.id) });
            }
        }
        sShortcutItems.clear();
        sFolderItems.clear();
        int defaultScreen = PreferencesProvider.getDefaultScreen(context);
        for (ItemInfo info : sBgWorkspaceItems) {
            if (info instanceof FolderInfo) {
                if (!findInFolderBackup(info)) {
                    sFolderItems.add(info);
                }
            }
            if (info instanceof ShortcutInfo || info instanceof ApplicationInfo) {
                if (defaultScreen == info.screen) {
                    continue;
                }
                sShortcutItems.add(info);
            }
        }
        for (ItemInfo info : sShorcutItemsBackup) {
            ItemInfo itemInfo = sBgItemsIdMap.get(info.id);
            if (itemInfo != null && itemInfo.container > 0) {
                info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
                sShortcutItems.add(info);
            }
        }
        //arrange apps in workspace
        arrangeAppsInWorkspace(context);
        //update Database in background
        updateInDatabase(context);
        sFolderIdBackup.clear();
        sShorcutItemsBackup.clear();
    }

    private boolean findInFolderBackup(ItemInfo info) {
        for (Long id : sFolderIdBackup) {
            if (info.id == id) {
                return true;
            }
        }
        return false;
    }

    private boolean findInShortcutBackup(ItemInfo shortcutInfo) {
        if (shortcutInfo.container < 0) {
            return true;
        }
        for (ItemInfo info : sShorcutItemsBackup) {
            if (info.id == shortcutInfo.id) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<ItemInfo> sShorcutItemsBackup = new ArrayList<ItemInfo>();
    private static ArrayList<ItemInfo> sShortcutItems = new ArrayList<ItemInfo>();
    private static ArrayList<ItemInfo> sFolderItems = new ArrayList<ItemInfo>();
    private static ArrayList<Long> sFolderIdBackup = new ArrayList<Long>();
    private static ArrayList<String> sWhiteList ;

    public void smartSortApps(Context context) {
        //yixiao add #962047
        SharedPreferences sp = PreferencesProvider.getSharedPreferences(context);
        sp.edit().putInt("screen_cnt", getScreenCnt()).commit();
        // sort apps with category
        sortAppsByCategory(context);
        // arrange apps in workspace
        arrangeAppsInWorkspace(context);
        // update Database in background
        updateInDatabase(context);
    }

    private void getWhiteList(Context context) {
        BufferedReader reader = null;
        try {
            sWhiteList = new ArrayList<String>();
            reader = new BufferedReader(new InputStreamReader(context.openFileInput("whiteList")));
            while (reader.ready()) {
                sWhiteList.add(reader.readLine());
            }
        } catch (FileNotFoundException e) {
            // TODO: handle exception
            e.printStackTrace();
        } catch (IOException e) {
            // TODO: handle exception
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e2) {
                // TODO: handle exception
                e2.printStackTrace();
            }
        }
    }

    private boolean foundInWhiteList(String packageName) {
        if (packageName == null || sWhiteList == null) {
            return false;
        }
        for (String pkgName : sWhiteList) {
            if (packageName.equals(pkgName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInfoDownloaded(Context context) {
        getWhiteList(context);
        SharedPreferences sp = PreferencesProvider.getSharedPreferences(context);
        long lastUpdateTime = sp.getLong("last_get_category_time", 0);
        int networkType = Utilities.getNetworkType(context);
        if (sWhiteList.size() < 1 || lastUpdateTime == 0) {
            if (networkType == -1) {
                Toast.makeText(context, R.string.no_available_network, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.downloading_sort, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    private void sortAppsByCategory(Context context){
        sShortcutItems.clear();
        sFolderItems.clear();
        Resources res = context.getResources();
        int defaultScreen = PreferencesProvider.getDefaultScreen(context);
        for (int i = 0; i < sBgWorkspaceItems.size(); i++) {
            ItemInfo info = sBgWorkspaceItems.get(i);
            if (defaultScreen == info.screen
                    || info.container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                continue;
            }
            if (info instanceof ShortcutInfo||info instanceof ApplicationInfo) {
                String packageName;
                if (info instanceof ShortcutInfo) {
                    packageName = ((ShortcutInfo) info).getPackageName();
                } else {
                    packageName = ((ApplicationInfo) info).getPackageName();
                }
                if(foundInWhiteList(packageName)){
                    sShortcutItems.add(info);
                    continue;
                }
                String appCategory = getCategoryByPackageName(context, packageName);
                long folderId = getFolderIdByCategory(context, appCategory, info);
                if (folderId == -1) {
                    ItemInfo folderInfo = createFolderInfo(context, appCategory);
                    info.container = folderInfo.id;
                    sFolderItems.add(folderInfo);
                } else {
                    info.container = folderId;
                }
                ItemInfo backupInfo = new ItemInfo(info);
                sShorcutItemsBackup.add(backupInfo);
                sShortcutItems.add(info);
            }

            if (info instanceof FolderInfo) {
                sFolderItems.add(info);
            }
        }
    }

    private void arrangeAppsInWorkspace(Context context) {
        int defaultScreen = PreferencesProvider.getDefaultScreen(context);
        int shortcutIndex = 0;
        int folderIndex = 0;
        boolean isShortcutEnd = false;
        int start = PreferencesProvider.piflowEnabled(context) ? Constants.FULL_WIDGET_COUNT : 0;
        //yixiao add #962047 start
        SharedPreferences sp = PreferencesProvider.getSharedPreferences(context);
        int screenCnt = sp.getInt("screen_cnt", getScreenCnt());
        // --end
        for (int screenIndex = start; screenIndex < screenCnt; screenIndex++) {
            if (defaultScreen == screenIndex) {
                continue;
            }
            if(screenIndex>=getScreenCnt()){
                addScreenItem(context, screenIndex);
            }
            final boolean isOccupied[][] = new boolean[mCellCountX][mCellCountY];
            checkIsOccupied(isOccupied, screenIndex, false);
            for (int y = 0; y < mCellCountY; y++) {
                for (int x = 0; x < mCellCountX;) {
                    if (!isOccupied[x][y]) {
                        if (shortcutIndex < sShortcutItems.size()) {
                            ItemInfo info = sShortcutItems.get(shortcutIndex);
                            if (info.container < 0) {
                                info.screen = screenIndex;
                                info.cellX = x++;
                                info.cellY = y;
                            }
                            shortcutIndex++;
                        } else {
                            isShortcutEnd = true;
                        }
                        if (isShortcutEnd) {
                            if (folderIndex < sFolderItems.size()) {
                                ItemInfo info = sFolderItems.get(folderIndex);
                                info.screen = screenIndex;
                                info.cellX = x++;
                                info.cellY = y;
                                folderIndex++;
                            } else {
                                return;
                            }
                        }
                    } else {
                        x++;
                    }
                }
            }
        }
    }

    private void updateInDatabase(Context context) {
        for (ItemInfo info : sShortcutItems) {
            updateItemInfo(context,info);
        }
        for (ItemInfo info : sFolderItems) {
            insertItem2DB(context, info, LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
            updateRecommendInfo(context, info);
        }
    }

    private ItemInfo createFolderInfo(Context context, String folderName) {
        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        ContentResolver cr = context.getContentResolver();
        //SharedPreferences sp = PreferencesProvider.getSharedPreferences(context);
        //sp.edit().putLong(folderName, id).commit();
        long newId = app.getLauncherProvider().generateNewId();
        long folderId = getFolderByCategoryInMap(context,folderName);
        if (folderId == -1) {
            ContentValues values = new ContentValues();
            values.put(LauncherSettings.CategoryMap.CATEGORY, folderName);
            values.put(LauncherSettings.CategoryMap.FOLDER_ID, newId);
            cr.insert(LauncherSettings.CategoryMap.CONTENT_URI, values);
        } else {
            ContentValues values = new ContentValues();
            values.put(LauncherSettings.CategoryMap.FOLDER_ID, newId);
            cr.update(LauncherSettings.CategoryMap.CONTENT_URI, values, "category=?", 
                    new String[] {folderName});
        }
        sFolderIdBackup.add(newId);

        ItemInfo info = new ItemInfo();
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        info.title = folderName;
        info.spanX = 1;
        info.spanY = 1;
        info.id = newId;
        return info;
    }

    private void checkIsOccupied(boolean[][] isOccupied, int screenIndex, boolean checkAll) {
        for (ItemInfo itemInfo : sBgAppWidgets) {
            if (itemInfo.screen == screenIndex) {
                int cellX = itemInfo.cellX;
                int cellY = itemInfo.cellY;
                int spanX = itemInfo.spanX;
                int spanY = itemInfo.spanY;

                for (int i = cellX; i >= 0 && i < cellX + spanX && i < mCellCountX; i++) {
                    for (int j = cellY; j >= 0 && j < cellY + spanY && j < mCellCountY; j++) {
                        isOccupied[i][j] = true;
                    }
                }
            }
        }
        if (checkAll) {
            for (ItemInfo itemInfo : sBgWorkspaceItems) {
                if (itemInfo.screen == screenIndex
                        && itemInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    int cellX = itemInfo.cellX;
                    int cellY = itemInfo.cellY;
                    int spanX = itemInfo.spanX;
                    int spanY = itemInfo.spanY;

                    for (int i = cellX; i >= 0 && i < cellX + spanX && i < mCellCountX; i++) {
                        for (int j = cellY; j >= 0 && j < cellY + spanY && j < mCellCountY; j++) {
                            isOccupied[i][j] = true;
                        }
                    }
                }
            }
        }
    }

    private long getFolderIdByCategory(Context context, String appCategory, ItemInfo itemInfo) {
        long folderId = -1;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(LauncherSettings.CategoryMap.CONTENT_URI,
                    new String[] {"folderId"}, "category=? and folderId not null", new String[] {
                        appCategory}, null);
            if (cursor.moveToFirst()) {
                folderId = cursor.getLong(0);
            }
            ItemInfo item = sBgItemsIdMap.get(folderId);
            if (item != null && item.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                return folderId;
            }
            for (ItemInfo info : sFolderItems) {
                if (appCategory.equals(info.title)) {
                    itemInfo.container = info.id;
                    return info.id;
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return -1;
    }

    public static long getFolderByName(Context context, String name) {
        for (ItemInfo itemInfo : sBgWorkspaceItems) {
            String title = Utilities.convertStr(context, (String) itemInfo.title);
            if (name.equals(title)) {
                return itemInfo.id;
            }
        }
        return -1;
    }

    private void updateItemInfo(Context context, ItemInfo info) {
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, info.container);
        values.put(LauncherSettings.Favorites.SCREEN, getScreenIdByIndex(info.screen));
        values.put(LauncherSettings.Favorites.CELLX, info.cellX);
        values.put(LauncherSettings.Favorites.CELLY, info.cellY);
        cr.update(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values, "_id=?", new String[] { String.valueOf(info.id) });
    }

    public void insertItem2DB(Context context, ItemInfo info, int itemType) {
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites._ID, info.id);
        values.put(LauncherSettings.Favorites.ITEM_TYPE, itemType);
        values.put(LauncherSettings.Favorites.TITLE, (String)info.title);
        values.put(LauncherSettings.Favorites.CONTAINER, info.container);
        values.put(LauncherSettings.Favorites.SCREEN, getScreenIdByIndex(info.screen));
        values.put(LauncherSettings.Favorites.CELLX, info.cellX);
        values.put(LauncherSettings.Favorites.CELLY, info.cellY);
        values.put(LauncherSettings.Favorites.SPANX, 1);
        values.put(LauncherSettings.Favorites.SPANY, 1);
        Cursor c=null;
        try {
            c = context.getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION,
                    new String[] { "_id" }, "_id=?",
                    new String[] {String.valueOf(info.id)}, null);
            if (c != null && c.getCount() > 0) {
                cr.update(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values, "_id=?", new String[] { String.valueOf(info.id) });
            } else {
                if (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                    values.put(Favorites.INTENT, ((ShortcutInfo) info).intent.toUri(0));
                }
                cr.insert(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values);
                sBgItemsIdMap.put(info.id, info);
                sBgWorkspaceItems.add(info);
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private void updateRecommendInfo(Context context, ItemInfo info) {
        String title = Utilities.convertStr(context, info.title.toString());
        ContentResolver cr = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, info.id);
        cr.update(LauncherSettings.RecommendApps.CONTENT_URI, values, "category=?", 
                new String[] {(String) info.title});
    }

    public static String getCategoryByPackageName(Context context, String pkgName) {
        Cursor c = null;
        String category = context.getResources().getString(R.string.server_others_category);
        Uri uri = LauncherSettings.AppCategory.CONTENT_URI;
        try {
            c = context.getContentResolver().query(uri,
                    new String[] {"category"}, "packageName=?", new String[] {pkgName}, null);
            if (c.moveToFirst()) {
                category = c.getString(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return category;
    }

    public static long getFolderByCategoryInMap(Context context, String categoryName){
        Cursor c = null;
        try {
            c = context.getContentResolver().query(LauncherSettings.CategoryMap.CONTENT_URI,
                    new String[] {"folderId"}, "category=?", new String[] {categoryName}, null);
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return -1;
    }

    public static FolderInfo getFolderByCategoryIdInMap(Context context, String categoryId){
        Cursor c = null;
        long id = -1;
        try {
            c = context.getContentResolver().query(LauncherSettings.CategoryMap.CONTENT_URI,
                    new String[] {"folderId"}, "categoryId=?", new String[] {categoryId}, null);
            if (c.moveToFirst()) {
                id =  c.getLong(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return sBgFolders.get(id);
    }

    public static String getCategoryIdByFolderInMap(Context context,long folderId){
        Cursor c = null;
        try {
            c = context.getContentResolver().query(LauncherSettings.CategoryMap.CONTENT_URI,
                    new String[] { LauncherSettings.CategoryMap.CATEGORY_ID },
                    "folderId=?", new String[] {String.valueOf(folderId)}, null);
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    public static FolderInfo getFolderbyPackageName(Context context,String packageName){
        Cursor cursor = null;
        try {//and installed IS NOT ?
            cursor = context.getContentResolver().query(
                    LauncherSettings.RecommendApps.CONTENT_URI, new String[] {"container"},
                    "packageName=? ", new String[] {packageName}, null);
            if (cursor.moveToFirst()) {
                long folderId = Long.valueOf(cursor.getString(0));
                FolderInfo folderInfo = sBgFolders.get(folderId);
                if (folderInfo != null) {
                    return folderInfo;
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return null;
    }

    public boolean isShortcutBackupEmpty() {
        if (sShorcutItemsBackup.size() > 0) {
            return false;
        }
        return true;
    }

    public void addRecommendFolderInWorkspace(Context context) {
        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        final ContentResolver cr = context.getContentResolver();
        Resources res = context.getResources();

        int[] xy = new int[2];
        int screen = findEmptyCell(context, xy, 0);
        long container = createFolderValues(app, xy[0], xy[1], screen,
                res.getString(R.string.server_game_category));
        insertRecommendMap(app, container, res.getString(R.string.server_game_category));
        Intent intent = createShortcutIntent(context,"com.lewa.gamecenter","com.lewa.gamecenter.ListActivity");
        insertShortcut(app, intent, R.string.server_game_recommend1, R.drawable.recommend_icon_game1,
                container, 0, false);
        insertShortcut(app, intent, R.string.server_game_recommend2, R.drawable.recommend_icon_game2,
                container, 0, false);
        screen = findEmptyCell(context, xy, 1);
        container = createFolderValues(app, xy[0], xy[1], screen,
                res.getString(R.string.server_lifestyle_category));
        insertRecommendMap(app, container, res.getString(R.string.server_lifestyle_category));
        intent = createShortcutIntent(context,"com.lewa.appstore","com.lewa.appstore.ListActivity");
        insertShortcut(app, intent, R.string.server_lifestyle_category, R.drawable.recommend_icon_life,
                container, 0, true);
        // add by weihong, 20140902
        intent = createShortcutIntent(context,"com.baixing.lewa","com.baixing.lewa.HouseActivityLewa");
        insertShortcut(app, intent, R.string.server_lifestyle_house, R.drawable.recommend_icon_house,
                container, 0, true);
        intent = createShortcutIntent(context,"com.baixing.lewa","com.baixing.lewa.TradeActivityLewa");
        insertShortcut(app, intent, R.string.server_lifestyle_trade, R.drawable.recommend_icon_trade,
                container, 0, true);
        intent = createShortcutIntent(context,"com.baixing.lewa","com.baixing.lewa.JobActivityLewa");
        insertShortcut(app, intent, R.string.server_lifestyle_jop, R.drawable.recommend_icon_job,
                container, 0, true);
        intent = createShortcutIntent(context,"com.baixing.lewa","com.baixing.lewa.MoreActivityLewa");
        insertShortcut(app, intent, R.string.server_lifestyle_more, R.drawable.recommend_icon_more,
                container, 0, true);
    }

    private long createFolderValues(LauncherApplication app, int cellX, int cellY, int screenIndex,
            String title) {
        long newId = app.getLauncherProvider().generateNewId();
        ContentResolver cr = app.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER,
                LauncherSettings.Favorites.CONTAINER_DESKTOP);
        values.put(LauncherSettings.Favorites.SCREEN, getScreenIdByIndex(screenIndex));
        values.put(Favorites.TITLE, title);
        values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER);
        values.put(LauncherSettings.Favorites.CELLX, cellX);
        values.put(LauncherSettings.Favorites.CELLY, cellY);
        values.put(Favorites.SPANX, 1);
        values.put(Favorites.SPANY, 1);
        values.put(Favorites._ID, newId);
        cr.insert(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values);
        return newId;
    }

    private Intent createShortcutIntent(Context context, String packageName, String className) {
        Intent intent = new Intent();
        intent.setClassName(packageName, className);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void insertRecommendMap(LauncherApplication app, long folderId, String category) {
        ContentResolver cr = app.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(LauncherSettings.CategoryMap.CATEGORY, category);
        values.put(LauncherSettings.CategoryMap.FOLDER_ID, folderId);
        cr.insert(LauncherSettings.CategoryMap.CONTENT_URI, values);
    }

    private void insertShortcut(LauncherApplication app, Intent intent, int titleResId,
            int iconResId, long container, long screenId, boolean notify) {
        long id = app.getLauncherProvider().generateNewId();
        ContentResolver cr = app.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, container);
        values.put(LauncherSettings.Favorites.SCREEN, screenId);
        values.put(LauncherSettings.Favorites.CELLX, 0);
        values.put(LauncherSettings.Favorites.CELLY, 0);
        values.put(Favorites.INTENT, intent.toUri(0));
        values.put(Favorites.TITLE, app.getResources().getResourceName(titleResId));
        values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_SHORTCUT);
        values.put(Favorites.SPANX, 1);
        values.put(Favorites.SPANY, 1);
        values.put(Favorites.ICON_TYPE, Favorites.ICON_TYPE_RESOURCE);
        values.put(Favorites.ICON_PACKAGE, app.getPackageName());
        values.put(Favorites.ICON_RESOURCE, app.getResources().getResourceName(iconResId));
        values.put(Favorites._ID, id);
        cr.insert(notify ? LauncherSettings.Favorites.CONTENT_URI :
            LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values);
    }

    private int findEmptyCell(Context context, int[] xy, int count) {
        for (int screenIndex = 0; screenIndex < getScreenCnt(); screenIndex++) {
            final boolean isOccupied[][] = new boolean[mCellCountX][mCellCountY];
            boolean found = false;
            checkIsOccupied(isOccupied, screenIndex, true);
            for (int y = 0; y < mCellCountY; y++) {
                for (int x = 0; x < mCellCountX; x++) {
                    if (!isOccupied[x][y]) {
                        if (count > 0) {
                            count--;
                            continue;
                        }
                        xy[0] = x;
                        xy[1] = y;
                        return screenIndex;
                    }
                }
            }
        }
        xy[0] = 0;
        xy[1] = 0;
        addScreenItem(context, getScreenCnt());
        return getScreenCnt() - 1;
    }

    public static String getInterfaceByCategory(Context context, String category) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(LauncherSettings.CategoryMap.CONTENT_URI,
                    new String[] {"interface"}, "category=?", new String[] {
                        category
                    }, null);
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            // TODO: handle exception
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static  void isRecommendShortcut(Context context,Intent intent, String title) {
        String iconTitle = Utilities.convertStr(context, title);
        if (intent == null || intent.getComponent() == null || intent.getComponent().getPackageName() == null){
            return ;
        }

        if (intent.getComponent().getPackageName().equals("com.lewa.gamecenter")
                && iconTitle.equals(context.getResources().getString(R.string.server_game_recommend1))) {
            String category = context.getResources().getString(R.string.server_game_category);
            intent.putExtra("data_url", getInterfaceByCategory(context, category));
            intent.putExtra("page_title", iconTitle);
        } else if (intent.getComponent().getPackageName().equals("com.lewa.gamecenter")
                && iconTitle.equals(context.getResources().getString(R.string.server_game_recommend2))) {
            intent.putExtra("data_url", LauncherModel.getInterfaceByCategory(context, iconTitle));
            intent.putExtra("page_title", iconTitle);
        } else if (intent.getComponent().getPackageName().equals("com.lewa.appstore")
                && iconTitle.equals(context.getResources().getString(R.string.server_lifestyle_category))) {
            intent.putExtra("data_url", LauncherModel.getInterfaceByCategory(context, iconTitle));
            intent.putExtra("page_title", iconTitle);
        }
    }
    
    public static boolean isNewAdded(String packageName){
        return packageName != null && newAddApps.size() > 0 && newAddApps.contains(packageName);
    }
    
    public static void removeNewAdded(Context context,String packageName){
        if(packageName != null && newAddApps.contains(packageName)){
            newAddApps.remove(packageName);
            //lqwang-PR58643-modify begin
            PreferencesProvider.putStringValue(context, Constants.NEWAPPSKEY,newAddApps.toString());
            //lqwang-PR58643-modify end
        }
    }

    public static boolean isSameWidgetExists(ComponentName componentName){
        boolean exists = false;
        if(componentName != null){
            for(LauncherAppWidgetInfo appWidgetInfo : sBgAppWidgets){
                if(appWidgetInfo.providerName != null && appWidgetInfo.providerName.equals(componentName)){
                    exists = true;
                    break;
                }
            }
        }
        return exists;
    }

    public static void updateWidgets(){
        for(LauncherAppWidgetInfo appWidgetInfo : sBgAppWidgets){
            if(appWidgetInfo.hostView instanceof LauncherAppWidgetHostView){
                ((LauncherAppWidgetHostView)appWidgetInfo.hostView).updateViews();
            }
        }
    }

    /**
     * when item screen is large than max screen index,must call this method to update sScreenInfoMap before
     * call getScreenIdByIndex/getScreenIndexById
     * @param context
     * @param screen
     */
    public static void addInScreenMap(Context context,int screen){
        int screenCnt = getScreenCnt();
        while (screen >= screenCnt) {
            LauncherModel.addScreenItem(context, screenCnt);
            screenCnt++;
        }
    }

    public static void logScreenMap(String methodName){
       if(DEBUG_SCREENS){
          Log.e(TAG,methodName+ "  :  " + sScreenInfoMap.toString());
       }
    }
}
