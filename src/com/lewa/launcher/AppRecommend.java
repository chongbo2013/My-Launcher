package com.lewa.launcher;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import lewa.content.res.IconCustomizer;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;
import com.lewa.launcher.AppRecommendHelper.AppRecommendCallbacks;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.toolbox.BitmapLruCache;
import com.lewa.toolbox.MyVolley;

public class AppRecommend extends LinearLayout implements OnClickListener, AppRecommendCallbacks {
    private static final String TAG = "AppRecommend";
    public static final int MAX_RECOMMEND_CNT = 4;
    
    private Context mContext;
    private Launcher mLauncher;
    private FolderInfo openFolderInfo;
    private AppRecommendHelper mRecommendHelper;
    private BitmapLruCache mBitmapLruCache;
    private static ApkDownloadMgr mDownloadMgr;
    
    private ImageView refreshBtn, moreBtn;
    private LinearLayout recomContent;
    int recommendIndex;

    public AppRecommend(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlwaysDrawnWithCacheEnabled(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        refreshBtn = (ImageView) findViewById(R.id.recommend_refresh_btn);
        refreshBtn.setOnClickListener(this);
        moreBtn = (ImageView) findViewById(R.id.recommend_more_btn);
        moreBtn.setOnClickListener(this);
        recomContent = (LinearLayout)findViewById(R.id.recommend_content);
    }

    public void initialize(LauncherApplication app, Launcher launcher) {
        mRecommendHelper = app.getRecommendHelper();
        mRecommendHelper.initialize(this);
        mBitmapLruCache = app.getBitmapCache();
        if (mDownloadMgr == null) {
            mDownloadMgr = new ApkDownloadMgr(launcher);
            //lqwang - PR63468 - modify begin
            mDownloadMgr.resumeDownloading();
            //lqwang - PR63468 - modify end
        }
        mContext = app.getApplicationContext();
        mLauncher = launcher;
    }

    public void clearRecomContent(){
        for(int i = 0 ; i < recomContent.getChildCount() ; i++){
            ImageView iv = (ImageView) recomContent.getChildAt(i).findViewById(R.id.r_icon);
            iv.setImageBitmap(null);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.equals(refreshBtn)) {
            openFolderInfo = mLauncher.getWorkspace().getOpenFolder().getInfo();
            Log.d(TAG, "onClick refresh,isNeedUpdateFromNet="+isNeedUpdateFromNet());
            getUrl();
            if (isNeedUpdateFromNet()) {
                //String idName = getCurrentCategoryID();
                if (currentCategoryId != null) {
                    mRecommendHelper.startUpdater(true, currentCategoryId, false, null, true);
                }
            } else {
                refreshUI(openFolderInfo);
            }
        } else if (v.equals(moreBtn)) {
            openFolderInfo = mLauncher.getWorkspace().getOpenFolder().getInfo();
            getUrl();
            String gameString = mContext.getResources().getString(R.string.server_game_category);
            if (gameString.equals(currentCategory)) {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setClassName("com.lewa.gamecenter", "com.lewa.gamecenter.MainActivity");
                Utilities.startActivitySafely(mContext,intent);//lqwang - pr962172 - modify
            } else if (currentUrl != null && currentCategory != null) {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                intent.setClassName("com.lewa.appstore", "com.lewa.appstore.ListActivity");
                intent.setClassName("com.tclmarket", "com.tclmarket.activity.MainActivity");
                intent.putExtra("data_url", currentUrl);
                intent.putExtra("page_title", currentCategory);
                intent.putExtra("source", Constants.ENTER_FROM_LAUNCHER);
                Utilities.startActivitySafely(mContext,intent);//lqwang - pr962172 - modify
            }
        } else {
            promptForDownload(v);
        }
    }

    private String currentCategory,currentUrl,currentCategoryId;
    private void getUrl() {
        if (openFolderInfo == null) {
            return;
        }
        long folderId = openFolderInfo.id;
        Cursor cursor = null;
        currentCategory = null;
        currentUrl = null;
        currentCategoryId = null;
        try {
            cursor = mContext.getContentResolver().query(LauncherSettings.CategoryMap.CONTENT_URI,
                    new String[] {"interface","category","categoryId"}, "folderId=?", new String[] {
                        String.valueOf(folderId)
                    }, null);
            if (cursor.moveToFirst()) {
                currentUrl = cursor.getString(0);
                currentCategory = cursor.getString(1);
                currentCategoryId = cursor.getString(2);
            }
        } catch (Exception e) {
            // TODO: handle exception
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean isNeedUpdateFromNet() {

        int size = openFolderInfo.recommendApps.size();
        int networkType = Utilities.getNetworkType(mContext);
        if (networkType == -1) {
            mLauncher.makeToast(R.string.no_available_network);
            // showToast(mContext.getResources().getString(R.string.no_available_network));
            return false;
        }
        if (size - recommendIndex > 0) {
            return false;
        }
        showUpdateProgress();
        return true;
    }

    private void showToast(String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    private void showUpdateProgress() {
        for (int i = 0; i < recomContent.getChildCount(); i++) {
            View item = recomContent.getChildAt(i);
            if (item != null) {
                ProgressBar bar = (ProgressBar) item.findViewById(R.id.refresh_progress);
                bar.setVisibility(View.VISIBLE);
            }
        }
    }

    private final int REFRESH_MSG = 1; 
    private final int DOWNLOAD_MSG = 2;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case REFRESH_MSG:
                    Folder openFolder = mLauncher.getWorkspace().getOpenFolder();
                    if (openFolder != null) {
                        openFolderInfo = openFolder.getInfo();
                        recommendIndex = 0;
                        refreshUI(openFolderInfo);
                    }
                    break;
                case DOWNLOAD_MSG:
                	Log.d(TAG,"handle download msg");
                    View view = getView((ShortcutInfo) msg.obj);
                    if (view != null) {
                        mDownloadMgr.downloadApk(view);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private View getView(ShortcutInfo info) {
        if (recomContent == null) {
            Log.e(TAG, "recommendContent is null");
        }
        int childCount = recomContent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = recomContent.getChildAt(i);
            ShortcutInfo shortcut = (ShortcutInfo) child.getTag();
            if (shortcut.equals(info)) {
                return child;
            }
        }
        return null;
    }

    public void refreshUI(FolderInfo folderInfo) {
        refreshUI(folderInfo, true);
    }

    public void refreshUI(FolderInfo folderInfo, boolean check) {
        if (folderInfo == null) {
            return;
        }
        if (PreferencesProvider.isRecommendOn(mContext)) {
            Log.d(TAG, "refreshUI,recommendIndex=" + recommendIndex);
            for (int i = folderInfo.recommendApps.size() - 1; i >= 0; i--) {
                ShortcutInfo rInfo = folderInfo.recommendApps.get(i);
                    //lqwang - PR63468 - modify begin
                if (Utilities.isAppAlreadyInstalled(mContext, rInfo.recommendPkgName) || mDownloadMgr.isResumeDownloaded(rInfo.recommendPkgName)) {
                    //lqwang - PR63468 - modify end
                    folderInfo.recommendApps.remove(rInfo);
                    ContentValues values = new ContentValues();
                    values.put(LauncherSettings.RecommendApps.INSTALLED, 1);
                    mContext.getContentResolver().update(
                            LauncherSettings.RecommendApps.CONTENT_URI, values, "packageName=?",
                            new String[] { rInfo.recommendPkgName });
                }
            }
            int appSize = folderInfo.recommendApps.size();
            if (appSize == 0) {
                return;
            }
            recomContent.removeAllViews();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT, 1.0f);

            for (int i = 0; i < MAX_RECOMMEND_CNT; i++) {
                AppRecommendItem v = (AppRecommendItem) LayoutInflater.from(mContext)
                        .inflate(R.layout.recommend_item, null);
                recomContent.addView(v, lp);
                if (i > appSize - 1 || recommendIndex > appSize - 1) {
                    v.setVisibility(View.INVISIBLE);
                    continue;
                }
                final ShortcutInfo rInfo = folderInfo.recommendApps.get(recommendIndex);
                final ImageView rIcon = (ImageView) v.findViewById(R.id.r_icon);
                final TextView rTitle = (TextView) v.findViewById(R.id.r_title);
                v.setTag(rInfo);
                v.setOnClickListener(this);
                rTitle.setText(rInfo.title);
                Bitmap cache = mBitmapLruCache.get(rInfo.iconUrl);
                if (cache != null) {
                    rIcon.setImageBitmap(cache);
                } else {
                    MyVolley.getImageLoader().get(rInfo.iconUrl, new ImageListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Drawable dr = getResources().getDrawable(R.drawable.recommend_error);
                            rIcon.setImageDrawable(IconCustomizer.generateIconDrawable(dr));
                        }

                        @Override
                        public void onResponse(ImageContainer response, boolean isImmediate) {
                            Bitmap orignal = response.getBitmap();
                            Bitmap scaleBitmap = getScaleBitmap(orignal);
                            if (scaleBitmap != null) {
                                BitmapDrawable customDrawable = IconCustomizer.generateIconDrawable(
                                        new BitmapDrawable(getResources(), scaleBitmap));
                                mBitmapLruCache.put(rInfo.iconUrl, customDrawable.getBitmap());
                                rIcon.setImageDrawable(customDrawable);
                            } else {
                                Drawable dr = getResources()
                                        .getDrawable(R.drawable.recommend_error);
                                rIcon.setImageDrawable(IconCustomizer.generateIconDrawable(dr));
                            }
                        }
                    });
                }
                recommendIndex++;
                   //lqwang - PR63468 - modify begin
                if (check && (mDownloadMgr.checkIsDownloading(v) || mDownloadMgr.isResumeDownloading(v))) {
                    //lqwang - PR63468 - modify end
                    beginDownload(v);
                }
            }
        }
    }

    private Bitmap getScaleBitmap(Bitmap orignal) {
        if (orignal == null || orignal.getWidth() <= 0 || orignal.getHeight() <= 0) {
            return null;
        }
        Matrix matrix = new Matrix();
        float scaleX = (float) IconCustomizer.sCustomizedIconWidth / (float) orignal.getWidth();
        float scaleY = (float) IconCustomizer.sCustomizedIconHeight / (float) orignal.getHeight();
        matrix.postScale(scaleX, scaleY);
        Bitmap resizeBmp = Bitmap.createBitmap(orignal, 0, 0, orignal.getWidth(),
                orignal.getHeight(), matrix, true);
        return resizeBmp;
    }

    private void promptForDownload(final View v) {
        int networkType = Utilities.getNetworkType(mContext);
        if (networkType == ConnectivityManager.TYPE_MOBILE) {
            new AlertDialog.Builder(mLauncher)
                    .setTitle(R.string.download_prompt_title)
                    .setMessage(R.string.download_prompt_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //mDownloadMgr.downloadApk(v);
                            getTransformUrl(v);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            }).create().show();
        } else if (networkType == ConnectivityManager.TYPE_WIFI) {
            //mDownloadMgr.downloadApk(v);
            getTransformUrl(v);
        } else if (networkType == -1) {
            mLauncher.makeToast(R.string.no_available_network);
            // Toast.makeText(mContext, R.string.no_available_network, Toast.LENGTH_SHORT).show();
        }
    }

