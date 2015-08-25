
package com.lewa.launcher;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.lewa.toolbox.HttpUtils;
import com.lewa.toolbox.LewaUtils;
import com.lewa.toolbox.HttpUtils.HttpResponse;
import com.lewa.launcher.preference.PreferencesProvider;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

public class AppRecommendHelper extends BroadcastReceiver{
    private static final String TAG = "AppRecommendHelper";
    private static final String LAST_GET_RECOMMEND_TIME = "last_get_recommend_time";
    private static final String LAST_GET_CATEGORY_TIME = "last_get_category_time";
    private static final String FIRST_GET_WHITE_LIST = "first_get_white_list";
    private static final String URL = "http://api.lewaos.com/";
    private static final String APP_RECOMMEND_URL = "http://api.lewaos.com/apps/content/folder_suggest_apps?category_id=";
    private static final String APP_RECOMMEND_URL_ALL = "http://api.lewaos.com/apps/content/folder_suggest_apps";
    private static final String APP_CATEGORY_URL = "http://api.lewaos.com/apps/content/folder_apps_category?pkgs=";
    private static final String APP_CATEGORY_URL_ALL = "http://api.lewaos.com/apps/content/get_folder_apps_category_list";
    private static final String WHITE_LIST_URL = "http://api.lewaos.com/apps/content/white_list ";
    private static final String RESULT_DATA = "result";
    private static final String SELF_DEFINE_DATA = "self_define";
    private static final String APK_DETAILS_DATA = "data";
    public static final String NETWORK_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    private static final long UPDATE_PERIOD = 2 * 24 * 3600 * 1000;
    private static final int REQUEST_RETRY_CNT = 5;
    private static final int MAX_RECOMMEND_CNT = 4;

    private String gameTags[] ={};
    private ArrayList<String> mWhitList = new ArrayList<String>();

    private static final Object sBgLock = new Object();
    private static final HandlerThread sWorkerThread = new HandlerThread("recommend-loader");
    static {
        sWorkerThread.start();
    }
    private static final Handler sWorker = new Handler(sWorkerThread.getLooper());

    private Context mContext;
    private Resources resources;
    private AppRecommendCallbacks mCallbacks;
    private String appCategoryUrl;
    private String appCategoryUpdateId = "-1";
    private String appRecommendUrl;
    private boolean isImmediate = false;
    private boolean isRecommendUpdated = true;
    private boolean isCategoryUpdated = true;
    private boolean isLauncherFinishLoading = false;

    public AppRecommendHelper(Context context) {
        mContext = context;
        resources = context.getResources();
        gameTags = resources.getStringArray(R.array.game_second_category);
    }

    public void initialize(AppRecommendCallbacks callbacks) {
        if (callbacks != null) {
            mCallbacks = callbacks;
        }
    }

    public void setFinishLoading(boolean flag) {
        isLauncherFinishLoading = flag;
    }

    public void startUpdater(boolean isUpdateRecommend, String updateId,
            boolean isUpdateCategory, ArrayList<String> updateApps, boolean immediate) {
        synchronized (sBgLock) {
            isImmediate = immediate;
            if (isUpdateRecommend) {
                if (appCategoryUpdateId.equals(updateId)) {
                    return;
                }
                isRecommendUpdated = false;
                initAppRecommendUrl(updateId);
            }
            if (isUpdateCategory || Utilities.mAddedPackages.size() > 0) {
                isCategoryUpdated = false;
                initAppCategoryUrl(updateApps);
            }
            UpdateTask updateTask = new UpdateTask();
            sWorker.post(updateTask);
        }
    }

    private void initAppCategoryUrl(List<String> updateApps) {
        if (updateApps == null) {
            updateApps = getAllAppPackgeName();
        }
        appCategoryUrl = APP_CATEGORY_URL;
        //lqwang - pr1003455 - modify begin
        synchronized (updateApps){
            for (String pkName : updateApps) {
                appCategoryUrl += pkName + "|";
            }
        }
        //lqwang - pr1003455 - modify end
    }

