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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lewa.launcher.LauncherSettings.Favorites;
import com.lewa.reflection.ReflUtils;
import com.lewa.reflection.ReflUtils.SystemProperties;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Stores the list of all applications for the all apps view.
 */
class AllAppsList {
    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;
    
    /** The list off all apps. */
    public ArrayList<ApplicationInfo> data =
            new ArrayList<ApplicationInfo>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been added since the last notify() call. */
    public ArrayList<ApplicationInfo> added =
            new ArrayList<ApplicationInfo>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been removed since the last notify() call. */
    public ArrayList<ApplicationInfo> removed = new ArrayList<ApplicationInfo>();
    /** The list of apps that have been modified since the last notify() call. */
    public ArrayList<ApplicationInfo> modified = new ArrayList<ApplicationInfo>();
    /** The list of apps that missed, and need to be binded to workspace */
    public ArrayList<ItemInfo> missed = new ArrayList<ItemInfo>();

    private IconCache mIconCache;
    
    private Context mContext;
    
    private LauncherModel mLauncherModel;
    // for workspace application ites
    public HashMap<ComponentName, ShortcutInfo> workspaceAppItems;
    public boolean workspaceLoadCompleted;
    // end
    public static final boolean APP_MANAGE_DEBUG = true;
    private static final String TAG = LauncherModel.TAG;
    private static long sBootTime;

    /**
     * Boring constructor.
     */
    public AllAppsList(Context context, LauncherModel launcherModel, IconCache iconCache) {
        mIconCache = iconCache;
        mContext = context;
        mLauncherModel = launcherModel;
        sBootTime = ReflUtils.SystemProperties.getLong("ro.runtime.firstboot", 0);
    }

    /**
     * Add the supplied ApplicationInfo objects to the list, and enqueue it into the
     * list to broadcast when notify() is called.
     *
     * If the app is already in the list, doesn't add it.
     */
    public boolean add(ApplicationInfo info) {
        if (!findActivity(data, info.componentName)) {
            data.add(info);
            added.add(info);
            return true;
        }
        return false;
    }
    
    // TODO: need to check the workspace is loading?
    public boolean addAppInfo(ApplicationInfo info) {
        boolean result = false;
        if (workspaceAppItems == null) {
            Log.e(TAG, "ERROR!! when addAppInfo(). info.componentName =" + info.componentName + " workspaceAppItems = null");
           return true; 
        } else if (info.componentName == null) {
            Log.e(TAG, "ERROR!! addAppInfo failed. info.componentName = null" );
            return false;
        }
        ShortcutInfo shortcutInfo = workspaceAppItems.get(info.componentName);
        if (shortcutInfo == null || shortcutInfo.state != ShortcutInfo.STATE_OK) {
            workspaceAppItems.put(info.componentName, new ShortcutInfo(info));
            result = true;
        }
        return result;
    }
    
    public void clear() {
        data.clear();
        // TODO: do we clear these too?
        added.clear();
        removed.clear();
        modified.clear();
    }

    public int size() {
        return data.size();
    }

    public ApplicationInfo get(int index) {
        return data.get(index);
    }

    /**
     * Add the icons for the supplied apk called packageName.
     */
    public void addPackage(Context context, String packageName) {
        if (APP_MANAGE_DEBUG) {
            android.util.Log.d(TAG, "addPackage():" + packageName);
        }
        final List<ResolveInfo> matches = findActivitiesForPackage(context, packageName);
        if (matches.size() > 0) {
            for (ResolveInfo info : matches) {
                ApplicationInfo appInfo = new ApplicationInfo(context.getPackageManager(), info, mIconCache, null);
                add(appInfo);
                if (addAppInfo(appInfo)) {
                	addAppToWorkspace(mContext, appInfo);
                } else {
                    Log.w(TAG, "WARNNING!! maybe the workspace has the same item or something wrong when adding:" + appInfo);
                }
            }
        }
    }
    
