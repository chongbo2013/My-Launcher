package com.lewa.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class Hidden extends RelativeLayout implements View.OnClickListener {
    private static final String TAG = "Hidden";
    protected DragController mDragController;
    protected Launcher mLauncher;
    protected Workspace mWorkspace;
    //zwsun@lewatek.com PR69251 2015.02.09 start
    public static boolean isShowApps = false;
    //zwsun@lewatek.com PR69251 2015.02.09 end
    private final LayoutInflater mInflater;
    private final IconCache mIconCache;
    private ArrayList<ShortcutInfo> mContents = new ArrayList<ShortcutInfo>();
    
    private boolean isEdit;
    private HiddenLayout mHiddenLayout;
    private DesktopIndicator mIndicator;
    private Button editBtn, cancelBtn, doneBtn;
    private TextView mTitle, mHint;
    private int[] targetCell = new int[2];

    public Hidden(Context context, AttributeSet attrs){
        super(context, attrs);
        
        setAlwaysDrawnWithCacheEnabled(false);
        mInflater = LayoutInflater.from(context);
        mIconCache = ((LauncherApplication)context.getApplicationContext()).getIconCache();

        mLauncher = (Launcher)context;
        mWorkspace = mLauncher.getWorkspace();
        setFocusableInTouchMode(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        mHiddenLayout = (HiddenLayout) findViewById(R.id.hidden_container);
        mTitle = (TextView) findViewById(R.id.hidden_title);
        mIndicator = (DesktopIndicator)findViewById(R.id.hidden_indicator);
        mHiddenLayout.setIndicator(mIndicator);
        mHiddenLayout.setHidden(this);
        mHint = (TextView)findViewById(R.id.hidden_hint);
        editBtn = (Button)findViewById(R.id.hidden_edit);
        editBtn.setOnClickListener(this);
        cancelBtn = (Button)findViewById(R.id.hidden_cancel);
        cancelBtn.setOnClickListener(this);
        doneBtn = (Button)findViewById(R.id.hidden_done);
        doneBtn.setOnClickListener(this);
    }

    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            ShortcutInfo item = (ShortcutInfo)tag;
            if (!isEdit) { 
                int[] pos = new int[2];
                v.getLocationOnScreen(pos);
                item.intent.setSourceBounds(new Rect(pos[0], pos[1], pos[0] + v.getWidth(), pos[1] + v.getHeight()));
                boolean success = mLauncher.startActivitySafely(v, item.intent, item);
                if (success && v.getParent() instanceof ShortcutIcon) {
                    Utilities.updateNewAddSymbol((ShortcutIcon) v.getParent(), ((ShortcutInfo) tag).getPackageName());
                }
            } else {
                ShortcutIcon icon = (ShortcutIcon)v.getParent(); 
                icon.setChecked(!icon.isHiddenSelected());
            }
            return;
        }
        if (v.equals(editBtn)) {
            //zwsun@lewatek.com PR69251 2015.02.09 start
            mTitle.setText(R.string.select_app_title);
            setupContentItems(false);
            isShowApps = true;
            //zwsun@lewatek.com PR69251 2015.02.09 end
        } else if (v.equals(cancelBtn)) {
            mTitle.setText(R.string.hidden_app_title);
            setupContentItems(true);
	    //zwsun@lewatek.com PR69251 2015.02.09 start
            isShowApps = false;
	    //zwsun@lewatek.com PR69251 2015.02.09 end
        } else if (v.equals(doneBtn)) {
            mTitle.setText(R.string.hidden_app_title);
            updateAppHiddenStatus();
            setupContentItems(true);
	    //zwsun@lewatek.com PR69251 2015.02.09 start
            isShowApps = false;
	    //zwsun@lewatek.com PR69251 2015.02.09 end
            mLauncher.getModel().resetLoadedState(false, true);
            mLauncher.getModel().startLoaderFromBackground();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    public void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    protected ShortcutIcon createItemIcon(ShortcutInfo item) {
        ShortcutIcon shortcut = (ShortcutIcon)mInflater.inflate(R.layout.application, this, false);
        shortcut.setHiddenMode(true);
        shortcut.applyFromShortcutInfo(item, mIconCache);
        shortcut.mFavorite.setOnClickListener(this);
        if (item.isHidden() && isEdit) {
            shortcut.setChecked(true);
        } else {
            shortcut.setChecked(false);
        }
        return shortcut;
    }

    public void setupContentItems(boolean onlyShow) {
        HashMap<ComponentName, ShortcutInfo> maps = mLauncher.getModel().getWorkspaceAppItems();
        if(maps == null){
            Log.e(TAG,"ERROR!,workspaceAppItems is null");
            return;
        }
        isEdit = !onlyShow;
        mHiddenLayout.removeAllViews();
        mContents.clear();

        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activityList = mLauncher.getPackageManager().queryIntentActivities(mainIntent, 0);
        for (ResolveInfo activity : activityList) {
            ComponentName cn = new ComponentName(activity.activityInfo.packageName, activity.activityInfo.name);
            ShortcutInfo info = maps.get(cn);
            if (info !=  null) {
                if (onlyShow) {
                    if (info.isHidden()) {
                        mContents.add(info);
                    }
                } else {
                    mContents.add(info);
                }
            }
        }
        resetButton(onlyShow);
   
        int col = LauncherModel.getCellCountX();
        int row = LauncherModel.getCellCountY();
        int pageCnt = (int)Math.ceil((double)mContents.size() / (col * row));
        for (int i = 0; i < pageCnt; i++) {
            HiddenScreen screen = new HiddenScreen(mLauncher, i);
            mHiddenLayout.addView(screen);
        }

        // here may throw exception in JDK 7.0, add this
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        Collections.sort(mContents, new HiddenComparator());
        int added = 0;
        for (ShortcutInfo shortcut : mContents) {
            if (onlyShow) {
                if (shortcut.isHidden()) {
                    ShortcutIcon icon = createItemIcon(shortcut);
                    mHiddenLayout.getScreen(added++).addView(icon);
                }
            } else {
                ShortcutIcon icon = createItemIcon(shortcut);
                mHiddenLayout.getScreen(added++).addView(icon);
            }
        }
        if (pageCnt > 0 && mHiddenLayout.getCurrentPage() >= mHiddenLayout.getChildCount()) {
            mHiddenLayout.snapToPage(0);
        }
        mIndicator.setItems(mHiddenLayout.getChildCount(), mHiddenLayout.getCurrentPage());
    }
    
    void resetButton(boolean bShowHidden) {
        mTitle.setText(bShowHidden ? R.string.hidden_app_title : R.string.select_app_title);
        cancelBtn.setVisibility(bShowHidden ? GONE : VISIBLE);
        doneBtn.setVisibility(bShowHidden ? GONE : VISIBLE);
        editBtn.setVisibility(bShowHidden ? VISIBLE : GONE);
        if (bShowHidden && mContents.size() == 0) {
            mHint.setVisibility(VISIBLE);
        } else {
            mHint.setVisibility(GONE);
        }
    }

    public int getItemCount() {
        return mContents.size();
    }
    
    public HiddenLayout getHiddenLayout() {
        return mHiddenLayout;
    }

    public void updateAppHiddenStatus() {
        if (!isEdit) {
            return;
        }
        int scrCnt = mHiddenLayout.getChildCount();
        for (int i = 0; i < scrCnt; i++) {
            HiddenScreen screen = (HiddenScreen)mHiddenLayout.getChildAt(i);
            int cntPerScr = screen.getChildCount();
            for (int j = 0; j < cntPerScr; j++) {
                View v = screen.getChildAt(j);
                if (v instanceof ShortcutIcon) {
                    ShortcutInfo info = (ShortcutInfo)v.getTag();
                    int flag = ((ShortcutIcon)v).isHiddenSelected() ? 1 : 0;
                    if (info.isHidden == 1 && flag == 0) { // need show it on workspace
                        View child = mLauncher.createShortcut(R.layout.application, null, info);
                        int lastScreen = mWorkspace.getChildCount() - 1;
                        CellLayout layout = mWorkspace.getCellLayout(lastScreen);
                        if (layout.findCellForSpan(targetCell, info.spanX, info.spanY)) { 
                            info.screen = lastScreen;
                        } else {
                            layout = mWorkspace.addScreen(lastScreen + 1);
                            if (layout != null && layout.findCellForSpan(targetCell, info.spanX, info.spanY)) {
                                info.screen = lastScreen + 1;
                            }
                        }
                        info.cellX = targetCell[0];
                        info.cellY = targetCell[1];
                        info.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;  // show all hidden items to workspace, including hotseat items
                        info.isHidden = flag;
                        mWorkspace.addInScreen(child, info.container, info.screen, info.cellX, info.cellY, info.spanX, info.spanY);
                        LauncherModel.moveItemInDatabase(mLauncher, info, info.container, info.screen, info.cellX, info.cellY);
                    } else if (info.isHidden == 0 && flag == 1) {   // do not show it on workspace
                        info.isHidden = flag;
                        LauncherModel.moveItemInDatabase(mLauncher, info, info.container, info.screen, info.cellX, info.cellY);
                        if (info.container != LauncherSettings.Favorites.CONTAINER_DESKTOP 
                            && info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT) {  // this item is in a folder
                            removeFolderIfNeeded(info);
                        }
                    }
                }
            }
        }
    }
    
    void removeFolderIfNeeded(ShortcutInfo item) {
        ArrayList<CellLayout> cellLayouts = mWorkspace.getWorkspaceAndHotseatCellLayouts();
        for (CellLayout layoutParent: cellLayouts) {
            ViewGroup layout = layoutParent.getShortcutsAndWidgets();
            int childCount = layout.getChildCount();
            for (int j = 0; j < childCount; j++) {
                View view = layout.getChildAt(j);
                Object tag = view.getTag();
                if (tag instanceof FolderInfo) {
                    FolderInfo info = (FolderInfo) tag;
                    if (info.has(item) && info.getVisibleCnt() == 0) {   // no visible item in the folder, we delete it from workspace
                        LauncherModel.deleteItemFromDatabase(mLauncher, info);
                        break;
                    }
                } 
            }
        }
    }

    
    private class HiddenComparator implements Comparator<ShortcutInfo> {
        @Override
        public int compare(ShortcutInfo lhs, ShortcutInfo rhs) {
            return rhs.isHidden - lhs.isHidden;
        }
    }
    //zwsun@lewatek.com PR69251 2015.02.09 start
    public void isIntentEdit() {
	mTitle.setText(R.string.hidden_app_title);
        setupContentItems(true);
    }
    //zwsun@lewatek.com PR69251 2015.02.09 end
}
