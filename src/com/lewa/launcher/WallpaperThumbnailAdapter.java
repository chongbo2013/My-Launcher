package com.lewa.launcher;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;
import com.lewa.launcher.bean.OnlineWallpaper;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.launcher.view.HorizontalListView;
import com.lewa.toolbox.EditModeUtils;
import com.lewa.toolbox.MyVolley;
import com.lewa.toolbox.SimpleLoadImageListener;

public class WallpaperThumbnailAdapter extends BaseAdapter {
    private static final String TAG = "WallpaperThumbnailAdapter";
    private List<File> mLocalFiles = new ArrayList<File>();
    private List<OnlineWallpaper> mOnlineWallpapers = new ArrayList<OnlineWallpaper>();
    private Context mContext;
    int previewX;
    int previewY;
    private String wallpaper_path = "";

    private int mCheckPos = -1;
    private int[] DEFAULT_WALLPAPERS ;
    private String[] DEFAULT_WALLPAPERS_NAME;
    private Bitmap defaultPaper;
    private int defalutPos = 0;
    private boolean isOnlyLocal = true;
    private Resources res;
    private int itemWidth;
    private int ANIM_OUT_DURATION = 800;
    private int ANIM_IN_DURATION = 150;
    private int ANIM_IN_DURATION_DELAY = 200;
    private Launcher mLauncher;
    private float startX;
    private boolean isAnimExcute = true;
    private HorizontalListView mHorizontalListView;
    private int lastAnimPos;
    private Bitmap wallpaper_local;
    private Bitmap wallpaper_online;
    public WallpaperThumbnailAdapter(Context context, List<File> files, HorizontalListView horizontalListView) {
        mLocalFiles = files;
        mContext = context;
        mLauncher = (Launcher) context;
        res = context.getResources();
        previewY = res.getDimensionPixelSize(R.dimen.edit_wallpaper_size);
        previewX = previewY;
        DEFAULT_WALLPAPERS_NAME = res.getStringArray(R.array.wallpapers);
        DEFAULT_WALLPAPERS = new int[DEFAULT_WALLPAPERS_NAME.length];
        final String packageName = res.getResourcePackageName(R.array.wallpapers);
        for (int i = 0; i < DEFAULT_WALLPAPERS_NAME.length; i++) {
            int resId = res.getIdentifier(DEFAULT_WALLPAPERS_NAME[i], "drawable", packageName);
            DEFAULT_WALLPAPERS[i] = resId;
        }
//        setWallpaperPath(getWallpaperName(defalutPos)); lqwang - pr985740 - modify
        defaultPaper = initDefaultBitmaps("wallpaper_default", R.drawable.wallpaper_default);
        wallpaper_local = initDefaultBitmaps("wallpaper_local", R.drawable.wallpaper_local);
        wallpaper_online = initDefaultBitmaps("wallpaper_online", R.drawable.wallpaper_online);
        itemWidth = mContext.getResources().getDimensionPixelSize(R.dimen.edit_item_wallpaper_width);
        startX = mLauncher.getEditModeLayer().screenWidth();
        mHorizontalListView = horizontalListView;
    }