    private void initAppRecommendUrl(String updateId) {
        StringBuilder sb = new StringBuilder();
        String partner = LewaUtils.getPartner();
        String model = Build.MODEL;
        if (updateId == null) {
            appCategoryUpdateId = "-1";
            sb.append(APP_RECOMMEND_URL_ALL).append("?channel=").append(partner).append("&model=").append(model.replace(" ", "_")).append("&device=phone")
                    .append("&package=").append(mContext.getPackageName()).append("&_refresh=auto");
        } else {
            appCategoryUpdateId = updateId;
            sb.append(APP_RECOMMEND_URL).append(updateId).append("&channel=").append(partner).append("&model=").append(model.replace(" ", "_")).append("&device=phone")
                    .append("&package=").append(mContext.getPackageName()).append("&_refresh=manual");
        }
        appRecommendUrl = sb.toString();

    }

    private List<String> getAllAppPackgeName() {
        if (Utilities.mAddedPackages.size() > 0) {
            return Utilities.mAddedPackages;
        }
        ArrayList<String> mAllApps = new ArrayList<String>();
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        final PackageManager packageManager = mContext.getPackageManager();
        List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo info : apps) {
            mAllApps.add(info.activityInfo.packageName);
        }
        return mAllApps;
    }

    private long getFolderNameMaps(String categoryName, String categoryId, String url) {
        ContentResolver cr = mContext.getContentResolver();
        long result = LauncherModel.getFolderByCategoryInMap(mContext, categoryName);
        if (result == -1) {
            result = LauncherModel.getFolderByName(mContext, categoryName);
            ContentValues values = new ContentValues();
            values.put(LauncherSettings.CategoryMap.CATEGORY_ID, categoryId);
            values.put(LauncherSettings.CategoryMap.CATEGORY, categoryName);
            values.put(LauncherSettings.CategoryMap.INTERFACE, URL+url);
            if (result < 30 && result > 0) {
                values.put(LauncherSettings.CategoryMap.FOLDER_ID, result);
            }
            cr.insert(LauncherSettings.CategoryMap.CONTENT_URI, values);
            //sp.edit().putLong(categoryName, result).commit();
        }else{
            ContentValues values = new ContentValues();
            values.put(LauncherSettings.CategoryMap.CATEGORY_ID, categoryId);
            values.put(LauncherSettings.CategoryMap.INTERFACE, URL+url);
            cr.update(LauncherSettings.CategoryMap.CONTENT_URI, values, "category=?", new String[] {categoryName});
        }
        return result;
    }

    private boolean isGameCategory(String categoryName) {
        for (int i = 0; i < gameTags.length; i++) {
            if (gameTags[i].equals(categoryName)) {
                return true;
            }
        }
        return false;
    }

    class UpdateTask implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            loadAndInitAppRecommentInfo();
        }
    }

    private void loadAndInitAppRecommentInfo() {
        if (Utilities.getNetworkType(mContext) == -1) {
            return;
        }
        Log.d(TAG, "loadAndInitAppRecommentInfo,isRecommendUpdated="+isRecommendUpdated);
        final SharedPreferences sp = PreferencesProvider.getSharedPreferences(mContext);
        if (PreferencesProvider.isRecommendOn(mContext) && !isRecommendUpdated) {
            long start = sp.getLong(LAST_GET_RECOMMEND_TIME, 0);

            if (isImmediate || (System.currentTimeMillis() - start >= UPDATE_PERIOD)) {
                getRecommendListFromNet();
                if(isImmediate){
                    mCallbacks.updateCurrentAppRecommend();
                }
                if (isRecommendUpdated) {
                    sp.edit().putLong(LAST_GET_RECOMMEND_TIME, System.currentTimeMillis()).commit();
                }
            }
        }

        if (PreferencesProvider.isSmartSortOn(mContext) && !isCategoryUpdated && appCategoryUrl != APP_CATEGORY_URL) {
            long start = sp.getLong(LAST_GET_CATEGORY_TIME, 0);
            if (Utilities.mAddedPackages.size() > 0
                    || (System.currentTimeMillis() - start >= UPDATE_PERIOD)) {
                getAppCategoryFromNet(appCategoryUrl);
                if (isCategoryUpdated) {
                    sp.edit().putLong(LAST_GET_CATEGORY_TIME, System.currentTimeMillis()).commit();
                }
            }
        }

        if(PreferencesProvider.isSmartSortOn(mContext) && PreferencesProvider.isFirstRun(mContext, FIRST_GET_WHITE_LIST)){
            getWhiteListFromNet(WHITE_LIST_URL);
            LauncherApplication application = ((LauncherApplication)(mContext.getApplicationContext()));
            application.sortApps();
            application.setWaitToSort(false);
        }
        appCategoryUpdateId = "-1";
    }

    private void getRecommendListFromNet() {
        Log.i(TAG, "getRecommendListFromNet() ");
        try {
            String resultStr = null;
            for (int retry = 0; retry < REQUEST_RETRY_CNT; retry++) {
                resultStr = sendHttpRequest(appRecommendUrl);
                if (!TextUtils.isEmpty(resultStr)) {
                    break;
                }
            }
            if (TextUtils.isEmpty(resultStr)) {
                Log.e(TAG, "Not get result from server ! server problem ?");
                isRecommendUpdated = false;
                return;
            }

            // all data
            JSONObject all = new JSONObject(resultStr);
            int errorCode = all.getInt("code");
            String msgCode = all.getString("message");
            if (errorCode != 0 || !"OK".equalsIgnoreCase(msgCode)) {
                Log.e(TAG, "errorCode = " + errorCode + " , msgCode = " + msgCode);
                isRecommendUpdated = false;
                return;
            }
            if (isImmediate) {
                JSONArray apkArray = all.getJSONArray(RESULT_DATA);
                parseJSONArray(apkArray);
            } else {
                JSONObject result = all.getJSONObject(RESULT_DATA);
                parseJSONObject(result);
            }
        } catch (Exception e) {
            Log.e(TAG, "getRecommendListFromNet erroe:"+e.toString());
            e.printStackTrace();
        }
    }

    private void getAppCategoryFromNet(String url) {
        Log.i(TAG, "getAppCategoryFromNet");
        try {
            String resultStr = null;
            for (int retry = 0; retry < REQUEST_RETRY_CNT; retry++) {
                resultStr = sendHttpRequest(url);
                if (!TextUtils.isEmpty(resultStr)) {
                    break;
                }
            }
            if (TextUtils.isEmpty(resultStr)) {
                isCategoryUpdated = false;
                return;
            }

            JSONObject all = new JSONObject(resultStr);
            int errorCode = all.getInt("code");
            String msgCode = all.getString("message");
            if (errorCode != 0 || !"OK".equalsIgnoreCase(msgCode)) {
                isCategoryUpdated = false;
                return;
            }
            JSONObject result = all.getJSONObject(RESULT_DATA);
            JSONArray ids = result.names();
            for (int i = 0; i < ids.length(); i++) {
                String pkgName = ids.getString(i);
                if (LauncherModel.getCategoryByPackageName(mContext, pkgName) != null) {
                    mContext.getContentResolver().delete(LauncherSettings.AppCategory.CONTENT_URI,
                            LauncherSettings.AppCategory.PKG_NAME + "=?", new String[] {pkgName});
                }
                JSONObject categoryObject = result.getJSONObject(pkgName);
                String category = categoryObject.getString("category");
                if (isGameCategory(category)) {
                    category = resources.getString(R.string.system_game_folder);
                }
                ContentValues values = new ContentValues();
                values.put(LauncherSettings.AppCategory.PKG_NAME, pkgName);
                values.put(LauncherSettings.AppCategory.CATEGORY, category);
                mContext.getContentResolver().insert(LauncherSettings.AppCategory.CONTENT_URI,
                        values);
                isCategoryUpdated = true;
            }
            Utilities.mAddedPackages.clear();
        } catch (Exception e) {
            // TODO: handle exception
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private void getWhiteListFromNet(String url) {
        Log.d(TAG, "getWhiteListFromNet, url=" + url);
        try {
            JSONObject httpResult = getHttpResult(url);
            if (httpResult == null) {
                return;
            }
            //JSONObject result = httpResult.getJSONObject(RESULT_DATA);
            JSONArray apkArray = httpResult.getJSONArray(RESULT_DATA);
            mWhitList.clear();
            for (int i = 0; i < apkArray.length(); i++) {
                JSONObject detail = apkArray.getJSONObject(i);
                String packageName = detail.getString("package");
                String titleName = detail.getString("name");
                if (!TextUtils.isEmpty(packageName)) {
                    mWhitList.add(packageName);
                }
            }
            doSaveWhiteList();
        } catch (Exception e) {
            // TODO: handle exception
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private void doSaveWhiteList() {
        String fileName = "whiteList";
        FileOutputStream outputStream = null;
        BufferedOutputStream bufferedOutputStream = null;
        DataOutputStream dataOutputStream = null;
        try {
            outputStream = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
            bufferedOutputStream = new BufferedOutputStream(outputStream);
            dataOutputStream = new DataOutputStream(bufferedOutputStream);
            for (int i = 0; i < mWhitList.size(); i++) {
                dataOutputStream.writeBytes(mWhitList.get(i) + "\n");
            }
        } catch (FileNotFoundException e) {
            // TODO: handle exception
            e.printStackTrace();
        } catch (IOException e) {
            // TODO: handle exception
            e.printStackTrace();
        } finally {
            try {
                dataOutputStream.close();
                bufferedOutputStream.close();
                outputStream.close();
            } catch (Exception e2) {
                // TODO: handle exception
                e2.printStackTrace();
            }
        }
    }

    private String sendHttpRequest(String httpUrl) {
        Log.d(TAG, "sendHttpRequest, httpUrl=" + httpUrl);
        BufferedReader input = null;
        HttpURLConnection con = null;
        try {
            URL url = new URL(httpUrl);
            try {
                con = (HttpURLConnection) url.openConnection();
                Log.d(TAG, "User-Agent="+LewaUtils.getUserAgent(mContext));
                con.setRequestProperty("User-Agent", LewaUtils.getUserAgent(mContext));
                con.setConnectTimeout(5000);
                input = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String s;
                while ((s = input.readLine()) != null) {
                    sb.append(s).append("\n");
                }
                return sb.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (con != null) {
                con.disconnect();
            }
        }
        return null;
    }

    private JSONObject getHttpResult(String httpUrl) {
        JSONObject all = null;
        try {
            String resultStr = null;
            for (int retry = 0; retry < REQUEST_RETRY_CNT; retry++) {
                resultStr = sendHttpRequest(httpUrl);
                if (!TextUtils.isEmpty(resultStr)) {
                    break;
                }
            }
            if (TextUtils.isEmpty(resultStr)) {
                return null;
            }
            all = new JSONObject(resultStr);
            int errorCode = all.getInt("code");
            String msgCode = all.getString("message");
            if (errorCode != 0 || !"OK".equalsIgnoreCase(msgCode)) {
                return null;
            }
        } catch (Exception e) {
            // TODO: handle exception
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return all;
    }

    private void parseJSONObject(JSONObject result) {
        Log.d(TAG, "parseJSONObject");
        try {
            JSONObject selfDefData = result.getJSONObject(SELF_DEFINE_DATA);
            JSONObject data = result.getJSONObject(APK_DETAILS_DATA);
            JSONArray ids = selfDefData.names();
            for (int i = 0; i < ids.length(); i++) {
                String keyId = (String) ids.get(i);
                JSONObject single = selfDefData.getJSONObject(keyId);
                String categoryName = single.getString("name");
                String url = single.getString("interface");
                String description = single.getString("description");
                if (description != null && description.equals("updateWhiteList")) {
                    SharedPreferences sp = PreferencesProvider.getSharedPreferences(mContext);
                    sp.edit().putBoolean(FIRST_GET_WHITE_LIST, true).commit();
                }
                long folderId = getFolderNameMaps(categoryName, keyId, url);
                if (!data.has(keyId)) {
                    continue;
                }
                FolderInfo folderInfo = LauncherModel.sBgFolders.get(folderId);
                if (folderInfo != null) {
                    folderInfo.clearRecommendApp();
                    mContext.getContentResolver().delete(LauncherSettings.RecommendApps.CONTENT_URI,
                            "container=?", new String[] { String.valueOf(folderInfo.id)});
                }
                JSONArray apkArr = data.getJSONArray(keyId);
                Log.e(TAG, "get " + apkArr.length() + " apk from server"+",keyId="+keyId);
                for (int j = 0; j < apkArr.length(); j++) {
                    JSONObject apkDetail = apkArr.getJSONObject(j);
                    doSaveAppRecommend(apkDetail, folderInfo, categoryName, keyId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseJSONObject error:" + e.toString());
            e.printStackTrace();
        }
    }

    private void doSaveAppRecommend(JSONObject data, FolderInfo folderInfo, String category, String categoryId) {
        try {
            String packageName = data.getString("package");
            if (packageName == null
                    || Utilities.isAppAlreadyInstalled(mContext, packageName)) {
                Log.e(TAG, "This APP already installed, ignored! packageName = " + packageName+",title="+data.getString("name"));
                return;
            }
            String title = data.getString("name");
            String iconUrl = data.getString("icon");
            String downloadUrl = data.getString("url");
            long container = -2;
            if (folderInfo != null) {
                ShortcutInfo info = new ShortcutInfo();
                info.title = title;
                info.container = folderInfo.id;
                info.recommendPkgName = packageName;
                info.iconUrl = iconUrl;
                info.downloadUrl = downloadUrl;
                folderInfo.addRecommendApp(info);
                container = folderInfo.id;
            }
            ContentValues values = new ContentValues();
            values.put(LauncherSettings.Favorites.CONTAINER, container);
            values.put(LauncherSettings.Favorites.TITLE, title);
            values.put(LauncherSettings.RecommendApps.ICON_URL, iconUrl);
            values.put(LauncherSettings.RecommendApps.DOWNLOAD_URL, downloadUrl);
            values.put(LauncherSettings.RecommendApps.PKG_NAME, packageName);
            values.put(LauncherSettings.RecommendApps.CATEGORY, category);
            mContext.getContentResolver().insert(LauncherSettings.RecommendApps.CONTENT_URI, values);
            isRecommendUpdated = true;
        } catch (Exception e) {
            // TODO: handle exception
            Log.d(TAG, "doSaveAppRecommend error:"+e.toString());
            e.printStackTrace();
        }
    }

    private void parseJSONArray(JSONArray result) {
        Log.d(TAG, "parseJSONArray");
        try {
            FolderInfo folderInfo = LauncherModel.getFolderByCategoryIdInMap(mContext,
                    appCategoryUpdateId);
            if (folderInfo != null) {
                folderInfo.recommendApps.clear();
            }
            mContext.getContentResolver().delete(LauncherSettings.RecommendApps.CONTENT_URI,
                    "container=?", new String[] { String.valueOf(folderInfo.id)});
            String category = getCategoryByIdInMap(appCategoryUpdateId);
            for (int i = 0; i < result.length(); i++) {
                JSONObject apkDetail = result.getJSONObject(i);
                String packageName = apkDetail.getString("package");
                if (packageName == null || Utilities.isAppAlreadyInstalled(mContext, packageName)) {
                    Log.e(TAG, "This APP already installed, ignored! packageName = " + packageName);
                    continue;
                }
                String title = apkDetail.getString("name");
                String iconUrl = apkDetail.getString("icon");
                String downloadUrl = apkDetail.getString("url");
                Log.d(TAG, "getRecommendListFromNet:" + i + ",title=" + title + ",packageName="
                        + packageName);

                if (folderInfo != null) {
                    ShortcutInfo info = new ShortcutInfo();
                    info.title = title;
                    info.container = folderInfo.id;
                    info.recommendPkgName = packageName;
                    info.iconUrl = iconUrl;
                    info.downloadUrl = downloadUrl;
                    folderInfo.addRecommendApp(info);
                    Log.d(TAG, "-folderInfo,recommendApp size="+folderInfo.recommendApps.size());
                }
                ContentValues values = new ContentValues();
                values.put(LauncherSettings.Favorites.CONTAINER, folderInfo.id);
                values.put(LauncherSettings.Favorites.TITLE, title);
                values.put(LauncherSettings.RecommendApps.ICON_URL, iconUrl);
                values.put(LauncherSettings.RecommendApps.DOWNLOAD_URL, downloadUrl);
                values.put(LauncherSettings.RecommendApps.PKG_NAME, packageName);
                values.put(LauncherSettings.RecommendApps.CATEGORY, category);
                mContext.getContentResolver().insert(LauncherSettings.RecommendApps.CONTENT_URI,
                        values);
            }

        } catch (Exception e) {
            // TODO: handle exception
            Log.e(TAG, "parseJSONArray error:"+e.toString());
            e.printStackTrace();
        }

    }

    private String getCategoryByIdInMap(String appCategoryUpdateId) {
        Cursor c = null;
        try {
            Log.d(TAG, "getCategoryByIdInMap,appCategoryUpdateId="+appCategoryUpdateId);
            c = mContext.getContentResolver().query(LauncherSettings.CategoryMap.CONTENT_URI,
                    new String[] { LauncherSettings.CategoryMap.CATEGORY },
                    "categoryId=?", new String[] {String.valueOf(appCategoryUpdateId)}, null);
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

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub

        int networkType = Utilities.getNetworkType(mContext);

        if (isLauncherFinishLoading
                && (networkType == ConnectivityManager.TYPE_MOBILE || networkType == ConnectivityManager.TYPE_WIFI)) {
            startUpdater(true, null, true, null, false);
        }
    }

    public void getApkUrl(final ShortcutInfo shortcutInfo) {
        sWorker.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                String real = getTransformResponse(HttpUtils.httpGet(mContext, shortcutInfo.downloadUrl));
                shortcutInfo.transformUrl = real;
                mCallbacks.postTransformUrl(shortcutInfo);
            }

        });
    }

    private String getTransformResponse(HttpResponse httpResponse) {
        String s = httpResponse.response;
        if (s == null || s.length() == 0) {
        	Log.d(TAG,"getTransformResponse,HttpResponse result is null");
            return null;
        }

        try {
            JSONObject js = new JSONObject(s);
            String result = js.getString("result");
            Log.d(TAG, "getTransformResponse,result=" + result);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            Log.e(TAG, "new JSONObject from String error in getAppApkUrl. Error : " + e);
            return null;
        }
        return null;
    }

    public interface AppRecommendCallbacks {
        public void bindAppRecommendInfo();

        public void postTransformUrl(ShortcutInfo shortcutInfo);

        public void updateCurrentAppRecommend();
    }
    //lqwang - pr962182 - add begin
    public interface SortAppsListener{
        void sortApps();
    }
   //lqwang - pr962182 - add end
}