    public void packageAvailable(Context context, String packageName) {
        if (APP_MANAGE_DEBUG)
            android.util.Log.d(TAG, "packageAvailable():" + packageName);
        final List<ResolveInfo> matches = findActivitiesForPackage(context, packageName);
        
        if (matches.size() > 0) {
            PackageManager pm = context.getPackageManager();
            if (workspaceAppItems == null) {
                Log.w(TAG, "workspaceAppItems was not init : " + packageName);
                return;
            }
            for (ResolveInfo info : matches) {
                mIconCache.remove(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
                ApplicationInfo appInfo = new ApplicationInfo(pm, info, mIconCache, null);
                ShortcutInfo shortcut = workspaceAppItems.get(appInfo.componentName);
                if (shortcut != null) {
                    shortcut.setIcon(appInfo.iconBitmap);
                    if (shortcut.id == -1) { // this ShortcutInfo add by addToWorkspace()
                        shortcut = getShortcutInfoByIntent(appInfo.intent);
                        workspaceAppItems.put(appInfo.componentName, shortcut);
                        // TODO: check get the duplication item.
                        Log.w(TAG, "WARNING!! pls check if there is an error, this ShortcutInfo add by addToWorkspace():"+ shortcut);
                    } 
                    if (add(appInfo)) {
                        // Log.e(TAG, "ERROR! add to added list failed:" + appInfo);
                    } else {
                        // sometimes the AllAppsList may has this item.
                        if (shortcut.state != ShortcutInfo.STATE_OK) {
                            added.add(appInfo);
                        }
                    }
                    
                    shortcut.state = ShortcutInfo.STATE_OK;
                    
                    final ArrayList<ItemInfo> items = LauncherModel
                            .getItemsInLocalCoordinates(context);
                    ItemInfo itemAtPos = getItemByPos(items, shortcut.id, shortcut.cellX,
                            shortcut.cellY, shortcut.screen, shortcut.container);
                    if (APP_MANAGE_DEBUG)
                        android.util.Log.d(TAG, "packageAvailable, get location:" + itemAtPos);
                    
                    if (itemAtPos != null && shortcut.container > 0) {
                        // this item in folder
                        appInfo.id = shortcut.id;

                        FolderInfo folderInfo = LauncherModel.sBgFolders.get(shortcut.container);
                        if (APP_MANAGE_DEBUG)
                            Log.e(TAG,
                                    (folderInfo == null) ? ("WARNING!!! folder==null, all icons will return to workspace, folder id:" + shortcut.container)
                                            : ("folder has this item ? " + folderInfo.has(shortcut)));
                        // the folder is disappear, re-add to workspace.
                        //lqwang - PR968842 - modify begin
                        if(folderInfo != null && !folderInfo.has(shortcut)){
                            folderInfo.contents.add(shortcut);
                        }
                        //lqwang - PR968842 - modify end

                        if (itemAtPos.spanX == -1 && itemAtPos.spanY == -1) {
                            appInfo.id = shortcut.id;
                            LauncherModel.deleteItemFromDatabase(context, appInfo);
                            addAppToWorkspace(mContext, appInfo);
                        } else {
                            appInfo.container = shortcut.container;
                            appInfo.screen = shortcut.screen;
                            appInfo.cellX = shortcut.cellX;
                            appInfo.cellY = shortcut.cellY;
                        }
                    } else if (itemAtPos != null) {
                        // the cell was occupied, find re-add to workspace for a new cell.
                        appInfo.id = shortcut.id;
                        LauncherModel.deleteItemFromDatabase(context, appInfo);
                        addAppToWorkspace(mContext, appInfo);
                    }
                } else {
                    Log.w(TAG, "WARNING!! can't find info when available:"+ shortcut);
                    addAppToWorkspace(mContext, appInfo);
                }
            }
        }
    }
    
    public void addActivity(Context context, ResolveInfo info, IconCache iconCache) {
        if (APP_MANAGE_DEBUG)
            android.util.Log.d(TAG, "addActivity():" + info);
        ApplicationInfo appInfo = new ApplicationInfo(context.getPackageManager(), info, iconCache,
                null);
        add(appInfo);
        if (addAppInfo(appInfo)) {
            addAppToWorkspace(context, appInfo);
        }
    }

    /**
     * Remove the apps for the given apk identified by packageName.
     */
    public void removePackage(String packageName, boolean unavailable) {
        if (APP_MANAGE_DEBUG)
            android.util.Log.d(TAG, "removePackage():" + packageName);
        final List<ApplicationInfo> data = this.data;
        for (int i = data.size() - 1; i >= 0; i--) {
            ApplicationInfo info = data.get(i);
            final ComponentName component = info.intent.getComponent();
            if (packageName.equals(component.getPackageName())) {
                removed.add(info);
                data.remove(i);
            }
        }
        if (workspaceAppItems == null) {
            Log.e(TAG, "removePackage failed! workspaceAppItems == null :" + packageName + " unavailable=" +unavailable);
            return;
        }
        Iterator<Map.Entry<ComponentName, ShortcutInfo>> it = workspaceAppItems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ComponentName, ShortcutInfo> entry = it.next();
            ComponentName cn = entry.getKey();
            if (cn != null && packageName.equals(cn.getPackageName())) {
                if (unavailable) {
                    if (entry.getValue() != null) {
                        entry.getValue().state = ShortcutInfo.STATE_UNAVAILABLE;
                    }
                } else {
                    it.remove();
                }
            }
        }
        // This is more aggressive than it needs to be.
        mIconCache.flush();
    }

