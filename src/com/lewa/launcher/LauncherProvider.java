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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.SearchManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import com.lewa.launcher.LauncherSettings.CategoryMap;
import com.lewa.launcher.LauncherSettings.Favorites;
import com.lewa.launcher.preference.OEMAppTypeProvider;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.reflection.ReflUtils;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "Launcher.LauncherProvider";
    private static final boolean LOGD = false;

    private static final String DATABASE_NAME = "launcher.db";

    private static final int DATABASE_VERSION = 15;

    static final String AUTHORITY = "com.lewa.launcher.settings";

    static final String TABLE_FAVORITES = "favorites";
    static final String TABLE_SCREENS = "screens";
    static final String TABLE_RECOMMEND = "recommend";
    static final String TABLE_CATEGORY = "app_category";
    static final String TABLE_CATEGORY_MAP = "category_map";
    static final String PARAMETER_NOTIFY = "notify";
    static final String DB_CREATED_BUT_DEFAULT_WORKSPACE_NOT_LOADED =
            "DB_CREATED_BUT_DEFAULT_WORKSPACE_NOT_LOADED";
    static final String DEFAULT_WORKSPACE_RESOURCE_ID =
            "DEFAULT_WORKSPACE_RESOURCE_ID";
    
    private static final String ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE =
            "com.android.launcher.action.APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE";

    /**
     * {@link Uri} triggered at any registered {@link android.database.ContentObserver} when
     * {@link AppWidgetHost#deleteHost()} is called during database creation.
     * Use this to recall {@link AppWidgetHost#startListening()} if needed.
     */
    static final Uri CONTENT_APPWIDGET_RESET_URI =
            Uri.parse("content://" + AUTHORITY + "/appWidgetReset");

    private DatabaseHelper mOpenHelper;
    private static int mCellCountX;
    private static int mCellCountY;

    @Override
    public boolean onCreate() {
        mCellCountX = PreferencesProvider.getCellCountX(getContext());
        mCellCountY = PreferencesProvider.getCellCountY(getContext());
        mOpenHelper = new DatabaseHelper(getContext());
        ((LauncherApplication) getContext()).setLauncherProvider(this);
        return true;
    }

    @Override
    public String getType(Uri uri) {
        SqlArguments args = new SqlArguments(uri, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
        result.setNotificationUri(getContext().getContentResolver(), uri);

        return result;
    }

    private static long dbInsertAndCheck(DatabaseHelper helper,
            SQLiteDatabase db, String table, String nullColumnHack, ContentValues values) {
        if (TABLE_FAVORITES.equals(table) && !values.containsKey(LauncherSettings.Favorites._ID)) {
            throw new RuntimeException("Error: attempting to add item without specifying an id");
        }
        return db.insert(table, nullColumnHack, values);
    }

    private static void deleteId(SQLiteDatabase db, long id) {
        Uri uri = LauncherSettings.Favorites.getContentUri(id, false);
        SqlArguments args = new SqlArguments(uri, null, null);
        db.delete(args.table, args.where, args.args);
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final long rowId = dbInsertAndCheck(mOpenHelper, db, args.table, null, initialValues);
        if (rowId <= 0) return null;

        uri = ContentUris.withAppendedId(uri, rowId);
        sendNotify(uri);

        return uri;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            int numValues = values.length;
            for (int i = 0; i < numValues; i++) {
                if (dbInsertAndCheck(mOpenHelper, db, args.table, null, values[i]) < 0) {
                    return 0;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        sendNotify(uri);
        return values.length;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.delete(args.table, args.where, args.args);
        if (count > 0) sendNotify(uri);

        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(args.table, values, args.where, args.args);
        if (count > 0) sendNotify(uri);

        return count;
    }

    private void sendNotify(Uri uri) {
        String notify = uri.getQueryParameter(PARAMETER_NOTIFY);
        if (notify == null || "true".equals(notify)) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }

    public long generateNewId() {
        return mOpenHelper.generateNewId();
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String TAG_FAVORITES = "favorites";
        private static final String TAG_FAVORITE = "favorite";
        private static final String TAG_CLOCK = "clock";
        private static final String TAG_SEARCH = "search";
        private static final String TAG_APPWIDGET = "appwidget";
        private static final String TAG_SHORTCUT = "shortcut";
        private static final String TAG_FOLDER = "folder";
        private static final String TAG_EXTRA = "extra";
        private static final String TAG_NONCUSTOM = "noncustom";

        private final Context mContext;
        private final AppWidgetHost mAppWidgetHost;
        private long mMaxId = -1;
        private HashMap<ComponentName, Boolean> mCustomApps;
        private List<Long> mScreenIds;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
            mAppWidgetHost = new AppWidgetHost(context, Launcher.APPWIDGET_HOST_ID);

            // In the case where neither onCreate nor onUpgrade gets called, we read the maxId from
            // the DB here
            if (mMaxId == -1) {
                mMaxId = initializeMaxId(getWritableDatabase());
            }
        }

        /**
         * Send notification that we've deleted the {@link AppWidgetHost},
         * probably as part of the initial database creation. The receiver may
         * want to re-call {@link AppWidgetHost#startListening()} to ensure
         * callbacks are correctly set.
         */
        private void sendAppWidgetResetNotify() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.notifyChange(CONTENT_APPWIDGET_RESET_URI, null);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (LOGD) Log.d(TAG, "creating new launcher database");

            mMaxId = 1;

            db.execSQL("CREATE TABLE favorites (" +
                    "_id INTEGER PRIMARY KEY," +
                    "title TEXT," +
                    "intent TEXT," +
                    "container INTEGER," +
                    "screen INTEGER," +
                    "cellX INTEGER," +
                    "cellY INTEGER," +
                    "spanX INTEGER," +
                    "spanY INTEGER," +
                    "itemType INTEGER," +
                    "appWidgetId INTEGER NOT NULL DEFAULT -1," +
                    "isShortcut INTEGER," +
                    "iconType INTEGER," +
                    "iconPackage TEXT," +
                    "iconResource TEXT," +
                    "icon BLOB," +
                    "uri TEXT," +
                    "displayMode INTEGER," +
                    "isHidden INTEGER DEFAULT 0" +
                    ");");

            db.execSQL("CREATE TABLE recommend (" +
                    "_id INTEGER PRIMARY KEY," +
                    "title TEXT," +
                    "container INTEGER," +
                    "iconUrl TEXT," +
                    "downloadUrl TEXT," +
                    "packageName TEXT," +
                    "category TEXT," +
                    "installed INTEGER," +
                    "tags TEXT" +
                    ");");

            db.execSQL("CREATE TABLE app_category (" +
                    "_id INTEGER PRIMARY KEY," +
                    "packageName TEXT," +
                    "category TEXT" +
                    ");");

            db.execSQL("CREATE TABLE category_map (" +
                    "_id INTEGER PRIMARY KEY," +
                    "folderId INTEGER," +
                    "categoryId TEXT,"+
                    "interface TEXT,"+
                    "category TEXT" +
                    ");");
            db.execSQL("DROP TABLE IF EXISTS screens");
            db.execSQL("CREATE TABLE screens (_id INTEGER PRIMARY KEY, screenOrder INTEGER NOT NULL DEFAULT -1);");

            // Database was just created, so wipe any previous widgets
            if (mAppWidgetHost != null) {
                mAppWidgetHost.deleteHost();
                sendAppWidgetResetNotify();
            }

            if (!convertDatabase(db)) {
                // Set a shared pref so that we know we need to load the default workspace later
            int workspaceResId;
            if (LauncherApplication.isNormalScreen(mContext)) {
                workspaceResId = R.xml.default_workspace;
            } else {
                workspaceResId = R.xml.default_workspace_4x5;
            }
                loadFavorites(db, workspaceResId);
                //createScreensTable(db);
            }
        }
        
        public void createScreensTable(SQLiteDatabase db) {
            ArrayList<Long> screenMap = LauncherModel.getScreenInfoMap();
            screenMap.clear();
            Cursor cursor = null;
            try {
                cursor = db.query(TABLE_FAVORITES, new String[] { "MAX(screen)" }, null, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int maxScreenIdx = cursor.getInt(0) + 1;
                    ContentValues values = new ContentValues();
                    long[] pos = new long[maxScreenIdx];
                    int idx;
                    for (idx = 0; idx < maxScreenIdx; idx++) {
                        values.clear();
                        values.put("screenOrder", idx);
                        pos[idx] = db.insert(TABLE_SCREENS, null, values);
                        screenMap.add(idx, pos[idx]);
                    }
                    
                    for (idx = maxScreenIdx - 1; idx >= 0; idx-- ) {
                        values.clear();
                        values.put("screen", pos[idx]);
                        db.update(TABLE_FAVORITES, values, "screen=?", new String[]{Integer.toString(idx)});
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        
        private boolean convertDatabase(SQLiteDatabase db) {
            if (LOGD) Log.d(TAG, "converting database from an older format, but not onUpgrade");
            boolean converted = false;

            final Uri uri = Uri.parse("content://" + Settings.AUTHORITY +
                    "/old_favorites?notify=true");
            final ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = null;

            try {
                cursor = resolver.query(uri, null, null, null, null);
            } catch (Exception e) {
                // Ignore
            }

            // We already have a favorites database in the old provider
            if (cursor != null && cursor.getCount() > 0) {
                try {
                    converted = copyFromCursor(db, cursor) > 0;
                } finally {
                    cursor.close();
                }

                if (converted) {
                    resolver.delete(uri, null, null);
                }
            }

            if (converted) {
                // Convert widgets from this import into widgets
                if (LOGD) Log.d(TAG, "converted and now triggering widget upgrade");
                convertWidgets(db);
            }

            return converted;
        }

        private int copyFromCursor(SQLiteDatabase db, Cursor c) {
            final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
            final int intentIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
            final int titleIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
            final int iconTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_TYPE);
            final int iconIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
            final int iconPackageIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_PACKAGE);
            final int iconResourceIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_RESOURCE);
            final int containerIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
            final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
            final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
            final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
            final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
            final int uriIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.URI);
            final int displayModeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.DISPLAY_MODE);

            ContentValues[] rows = new ContentValues[c.getCount()];
            int i = 0;
            while (c.moveToNext()) {
                ContentValues values = new ContentValues(c.getColumnCount());
                values.put(LauncherSettings.Favorites._ID, c.getLong(idIndex));
                values.put(LauncherSettings.Favorites.INTENT, c.getString(intentIndex));
                values.put(LauncherSettings.Favorites.TITLE, c.getString(titleIndex));
                values.put(LauncherSettings.Favorites.ICON_TYPE, c.getInt(iconTypeIndex));
                values.put(LauncherSettings.Favorites.ICON, c.getBlob(iconIndex));
                values.put(LauncherSettings.Favorites.ICON_PACKAGE, c.getString(iconPackageIndex));
                values.put(LauncherSettings.Favorites.ICON_RESOURCE, c.getString(iconResourceIndex));
                values.put(LauncherSettings.Favorites.CONTAINER, c.getInt(containerIndex));
                values.put(LauncherSettings.Favorites.ITEM_TYPE, c.getInt(itemTypeIndex));
                values.put(LauncherSettings.Favorites.APPWIDGET_ID, -1);
                values.put(LauncherSettings.Favorites.SCREEN, c.getInt(screenIndex));
                values.put(LauncherSettings.Favorites.CELLX, c.getInt(cellXIndex));
                values.put(LauncherSettings.Favorites.CELLY, c.getInt(cellYIndex));
                values.put(LauncherSettings.Favorites.URI, c.getString(uriIndex));
                values.put(LauncherSettings.Favorites.DISPLAY_MODE, c.getInt(displayModeIndex));
                rows[i++] = values;
            }

            db.beginTransaction();
            int total = 0;
            try {
                int numValues = rows.length;
                for (i = 0; i < numValues; i++) {
                    if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, rows[i]) < 0) {
                        return 0;
                    } else {
                        total++;
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            return total;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (LOGD) Log.d(TAG, "onUpgrade triggered");

            int version = oldVersion;
            if (version < 3) {
                // upgrade 1,2 -> 3 added appWidgetId column
                db.beginTransaction();
                try {
                    // Insert new column for holding appWidgetIds
                    db.execSQL("ALTER TABLE favorites " +
                        "ADD COLUMN appWidgetId INTEGER NOT NULL DEFAULT -1;");
                    db.setTransactionSuccessful();
                    version = 3;
                } catch (SQLException ex) {
                    // Old version remains, which means we wipe old data
                    Log.e(TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }

                // Convert existing widgets only if table upgrade was successful
                if (version == 3) {
                    convertWidgets(db);
                }
            }

            if (version < 4) {
                version = 4;
            }

            // Where's version 5?
            // - Donut and sholes on 2.0 shipped with version 4 of launcher1.
            // - Passion shipped on 2.1 with version 6 of launcher2
            // - Sholes shipped on 2.1r1 (aka Mr. 3) with version 5 of launcher 1
            //   but version 5 on there was the updateContactsShortcuts change
            //   which was version 6 in launcher 2 (first shipped on passion 2.1r1).
            // The updateContactsShortcuts change is idempotent, so running it twice
            // is okay so we'll do that when upgrading the devices that shipped with it.
            if (version < 6) {
                // We went from 3 to 5 screens. Move everything 1 to the right
                db.beginTransaction();
                try {
                    db.execSQL("UPDATE favorites SET screen=(screen + 1);");
                    db.setTransactionSuccessful();
                } catch (SQLException ex) {
                    // Old version remains, which means we wipe old data
                    Log.e(TAG, ex.getMessage(), ex);
                } finally {
                    db.endTransaction();
                }

               // We added the fast track.
                if (updateContactsShortcuts(db)) {
                    version = 6;
                }
            }

            if (version < 7) {
                // Version 7 gets rid of the special search widget.
                convertWidgets(db);
                version = 7;
            }

            if (version < 8) {
                // Version 8 (froyo) has the icons all normalized.  This should
                // already be the case in practice, but we now rely on it and don't
                // resample the images each time.
                normalizeIcons(db);
                version = 8;
            }

            if (version < 9) {
                // The max id is not yet set at this point (onUpgrade is triggered in the ctor
                // before it gets a change to get set, so we need to read it here when we use it)
                if (mMaxId == -1) {
                    mMaxId = initializeMaxId(db);
                }

                // Add default hotseat icons
                loadFavorites(db, R.xml.default_workspace);
                version = 9;
            }

            // We bumped the version three time during JB, once to update the launch flags, once to
            // update the override for the default launch animation and once to set the mimetype
            // to improve startup performance
            if (version < 12) {
                // Contact shortcuts need a different set of flags to be launched now
                // The updateContactsShortcuts change is idempotent, so we can keep using it like
                // back in the Donut days
                updateContactsShortcuts(db);
                version = 12;
            }
            
            if (version < 14) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN isHidden INTEGER DEFAULT 0;");
                version = 14;
            }

            // Add recommend for update, delete tables first
//            if (version < 15) {
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECOMMEND);
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORY);
//                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORY_MAP);
//                db.execSQL("CREATE TABLE recommend (" + "_id INTEGER PRIMARY KEY," + "title TEXT,"
//                        + "container INTEGER," + "iconUrl TEXT," + "downloadUrl TEXT,"
//                        + "packageName TEXT," + "category TEXT," + "installed INTEGER,"
//                        + "tags TEXT" + ");");
//
//                db.execSQL("CREATE TABLE app_category (" + "_id INTEGER PRIMARY KEY,"
//                        + "packageName TEXT," + "category TEXT" + ");");
//
//                db.execSQL("CREATE TABLE category_map (" + "_id INTEGER PRIMARY KEY,"
//                        + "folderId INTEGER," + "categoryId TEXT," + "interface TEXT,"
//                        + "category TEXT" + ");");
//                version = 15;
//            }

            if (version != DATABASE_VERSION) {
                Log.w(TAG, "Destroying all old data.");
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECOMMEND);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORY);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORY_MAP);
                onCreate(db);
            }

            SharedPreferences sp = PreferencesProvider.getSharedPreferences(mContext);
            sp.edit().putLong("last_get_recommend_time", 0).commit();
            sp.edit().putLong("last_get_category_time", 0).commit();
            sp.edit().putBoolean("first_get_white_list", true).commit();
            if (version == 15) {
                sp.edit().putBoolean("add_recommend_folder", true).commit();
            }
        }
        
        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion,
                int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
            onCreate(db);
        }

        private boolean updateContactsShortcuts(SQLiteDatabase db) {
            final String selectWhere = buildOrWhereString(Favorites.ITEM_TYPE,
                    new int[] { Favorites.ITEM_TYPE_SHORTCUT });

            Cursor c = null;
            final String actionQuickContact = "com.android.contacts.action.QUICK_CONTACT";
            db.beginTransaction();
            try {
                // Select and iterate through each matching widget
                c = db.query(TABLE_FAVORITES,
                        new String[] { Favorites._ID, Favorites.INTENT },
                        selectWhere, null, null, null, null);
                if (c == null) return false;

                if (LOGD) Log.d(TAG, "found upgrade cursor count=" + c.getCount());

                final int idIndex = c.getColumnIndex(Favorites._ID);
                final int intentIndex = c.getColumnIndex(Favorites.INTENT);

                while (c.moveToNext()) {
                    long favoriteId = c.getLong(idIndex);
                    final String intentUri = c.getString(intentIndex);
                    if (intentUri != null) {
                        try {
                            final Intent intent = Intent.parseUri(intentUri, 0);
                            android.util.Log.d("Home", intent.toString());
                            final Uri uri = intent.getData();
                            if (uri != null) {
                                final String data = uri.toString();
                                if ((Intent.ACTION_VIEW.equals(intent.getAction()) ||
                                        actionQuickContact.equals(intent.getAction())) &&
                                        (data.startsWith("content://contacts/people/") ||
                                        data.startsWith("content://com.android.contacts/" +
                                                "contacts/lookup/"))) {

                                    final Intent newIntent = new Intent(actionQuickContact);
                                    // When starting from the launcher, start in a new, cleared task
                                    // CLEAR_WHEN_TASK_RESET cannot reset the root of a task, so we
                                    // clear the whole thing preemptively here since
                                    // QuickContactActivity will finish itself when launching other
                                    // detail activities.
                                    newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    newIntent.putExtra(
                                            Launcher.INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION, true);
                                    newIntent.setData(uri);
                                    // Determine the type and also put that in the shortcut
                                    // (that can speed up launch a bit)
                                    newIntent.setDataAndType(uri, newIntent.resolveType(mContext));

                                    final ContentValues values = new ContentValues();
                                    values.put(LauncherSettings.Favorites.INTENT,
                                            newIntent.toUri(0));

                                    String updateWhere = Favorites._ID + "=" + favoriteId;
                                    db.update(TABLE_FAVORITES, values, updateWhere, null);
                                }
                            }
                        } catch (RuntimeException ex) {
                            Log.e(TAG, "Problem upgrading shortcut", ex);
                        } catch (URISyntaxException e) {
                            Log.e(TAG, "Problem upgrading shortcut", e);
                        }
                    }
                }

                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(TAG, "Problem while upgrading contacts", ex);
                return false;
            } finally {
                db.endTransaction();
                if (c != null) {
                    c.close();
                }
            }

            return true;
        }

        private void normalizeIcons(SQLiteDatabase db) {
            Log.d(TAG, "normalizing icons");

            db.beginTransaction();
            Cursor c = null;
            SQLiteStatement update = null;
            try {
                boolean logged = false;
                update = db.compileStatement("UPDATE favorites "
                        + "SET icon=? WHERE _id=?");

                c = db.rawQuery("SELECT _id, icon FROM favorites WHERE iconType=" +
                        Favorites.ICON_TYPE_BITMAP, null);

                final int idIndex = c.getColumnIndexOrThrow(Favorites._ID);
                final int iconIndex = c.getColumnIndexOrThrow(Favorites.ICON);

                while (c.moveToNext()) {
                    long id = c.getLong(idIndex);
                    byte[] data = c.getBlob(iconIndex);
                    try {
                        Bitmap bitmap = Utilities.resampleIconBitmap(
                                BitmapFactory.decodeByteArray(data, 0, data.length),
                                mContext);
                        if (bitmap != null) {
                            update.bindLong(1, id);
                            data = ItemInfo.flattenBitmap(bitmap);
                            if (data != null) {
                                update.bindBlob(2, data);
                                update.execute();
                            }
                            bitmap.recycle();
                        }
                    } catch (Exception e) {
                        if (!logged) {
                            Log.e(TAG, "Failed normalizing icon " + id, e);
                        } else {
                            Log.e(TAG, "Also failed normalizing icon " + id);
                        }
                        logged = true;
                    }
                }
                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(TAG, "Problem while allocating appWidgetIds for existing widgets", ex);
            } finally {
                db.endTransaction();
                if (update != null) {
                    update.close();
                }
                if (c != null) {
                    c.close();
                }
            }
        }

        // Generates a new ID to use for an object in your database. This method should be only
        // called from the main UI thread. As an exception, we do call it when we call the
        // constructor from the worker thread; however, this doesn't extend until after the
        // constructor is called, and we only pass a reference to LauncherProvider to LauncherApp
        // after that point
        public long generateNewId() {
            if (mMaxId < 0) {
                throw new RuntimeException("Error: max id was not initialized");
            }
            mMaxId += 1;
            return mMaxId;
        }

        private long initializeMaxId(SQLiteDatabase db) {
            Cursor c = db.rawQuery("SELECT MAX(_id) FROM favorites", null);

            // get the result
            final int maxIdIndex = 0;
            long id = -1;
            if (c != null && c.moveToNext()) {
                id = c.getLong(maxIdIndex);
            }
            if (c != null) {
                c.close();
            }

            if (id == -1) {
                throw new RuntimeException("Error: could not query max id");
            }

            return id;
        }

        /**
         * Upgrade existing clock and photo frame widgets into their new widget
         * equivalents.
         */
        private void convertWidgets(SQLiteDatabase db) {
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            final int[] bindSources = new int[] {
                    Favorites.ITEM_TYPE_WIDGET_CLOCK,
                    Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME,
                    Favorites.ITEM_TYPE_WIDGET_SEARCH,
            };

            final String selectWhere = buildOrWhereString(Favorites.ITEM_TYPE, bindSources);

            Cursor c = null;

            db.beginTransaction();
            try {
                // Select and iterate through each matching widget
                c = db.query(TABLE_FAVORITES, new String[] { Favorites._ID, Favorites.ITEM_TYPE },
                        selectWhere, null, null, null, null);

                if (LOGD) Log.d(TAG, "found upgrade cursor count=" + c.getCount());

                final ContentValues values = new ContentValues();
                while (c != null && c.moveToNext()) {
                    long favoriteId = c.getLong(0);
                    int favoriteType = c.getInt(1);

                    // Allocate and update database with new appWidgetId
                    try {
                        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();

                        if (LOGD) {
                            Log.d(TAG, "allocated appWidgetId=" + appWidgetId
                                    + " for favoriteId=" + favoriteId);
                        }
                        values.clear();
                        values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET);
                        values.put(Favorites.APPWIDGET_ID, appWidgetId);

                        // Original widgets might not have valid spans when upgrading
                        if (favoriteType == Favorites.ITEM_TYPE_WIDGET_SEARCH) {
                            values.put(LauncherSettings.Favorites.SPANX, 4);
                            values.put(LauncherSettings.Favorites.SPANY, 1);
                        } else {
                            values.put(LauncherSettings.Favorites.SPANX, 2);
                            values.put(LauncherSettings.Favorites.SPANY, 2);
                        }

                        String updateWhere = Favorites._ID + "=" + favoriteId;
                        db.update(TABLE_FAVORITES, values, updateWhere, null);
                        if (Build.VERSION.SDK_INT > 16) {
                            if (favoriteType == Favorites.ITEM_TYPE_WIDGET_CLOCK) {
                                ReflUtils.bindAppWidgetIdIfAllowed(appWidgetManager, appWidgetId,
                                        new ComponentName("com.android.alarmclock", "com.android.alarmclock.AnalogAppWidgetProvider"));
                            } else if (favoriteType == Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME) {
                                ReflUtils.bindAppWidgetIdIfAllowed(appWidgetManager, appWidgetId,
                                        new ComponentName("com.android.camera", "com.android.camera.PhotoAppWidgetProvider"));
                            } else if (favoriteType == Favorites.ITEM_TYPE_WIDGET_SEARCH) {
                                ReflUtils.bindAppWidgetIdIfAllowed(appWidgetManager, appWidgetId,
                                        new ComponentName("com.android.camera", "com.android.camera.PhotoAppWidgetProvider"));
                            }
                        } else {
                            if (favoriteType == Favorites.ITEM_TYPE_WIDGET_CLOCK) {
                                ReflUtils.bindAppWidgetId(appWidgetManager, appWidgetId,
                                        new ComponentName("com.android.alarmclock", "com.android.alarmclock.AnalogAppWidgetProvider"));
                            } else if (favoriteType == Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME) {
                                ReflUtils.bindAppWidgetId(appWidgetManager, appWidgetId,
                                        new ComponentName("com.android.camera", "com.android.camera.PhotoAppWidgetProvider"));
                            } else if (favoriteType == Favorites.ITEM_TYPE_WIDGET_SEARCH) {
                                ReflUtils.bindAppWidgetId(appWidgetManager, appWidgetId, getSearchWidgetProvider());
                            }
                        }
                    } catch (RuntimeException ex) {
                        Log.e(TAG, "Problem allocating appWidgetId", ex);
                    }
                }

                db.setTransactionSuccessful();
            } catch (SQLException ex) {
                Log.w(TAG, "Problem while allocating appWidgetIds for existing widgets", ex);
            } finally {
                db.endTransaction();
                if (c != null) {
                    c.close();
                }
            }
        }

        private static final void beginDocument(XmlPullParser parser, String firstElementName)
                throws XmlPullParserException, IOException {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                ;
            }

            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }

            if (!parser.getName().equals(firstElementName)) {
                throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                        ", expected " + firstElementName);
            }
        }

        private int getIconIdByResName(String ResName) {
            int resourceId = 0;
            try {
                Field field = R.drawable.class.getField(ResName);
                field.setAccessible(true);

                try {
                    resourceId = field.getInt(null);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "IllegalArgumentException:" + e.toString());
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "IllegalAccessException:" + e.toString());
                }
            } catch (NoSuchFieldException e) {
                Log.e(TAG, "NoSuchFieldException:" + e.toString());
            }
            return resourceId;
        }

        private int getStringIdByResName(String ResName) {
            int resourceId = 0;
            try {
                Field field = R.string.class.getField(ResName);
                field.setAccessible(true);

                try {
                    resourceId = field.getInt(null);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "IllegalArgumentException:" + e.toString());
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "IllegalAccessException:" + e.toString());
                }
            } catch (NoSuchFieldException e) {
                Log.e(TAG, "NoSuchFieldException:" + e.toString());
            }
            return resourceId;
        }

        public int loadFavoritesFromXML(SQLiteDatabase db, File file){
            SAXReader reader = new SAXReader();
            Document document = null;
            try {
                document = reader.read(file);
            } catch (DocumentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return -1;
            }
            int noCustomArea_X = 0;
            int noCustomArea_Y = 0;
            int noCustomArea_Scr = 2;
            int lastX = 0;
            int lastY = 0;
            int lastScr = 0;
            int i = 0;

            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            ContentValues values = new ContentValues();
            PackageManager packageManager = mContext.getPackageManager();

            Element element = null;
            try{
                element = document.getRootElement();
            }catch(Exception e){
                Log.e(TAG, "getRootElement" + e.toString());
                return -1;
            }
            for (Iterator iterator = element.elementIterator(); iterator.hasNext();) {
                boolean added = false;
                String x = "0";
                String y = "0";
                int spanX = 1;
                int spanY = 1;

                element = (Element) iterator.next();

                x = element.attributeValue("x");
                y = element.attributeValue("y");

                String container = element.attributeValue("container");
                long containerId = container == null ? -1 : Long.valueOf(container);

                if(containerId == -1){
                    containerId = LauncherSettings.Favorites.CONTAINER_DESKTOP;
                }

                String screen = element.attributeValue("screen");
                int screenIdx = screen == null ? 0 : Integer.valueOf(screen);
                int screenId = screenIdx;
                if (containerId == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    screenId = getScreenId(db, screenIdx);
                }

                values.clear();
                values.put(LauncherSettings.Favorites.CONTAINER, containerId);
                values.put(LauncherSettings.Favorites.SCREEN, screenId);
                values.put(LauncherSettings.Favorites.CELLX, x);
                values.put(LauncherSettings.Favorites.CELLY, y);

                if(TAG_FAVORITE.equals(element.getName())){
                    String appType = element.attributeValue("appType");
                    String packageName = element.attributeValue("packageName");
                    String className = element.attributeValue("className");

                    if (appType != null) {
                        ComponentName tempCn = OEMAppTypeProvider.getAppComponentNameByType(mContext, appType);
                        if (tempCn != null) {
                            packageName = tempCn.getPackageName();
                            className = tempCn.getClassName();
                        }
                    }
                    if(!TextUtils.isEmpty(packageName)){
                        long id = addAppShortcutDirect(db,values,packageManager,intent,packageName,className);
                        added = id >= 0;
                    }
                }else if(TAG_SHORTCUT.equals(element.getName())){
                    Intent intents;
                    String uri = null;
                    try {
                        uri = element.attributeValue("uri");
                        intents = Intent.parseUri(uri, 0);
                    } catch (URISyntaxException e) {
                         Log.w(TAG, "Shortcut has malformed uri: " + uri);
                         continue;
                    }

                    String iconStr = element.attributeValue("icon");
                    String titleStr = element.attributeValue("title");
                    long id = addUriShortcutDirect(db, values, intents,iconStr, titleStr,true);
                    added = id >= 0;
                }else if(TAG_SEARCH.equals(element.getName())){
                    spanX = 4;
                    spanX = 1;
                    added = addSearchWidget(db, values);
                }else if(TAG_CLOCK.equals(element.getName())){
                    spanX = 2;
                    spanX = 2;
                    added = addClockWidget(db, values);
                }else if(TAG_APPWIDGET.equals(element.getName())){
                    spanX = convertToInt(element.attributeValue("spanX"), 0);
                    spanY = convertToInt(element.attributeValue("spanY"), 0);

                    String packageName = element.attributeValue("packageName");
                    String className = element.attributeValue("className");
                    Bundle extras = new Bundle();
                    for (Iterator j = element.elementIterator(TAG_EXTRA); j.hasNext();) {
                        Element child = (Element) j.next();
                        extras.putString(child.attributeValue("key"), child.attributeValue("value"));
                    }
                    added = addAppwidgetDirect(db,values,packageManager,packageName,className,spanX,spanY,extras);
                }else if(TAG_FOLDER.equals(element.getName())){
                    String title = element.attributeValue("title");
                    if (title == null) {
                        title = mContext.getResources().getResourceName(R.string.folder_name);
                    }else{
                        //lqwang - for get res id problem - modify begin
                        //title = getRefString(title);
                        int idx = title.lastIndexOf("/") + 1;
                        String resName = title.substring(idx, title.length());
                        int titleResId = Utilities.getResourceId(mContext,"string",resName);
                        title = mContext.getResources().getResourceName(titleResId);

                        //lqwang - for get res id problem - modify end
                    }
                    values.put(LauncherSettings.Favorites.TITLE, title);
                    long folderId = addFolder(db, values);
                    added = folderId >= 0;
                    ArrayList<Long> folderItems = new ArrayList<Long>();
                    for (Iterator iteratorex = element.elementIterator(); iteratorex.hasNext();){
                        Element child = (Element) iteratorex.next();
                        if(TAG_FAVORITE.equals(child.getName())){
                            //lqwang - for only config appType fc - modify begin
                            String packageName = null;
                            String className = null;
                            String appType = child.attributeValue("appType");
                            if (appType != null) {
                                appType = getRefString(appType);
                                ComponentName tempCn = OEMAppTypeProvider.getAppComponentNameByType(mContext, appType);
                                if (tempCn != null) {
                                    packageName = tempCn.getPackageName();
                                    className = tempCn.getClassName();
                                }
                            }else{
                                packageName = child.attributeValue("packageName");
                                className = child.attributeValue("className");
                            }
                            values.clear();
                            values.put(LauncherSettings.Favorites.CONTAINER, folderId);
                            if(!TextUtils.isEmpty(packageName)){
                                long id = addAppShortcutDirect(db,values,packageManager,intent,packageName,className);
                                if (id >= 0) {
                                    folderItems.add(id);
                                }
                            }
                            //lqwang - for only config appType fc - modify end
                        }else if(TAG_SHORTCUT.equals(child.getName())){
                            //lqwang - for uri null fc and icon can not be add to folder - modify begin
                            Intent intents = new Intent();
                            String uri = null;
                            String packageName = child.attributeValue("packageName");
                            String className = child.attributeValue("className");
                            try {
                                uri = child.attributeValue("uri");
                                if(uri != null){
                                    intents = Intent.parseUri(uri, 0);
                                }else if(packageName != null && className != null){
                                    intents.setClassName(packageName, className);
                                }
                            } catch (URISyntaxException e) {
                                 Log.w(TAG, "Shortcut has malformed uri: " + uri);
                                 continue;
                            }
                            values.clear();
                            values.put(LauncherSettings.Favorites.CONTAINER, folderId);
                            String iconStr = child.attributeValue("icon");
                            String titleStr = child.attributeValue("title");
                            long id = addUriShortcutDirect(db, values, intents,iconStr,titleStr,true);
                            //lqwang - for uri null fc and icon can not be add to folder - modify end
                            if (id >= 0) {
                                folderItems.add(id);
                            }
                        }
                    }

                    if (folderItems.size() < 1 && folderId >= 0) {
                        // We just delete the folder and any items that made it
                        deleteId(db, folderId);
                        if (folderItems.size() > 0) {
                            deleteId(db, folderItems.get(0));
                        }
                        added = false;
                    }

                    String folderTag = element.attributeValue("appType");
                    if (added && folderTag != null) {
                        folderTag = getRefString(folderTag);
                        ContentValues values2 = new ContentValues();
                        values2.put(LauncherSettings.CategoryMap.CATEGORY, folderTag);
                        values2.put(LauncherSettings.CategoryMap.FOLDER_ID, folderId);
                        db.insert(TABLE_CATEGORY_MAP, null, values2);
                    }
                }else if (TAG_NONCUSTOM.equals(element.getName())) {
                    noCustomArea_X = convertToInt(x, 0);
                    noCustomArea_Y = convertToInt(y, 0);
                    noCustomArea_Scr = convertToInt(screen, 0);
                }
                if (added) {
                    int[] cellPos = nextCell(convertToInt(x, 1), convertToInt(y, 1),  convertToInt(screen, 0),
                        spanX, spanY);
                    if (positionComparator(cellPos[0], cellPos[1], cellPos[2], lastX, lastY, lastScr) > 0) {
                        lastX = cellPos[0];
                        lastY = cellPos[1];
                        lastScr = cellPos[2];
                    }
                }
            }

            if (positionComparator(lastX, lastY, lastScr, noCustomArea_X,
                    noCustomArea_Y, noCustomArea_Scr) > 0) {
                  noCustomArea_X = lastX;
                  noCustomArea_Y = lastY;
                  noCustomArea_Scr = lastScr;
            }
            loadAllApps(db, packageManager, noCustomArea_X, noCustomArea_Y, noCustomArea_Scr);
            mCustomApps.clear();
            mCustomApps = null;
            mScreenIds = null;
            return i;
        }

        private String getRefString(String s) {
            if(s.contains("/")){
                int idx = s.lastIndexOf("/") + 1;
                String resName = s.substring(idx, s.length());
                int titleResId = Utilities.getResourceId(mContext, "string", resName);
                s = mContext.getResources().getString(titleResId);
            }
            return s;
        }

        /**
         * Loads the default set of favorite packages from an xml file.
         *
         * @param db The database to write the values into
         * @param filterContainerId The specific container id of items to load
         */
        private int loadFavorites(SQLiteDatabase db, int workspaceResourceId) {
        	int noneCustomX = 0;
        	int noneCustomY = 0;
        	int noneCustomScreen = 1;
        	int lastCustomX = 0;
        	int lastCustomY = 0;
        	int lastCustomScreen = 0;
        	
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            ContentValues values = new ContentValues();
            
            PackageManager packageManager = mContext.getPackageManager();
            if (mCustomApps == null) {
            	mCustomApps = new HashMap<ComponentName, Boolean>();
            }
            if (mScreenIds == null) {
                mScreenIds = new ArrayList<Long>();
            }
            int i = 0;

            String defWorkSpacePatch = "/system/etc/res/default_workspace.xml";

            File externFile = new File(defWorkSpacePatch);
            if(externFile.exists()){
                i = loadFavoritesFromXML(db, externFile);
                if(i != -1){
                    return i;
                }
                i = 0;
            }
            try {
                XmlResourceParser parser = mContext.getResources().getXml(workspaceResourceId);
                AttributeSet attrs = Xml.asAttributeSet(parser);
                beginDocument(parser, TAG_FAVORITES);

                final int depth = parser.getDepth();

                int type;
                while (((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {

                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    }

                    boolean added = false;
                    final String name = parser.getName();
                    int spanX = 1;
                    int spanY = 1;
                    
                    TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.Favorite);

                    long container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
                    if (a.hasValue(R.styleable.Favorite_container)) {
                        container = Long.valueOf(a.getString(R.styleable.Favorite_container));
                    }

                    String screen = a.getString(R.styleable.Favorite_screen);
                    int screenIdx = screen == null ? 0 : Integer.valueOf(screen);
                    int screenId = screenIdx;
                    if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                         screenId = getScreenId(db, screenIdx);
                    }
                    String x = a.getString(R.styleable.Favorite_x);
                    String y = a.getString(R.styleable.Favorite_y);

                    values.clear();
                    values.put(LauncherSettings.Favorites.CONTAINER, container);
                    values.put(LauncherSettings.Favorites.SCREEN, screenId);
                    values.put(LauncherSettings.Favorites.CELLX, x);
                    values.put(LauncherSettings.Favorites.CELLY, y);

                    if (TAG_FAVORITE.equals(name)) {
                        long id = addAppShortcut(db, values, a, packageManager, intent);
                        added = id >= 0;
                    } else if (TAG_SEARCH.equals(name)) {
                    	spanX = 4;
                    	spanX = 1;
                        added = addSearchWidget(db, values);
                    } else if (TAG_CLOCK.equals(name)) {
                    	spanX = 2;
                    	spanX = 2;
                        added = addClockWidget(db, values);
                    } else if (TAG_APPWIDGET.equals(name)) {
                    	spanX = a.getInt(R.styleable.Favorite_spanX, 0);
                        spanY = a.getInt(R.styleable.Favorite_spanY, 0);
                        added = addAppWidget(parser, attrs, type, db, values, a, packageManager);
                    } else if (TAG_SHORTCUT.equals(name)) {
                        long id = addUriShortcut(db, values, a);
                        added = id >= 0;
                    } else if (TAG_FOLDER.equals(name)) {
                        String title;
                        int titleResId =  a.getResourceId(R.styleable.Favorite_title, -1);
                        if (titleResId != -1) {
                            title = mContext.getResources().getResourceName(titleResId);
                        } else {
                            title = mContext.getResources().getResourceName(R.string.folder_name);
                        }
                        values.put(LauncherSettings.Favorites.TITLE, title);
                        long folderId = addFolder(db, values);
                        added = folderId >= 0;

                        ArrayList<Long> folderItems = new ArrayList<Long>();

                        int folderDepth = parser.getDepth();
                        while ((type = parser.next()) != XmlPullParser.END_TAG ||
                                parser.getDepth() > folderDepth) {
                            if (type != XmlPullParser.START_TAG) {
                                continue;
                            }
                            final String folder_item_name = parser.getName();
                            TypedArray ar = mContext.obtainStyledAttributes(attrs, R.styleable.Favorite);
                            values.clear();
                            values.put(LauncherSettings.Favorites.CONTAINER, folderId);

                            if (TAG_FAVORITE.equals(folder_item_name) && folderId >= 0) {
                                long id =
                                    addAppShortcut(db, values, ar, packageManager, intent);
                                if (id >= 0) {
                                    folderItems.add(id);
                                }
                            } else if (TAG_SHORTCUT.equals(folder_item_name) && folderId >= 0) {
                                long id = addUriShortcut(db, values, ar);
                                if (id >= 0) {
                                    folderItems.add(id);
                                }
                            } else {
                                throw new RuntimeException("Folders can " +
                                        "contain only shortcuts");
                            }
                            ar.recycle();
                        }
                        // We can only have folders with >= 2 items, so we need to remove the
                        // folder and clean up if less than 2 items were included, or some
                        // failed to add, and less than 2 were actually added
                        if (folderItems.size() < 1 && folderId >= 0) {
                            // We just delete the folder and any items that made it
                            deleteId(db, folderId);
                            if (folderItems.size() > 0) {
                                deleteId(db, folderItems.get(0));
                            }
                            added = false;
                        }
                        String folderTag = a.getString(R.styleable.Favorite_appType);
                        if (added && folderTag != null) {
                            ContentValues values2 = new ContentValues();
                            values2.put(LauncherSettings.CategoryMap.CATEGORY, folderTag);
                            values2.put(LauncherSettings.CategoryMap.FOLDER_ID, folderId);
                            db.insert(TABLE_CATEGORY_MAP, null, values2);
                        }
                    } else if (TAG_NONCUSTOM.equals(name)) {
                    	noneCustomX = Integer.valueOf(x);
                    	noneCustomY = Integer.valueOf(y);
                    	noneCustomScreen = Integer.valueOf(screen);
                    }
                    if (added) {
                    	i++;
                    	int[] cellPos = nextCell(Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(screen), spanX, spanY);
                    	if (positionComparator(cellPos[0], cellPos[1], cellPos[2], lastCustomX, lastCustomY, lastCustomScreen) > 0) {
                    		lastCustomX = cellPos[0];
                    		lastCustomY = cellPos[1];
                    		lastCustomScreen = cellPos[2];
                    	}

                    }
                    a.recycle();
                }
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Got exception parsing favorites.", e);
            } catch (IOException e) {
                Log.w(TAG, "Got exception parsing favorites.", e);
            } catch (RuntimeException e) {
                Log.w(TAG, "Got exception parsing favorites.", e);
            }
            if (positionComparator(lastCustomX, lastCustomY, lastCustomScreen, noneCustomX, noneCustomY, noneCustomScreen) > 0) {
            	noneCustomX = lastCustomX;
            	noneCustomY = lastCustomY;
            	noneCustomScreen = lastCustomScreen;
            	
            }
            // load other apps not in default workspace
            loadAllApps(db, packageManager, noneCustomX, noneCustomY, noneCustomScreen);
            mCustomApps.clear();
            mCustomApps = null;
            mScreenIds = null;
            return i;
        }
        
        int getScreenId(SQLiteDatabase db, int screenIdx) {
            long screenId = 1;
            if (mScreenIds == null || screenIdx < 0 && screenIdx >= PreferencesProvider.MAX_SCREEN_COUNT) {
                return (int)screenId;
            }
            if (screenIdx >= mScreenIds.size()) {
                ContentValues values = new ContentValues();
                for (int c = mScreenIds.size(); c <= screenIdx; c++) {
                    values.clear();
                    values.put("screenOrder", Integer.valueOf(c));
                    screenId  = db.insert(TABLE_SCREENS, null, values);
                    mScreenIds.add(screenId);
                }
            } else {
                screenId = mScreenIds.get(screenIdx);
            }
            return (int)screenId;
        }

        int positionComparator (int x1, int y1, int scr1, int x2, int y2, int scr2) {
        	return (scr1 * mCellCountX * mCellCountY + y1 * mCellCountX + x1) - 
        			(scr2 * mCellCountX * mCellCountY + y2 * mCellCountX + x2); 
        	
        }
        
        int[] nextCell (int x, int y, int scr, int spanX, int spanY) {
        	int[] result = new int[3];
        	result[0] = x + spanX;
        	result[1] = y + spanY - 1;
        	result[2] = scr;
        	
        	if (result[0] >= mCellCountX) {
        		result[0] = 0;
        		result[1] ++;
        	} 
        	if (result[1] >= mCellCountY) {
        		result[0] = 0;
        		result[1] = 0;
        		result[2] ++;
        	} 
        	return result;
        }
        
        private List<ResolveInfo> getAllApps(PackageManager packageManager){
        	final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
            Comparator<Object> comparator = new Utilities.PackageInstalledTimeComparator(packageManager);
            Collections.sort(apps, comparator);
            return apps;
        }
        
        private static int convertToInt(String str, int def){
            if(str == null){
                return def;
            }
            try{
                return Integer.valueOf(str);
            } catch (Exception e){
                Log.w(TAG, "convert to int failed:" + str);
            }
            return def;
        }

        private int loadAllApps(SQLiteDatabase db, PackageManager pm, int startX, int startY,
                int startScreen) {
            int load = 0;
            List<ResolveInfo> allApps = getAllApps(pm);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            ContentValues values = new ContentValues();
            int curX = startX;
            int curY = startY;
            int curScr = startScreen;
            
            String className = null, packageName = null;
            try {
                for (ResolveInfo resolveInfo : allApps) {
                    className = resolveInfo.activityInfo.name;
                    packageName = resolveInfo.activityInfo.packageName;
                    ComponentName cn = new ComponentName(packageName, className);
                    Boolean added = mCustomApps.get(cn);
                    //lqwang - PR62913 - modify begin
                    if (added != null && added == true || LauncherModel.isAppNeedHidden(mContext,resolveInfo)) {
                        continue;
                    }
                    //lqwang - PR62913 - modify end
                    intent.setComponent(cn);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    values.clear();
                    values.put(LauncherSettings.Favorites.CONTAINER, LauncherSettings.Favorites.CONTAINER_DESKTOP);
                    int curScrId =  getScreenId(db, curScr);
                    values.put(LauncherSettings.Favorites.SCREEN, curScrId);
                    values.put(LauncherSettings.Favorites.CELLX, curX);
                    values.put(LauncherSettings.Favorites.CELLY, curY);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    values.put(Favorites.INTENT, intent.toUri(0));
                    values.put(Favorites.TITLE, resolveInfo.loadLabel(pm).toString());
                    values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION);
                    values.put(Favorites.SPANX, 1);
                    values.put(Favorites.SPANY, 1);
                    values.put(Favorites._ID, generateNewId());
                    if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values) < 0) {
                        return -1;
                    }
                    if (curX < mCellCountX - 1) {
                        curX++;
                    } else {
                        curX = 0;
                        if (curY < mCellCountY - 1) {
                            curY++;
                        } else {
                            curY = 0;
                            curScr++;
                        }
                    }
                    load++;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to add app: " + packageName + "/"
                        + className, e);
            }
            return load;
        }
        
        private long addAppShortcut(SQLiteDatabase db, ContentValues values, TypedArray a,
                PackageManager packageManager, Intent intent) {
            long id = -1;
            ActivityInfo info;
            String packageName = a.getString(R.styleable.Favorite_packageName);
            String className = a.getString(R.styleable.Favorite_className);
            
            String appType = a.getString(R.styleable.Favorite_appType);
            if (appType != null) {
                ComponentName tempCn = OEMAppTypeProvider.getAppComponentNameByType(mContext, appType);
                if (tempCn != null) {
                    packageName = tempCn.getPackageName();
                    className = tempCn.getClassName();
                } else {
                    return -1;
                }
            } else {
                packageName = a.getString(R.styleable.Favorite_packageName);
                className = a.getString(R.styleable.Favorite_className);
            }
            
            id = addAppShortcutDirect(db,values,packageManager,intent,packageName,className);
            return id;
        }

        private long addAppShortcutDirect(SQLiteDatabase db,ContentValues values,PackageManager packageManager,Intent intent,
                String packageName,String className){
            long id = -1;
            ActivityInfo info;
            try {
                ComponentName cn;
                try {
                    cn = new ComponentName(packageName, className);
                    info = packageManager.getActivityInfo(cn, 0);
                } catch (PackageManager.NameNotFoundException nnfe) {
                    String[] packages = packageManager.currentToCanonicalPackageNames(
                        new String[] { packageName });
                    cn = new ComponentName(packages[0], className);
                    info = packageManager.getActivityInfo(cn, 0);
                }
                id = generateNewId();
                intent.setComponent(cn);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                values.put(Favorites.INTENT, intent.toUri(0));
                values.put(Favorites.TITLE, info.loadLabel(packageManager).toString());
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION);
                values.put(Favorites.SPANX, 1);
                values.put(Favorites.SPANY, 1);
                values.put(Favorites._ID, id);
                if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values) < 0) {
                    return -1;
                }
                mCustomApps.put(cn, true);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Unable to add favorite: " + packageName +
                        "/" + className, e);
            }
            return id;
        }

        private long addFolder(SQLiteDatabase db, ContentValues values) {
            values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER);
            values.put(Favorites.SPANX, 1);
            values.put(Favorites.SPANY, 1);
            long id = generateNewId();
            values.put(Favorites._ID, id);
            if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values) <= 0) {
                return -1;
            } else {
                return id;
            }
        }

        private ComponentName getSearchWidgetProvider() {
            SearchManager searchManager =
                    (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
            ComponentName searchComponent = ReflUtils.getGlobalSearchActivity(searchManager);
            if (searchComponent == null) return null;
            return getProviderInPackage(searchComponent.getPackageName());
        }

        /**
         * Gets an appwidget provider from the given package. If the package contains more than
         * one appwidget provider, an arbitrary one is returned.
         */
        private ComponentName getProviderInPackage(String packageName) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
            if (providers == null) return null;
            final int providerCount = providers.size();
            for (int i = 0; i < providerCount; i++) {
                ComponentName provider = providers.get(i).provider;
                if (provider != null && provider.getPackageName().equals(packageName)) {
                    return provider;
                }
            }
            return null;
        }

        private boolean addSearchWidget(SQLiteDatabase db, ContentValues values) {
            ComponentName cn = getSearchWidgetProvider();
            return addAppWidget(db, values, cn, 4, 1, null);
        }

        private boolean addClockWidget(SQLiteDatabase db, ContentValues values) {
            ComponentName cn = new ComponentName("com.android.alarmclock",
                    "com.android.alarmclock.AnalogAppWidgetProvider");
            return addAppWidget(db, values, cn, 2, 2, null);
        }

        private boolean addAppwidgetDirect(SQLiteDatabase db,ContentValues values,PackageManager packageManager,
                String packageName,String className,int spanX, int spanY,Bundle extras){

            if (packageName == null || className == null) {
                return false;
            }

            boolean hasPackage = true;
            ComponentName cn = new ComponentName(packageName, className);
            try {
                packageManager.getReceiverInfo(cn, 0);
            } catch (Exception e) {
                String[] packages = packageManager.currentToCanonicalPackageNames(
                        new String[] { packageName });
                cn = new ComponentName(packages[0], className);
                try {
                    packageManager.getReceiverInfo(cn, 0);
                } catch (Exception e1) {
                    hasPackage = false;
                }
            }

            if (hasPackage) {
                return addAppWidget(db, values, cn, spanX, spanY, extras);
            }

            return false;
        }

        private boolean addAppWidget(XmlResourceParser parser, AttributeSet attrs, int type,
                SQLiteDatabase db, ContentValues values, TypedArray a,
                PackageManager packageManager) throws XmlPullParserException, IOException {

            String packageName = a.getString(R.styleable.Favorite_packageName);
            String className = a.getString(R.styleable.Favorite_className);

            if (packageName == null || className == null) {
                return false;
            }

            boolean hasPackage = true;
            ComponentName cn = new ComponentName(packageName, className);
            try {
                packageManager.getReceiverInfo(cn, 0);
            } catch (Exception e) {
                String[] packages = packageManager.currentToCanonicalPackageNames(
                        new String[] { packageName });
                cn = new ComponentName(packages[0], className);
                try {
                    packageManager.getReceiverInfo(cn, 0);
                } catch (Exception e1) {
                    hasPackage = false;
                }
            }

            if (hasPackage) {
                int spanX = a.getInt(R.styleable.Favorite_spanX, 0);
                int spanY = a.getInt(R.styleable.Favorite_spanY, 0);

                // Read the extras
                Bundle extras = new Bundle();
                int widgetDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > widgetDepth) {
                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    }

                    TypedArray ar = mContext.obtainStyledAttributes(attrs, R.styleable.Extra);
                    if (TAG_EXTRA.equals(parser.getName())) {
                        String key = ar.getString(R.styleable.Extra_key);
                        String value = ar.getString(R.styleable.Extra_value);
                        if (key != null && value != null) {
                            extras.putString(key, value);
                        } else {
                            throw new RuntimeException("Widget extras must have a key and value");
                        }
                    } else {
                        throw new RuntimeException("Widgets can contain only extras");
                    }
                    ar.recycle();
                }
                return addAppWidget(db, values, cn, spanX, spanY, extras);
            }
            return false;
        }

        private boolean addAppWidget(SQLiteDatabase db, ContentValues values, ComponentName cn,
                int spanX, int spanY, Bundle extras) {
            boolean allocatedAppWidgets = false;
            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            try {
                int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET);
                values.put(Favorites.SPANX, spanX);
                values.put(Favorites.SPANY, spanY);
                values.put(Favorites.APPWIDGET_ID, appWidgetId);
                values.put(Favorites._ID, generateNewId());
                dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values);

                allocatedAppWidgets = true;

                // TODO: need to check return value
                if (Build.VERSION.SDK_INT >= 16) {
                    allocatedAppWidgets = ReflUtils.bindAppWidgetIdIfAllowed(appWidgetManager, appWidgetId, cn);
                } else {
                    allocatedAppWidgets = ReflUtils.bindAppWidgetId(appWidgetManager, appWidgetId, cn);
                }
                // Send a broadcast to configure the widget
                if (extras != null && !extras.isEmpty()) {
                    Intent intent = new Intent(ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE);
                    intent.setComponent(cn);
                    intent.putExtras(extras);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    mContext.sendBroadcast(intent);
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Problem allocating appWidgetId", ex);
            }
            return allocatedAppWidgets;
        }

        private long addUriShortcutDirect(SQLiteDatabase db, ContentValues values, Intent intent,
                String iconStr, String titleStr,boolean fromOutLoad){
            //lqwang - for get res id problem - modify begin
            int iconResId = 0;
            String title = null;
            String iconResName;
            try {
                int iconIndex = iconStr.lastIndexOf("/") + 1;
                int titleIndex = titleStr.indexOf("/") + 1;
                if(fromOutLoad){
                    iconResName = iconStr.substring(iconIndex, iconStr.length()); //iconStr : @drawable/recommend_icon_life titlestr : @string/server_lifestyle_category
                    int titleId = Utilities.getResourceId(mContext,"string",titleStr.substring(titleIndex,titleStr.length()));
                    title = mContext.getResources().getString(titleId);
                } else {
                    iconResName = iconStr.substring(iconIndex, iconStr.lastIndexOf(".")); //iconStr : res/drawable-xhdpi/recommend_icon_life.png titlestr = text
                    title = titleStr.substring(titleIndex, titleStr.length());
                }
                iconResId = Utilities.getResourceId(mContext, "drawable", iconResName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //lqwang - for get res id problem - modify end
            if (iconResId == 0 || title == null) {
                Log.w(TAG, "Shortcut is missing title or icon resource ID");
                return -1;
            }
            Resources r = mContext.getResources();
            long id = generateNewId();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            values.put(Favorites.INTENT, intent.toUri(0));
            values.put(Favorites.TITLE, title);
            values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_SHORTCUT);
            values.put(Favorites.SPANX, 1);
            values.put(Favorites.SPANY, 1);
            values.put(Favorites.ICON_TYPE, Favorites.ICON_TYPE_RESOURCE);
            values.put(Favorites.ICON_PACKAGE, mContext.getPackageName());
            values.put(Favorites.ICON_RESOURCE, r.getResourceName(iconResId));
            values.put(Favorites._ID, id);
            if (dbInsertAndCheck(this, db, TABLE_FAVORITES, null, values) < 0) {
                return -1;
            }
            PackageManager packageManager = mContext.getPackageManager();
            // put shortcut to  mCustomApps
            final ResolveInfo resolveInfo = packageManager.resolveActivity(intent, 0);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
            	ComponentName cn = new ComponentName(resolveInfo.activityInfo.packageName, 
            			resolveInfo.activityInfo.name);
            	mCustomApps.put(cn, true);
            }
            return id;
        }

        private long addUriShortcut(SQLiteDatabase db, ContentValues values,
                TypedArray a) {

            int iconResId = a.getResourceId(R.styleable.Favorite_icon, 0);
            int titleResId = a.getResourceId(R.styleable.Favorite_title, 0);
            //lqwang - for uri null fc  - modify begin
            String packageName = a.getString(R.styleable.Favorite_packageName);
            String className = a.getString(R.styleable.Favorite_className);

            Intent intent = new Intent();
            String uri = null;
            String iconStr = null;
            String titleStr = null;
            try {
                uri = a.getString(R.styleable.Favorite_uri);
                if(uri != null){
                    intent = Intent.parseUri(uri, 0);
                }  else if (packageName != null && className != null) {
                    intent.setClassName(packageName, className);
                 }
            } catch (URISyntaxException e) {
                Log.w(TAG, "Shortcut has malformed uri: " + uri);
                return -1; // Oh well
            }
            iconStr = a.getString(R.styleable.Favorite_icon);
            titleStr = a.getString(R.styleable.Favorite_title);
            //lqwang - for uri null fc  - modify begin
            return addUriShortcutDirect(db,values,intent,iconStr,titleStr,false);
        }
    }
    
    public static ComponentName findLaunchComp(PackageManager pm, final ResolveInfo info) {
        String pkgName = info.activityInfo.packageName;
        String className = info.activityInfo.name;
        final Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory(Intent.CATEGORY_LAUNCHER);
        intentToResolve.setPackage(pkgName);
        List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);
        if (ris != null) {
            for (ResolveInfo tInfo : ris) {
                if (className != null && className.equals(tInfo.activityInfo.name)) {
                    return new ComponentName(pkgName, className);
                }
            }
        }
        return null;
    }
    
    /**
     * Build a query string that will match any row where the column matches
     * anything in the values list.
     */
    static String buildOrWhereString(String column, int[] values) {
        StringBuilder selectWhere = new StringBuilder();
        for (int i = values.length - 1; i >= 0; i--) {
            selectWhere.append(column).append("=").append(values[i]);
            if (i > 0) {
                selectWhere.append(" OR ");
            }
        }
        return selectWhere.toString();
    }

    static class SqlArguments {
        public final String table;
        public final String where;
        public final String[] args;

        SqlArguments(Uri url, String where, String[] args) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                this.where = where;
                this.args = args;
            } else if (url.getPathSegments().size() != 2) {
                throw new IllegalArgumentException("Invalid URI: " + url);
            } else if (!TextUtils.isEmpty(where)) {
                throw new UnsupportedOperationException("WHERE clause not supported: " + url);
            } else {
                this.table = url.getPathSegments().get(0);
                this.where = "_id=" + ContentUris.parseId(url);
                this.args = null;
            }
        }

        SqlArguments(Uri url) {
            if (url.getPathSegments().size() == 1) {
                table = url.getPathSegments().get(0);
                where = null;
                args = null;
            } else {
                throw new IllegalArgumentException("Invalid URI: " + url);
            }
        }
    }
}