    private Bitmap initDefaultBitmaps(String tag, int id) {
        String key = LauncherApplication.getCacheKey(tag, previewX, previewY);
        Bitmap cache = LauncherApplication.getCacheBitmap(key);
        if (cache != null) {
            return cache;
        } else {
            Bitmap b = EditModeUtils.parseBitmap(res, id, Config.RGB_565, previewX, previewY);
            Bitmap reflect = EditModeUtils.createReflectedBitmap(res, b, true);
            LauncherApplication.cacheBitmap(key, reflect);
            return reflect;
        }
    }

    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        int button_cnt = isOnlyLocal ? 2 : 1;
        return mOnlineWallpapers.size() + mLocalFiles.size() + DEFAULT_WALLPAPERS.length + button_cnt;//first is online key,and last is gallery key
    }

    @Override
    public Object getItem(int position) {
        // TODO Auto-generated method stub
        if (isOnlyLocal && position == 0 || position == getCount() - 1) {
            return null;
        }
        int onlineSize = mOnlineWallpapers.size();
        if (!isOnlyLocal && position < onlineSize) {
            return mOnlineWallpapers.get(position);
        } else if (isDefaultPos(position)) {
            return DEFAULT_WALLPAPERS[getLocalPos(position)];
        } else {
            return mLocalFiles.get(getLocalPos(position) - DEFAULT_WALLPAPERS.length);
        }
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // TODO Auto-generated method stub
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = View.inflate(mContext, R.layout.wallpaper_grid_item,
                    null);
            holder.thumb_nail = (ImageView) convertView
                    .findViewById(R.id.wallpaper_thumb);
            holder.thumb_checked = (ImageView) convertView
                    .findViewById(R.id.wallpaper_checked);
            holder.loading_iv = (ImageView) convertView
                    .findViewById(R.id.loading_iv);
            holder.wallpaper_tv = (TextView) convertView.findViewById(R.id.wallpaper_tv);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
            holder.thumb_nail.setTag("");
        }
        String path = null;
        boolean cacheHit = false;
        holder.wallpaper_tv.setVisibility(View.INVISIBLE);
        if (!isOnlyLocal && position < mOnlineWallpapers.size()) {
            OnlineWallpaper onlineWallpaper = mOnlineWallpapers.get(position);
            String origin_thumbnail = onlineWallpaper.getThumbnail();
            path = EditModeUtils.getOnlineWallpaperSavedPath(onlineWallpaper);
            if (path != null) {
                Bitmap cacheBitmap = LauncherApplication.getCacheBitmap(path);
                if (cacheBitmap != null) {
                    holder.thumb_nail.setImageBitmap(cacheBitmap);
                    cacheHit = true;
                    EditModeUtils.logE(TAG, "wallpaper reflect bitmap cache hit");
                }
            }
            if (!cacheHit) {
                if (origin_thumbnail != null) {
                    holder.thumb_nail.setImageBitmap(defaultPaper);
                    String sized_thumbnail = setUrlImageSize(origin_thumbnail);
                    holder.thumb_nail.setTag(path);
                    MyVolley.getImageLoader().get(sized_thumbnail, new SimpleImageListener(holder.thumb_nail, defaultPaper, path));
                } else {
                    holder.thumb_nail.setImageBitmap(defaultPaper);
                }
            }
            if(onlineWallpaper.isDownloading()) {
                EditModeUtils.startLoading(holder.loading_iv);
            }else{
                EditModeUtils.stopLoading(holder.loading_iv);
            }
        } else if (isOnlyLocal && position == 0 || position == getCount() - 1) {
            holder.thumb_nail.setImageBitmap(position == 0 ? wallpaper_online : wallpaper_local);
            holder.wallpaper_tv.setVisibility(View.VISIBLE);
            holder.wallpaper_tv.setText(position == 0 ? mContext.getString(com.lewa.launcher.R.string.wallpaper_online) : mContext.getString(com.lewa.launcher.R.string.wallpaper_local));
            holder.thumb_checked.setVisibility(View.GONE);
        } else {
            holder.thumb_nail.setImageBitmap(defaultPaper);
            if (isDefaultPos(position)) {
                path = getWallpaperName(position);
                int local_pos = getLocalPos(position);
                holder.thumb_nail.setTag(DEFAULT_WALLPAPERS_NAME[local_pos]);
                MyVolley.displayImage(mContext, DEFAULT_WALLPAPERS_NAME[local_pos],
                        DEFAULT_WALLPAPERS[local_pos], Config.RGB_565, previewX,
                        previewY, new SimpleLoadImageListener(
                                new WeakReference<ImageView>(holder.thumb_nail), DEFAULT_WALLPAPERS_NAME[local_pos]));
            } else {
                File file = mLocalFiles.get(getLocalPos(position) - DEFAULT_WALLPAPERS.length);
                path = file.getPath();
                holder.thumb_nail.setTag(path);
                MyVolley.displayImage(mContext, file.getAbsolutePath(), 0,
                        Config.RGB_565, previewX, previewY,
                        new SimpleLoadImageListener(new WeakReference<ImageView>(
                                holder.thumb_nail), path));
            }
        }
        boolean selected = wallpaper_path.equals(path);

        if (selected) {
            holder.thumb_checked.setVisibility(View.VISIBLE);
        } else {
            holder.thumb_checked.setVisibility(View.GONE);
        }
        if (!isOnlyLocal && isAnimExcute) {
            WeakReference<View> ref = new WeakReference<View>(convertView);
            if (position <= Constants.ONLINE_PAGESIZE - 1) {
                lastAnimPos = Math.min(lastAnimPos, position);
                float startX = -itemWidth * (position + 1);
                convertView.setX(startX);
                EditModeUtils.translateAnimate(View.X, ref, ANIM_IN_DURATION * (position + 1), (Constants.ONLINE_PAGESIZE - position - 1) * ANIM_IN_DURATION_DELAY, startX, position * itemWidth, new AnimatorListener(position), null);
            } else if (position > Constants.ONLINE_PAGESIZE - 1 && position <= Constants.ONLINE_PAGESIZE * 2 - 1) {
                EditModeUtils.translateAnimate(View.TRANSLATION_X, ref, ANIM_OUT_DURATION, 0, -Constants.ONLINE_PAGESIZE * itemWidth, 0, new AnimatorListener(position), null);
                lastAnimPos = Math.min(lastAnimPos, position);
            } else {
                isAnimExcute = false;
            }
        } else if (isAnimExcute) {
            if (position <= Constants.LOCAL_ANIM_COUNT) {
                lastAnimPos = Math.max(lastAnimPos, position);
                WeakReference<View> ref = new WeakReference<View>(convertView);
                convertView.setX(startX);
                EditModeUtils.translateAnimate(View.X, ref, Constants.ANIM_IN_DURATION * (Constants.LOCAL_ANIM_COUNT - position), position * Constants.ANIM_IN_DURATION_DELAY, startX, position * itemWidth, new AnimatorListener(position), null);
            } else {
                isAnimExcute = false;
            }
            if(isAnimExcute && position == getCount() - 1){
                isAnimExcute = false;
            }
        }
        return convertView;
    }


    public void setWallpaperPath(String defVal){
        wallpaper_path = Settings.System.getString(mContext.getContentResolver(),EditModeUtils.PREFERENCE_WALLPAPER_PATH);
        if(TextUtils.isEmpty(wallpaper_path)) wallpaper_path = defVal;
    }

    public void setChecked(int position) {
        mCheckPos = position;
        notifyDataSetChanged();
    }

    public static class ViewHolder {
        ImageView thumb_nail;
        ImageView thumb_checked;
        TextView wallpaper_tv;
        ImageView loading_iv;
    }

    private boolean isDefaultPos(int pos) {
        int local_pos = getLocalPos(pos);
        return local_pos >= 0 && local_pos < DEFAULT_WALLPAPERS.length;
    }

    public String getWallpaperName(int pos) {
        EditModeUtils.logE(TAG, "pos before: " + pos);
        int local_pos = getLocalPos(pos);
        EditModeUtils.logE(TAG, "pos after: " + local_pos);
        if (local_pos < DEFAULT_WALLPAPERS_NAME.length) {
            return DEFAULT_WALLPAPERS_NAME[local_pos];
        }
        return "";
    }

    public boolean isChecked(int pos) {
        return mCheckPos == pos;
    }

    public int defaultWallpaperSize() {
        return DEFAULT_WALLPAPERS.length;
    }

    public String getCacheKey(String path) {
        return LauncherApplication.getCacheKey(path, previewX, previewY);
    }

    public int getRealPos(int pos) {
        return isOnlyLocal && pos > 0 ? pos - 1 : pos;
    }

    public void setAnimExcute(boolean isAnimExcute) {
        this.isAnimExcute = isAnimExcute;
    }

    /*
         * get local files position
         */
    public int getLocalPos(int pos) {
        return getRealPos(pos) - mOnlineWallpapers.size();
    }

    public void setOnlyLocal(boolean isOnlyLocal) {
        this.isOnlyLocal = isOnlyLocal;
    }

    public boolean isOnlyLocal() {
        return isOnlyLocal;
    }

    public void addOnlineItems(List<OnlineWallpaper> lists) {
        for (OnlineWallpaper wallpaper : lists) {
            mOnlineWallpapers.add(0, wallpaper);
        }
        isAnimExcute = true;
        notifyDataSetChanged();
    }

    public List<OnlineWallpaper> getOnlineWallpapers() {
        return mOnlineWallpapers;
    }

    public String setUrlImageSize(String url) {
        String start = url.substring(0, url.lastIndexOf("/") + 1);
        String end = url.substring(url.lastIndexOf("/"), url.length());
        return start + "_" + previewX + "-" + previewY + end;
    }

    private class SimpleImageListener implements ImageListener {
        private ImageView mIv;
        private Bitmap mDefaultBg;
        private int mErrorRes;
        private String cacheKey;

        public SimpleImageListener(ImageView iv, Bitmap defautBg, String key) {
            mIv = iv;
            mDefaultBg = defautBg;
            cacheKey = key;
        }

        @Override
        public void onErrorResponse(VolleyError error) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onResponse(ImageContainer response, boolean isImmediate) {
            // TODO Auto-generated method stub
            if (mIv.getTag().equals(cacheKey)) {
                Bitmap bitmap = response.getBitmap();
                if (bitmap != null) {
                    Bitmap reflectBitmap = EditModeUtils.createReflectedBitmap(res, bitmap, false);
                    LauncherApplication.cacheBitmap(cacheKey, reflectBitmap);
                    mIv.setImageBitmap(reflectBitmap);
                } else if (mDefaultBg != null) {
                    mIv.setImageBitmap(mDefaultBg);
                }
            }
        }

    }

    private class AnimatorListener extends AnimatorListenerAdapter {
        private int position;

        public AnimatorListener(int pos) {
            position = pos;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            // TODO Auto-generated method stub
            EditModeUtils.logE(TAG, "isAnimExcute: " + isAnimExcute + " position: " + position + " lastAnimPos: " + lastAnimPos);
            if (!isAnimExcute && mHorizontalListView != null && position == lastAnimPos) {
                EditModeUtils.logE(TAG, "AnimatorListener set layout after animation");
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                mHorizontalListView.setLayoutParams(params);
                mLauncher.getEditModeLayer().setHwLayerEnabled(false);
                notifyDataSetChanged();
            }else if(position == lastAnimPos){
                isAnimExcute = false;
            }
        }
    }
}