    /**
     * Add and remove icons for this package which has been updated.
     */
    public void updatePackage(Context context, String packageName) {
        if (APP_MANAGE_DEBUG)
            android.util.Log.d(TAG, "updatePackage():" + packageName);
        if (workspaceAppItems == null) {
            Log.w(TAG, "updatePackage warning! workspaceAppItems == null :"
                    + packageName);
        }
        final List<ResolveInfo> matches = findActivitiesForPackage(context, packageName);        
        ArrayList<ComponentName> installeds = getWorkspaceItemsByPackageName(packageName); 
        HashMap<ComponentName, ApplicationInfo> oldAppInfos = new HashMap<ComponentName, ApplicationInfo>();
        
        if (matches.size() > 0) {
            boolean[] replaced = new boolean[installeds.size()]; 
            HashMap<ApplicationInfo, ShortcutInfo> replaceMap = new HashMap<ApplicationInfo, ShortcutInfo>();
            
            // remove the old item in data.
            for (int i = data.size() - 1; i >= 0; i--) {
                final ApplicationInfo applicationInfo = data.get(i);
                final ComponentName component = applicationInfo.intent.getComponent();
                if (packageName.equals(component.getPackageName())) {
                    if (APP_MANAGE_DEBUG) {
                        Log.w(TAG, "updatePackage remove all in data, remove :" + applicationInfo);
                    }
                    data.remove(i);
                    oldAppInfos.put(applicationInfo.componentName, applicationInfo);
                    mIconCache.remove(component);
                }
            }
                        
            // replace priority:
            // 1. same ComponentName
            // 2. same title.
            // 3. same package.
            for (ResolveInfo rsvInfo : matches) {
                int pos = 0;
                ApplicationInfo addInfo = new ApplicationInfo(
                        context.getPackageManager(), rsvInfo, mIconCache, null);
                replaceMap.put(addInfo, null);
                for (ComponentName installed : installeds) {
                    ShortcutInfo shortcut = workspaceAppItems != null ? workspaceAppItems
                            .get(installed) : null;
                    // same componentName
                    if (addInfo.componentName != null
                            && addInfo.componentName.equals(installed)) {
                        replaceMap.put(addInfo, shortcut);
                        replaced[pos] = true; 
                        break;
                    // same title
                    } else if (shortcut != null && shortcut.title != null
                            && shortcut.title.equals(addInfo.title)) {
                        replaceMap.put(addInfo, shortcut);
                        replaced[pos] = true; 
                    }
                    pos ++;
                }
                mIconCache.getTitleAndIcon(addInfo, rsvInfo, null);
            }
            // find the un-replaced to replace. 
            for (Map.Entry<ApplicationInfo, ShortcutInfo> entry : replaceMap.entrySet()) {
                ApplicationInfo appInfo = entry.getKey();
                ShortcutInfo oldShortcut = entry.getValue();
                
                // find an un-replaced old shortcut to replace.
                if (oldShortcut == null) {
                    for (int i = 0; i < replaced.length; i++ ) {
                        if (!replaced[i]) {
                            oldShortcut = workspaceAppItems != null ? workspaceAppItems
                                    .get(installeds.get(i)) : null;
                            if (oldShortcut != null) {
                                entry.setValue(oldShortcut);
                                replaced[i] = true;
                            }
                            break;
                        } 
                    }
                }
                // if oldShortcut.id == -1 oldShortcut is from  addAppToWorkspace, and can't be updated
                // so just addAppToWorkspace
                if (oldShortcut != null && oldShortcut.id > 0) {
                    ComponentName cn = oldShortcut.intent != null ? oldShortcut.intent.getComponent() : null;
                    modified.add(appInfo);
                    if (oldShortcut != null && oldShortcut.state != ShortcutInfo.STATE_OK) {
                        if (mContext.getPackageManager().resolveActivity(oldShortcut.intent, 0) != null) {
                            oldShortcut.state = ShortcutInfo.STATE_OK;

                            //lqwang - PR968842 - modify begin
                            if(oldShortcut.container > 0){
                                FolderInfo folderInfo = LauncherModel.sBgFolders.get(oldShortcut.container);
                                if(folderInfo != null && !folderInfo.has(oldShortcut)){
                                    folderInfo.contents.add(oldShortcut);
                                }
                            }
                            //lqwang - PR968842 - modify end

                            //  missed only handle the shortcut in workspace , not in folder.
                            if (oldShortcut.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                                missed.add(oldShortcut);
                            }

                            if (APP_MANAGE_DEBUG)
                                android.util.Log.d(TAG, "change shortcut state to STATE_OK:" + oldShortcut);
                        }
                    }
                    // is not the same componentName, update it.
                    if (!appInfo.componentName.equals(cn)) {
                        // hasn't reloaded yet
                        if (oldShortcut.id <= 0 || LauncherModel.sBgItemsIdMap.get(oldShortcut.id) == null) {
                            Log.e(TAG, "updatePackage failed invalid id:"+oldShortcut.id);
                        } else {
                            workspaceAppItems.remove(oldShortcut.intent.getComponent());
                            workspaceAppItems.put(appInfo.componentName, oldShortcut);
                            oldShortcut.intent.setComponent(appInfo.componentName);
                            LauncherModel.updateItemInDatabase(mContext, oldShortcut);
                        }
                    }
                    
                } else {
                    // if appInfo has already been in db, don't add it to workspace.
					//yixiao delete 2015.1.13
                    //if (!LauncherModel.shortcutExists(context, appInfo.componentName)) {
                        addAppToWorkspace(mContext, appInfo);
                    //} 
                    
                }
                if (!findActivity(data, appInfo.componentName)) {
                    data.add(appInfo);
                }
            }
            // remove un-replaced old shortcut from workspaceAppItems
            for (int i = 0; i < replaced.length; i++ ) {
                if (!replaced[i]) {
                    ComponentName cn = installeds.get(i);
                    workspaceAppItems.remove(cn);
                    ApplicationInfo removeApp = oldAppInfos.get(cn);
                    removed.add(removeApp);
                }
            }
            
        } else {
            // Remove all data for this package.
            if (workspaceAppItems != null) {
                for (ComponentName cn : installeds) {
                    workspaceAppItems.remove(cn);
                    mIconCache.remove(cn);
                }
            }
            
            for (int i = data.size() - 1; i >= 0; i--) {
                final ApplicationInfo applicationInfo = data.get(i);
                final ComponentName component = applicationInfo.intent.getComponent();
                if (packageName.equals(component.getPackageName())) {
                    removed.add(applicationInfo);
                    if (APP_MANAGE_DEBUG) {
                        Log.w(TAG, "updatePackage remove all, remove :" + applicationInfo);
                    }
                    mIconCache.remove(component);
                    data.remove(i);
                }
            }
        }
    }