    private void getTransformUrl(View v) {
        ShortcutInfo shortcutInfo = (ShortcutInfo) v.getTag();
        mRecommendHelper.getApkUrl(shortcutInfo);
        beginDownload(v);
    }

    private void beginDownload(View view) {
        view.setEnabled(false);
        ProgressBar bar = (ProgressBar) view.findViewById(R.id.download_progress);
        ImageView downloadIcon = (ImageView) view.findViewById(R.id.download_mark);
        if (downloadIcon != null) {
            downloadIcon.setVisibility(View.INVISIBLE);
        }
        if (bar != null) {
            bar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void bindAppRecommendInfo() {
        // TODO Auto-generated method stub

    }

    @Override
    public void updateCurrentAppRecommend() {
        // TODO Auto-generated method stub
        mHandler.sendEmptyMessage(REFRESH_MSG);
    }

    @Override
    public void postTransformUrl(ShortcutInfo shortcutInfo) {
        // TODO Auto-generated method stub
        Message msg = mHandler.obtainMessage();
        msg.what = DOWNLOAD_MSG;
        msg.obj = shortcutInfo;
        mHandler.sendMessage(msg);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // TODO Auto-generated method stub
        // return super.onTouchEvent(event);
        return true;
    }
    //lqwang - PR63468 - modify begin
    public void saveDownloading(){
        if(mDownloadMgr != null){
            mDownloadMgr.saveDownloading();
        }
    }
   //lqwang - PR63468 - modify begin
}
