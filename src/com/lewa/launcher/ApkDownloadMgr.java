
package com.lewa.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.app.DownloadManager;
import android.app.LewaDownloadManager;
import android.app.LewaDownloadManager.Request;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.lewa.launcher.R;
import com.lewa.launcher.Folder;
import com.lewa.launcher.ShortcutInfo;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.reflection.Advanceable;
import com.lewa.reflection.ReflUtils;
import com.lewa.toolbox.EditModeUtils;

import android.provider.Downloads;
public class ApkDownloadMgr {
    private static final String TAG = "ApkDownloadMgr" ;
    private Context mContext;
    private LewaDownloadManager dm;
    private DownloadsChangeObserver downloadObserver;
    private NotificationManager notiManager;
    private Handler handler = new Handler();
    private static Map<Long, View> views;
    private static final String SAVE_PATH = Environment.getExternalStorageDirectory()
            + "/LEWA/AppRecommend/Apps";
    private long lastUpdateTime = 0;
    private ArrayList<Long> failedDownloadId = new ArrayList<Long>();
    //lqwang - PR63468 - modify begin
    private Map<Long,String> resumeDownloading = Collections.synchronizedMap(new HashMap<Long, String>());
    private Map<Long,String> resumeDownloaded = Collections.synchronizedMap(new HashMap<Long, String>());
    //lqwang - PR63468 - modify end
    public ApkDownloadMgr(Context context) {
        super();

        mContext = context;
        if (views == null) {
            views = Collections.synchronizedMap(new HashMap<Long, View>());
        }
        dm = LewaDownloadManager.getInstance(mContext.getContentResolver(), mContext.getPackageName());
        notiManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        downloadObserver = new DownloadsChangeObserver(handler, context);
        context.getContentResolver().registerContentObserver(LewaDownloadManager.CONTENT_URI, true,
                downloadObserver);

        IntentFilter filter = new IntentFilter();
        filter.addAction(LewaDownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(successReceiver, filter);
    }

    public boolean checkIsDownloading(View v) {
        if (views == null) {
            return false;
        }
        Set<Entry<Long, View>> sets = views.entrySet();
        for (Entry<Long, View> entry : sets) {
            ShortcutInfo info = (ShortcutInfo) v.getTag();
            ShortcutInfo entryInfo = (ShortcutInfo) entry.getValue().getTag();
            if (info.equals(entryInfo)) {
                long key = entry.getKey();
                views.remove(key);
                views.put(key, v);
                return true;
            }
        }
        return false;
    }
    //lqwang - PR63468 - modify begin
    public void saveDownloading(){
        StringBuilder builder = new StringBuilder();//save as id:pkgName | id : pkgName
        if(views != null){
            Set<Entry<Long,View>> entries = views.entrySet();
            for(Entry<Long,View> entry : entries){
                View v = entry.getValue();
                ShortcutInfo info = (ShortcutInfo) v.getTag();
                long downloadId = entry.getKey();
                if(info != null && info.recommendPkgName != null)
                    builder.append(downloadId).append(":").append(info.recommendPkgName).append("|");
            }
        }
        for(Entry<Long,String> entry : resumeDownloading.entrySet()){
            builder.append(entry.getKey()).append(":").append(entry.getValue()).append("|");
        }

        for(Entry<Long,String> entry : resumeDownloaded.entrySet()){
            builder.append(entry.getKey()).append(":").append(entry.getValue()).append("|");
        }

        PreferencesProvider.putStringValue(mContext, Constants.RECOMMEND_DOWNLOADING_KEY,builder.toString());
        EditModeUtils.logE(TAG, "saveDownloading: "+builder.toString());
    }

    public void resumeDownloading(){
        try {
            String downString = PreferencesProvider.getStringValue(mContext,Constants.RECOMMEND_DOWNLOADING_KEY,"");
            EditModeUtils.logE(TAG,"resume string: "+downString);
            String[] downInfos = downString.split("\\|");
            EditModeUtils.logE(TAG,"downInfos length : "+downInfos.length);
            for(int i = 0 ; i < downInfos.length ; i++){
                String infoString = downInfos[i];
                final String[] info = infoString.split(":");
                if(info.length >= 2){
                    if(TextUtils.isEmpty(info[1])) continue;
                    EditModeUtils.logE(TAG,"downloadId: "+info[0]+"pkgname: "+info[1]);
                    Cursor c = mContext.getApplicationContext().getContentResolver().query(Downloads.Impl.CONTENT_URI
                            , new String[]{Downloads.Impl.COLUMN_STATUS}
                            , Downloads.Impl._ID +" = ?", new String[]{info[0]}, null);
                    if(c != null && c.moveToFirst()){
                        EditModeUtils.logE(TAG,"download status : "+c.getInt(0));
                        int status = c.getInt(0);
                        if(status < Downloads.Impl.STATUS_SUCCESS)//not downloaded
                        {
                            resumeDownloading.put(Long.parseLong(info[0]),info[1]);
                        }
                        else if(status == Downloads.Impl.STATUS_SUCCESS)//already downloaded
                        {   final long downId = Long.parseLong(info[0]);
                            resumeDownloaded.put(downId,info[1]);
                            new Thread(){
                                @Override
                                public void run() {
                                    List<String> paths = dm.getDownloadFilePath(downId);
                                    if (paths != null && paths.size() > 0) {
                                        boolean success = Utilities.install(mContext, paths.get(0), info[1]);
                                        if(success){
                                            resumeDownloaded.remove(downId);
                                        }
                                    }
                                }
                            }.start();
                        }
                    }
                    if(c != null)c.close();
                }
            }
            PreferencesProvider.putStringValue(mContext,Constants.RECOMMEND_DOWNLOADING_KEY,"");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * to check when launcher resume after killed,whether apk is downloaded
     * @return
     */
    public boolean isResumeDownloaded(String recommendPkgName){
        return resumeDownloaded.size() > 0 && recommendPkgName != null && resumeDownloaded.containsValue(recommendPkgName);
    }

    public boolean isResumeDownloading(View v){
        ShortcutInfo info = (ShortcutInfo) v.getTag();
        boolean downloading = false;
        long key = -1;
        if(info != null && info.recommendPkgName != null){
            for(Entry<Long,String> entry : resumeDownloading.entrySet()){
                if(info.recommendPkgName.equals(entry.getValue())){
                    downloading = true;
                    EditModeUtils.logE(TAG,info.recommendPkgName+" is resume downloading");
                    if(views != null){
                        key = entry.getKey();
                        views.remove(entry.getKey());
                        views.put(entry.getKey(),v);
                    }
                    break;
                }
            }
        }
        if(key != -1)
            resumeDownloading.remove(key);
        return downloading;
    }
    //lqwang - PR63468 - modify end

    public void downloadApk(View v) {
        if (views != null /*&& views.containsValue(v)*/) {
            Set<Entry<Long, View>> sets = views.entrySet();
            ShortcutInfo info = (ShortcutInfo) v.getTag();
            for (Entry<Long, View> entry : sets) {
                ShortcutInfo entryInfo = (ShortcutInfo) entry.getValue().getTag();
                if (info.equals(entryInfo)) {
                    handleUnnormalStatus(entry.getKey());
                    showToast(mContext.getString(R.string.download_start_hint, info.title));
                    return;
                }
            }
        }

        ShortcutInfo info = (ShortcutInfo) v.getTag();
        String url = getDownloadUrl(info);
        if (url == null) {
            return;
        }

        LewaDownloadManager.Request request = new Request(Uri.parse(url));
        File file = Environment.getExternalStoragePublicDirectory(SAVE_PATH);
        if (!file.exists() || !file.isDirectory()) {
            file.mkdirs();
        }
        try {
            request.setDestinationInExternalPublicDir("/LEWA/AppRecommend/Apps", info.title
                    + ".apk");
        } catch (Exception e) {
            return;
        }
        request.setNotiExtras(info.title);
        request.setMimeType("application/vnd.android.package-archive");
        request.setShowRunningNotification(true);
        request.setNotificationVisibility(LewaDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        long downloadId = -1;
        try {
            downloadId = dm.enqueue(request);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (downloadId == -1) {
            showToast(mContext.getString(R.string.download_failed_hint, info.title));
        } else {
            //showToast(mContext.getString(R.string.download_start_hint, info.title));
            views.put(downloadId, v);
        }
    }

    // Add for bug#56007 by Fan.Yang, check is http connection or not
    private String getDownloadUrl(ShortcutInfo info) {
        if (info == null || (info.transformUrl == null && info.downloadUrl == null)) {
            return null;
        }

        String url = info.transformUrl == null ? info.downloadUrl : info.transformUrl;
        if (url == null || !url.startsWith("http://")) {
            return null;
        }
        return url;
    }

    private void showToast(String msg) {
        ((Launcher) mContext).makeToast(msg);
        //Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    public void release() {
        mContext.unregisterReceiver(successReceiver);
        mContext.getContentResolver().unregisterContentObserver(downloadObserver);
    }

    private class DownloadsChangeObserver extends ContentObserver {
        private Context context;

        public DownloadsChangeObserver(Handler handler, Context context) {
            super(handler);
            this.context = context;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (views != null && views.size() > 0) {
                Set<Entry<Long, View>> sets = views.entrySet();
                failedDownloadId.clear();
                synchronized (views) {
                    for (Entry<Long, View> entry : sets) {
                        updateStatus(entry.getKey());
                    }
                }

                for(int i = 0, size = failedDownloadId.size(); i < size; i++){
                    views.remove(failedDownloadId.get(i));
                }
            }
        }
    }

    private void updateStatus(long downloadId) {
        View v = views.get(downloadId);
        ProgressBar bar = (ProgressBar) v.findViewById(R.id.download_progress);
        ImageView downloadIcon = (ImageView) v.findViewById(R.id.download_mark);
        int status = dm.getStatusById(downloadId);
        if (System.currentTimeMillis() - lastUpdateTime < 200
                && status != LewaDownloadManager.STATUS_FAILED) {
            return;
        }
        switch (status) {
            case LewaDownloadManager.STATUS_PENDING:
                v.setEnabled(false);
                downloadIcon.setVisibility(View.INVISIBLE);
                bar.setVisibility(View.VISIBLE);
                break;
            case LewaDownloadManager.STATUS_PAUSED:
                v.setEnabled(true);
                bar.setVisibility(View.VISIBLE);
                break;
            case LewaDownloadManager.STATUS_FAILED:
                v.setEnabled(true);
                bar.setVisibility(View.INVISIBLE);
                downloadIcon.setVisibility(View.VISIBLE);
                long failedReason = dm.getErrorCode(status);
                if (failedReason == LewaDownloadManager.ERROR_INSUFFICIENT_SPACE) {
                    showToast(mContext.getString(R.string.download_failed_nospace));
                } else {
                    showToast(mContext.getString(R.string.download_failed_other));
                }
                failedDownloadId.add(downloadId);
                break;
            case LewaDownloadManager.STATUS_RUNNING:
                v.setEnabled(false);
                bar.setVisibility(View.VISIBLE);
                int[] currentByte = dm.getDownloadBytes(downloadId);
                if (currentByte[1] != 0) {
                    int progress = (int) (100 * (long)currentByte[0] / currentByte[1]);//lqwang - pr983901 - modify
                    if (progress <= 100 && progress >= 0) {
                        bar.setProgress(progress);
                    }
                }
                break;
            case LewaDownloadManager.STATUS_SUCCESSFUL:
                v.setEnabled(false);
                bar.setVisibility(View.INVISIBLE);
                break;
            default:
                break;
        }
        lastUpdateTime = System.currentTimeMillis();
    }

    private void handleUnnormalStatus(long downloadId) {
        int status = dm.getStatusById(downloadId);
        // Log.i("zmq", "handleUnnormalStatus() .. status = "+status);
        switch (status) {
            case LewaDownloadManager.STATUS_PAUSED:
                dm.resumeDownload(downloadId);
                break;
            case LewaDownloadManager.STATUS_FAILED:
                dm.restartDownload(downloadId);
                break;
            default:
                break;
        }
    }

    private void makeNotification(long id, ShortcutInfo info, boolean success) {
        PackageManager packageManager = mContext.getPackageManager();
        Intent openintent = packageManager.getLaunchIntentForPackage(info.recommendPkgName);
        int hintTextId = success ? R.string.noti_install_success_text
                : R.string.noti_install_failed_text;
        Notification notif = new Notification(R.drawable.noti_icon, info.title
                + mContext.getString(hintTextId), (System.currentTimeMillis() + 1000));
        notif.flags = Notification.FLAG_AUTO_CANCEL;
        notif.setLatestEventInfo(mContext, info.title, info.title + mContext.getString(hintTextId),
                null);
        if (openintent != null) {
            notif.contentIntent = PendingIntent.getActivity(mContext, (int) id, openintent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }
        notiManager.notify((int) id, notif);
    }
//    
//    private void makeNotification(long id,String filePath, boolean success){
//
//        int hintTextId = success ? R.string.noti_install_success_text
//                : R.string.noti_install_failed_text;
//        Notification notif = new Notification(R.drawable.noti_icon, info.title
//                + mContext.getString(hintTextId), (System.currentTimeMillis() + 1000));
//        notif.flags = Notification.FLAG_AUTO_CANCEL;
//        notif.setLatestEventInfo(mContext, info.title, info.title + mContext.getString(hintTextId),
//                null);
//        if (intent != null) {
//            notif.contentIntent = PendingIntent.getActivity(mContext, (int) id, intent,
//                    PendingIntent.FLAG_UPDATE_CURRENT);
//        }
//        notiManager.notify((int) id, notif);
//    }

    private BroadcastReceiver successReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final long completeDownloadId = intent.getLongExtra(
                    LewaDownloadManager.EXTRA_DOWNLOAD_ID, -1);
            //lqwang - PR63468 - modify begin
            if(resumeDownloading.containsKey(completeDownloadId)){
                new Thread(){
                    @Override
                    public void run() {
                        List<String> paths = dm.getDownloadFilePath(completeDownloadId);
                        if (paths != null && paths.size() > 0) {
                            String pkgName = resumeDownloading.get(completeDownloadId);
                            boolean success = Utilities.install(mContext, paths.get(0), pkgName);
                            resumeDownloading.remove(completeDownloadId);
                            if(!success){
                                resumeDownloaded.put(completeDownloadId,pkgName);
                            }
                        }
                    }
                }.start();
            }else{
                //lqwang - PR63468 - modify end
                final View v = views.get(completeDownloadId);
                if (v == null) {
                    return;
                }
                v.setEnabled(false);
                v.findViewById(R.id.download_progress).setVisibility(View.INVISIBLE);
                final ShortcutInfo info = (ShortcutInfo) v.getTag();
                if (info != null) {
                    showToast(mContext.getString(R.string.download_end_hint, info.title));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List<String> paths = dm.getDownloadFilePath(completeDownloadId);
                            if (paths != null && paths.size() > 0) {
                                boolean success = Utilities.install(mContext, paths.get(0), info.recommendPkgName);
                                if (!success) {
                                    Utilities.mDownloadedPackages.remove(info.recommendPkgName);
                                }
                                handler.sendEmptyMessage(success ? 0 : 1);
                                makeNotification(completeDownloadId, info, success);
                                /// #63500 jietan 2014.11.18 delete
                                //dm.removeAndDeleteFile(completeDownloadId);
                            }
                        }
                    }).start();
                }
                views.remove(completeDownloadId);
            }
        }
    };
}