    /**
     * Query the package manager for MAIN/LAUNCHER activities in the supplied package.
     */
    private static List<ResolveInfo> findActivitiesForPackage(Context context, String packageName) {
        final PackageManager packageManager = context.getPackageManager();

        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        final List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
        return apps != null ? apps : new ArrayList<ResolveInfo>();
    }

    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(List<ResolveInfo> apps, ComponentName component) {
        final String className = component.getClassName();
        for (ResolveInfo info : apps) {
            final ActivityInfo activityInfo = info.activityInfo;
            if (activityInfo.name.equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(ArrayList<ApplicationInfo> apps, ComponentName component) {
        final int N = apps.size();
        for (int i=0; i<N; i++) {
            final ApplicationInfo info = apps.get(i);
            if (info.componentName.equals(component)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an ApplicationInfo object for the given packageName and className.
     */
    private ApplicationInfo findApplicationInfoLocked(String packageName, String className) {
        for (ApplicationInfo info: data) {
            final ComponentName component = info.intent.getComponent();
            if (packageName.equals(component.getPackageName())
                    && className.equals(component.getClassName())) {
                return info;
            }
        }
        return null;
    }

    private void addAppToWorkspace(Context context, ApplicationInfo info) {
        // Lock on the app so that we don't try and get the items while apps are being added
        if (APP_MANAGE_DEBUG) {
            Log.d(TAG, "addAppToWorkspace() info = " + info);
        }
        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        synchronized (app) {
            int[] xy = new int[2];
            int screen = findEmptyCell(xy);
            if (Utilities.isRecommendPackage(context, info) != null) {
                return;
            }
            int screenCnt = LauncherModel.getScreenCnt();
            if (screenCnt <= 0 || app == null) {
                return;
            }
            if (screen >= screenCnt) {
                LauncherModel.addScreenItem(context, screenCnt);
            }

            boolean notify = true;
            info.id = app.getLauncherProvider().generateNewId();
            final ContentValues values = new ContentValues();

            if (info.intent != null && info.title != null) {
                if(SystemProperties.getBoolean("ro.lewa.cu", false) || SystemProperties.getBoolean("ro.lewa.cu_mp", false) || SystemProperties.getBoolean("ro.lewa.use.launcher",false)){
                    values.put(LauncherSettings.Favorites.CONTAINER, LauncherSettings.Favorites.CONTAINER_DESKTOP);
                    //setContainer(context,values,info.getPackageName());
                }else{
                    //PR937172  zwsun modify begin
                    //values.put(LauncherSettings.Favorites.CONTAINER, LauncherSettings.Favorites.CONTAINER_DESKTOP);
                    setContainer(context,values,info.getPackageName());
                    //PR937172  zwsun modify end
                }
                values.put(LauncherSettings.Favorites.SCREEN, LauncherModel.getScreenIdByIndex(screen));
                values.put(LauncherSettings.Favorites.CELLX, xy[0]);
                values.put(LauncherSettings.Favorites.CELLY, xy[1]);
                info.intent.addCategory(Intent.CATEGORY_LAUNCHER);
                values.put(Favorites.INTENT, info.intent.toUri(0));
                values.put(Favorites.TITLE, info.title.toString());
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION);
                values.put(Favorites.SPANX, 1);
                values.put(Favorites.SPANY, 1);
                values.put(Favorites._ID, info.id);
                final ContentResolver cr = context.getContentResolver();
				//yixiao add 2015.1.13 begin
                if(!LauncherModel.shortcutExists(context, info.componentName)){
                    cr.insert(notify ? LauncherSettings.Favorites.CONTENT_URI
                            : LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values);
                }else{
                    Cursor c = cr.query(notify ? LauncherSettings.Favorites.CONTENT_URI
                            : LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, 
                            new String[]{"_id","screen","cellX","cellY","container","title"}, "intent = '"+info.intent.toUri(0)+"'", null, null);
                    while(c.moveToNext()){
                        info.id = c.getInt(0);
                        info.screen = c.getInt(1);
                        info.cellX = c.getInt(2);
                        info.cellY = c.getInt(3);
                        info.container = c.getLong(4);
                        info.title = c.getString(5);
                        checkPositionOccupied(workspaceAppItems, info);
                        mContext.getContentResolver().notifyChange(notify ? LauncherSettings.Favorites.CONTENT_URI
                                : LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, null);
                    }
                    c.close();
                }
				//yixiao add 2015.1.13 end
                // put this shortcut to avoid adding item duplicate.
                // Because in some conditions, the APP will be updated
                // immediately after installed.
                ShortcutInfo shortcut = new ShortcutInfo(info);
                shortcut.id = info.id;
                if (workspaceAppItems != null) {
                    workspaceAppItems.put(info.componentName, shortcut);
                }
            }
        }
    }
    //PR937172 zwsun modify begin
    private void setContainer(Context context, ContentValues values,String packageName){
        FolderInfo folder = mLauncherModel.getFolderByTitle(mContext, "com.lewa.launcher:string/system_secrity_folder");
        if(packageName.equals("com.android.stk") && folder != null) {
            values.put(LauncherSettings.Favorites.CONTAINER, folder.id);
        }else {
            values.put(LauncherSettings.Favorites.CONTAINER, LauncherSettings.Favorites.CONTAINER_DESKTOP);
        }
    }
    //PR937172 zwsun modify end
    /**
     * if the position store in db is not empty,put the icon in the last position of workspace
     * @param workspaceAppItems
     * @param addInfo
     */
    private boolean checkPositionOccupied(HashMap<ComponentName, ShortcutInfo> workspaceAppItems, ApplicationInfo addInfo) {
        boolean occupied = false;
        if(workspaceAppItems == null){
            return occupied;
        }
        for(Map.Entry<ComponentName,ShortcutInfo> entry : workspaceAppItems.entrySet()){
            ShortcutInfo info = entry.getValue();
            Log.e(TAG, "yixiao info:"+info.cellX+"-"+info.cellY);
            if(info.container == addInfo.container && info.screen == addInfo.screen && info.cellX == addInfo.cellX && info.cellY == addInfo.cellY){
                occupied = true;
                break;
                
            }
        }
        if(occupied){
            int[] xy = new int[2];
            int screen = findEmptyCell(xy);
            addInfo.cellX = xy[0];
            addInfo.cellY = xy[1];
            addInfo.screen = screen;
            addInfo.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
            ShortcutInfo appInfo = new ShortcutInfo(addInfo);
            appInfo.id = addInfo.id;
            //ShortcutInfo shortcutInfo = getShortcutInfoByIntent(appInfo.intent);
            LauncherModel.sBgItemsIdMap.put(appInfo.id,appInfo);
            LauncherModel.addInScreenMap(mContext,screen);
            LauncherModel.updateItemInDatabase(mContext,appInfo);
        }
        return occupied;
    }
    
    ShortcutInfo getShortcutInfoByIntent(Intent intent) {
        String uri = intent.toUri(0);
        final ContentResolver cr = mContext.getContentResolver();
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI, null, "intent=?", new String[] {uri}, null);

        try {
            if (c.moveToFirst()) {
                final int itemTypeIndex = c
                        .getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
                final int titleIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
                final int containerIndex = c
                        .getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
                final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
                final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
                final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
                final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);

                ShortcutInfo shortcutInfo = new ShortcutInfo();

                shortcutInfo.title = c.getString(titleIndex);
                shortcutInfo.id = c.getLong(idIndex);
                shortcutInfo.container = c.getInt(containerIndex);
                shortcutInfo.screen = LauncherModel.getScreenIndexById(c.getInt(screenIndex));
                shortcutInfo.cellX = c.getInt(cellXIndex);
                shortcutInfo.cellY = c.getInt(cellYIndex);
                shortcutInfo.itemType = c.getInt(itemTypeIndex);

                return shortcutInfo;
            }
        } finally {
            c.close();
        }
        return null;
    }

    private ItemInfo getItemByPos(List<ItemInfo> items, long ignorId, int cellX, int cellY,
            int screen, long container) {
        if (container > 0) { // is a folder
            if (mLauncherModel != null && mContext != null) {
                HashMap<Long, FolderInfo> list = new HashMap<Long, FolderInfo>();
                FolderInfo folderInfo = mLauncherModel.getFolderById(mContext, list, container);
                
                ItemInfo info = new ItemInfo();
                info.container = container;
                info.cellX = cellX;
                info.cellY = cellY;
                info.screen = screen;
                // folder no exist any more, mark spanX = -1 && spanY - 1;
                if (folderInfo == null) {
                    info.spanX = -1;
                    info.spanY = -1;
                } else {
                    info.spanX = 1;
                    info.spanY = 1;
                }
                return info;
            }
            return null;
        } else if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            for (ItemInfo item : items) {
                if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT
                        && item.cellX == cellX && item.cellY == cellY && (ignorId > 0 && ignorId != item.id)) {
                    return item;
                }
            }
            return null;
        } else if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            final int xCount = LauncherModel.getCellCountX();
            final int yCount = LauncherModel.getCellCountY();

            for (ItemInfo item : items) {
                if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    if (item.screen == screen) {
                        int tCellX = item.cellX;
                        int tCellY = item.cellY;
                        int spanX = item.spanX;
                        int spanY = item.spanY;
                        for (int x = tCellX; 0 <= x && x < tCellX + spanX && x < xCount; x++) {
                            for (int y = tCellY; 0 <= y && y < tCellY + spanY && y < yCount; y++) {
                                if (x == cellX && y == cellY && (ignorId > 0 && ignorId != item.id)) {
                                    return item;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private int findEmptyCell(int[] xy) {
        boolean isLandScape = LauncherApplication.isScreenLandscape(mContext);
        final int cellCountX = LauncherModel.getCellCountX();
        final int cellCountY = LauncherModel.getCellCountY();
        int countX = isLandScape ? cellCountY : cellCountX;
        int countY = isLandScape ? cellCountX : cellCountY;

        int maxCellX = 0;
        int maxCellY = 0;
        int maxSpanX = 1;
        int maxSpanY = 1;
        int maxNotEmptyScrIndex = 0;
        int maxAppIndexInWholeScreen = 0;
        
        Cursor c = mContext.getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                new String[] { "screen", "cellX", "cellY", "spanX", "spanY"}, "container=?",
                new String[] {String.valueOf(LauncherSettings.Favorites.CONTAINER_DESKTOP)},  null);
        try {
            while (c.moveToNext()) {
                int screenIndex = LauncherModel.getScreenIndexById(c.getLong(0));
                int cellX = c.getInt(1);
                int cellY = c.getInt(2);
                int spanX = c.getInt(3);
                int spanY = c.getInt(4);
//                int appIndex = screenIndex * (countX * countY) + cellY * countX + cellX;
                int appIndex = screenIndex * (countX * countY) + (cellY + spanY - 1) * countX + cellX + spanX;
                if (appIndex > maxAppIndexInWholeScreen) {
                    maxAppIndexInWholeScreen = appIndex;
                    maxNotEmptyScrIndex = screenIndex;
                    maxCellX = cellX;
                    maxCellY = cellY;
                    maxSpanX = spanX;
                    maxSpanY = spanY;
                }
            }
        } finally {
            c.close();
        }

        int index = maxCellX + (maxCellY + maxSpanY - 1) * countX + maxSpanX;
        if (index > countX * countY - 1) {
            xy[0] = 0;
            xy[1] = 0;
            maxNotEmptyScrIndex++;
        } else {
            xy[0] = index % countX;
            xy[1] = index / countX;
        }
        return maxNotEmptyScrIndex;
    }
    
    public boolean updateRemovedPackages(HashMap<ComponentName, ShortcutInfo> workspaceApps,
            Set<ComponentName> activities) {
        if (workspaceApps == null || workspaceApps.size() <= 0 || activities == null
                || activities.size() <= 0) {
            return false;
        }
        boolean hasUpdate = false;
        HashMap<ComponentName, ShortcutInfo> removedMap = new HashMap<ComponentName, ShortcutInfo>(workspaceApps);
        for (ComponentName cn : activities) {
            removedMap.remove(cn);
        }
        PackageManager pm = mContext.getPackageManager();
        for (Map.Entry<ComponentName, ShortcutInfo> entry : removedMap.entrySet()) {
            ShortcutInfo info = entry.getValue();
            if (info != null && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                ComponentName cn = entry.getKey();
                if (!Utilities.isOnSDCardOrDisabled(pm, cn)
                        && (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP 
                        || info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT)) {
                    Log.i(TAG, "delete removed item:" + info);
                    LauncherModel.deleteItemFromDatabase(mContext, info);
                    hasUpdate = true;
                } else {
                    info.state = ShortcutInfo.STATE_UNAVAILABLE;
                }
            }
        }
        return hasUpdate;
    }

    public boolean updateMissedPackages(HashMap<ComponentName, ShortcutInfo> workspaceApps,
            HashMap<ComponentName, ResolveInfo> activities) {
        if (workspaceApps == null || workspaceApps.size() <= 0 || activities == null
                || activities.size() <= 0) {
            return false;
        }
        HashSet<ComponentName> keySet = new HashSet<ComponentName>(activities.keySet());
        for (ComponentName cn : activities.keySet()) {
            if (workspaceApps.containsKey(cn)) {
                keySet.remove(cn);
            }
        }

        if (keySet.size() > 0) {
            for (ComponentName cn : keySet) {
                addActivity(mContext, activities.get(cn), mIconCache);
            }
            return true;
        }
        return false;
    }
    
//    public boolean checkAppRemovedOrInstalled(PackageManager pm) {
//        boolean hasUpdate = false;
//
//        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
//        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
//        List<ResolveInfo> allApps = pm.queryIntentActivities(mainIntent, 0);
//        updateMissedPackages(workspaceAppItems, allApps);
//        updateRemovedPackages(workspaceAppItems, allApps, false);
//        return hasUpdate;
//    }
    
    public static boolean isAllAppsScaned() {
        if (sBootTime != 0) {
            if ((System.currentTimeMillis() - sBootTime) > 2 * 60 * 1000) {
                return true;
            }
        }
        return false;
    }
    //CR:632969 SIM Toolkit still display.
    private ArrayList<ComponentName> getWorkspaceItemsByPackageName(String packageName) {
        ArrayList<ComponentName> shortcutNames = new ArrayList<ComponentName>();
        if (workspaceAppItems != null) {
            for (Map.Entry<ComponentName, ShortcutInfo> entry : workspaceAppItems
                    .entrySet()) {
                ComponentName cn = entry.getKey();
                if (packageName.equals(cn.getPackageName())) {
                    shortcutNames.add(cn);
                }
            }
        }
        return shortcutNames;
    }
}
