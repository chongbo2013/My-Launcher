package com.lewa.launcher;

import java.lang.ref.WeakReference;
import java.util.List;

import com.lewa.launcher.LoadPreviewTask.PREVIEWTYPE;
import com.lewa.launcher.bean.TransEffectItem;
import com.lewa.launcher.constant.Constants;
import com.lewa.toolbox.EditModeUtils;
import com.lewa.toolbox.IIoadImageListener;
import com.lewa.toolbox.MyVolley;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class EditGridAdapter extends BaseAdapter {
    private List<Object> mObjects;
    private Context mContext;
    private GADGETS mGadgets;
    private PackageManager mPackageManager;
    private VIEWTYPE mViewType;
    private Launcher mLauncher;
    private int mAppIconSize;
    private Resources res;
    private final float sWidgetPreviewIconPaddingPercentage = 0.20f;
    private static final float WIDGET_SCALE = 0.5f;
    private static final String TAG = "EditGridAdapter";
    private boolean isTempStorage;
    private int storagePadding;
    private int mCheckPos;
    private int columnWidth;
    private float startX;
    private boolean isAnimExcute = true;
    private int lastAnimPos;
    private EditModeLayer editModeLayer;
    private int totalHeight;
    private int[] viewHeight;
    private int maxAnimHeight;
    private int checkedSize;
    private int anim_checked_margin;

    public EditGridAdapter(Launcher launcher, List<Object> objects, GADGETS gadgets, VIEWTYPE viewType) {
        mContext = launcher;
        mLauncher = launcher;
        res = launcher.getResources();
        mObjects = objects;
        mGadgets = gadgets;
        mPackageManager = mContext.getPackageManager();
        mViewType = viewType;
        mAppIconSize = (int) (IconCache.getAppIconSize(res) * 1.14);
        storagePadding = res.getDimensionPixelSize(R.dimen.edit_storage_padding);
        columnWidth = res.getDimensionPixelSize(R.dimen.edit_anim_width);
        editModeLayer = mLauncher.getEditModeLayer();
        startX = editModeLayer.screenWidth();
        measureWidgetViewTotalHeight();
        checkedSize = res.getDimensionPixelSize(R.dimen.checked_size);
        anim_checked_margin = res.getDimensionPixelSize(R.dimen.anim_checked_margin);
        maxAnimHeight = editModeLayer.screenHeight() - mLauncher.getHotseat().getHeight();
    }

    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return mObjects.size();
    }

    @Override
    public Object getItem(int position) {
        // TODO Auto-generated method stub
        return mObjects.get(position);
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // TODO Auto-generated method stub
        final ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            if (mViewType == VIEWTYPE.GRID) {
                convertView = View.inflate(mContext, R.layout.edit_grid_item, null);
                holder.checked = (ImageView) convertView.findViewById(R.id.item_expand);
                android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(checkedSize, checkedSize);
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.bottomMargin = anim_checked_margin;
                holder.checked.setLayoutParams(params);
            } else {
                convertView = View.inflate(mContext, R.layout.edit_list_item, null);
                holder.iv_ll = (RelativeLayout) convertView.findViewById(R.id.item_iv_ll);
            }
            holder.iv = (ImageView) convertView.findViewById(R.id.item_iv);
            holder.tv = (TextView) convertView.findViewById(R.id.item_tv);
            convertView.setTag(R.id.widget_list_holder_tag, holder);
        } else {
            holder = (ViewHolder) convertView.getTag(R.id.widget_list_holder_tag);
        }
        Object object = mObjects.get(position);
        if (mGadgets == GADGETS.WIDGETSLIST) {
            PendingAddItemInfo createItemInfo = getCreateItemInfo(object, holder.tv);
            if (holder.iv_ll != null)
                holder.iv_ll.getLayoutParams().height = viewHeight[position];
            String tag = createItemInfo.getComponentName().getClassName();
            holder.iv.setImageBitmap(null);
            holder.iv.setTag(tag);

            convertView.setTag(R.id.widget_list_item_tag, createItemInfo);
            if (isTempStorage) {
                convertView.setPadding(storagePadding, 0, 0, storagePadding);
            } else {
                if (isAnimExcute && getItemY(position) < maxAnimHeight) {
                    convertView.setTranslationY(editModeLayer.screenHeight());
                    WeakReference<View> ref = new WeakReference<View>(convertView);
                    int duration = Constants.ANIM_IN_DURATION * (getCount() - position);
                    EditModeUtils.translateAnimate(View.TRANSLATION_Y, ref, duration > 500 ? 500 : duration , (position + 1) * Constants.ANIM_IN_DURATION_DELAY, editModeLayer.screenHeight(), 0, null, null);
                }
                if (position == getCount() - 1) {
                    isAnimExcute = false;
                }
            }
            MyVolley.displayPreview(mContext, PREVIEWTYPE.WIDGET, mViewType, null, createItemInfo, new GridImageLoadListener(new WeakReference<ImageView>(holder.iv), tag));
        } else if (mGadgets == GADGETS.EFFECTS) {
            TransEffectItem effect = (TransEffectItem) mObjects.get(position);
            holder.iv.setImageResource(effect.getDrawableId());
            holder.tv.setBackgroundResource(R.drawable.anim_text_bg);
            holder.tv.setText(effect.getEntry());
            holder.checked.setImageResource(R.drawable.edit_checked);
            if (effect.isSelected()) {
                mCheckPos = position;
                holder.checked.setVisibility(View.VISIBLE);
            } else {
                holder.checked.setVisibility(View.GONE);
            }
            if (position <= Constants.LOCAL_ANIM_COUNT && isAnimExcute) {
                WeakReference<View> ref = new WeakReference<View>(convertView);
                convertView.setX(position * columnWidth + startX);
                lastAnimPos = Math.max(lastAnimPos, position);
                EditModeUtils.translateAnimate(View.TRANSLATION_X, ref, 450 , position * 60, startX, 0, new AnimatorListener(position), null);
            } else {
                isAnimExcute = false;
            }
        }
        return convertView;
    }

    /**
     * return total height from 0 to position
     * @param position
     * @return
     */
    int getItemY(int position){
        int itemY = 0;
        int length = viewHeight.length;
        int padding = res.getDimensionPixelSize(R.dimen.widget_list_item_padding);
        for(int i = 0 ; i < position && i < length; i++){
            itemY += viewHeight[i] + i * 2 * padding;
        }
        return itemY;
    }

    int getViewHeight(int spanY) {
        int[] wh = mLauncher.getWorkspace().estimateItemSize(1, spanY, null, false);
        int minOffset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
        int targetHeight = (int) (wh[1] * WIDGET_SCALE);

        targetHeight = Math.max(targetHeight, 2 * minOffset + mAppIconSize);

        return targetHeight;
    }

    void measureWidgetViewTotalHeight() {
        if (mGadgets != GADGETS.WIDGETSLIST)
            return;
        totalHeight = 0;
        viewHeight = new int[mObjects.size()];
        int padding = res.getDimensionPixelSize(R.dimen.widget_list_item_padding);
        for (int i = 0; i < mObjects.size(); i++) {
            Object object = mObjects.get(i);
            PendingAddItemInfo createItemInfo = getCreateItemInfo(object, null);
            int height = getViewHeight(createItemInfo.spanY);
            viewHeight[i] = height;
            totalHeight += (height + padding * 2);
        }
    }

    public int getWidgetListHeight() {
        return totalHeight;
    }


    private PendingAddItemInfo getCreateItemInfo(Object object, TextView tv) {
        PendingAddItemInfo createItemInfo = null;
        if (object instanceof AppWidgetProviderInfo) {
            AppWidgetProviderInfo info = (AppWidgetProviderInfo) object;
            createItemInfo = new PendingAddWidgetInfo(info, null, null);
            int[] spanXY = Launcher.getSpanForWidget(mContext, info);
            createItemInfo.spanX = spanXY[0];
            createItemInfo.spanY = spanXY[1];
            int[] minSpanXY = Launcher.getMinSpanForWidget(mContext, info);
            createItemInfo.minSpanX = minSpanXY[0];
            createItemInfo.minSpanY = minSpanXY[1];
            if (tv != null)
                tv.setText(info.label);
        } else if (object instanceof ResolveInfo) {
            ResolveInfo info = (ResolveInfo) object;
            createItemInfo = new PendingAddShortcutInfo(((ResolveInfo) object).activityInfo);
            createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
            createItemInfo.spanX = 1;
            createItemInfo.spanY = 1;
            createItemInfo.componentName = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            if (tv != null)
                tv.setText(info.loadLabel(mPackageManager));
        }
        return createItemInfo;
    }

    private static class ViewHolder {
        ImageView iv;
        TextView tv;
        RelativeLayout iv_ll;
        ImageView checked;
    }

    public enum GADGETS {
        //		WIDGETSGROUP,
        WIDGETSLIST,
        EFFECTS
    }

    public enum VIEWTYPE {
        GRID,
        LIST
    }

    public class GridImageLoadListener implements IIoadImageListener {
        private WeakReference<ImageView> mRef;
        private String mTag;

        public GridImageLoadListener(WeakReference<ImageView> ref, String tag) {
            mRef = ref;
            mTag = tag;
        }

        @Override
        public void onLoadStart() {
            // TODO Auto-generated method stub

        }

        @Override
        public void onLoadComplete(Bitmap b) {
            // TODO Auto-generated method stub
            if (mRef != null) {
                ImageView iv = mRef.get();
                if (iv != null && b != null && mTag.equals(iv.getTag()))
                    iv.setImageBitmap(b);
            }

        }

        @Override
        public void onLoadFail() {
            // TODO Auto-generated method stub

        }

    }

    public void setObjects(List<Object> mObjects) {
        this.mObjects = mObjects;
    }

    public boolean isTempStorage() {
        return isTempStorage;
    }

    public void setTempStorage(boolean isTempStorage) {
        this.isTempStorage = isTempStorage;
    }

    public void setChecked(View view, int position) {
        TransEffectItem effect = (TransEffectItem) mObjects.get(position);
        effect.setSelected(true);
        TransEffectItem old_select_effect = (TransEffectItem) mObjects.get(mCheckPos);
        old_select_effect.setSelected(false);
        mCheckPos = position;
        notifyDataSetChanged();
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
            if (!isAnimExcute && position == lastAnimPos) {
                EditModeUtils.logE(TAG, "AnimatorListener set layout after animation");
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
                mLauncher.getEditModeLayer().setHorizontalListParams(params);
                mLauncher.getEditModeLayer().setHwLayerEnabled(false);
                notifyDataSetChanged();
            }
        }
    }
}
