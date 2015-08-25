package com.lewa.launcher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.RequestQueue;
import com.android.volley.Response.ErrorListener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.lewa.launcher.DropTarget.DragObject;
import com.lewa.launcher.EditGridAdapter.GADGETS;
import com.lewa.launcher.EditGridAdapter.VIEWTYPE;
import com.lewa.launcher.LoadPreviewTask.PREVIEWTYPE;
import com.lewa.launcher.PagedView.PageSwitchListener;
import com.lewa.launcher.Workspace.TransitionEffect;
import com.lewa.launcher.bean.EditWidgetGroup;
import com.lewa.launcher.bean.OnlineWallpaper;
import com.lewa.launcher.bean.TransEffectItem;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.launcher.pulltorefresh.PullToRefreshBase;
import com.lewa.launcher.pulltorefresh.PullToRefreshBase.Mode;
import com.lewa.launcher.pulltorefresh.PullToRefreshBase.OnRefreshListener;
import com.lewa.launcher.view.HorizontalListView;
import com.lewa.launcher.view.MovedFrameLayoutWrapper;
import com.lewa.launcher.view.MyHorizontalScrollView;
import com.lewa.launcher.wallpaper.NetBaseParam;
import com.lewa.toolbox.ChangeWallpaperTask;
import com.lewa.toolbox.EditModeUtils;
import com.lewa.toolbox.IIoadImageListener;
import com.lewa.toolbox.MyVolley;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.DownloadManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Property;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.nhaarman.listviewanimations.appearance.AnimationAdapter;
import com.nhaarman.listviewanimations.appearance.simple.ScaleInAnimationAdapter;
import com.nhaarman.listviewanimations.appearance.simple.SwingBottomInAnimationAdapter;
import com.nhaarman.listviewanimations.appearance.simple.SwingLeftInAnimationAdapter;
import com.nhaarman.listviewanimations.appearance.simple.SwingRightInAnimationAdapter;

import android.app.DownloadManager;
import android.provider.Downloads;
import android.widget.Toast;

public class EditModeLayer extends FrameLayout implements PageSwitchListener,
        OnClickListener, DragSource, MovedFrameLayoutWrapper.MovedStatusImpl, OnRefreshListener<FrameLayout>{
    private Launcher mLauncher;
    private Workspace mWorkspace;
    private CellLayout cellLayout;
    private static final int ANIM_DURATION = 500;
    private boolean isEditMode;
    private int startPage;
    private int curPage;
    private Type type;

    private int mWidgetPreviewIconPaddedDimension;
    private int mAppIconSize;
    private final float sWidgetPreviewIconPaddingPercentage = 0.25f;
    private Canvas mCanvas;
    private int mDragViewMultiplyColor;
    private PackageManager mPackageManager;
    private Drawable mDefaultWidgetBackground;
    private IconCache mIconCache;
    private DisplayMetrics dm;
    private DragController mDragController;
    private int HOTSEATHEIGHT;
    private int HOTSEAT_Y;
    public static final int GALLERYID = 1;
    private static final String TAG = "EditModeLayer";
    private View view_gallery;
    public static final File INTERNAL_WALLPAPER = new File(
            Environment.getRootDirectory(), "/media/wallpapers/");
    public static final String external_wallpaper_path = Environment
            .getExternalStorageDirectory().getAbsolutePath()
            + "/LEWA/theme/deskwallpaper/";
    public static  int WALLPAPER_WIDTH;
    public static  int WALLPAPER_HEIGHT;
    private Context mContext;
    public TextView fun_item_wallpaper;
    public TextView fun_item_widget;
    public TextView fun_item_anim;
    public TextView fun_item_arrange;
    //	public PullToRefreshListView component_list;
    public HorizontalListView component_group_list;
    public LinearLayout edit_fun_ll;
    public LinearLayout component_group_list_ll;
    public FrameLayout component_list_rl;
    public MyHorizontalScrollView component_group_scroll;
    public LinearLayout component_widgets_ll;
    public ListView widget_group_lv;
    public boolean isWidgetAdded = false;
    public float GROUP_VIEW_SCALE = 1.2f;
    private Map<String, ArrayList<Object>> gadgetsMap;
    private List<EditWidgetGroup> groupList;
    private boolean isGadgetsMapIniting = false;
    protected static final int SELECT_PICTRUE_FOR_WALLPAPER = 100;
    protected static final int FROM_WALLPAPER = 101;
    private Uri temp_uri;
    private View mScaleView;

    private Resources res;
    private FrameLayout edit_container;
    private View edit_background;
    private LinearLayout edit_list_ll;
    private int PAGESIZE = 5;
    private int PAGE = 0;
    private DownloadManager downloadManager;
    private long downloadId = -1;
    private DownloadsChangeObserver mObserver = new DownloadsChangeObserver();
    private String curDownloadedPath;
    private JSONArray mJsonArray;
    private int online_cnt;
    private int load_preview_task_cnt;
    private boolean isRequestWallpaper = false;
    private ANIMORIENTATION mAnimOrientation = ANIMORIENTATION.VERTICAL;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.ACTION_DOWNLOAD_UPDATEING:
                    Object[] values = (Object[]) msg.obj;
                    updateStatus((Long) values[0], (Integer) values[1], (Integer) values[2],(Integer) values[3]);
                    break;
            }
        }

        ;
    };
    FilenameFilter filter = new FilenameFilter() {

        @Override
        public boolean accept(File dir, String name) {
            // TODO Auto-generated method stub
            name = name.toLowerCase();
            return name.endsWith(".jpg") || name.endsWith(".png");
//			return wallpaper_path != null && wallpaper_path.contains(name) || name.equals(EditModeUtils.CUSTOM_WALLPAPER);
        }
    };
    private boolean isAddingWidgetFromDrag;
    private static final int ANIMATION_COUNT = 5;
    private static final int SCALE_IN = 0;
    private static final int BOTTOM_IN = 1;
    private static final int BOTTOM_RIGHT_IN = 2;
    private static final int LEFT_IN = 3;
    private static final int RIGHT_IN = 4;
    private boolean isFromEditEnterFloating;
    private LinearLayout widget_group_lv_ll;
    private HashMap<Long,OnlineWallpaper> downloadingMap = new HashMap<Long, OnlineWallpaper>();
    private boolean isEnterAnimExcuting ;
    private MovedFrameLayoutWrapper moved_layer;
    private List<Animator> runningAnims = new ArrayList<Animator>();
    private List<Object> widgetInfos;
    private boolean interceptTouch = false;//lqwang - pr69405 - modify
    private boolean onlineWallpaperEnabled;
    public EditModeLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
        init(context);
    }

    private void init(Context context) {
        // TODO Auto-generated method stub
        mLauncher = (Launcher) context;
        mContext = mLauncher.getApplicationContext();
        res = context.getResources();
        mPackageManager = context.getPackageManager();
        mAppIconSize = IconCache.getAppIconSize(res);
        mWidgetPreviewIconPaddedDimension = (int) (mAppIconSize * (1 + (2 * sWidgetPreviewIconPaddingPercentage)));
        mCanvas = new Canvas();
        mDragViewMultiplyColor = res.getColor(R.color.drag_view_multiply_color);
        mDefaultWidgetBackground = res
                .getDrawable(R.drawable.default_widget_preview_holo);
        mIconCache = ((LauncherApplication) context.getApplicationContext())
                .getIconCache();
        dm = new DisplayMetrics();
        mLauncher.getWindowManager().getDefaultDisplay().getMetrics(dm);
        WALLPAPER_WIDTH = dm.widthPixels * 2 ;
        WALLPAPER_HEIGHT = dm.heightPixels ;
        HOTSEATHEIGHT = res.getDimensionPixelSize(R.dimen.hotseat_height);
        HOTSEAT_Y = dm.heightPixels - HOTSEATHEIGHT;
        initScreenParam();
        onlineWallpaperEnabled = res.getBoolean(R.bool.config_online_wallpaper);
    }

    public void initViews() {
        // TODO Auto-generated method stub
        initFuncItem(fun_item_wallpaper, R.id.fun_item_wallpaper,
                R.string.launcher_wallpaper, res.getDrawable(R.drawable.wallpaper_bg));
        initFuncItem(fun_item_widget, R.id.fun_item_widget,
                R.string.launcher_widget, res.getDrawable(R.drawable.widget_bg));
        initFuncItem(fun_item_anim, R.id.fun_item_anim, R.string.launcher_anim,
                res.getDrawable(R.drawable.anim_bg));
        initFuncItem(fun_item_arrange, R.id.fun_item_arrange,
                R.string.fun_item_arrange, res.getDrawable(R.drawable.setting_bg));
        component_list_rl = (FrameLayout) findViewById(R.id.component_group_list);

        component_group_scroll = (MyHorizontalScrollView) findViewById(R.id.component_list);

//		component_list = (PullToRefreshListView) component_group_scroll.findViewById(R.id.edit_mode_list);
        component_widgets_ll = (LinearLayout) component_group_scroll.findViewById(R.id.edit_mode_scroll_ll);
        component_group_list = (HorizontalListView) component_list_rl.findViewById(R.id.edit_mode_list);
//        component_group_list.setMode(Mode.DISABLED);
        component_group_list_ll = (LinearLayout) component_list_rl.findViewById(R.id.edit_mode_list_ll);

        edit_fun_ll = (LinearLayout) findViewById(R.id.edit_fun_ll);
        widget_group_lv = (ListView) findViewById(R.id.app_widget_list);
        widget_group_lv_ll = (LinearLayout) widget_group_lv.getParent();
        edit_list_ll = (LinearLayout) findViewById(R.id.edit_list_ll);
        edit_container = (FrameLayout) findViewById(R.id.edit_container);
        moved_layer = (MovedFrameLayoutWrapper) findViewById(R.id.moved_layer);
        moved_layer.setMode(Mode.PULL_FROM_START);
        moved_layer.setMovedStatusImpl(this);
        moved_layer.setOnRefreshListener(this);
        moved_layer.setFriction(1.0f);
    }

    public void setEditBackground(View edit_background) {
        this.edit_background = edit_background;
    }

    public void initData() {
        initGadgetsMap();
        initSystemWallpaper();
    }

    private void initSystemWallpaper() {
       final int width = res.getDimensionPixelSize(R.dimen.edit_wallpaper_size);
       final int height = width;
        LauncherModel.runOnWorkerThread(new Runnable() {
            @Override
            public void run() {
//                File[] files = INTERNAL_WALLPAPER.listFiles();
//                for(File file : files){
//                    String key = LauncherApplication.getCacheKey(file.getAbsolutePath(), width, height);
//                    if(LauncherApplication.getCacheBitmap(key) == null){
//                        Bitmap b =  EditModeUtils.parseBitmap(file.getAbsolutePath(), Config.RGB_565, width, height);
//                        Bitmap reflectBitmap = EditModeUtils.createReflectedBitmap(res, b, true);
//                        LauncherApplication.cacheBitmap(key, reflectBitmap);
//                    }
//                }
            }
        });
    }

    public void clearData() {
        if(gadgetsMap != null)
            gadgetsMap.clear();
        if(groupList != null)
            groupList.clear();
    }

    void initFuncItem(TextView textView, int id, int str, Drawable bgres) {
        textView = (TextView) findViewById(id);
        textView.setText(res.getString(str));
        int width = res.getDimensionPixelSize(R.dimen.fun_img_width);
        bgres.setBounds(0, 0, width, width);
        textView.setCompoundDrawables(null, bgres, null, null);
        textView.setOnClickListener(this);
    }

    public void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    @Override
    public boolean isReadyForPullStartImpl() {
        boolean isReady = false;
        switch (type){
            case FUNCTION:
                isReady = true;
                break;
            case WIDGETS_GROUP:
                isReady = ((MyHorizontalScrollView)component_widgets_ll.getParent()).getScrollX() == 0;
                break;
            case WALLPAPER_ONLINE:
            case WALLPAPER_LOCAL:
            case ANIM_LIST:
                isReady = component_group_list.getFirstVisiblePosition() == 0 && component_group_list.getChildAt(0).getLeft() == 0 && component_group_list.getChildAt(0).getX() == 0;
                break;
        }
        if(type == Type.WALLPAPER_ONLINE){
            isReady = isReady && !isRequestWallpaper;
        }
        return isReady && !isExecAnimRunning();
    }

    @Override
    public boolean isReadyForPullEndImpl() {
        return false;
    }

    @Override
    public boolean hideHeaderViewImpl() {
        boolean isHide = true;
        switch (type){
            case WALLPAPER_ONLINE:
                isHide = false;
                break;
        }
        return isHide;
    }

    @Override
    public View getScrollViewImpl() {
        View v = null;
        switch (type){
            case FUNCTION:
                v = edit_fun_ll;
                break;
            case WIDGETS_GROUP:
                v = component_widgets_ll;
                break;
            case WALLPAPER_LOCAL:
            case ANIM_LIST:
                v = component_group_list;
                break;
        }
        return v;
    }

    @Override
    public void onRefresh(PullToRefreshBase<FrameLayout> refreshView) {
        switch (type){
            case FUNCTION:
                mAnimOrientation = ANIMORIENTATION.HORIZONTAL;

            case WIDGETS_GROUP:
            case WALLPAPER_LOCAL:
            case ANIM_LIST:
                onBack();
                break;
            case WALLPAPER_ONLINE:
                if (EditModeUtils.checkNetWork(mContext)) {
                    requestWallpaperData();
                } else {
                    moved_layer.onRefreshComplete();
                }
                break;
        }
    }

    public enum ANIMORIENTATION {
        HORIZONTAL,
        VERTICAL
    }

    public void startEditMode(ANIMORIENTATION anim_orientation) {
        if (isEditMode)
            return;
        initData();
        isEditMode = true;
        mAnimOrientation = anim_orientation;
        type = Type.FUNCTION;
        mWorkspace = mLauncher.getWorkspace();
        mWorkspace.setPageSwitchListener(this);
//        mWorkspace.setEditMode(true);
        // mWorkspace.blurWallpaper(true);
//        mLauncher.showStatusBar(false);
        startPage = mWorkspace.getCurrentPage();
        cellLayout = mLauncher.getCellLayout(0, startPage);
        setVisibility(View.VISIBLE);
        edit_container.setTranslationX(0);
        EditModeUtils.batchScaleCell(mLauncher, true, true, startPage);
        mContext.getApplicationContext().getContentResolver().registerContentObserver(Downloads.Impl.CONTENT_URI, true, mObserver);
        EditModeUtils.setInEditMode(mLauncher,isEditMode());
        excuteEnterAnim(mAnimOrientation);
        isRequestWallpaper = false;
    }

    public void setHwLayerEnabled(boolean enabled){
        mLauncher.setHotseatAndIndicatorHwLayerEnabled(enabled);
        setLayerType(enabled ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, null);
        CellLayout layout = mWorkspace.getCurrentCellLayout();
        if(layout != null && enabled){
            layout.enableHardwareLayers();
        }else if(layout != null){
            layout.disableHardwareLayers();
        }
        edit_background.setLayerType(enabled ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, null);
    }

    private void excuteEnterAnim(final ANIMORIENTATION animorientation) {
        clearRunningAnimator();
        final Hotseat hotseat = mLauncher.getHotseat();
        AnimatorSet enterAnim = new AnimatorSet();
        isEnterAnimExcuting = true;
        setHwLayerEnabled(true);
        final AnimatorListener animatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // TODO Auto-generated method stub
                mWorkspace.setEditMode(true);
                mLauncher.showStatusBar(false);
//                if (!isGadgetsMapIniting && groupList != null && groupList.size() > 0) {
//                    component_widgets_ll.setVisibility(View.GONE);
//                    showWidgetsGroup(groupList,0,groupList.size());
//                }
                if(animorientation == ANIMORIENTATION.HORIZONTAL){
                    mLauncher.getMovedHotseatWrapper().setState(PullToRefreshBase.State.RESET,false);
                    moved_layer.setMode(Mode.PULL_FROM_START);
                }
                setHwLayerEnabled(false);
                isEnterAnimExcuting = false;
            }
        };
        switch (animorientation) {
            case VERTICAL:
                edit_background.setAlpha(1.0f);
                List<Animator> animators = new ArrayList<Animator>();
                animators.add(ObjectAnimator.ofFloat(hotseat, View.TRANSLATION_Y, 0,
                        hotseat.getHeight()));
                animators.add(ObjectAnimator.ofFloat(edit_container, View.TRANSLATION_Y,
                        hotseat.getHeight() * 3, 0));
                enterAnim.setDuration(ANIM_DURATION);
                enterAnim.playTogether(animators);
                enterAnim.setInterpolator(new DecelerateInterpolator());
                enterAnim.addListener(animatorListener);
                enterAnim.start();
                break;
            case HORIZONTAL:
                moved_layer.setMode(Mode.DISABLED);
                addRunningAnimator(EditModeUtils.translateAnimate(View.TRANSLATION_X, new WeakReference<View>(hotseat), 600, 0, hotseat.getTranslationX(), -dm.widthPixels, null, new LinearInterpolator()));
                float itemWidth = edit_fun_ll.getMeasuredWidth() / 4.0f;
                int count = edit_fun_ll.getChildCount();
                for (int i = 0; i < count; i++) {
                    View v = edit_fun_ll.getChildAt(i);
                    EditModeUtils.logE(TAG, "itemWidth: " + itemWidth);
                    v.setX(screenWidth());
                    if (i == count - 1) {
                        Animator animator = EditModeUtils.translateAnimate(View.X, new WeakReference<View>(v), 100 * (Constants.LOCAL_ANIM_COUNT - i), (i+1) * Constants.ANIM_IN_DURATION_DELAY, screenWidth(), i * itemWidth, animatorListener, null);
                        addRunningAnimator(animator);
                    } else {
                        Animator animator = EditModeUtils.translateAnimate(View.X, new WeakReference<View>(v), 100 * (Constants.LOCAL_ANIM_COUNT - i), (i+1) * Constants.ANIM_IN_DURATION_DELAY, screenWidth(), i * itemWidth, null, null);
                        addRunningAnimator(animator);
                    }
                }

                break;

        }
        edit_background.setAlpha(0);
        addRunningAnimator(EditModeUtils.alphaAnimate(edit_background,500,0,0,1,null));
    }

    public void endEditMode(boolean animator) {
        if (!isEditMode || isEnterAnimExcuting || isExecAnimRunning()) {
            return;
        }
        clearRunningAnimator();
        // mWorkspace.blurWallpaper(false);
        Hotseat hotseat = mLauncher.getHotseat();
        if (animator) {
            final View in = hotseat;
            setHwLayerEnabled(true);
            final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator arg0) {
                    // TODO Auto-generated method stub
                    endEditMode();
                    moved_layer.setState(PullToRefreshBase.State.RESET,false);
                    setHwLayerEnabled(false);
                }
            };
            if(mAnimOrientation == ANIMORIENTATION.VERTICAL){
                in.setTranslationX(0);
                executeVerticalSwitch(in, edit_container, listener);
            }else{
                in.setTranslationY(0);
                executeHorizontalSwitch(in, edit_container, listener, true);
            }
            addRunningAnimator(EditModeUtils.alphaAnimate(edit_background, ANIM_DURATION * 2, 0, 1, 0, null));
            EditModeUtils.batchScaleCell(mLauncher, false, true, curPage);
        } else {
            endEditMode();
            EditModeUtils.batchScaleCell(mLauncher, false, animator, curPage);
        }
    }

    public void endEditMode() {
        if (!isEditMode || isEnterAnimExcuting) {
            return;
        }
        mJsonArray = null;
        LauncherApplication.clearCache();
        setVisibility(View.GONE);
        dismissWidgetList();
        mWorkspace.setEditMode(false);
        mWorkspace.setPageSwitchListener(null);
        if (!mLauncher.isFloating()) {
            mWorkspace.refreshFolderIconHat(false);
        }
        resetViews();
        clearData();
        edit_fun_ll.setTranslationY(0);
        edit_fun_ll.setTranslationX(0);
        component_group_list.setAdapter(null);
        component_widgets_ll.removeAllViews();
        isWidgetAdded = false;
        //lqwang - pr952308 - modify begin
        if(downloadingMap.size() == 0){
            Log.e(TAG,"unregister from end edit mode");
            mContext.getApplicationContext().getContentResolver().unregisterContentObserver(mObserver);
        }
//        downloadingMap.clear();
        //lqwang - pr952308 - modify end
        online_cnt = 0;
        mLauncher.getHotseat().setTranslationY(0);
        mLauncher.getHotseat().setTranslationX(0);
        if(widgetInfos != null){
            widgetInfos.clear();
            widgetInfos = null;
        }
        isEditMode = false;
        mLauncher.showStatusBar(true);
        setHwLayerEnabled(false);
        EditModeUtils.setInEditMode(mLauncher,isEditMode());
    }

    //lqwang - pr952308 - add begin
    private void checkAndUnregisterObserver(){
      if(!isEditMode && downloadingMap.size() == 0 ){
          Log.e(TAG,"checkAndUnregisterObserver");
          mContext.getApplicationContext().getContentResolver().unregisterContentObserver(mObserver);
      }
    }
    //lqwang - pr952308 - add end

    public boolean isEditMode() {
        return isEditMode;
    }

    public boolean isDraggingAddWidget() {
        return isAddingWidgetFromDrag;
    }

    public void setDraggingAddWidgetEnd() {
        if (isAddingWidgetFromDrag)
            isAddingWidgetFromDrag = false;
    }

    public void setEditMode(boolean isEditMode) {
        this.isEditMode = isEditMode;
    }

    // PageSwitchListener method begin
    @Override
    public void onPageSwitch(View newPage, int newPageIndex) {
        // TODO Auto-generated method stub
        if (mLauncher.getWorkspace() == null) {
            return;
        }
        cellLayout = mLauncher.getCellLayout(0, newPageIndex);
        if (curPage != newPageIndex) {
            curPage = newPageIndex;
        }
    }

    // PageSwitchListener method end

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        if (isExecAnimRunning()) {
            return;
        }
        EditModeUtils.logE(TAG, "load_preview_task_cnt : " + load_preview_task_cnt);
        if (!isGadgetsMapIniting && load_preview_task_cnt <= 0)
            MyVolley.clearFetrues();
        switch (v.getId()) {
            case R.id.fun_item_wallpaper:
                component_group_list.scrollTo(0,0);
                showLocalWallpaper();
                break;
            case R.id.fun_item_widget:
                resetListView();
                component_widgets_ll.scrollTo(0,0);
                showWidgetsGroup();
                break;
            case R.id.fun_item_anim:
                component_group_list.scrollTo(0,0);
                showAnimItems();
                break;
            case R.id.fun_item_arrange:
                isFromEditEnterFloating = true;
                cellLayout.clearTagCellInfo();
                mLauncher.getFloating().startFloating(cellLayout.getTag());
                isFromEditEnterFloating = false;
                break;
            case GALLERYID:
                Intent intent = new Intent();
                intent.setAction("android.intent.action.GET_CONTENT");
                intent.setType("image/*");
                mLauncher.startActivityForResult(intent,
                        SELECT_PICTRUE_FOR_WALLPAPER);
                break;
            default:
                break;
        }
    }

    public boolean isFromEditEnterFloating() {
        return isFromEditEnterFloating;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // TODO Auto-generated method stub
        if (event.getY() > HOTSEAT_Y  || interceptTouch)//lqwang - pr69405 - modify
            return true;
        return super.onTouchEvent(event);
    }

    public enum Type {
        FUNCTION, WALLPAPER_LOCAL,WALLPAPER_ONLINE, WIDGETS_GROUP, WIDGETS_LIST, ANIM_LIST
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void onBack() {
        if (isEditMode) {
            if (isExecAnimRunning()) {
                return;
            }
            switch (type) {
                case FUNCTION:
                    //yixiao add pr66574 2015.1.15
                    if(mWorkspace.getOpenFolder()==null){
                        endEditMode(true);
                    }
                    break;
                case WALLPAPER_ONLINE:
                    moved_layer.updateViewVisiable();
                    moved_layer.setFriction(1.0f);
                case WALLPAPER_LOCAL:
                    setHwLayerEnabled(true);
                    showFunctions(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            moved_layer.setState(PullToRefreshBase.State.RESET);
                            setHwLayerEnabled(false);
                        }
                    });
//                    component_group_list.setMode(Mode.DISABLED);
                    break;
                case WIDGETS_GROUP:
                    if(isWidgetAdded){
                        setHwLayerEnabled(true);
                        showFunctions(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                // TODO Auto-generated method stub
                                moved_layer.setState(PullToRefreshBase.State.RESET);
                                resetViews();
                                setHwLayerEnabled(false);
                            }
                        });
                    }
                    break;
                case WIDGETS_LIST:
                    dismissWidgetList();
                    setType(Type.WIDGETS_GROUP);
                    break;
                case ANIM_LIST:
                    setHwLayerEnabled(true);
                    showFunctions(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            moved_layer.setState(PullToRefreshBase.State.RESET);
                            setHwLayerEnabled(false);
                        }
                    });
                    break;
            }
        }
    }

    public void resetViews() {
        component_list_rl.setTranslationY(0);
        component_group_scroll.setTranslationY(0);
        component_group_scroll.scrollTo(0, 0);
        edit_container.setTranslationY(0);
        component_group_list.setVisibility(View.VISIBLE);
    }

    public void resetListView() {
        component_list_rl.scrollTo(0, 0);
        component_group_list_ll.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        component_group_list.setLayoutParams(params);
        component_group_list.setVisibility(View.GONE);
    }

    // DragSource interface begin
    @Override
    public boolean supportsFlingToDelete() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onDropCompleted(View target, DragObject d,
                                boolean isFlingToDelete, boolean success) {
        // TODO Auto-generated method stub

    }

    // DragSource interface end

    public void beginDraggingWidget(View v) {
        cellLayout = mWorkspace.getCurrentCellLayout();
        if (cellLayout.mIsAddCellLayout) {
            mLauncher.makeToast(R.string.widget_add_fail);
            //Toast.makeText(mContext, R.string.widget_add_fail, 0).show();
            return;
        }
        isAddingWidgetFromDrag = true;
        int isOffsetY, isOffsetX;
        // Get the widget preview as the drag representation
        ImageView image = (ImageView) v.findViewById(R.id.item_iv);
        PendingAddItemInfo createItemInfo = (PendingAddItemInfo) v
                .getTag(R.id.widget_list_item_tag);

        // Compose the drag image
        Bitmap preview;
        Bitmap outline;
        float initialDragViewScale;
        if (createItemInfo instanceof PendingAddWidgetInfo) {
            PendingAddWidgetInfo createWidgetInfo = (PendingAddWidgetInfo) createItemInfo;
            int[] spanXY = Launcher.getSpanForWidget(mLauncher,
                    createWidgetInfo);
            createItemInfo.spanX = spanXY[0];
            createItemInfo.spanY = spanXY[1];
            isOffsetX = spanXY[0];
            isOffsetY = spanXY[1];
            /*
			 * int[] maxSize =
			 * mLauncher.getWorkspace().estimateItemSize(spanXY[0], spanXY[1],
			 * createWidgetInfo, true);
			 */
            int maxWidth = cellLayout.getMeasuredWidth() / 3;
            int maxHeight = cellLayout.getMeasuredHeight() / 3;
            preview = getWidgetPreview(createWidgetInfo.componentName,
                    createWidgetInfo.previewImage, createWidgetInfo.icon,
                    spanXY[0], spanXY[1], maxWidth, maxHeight);
            initialDragViewScale = 0.5f;
        } else {
            // Workaround for the fact that we don't keep the original
            // ResolveInfo associated with
            // the shortcut around. To get the icon, we just render the preview
            // image (which has
            // the shortcut icon) to a new drag bitmap that clips the non-icon
            // space.
            preview = Bitmap.createBitmap(mWidgetPreviewIconPaddedDimension,
                    mWidgetPreviewIconPaddedDimension, Bitmap.Config.ARGB_8888);
            Drawable d = image.getDrawable();
            mCanvas.setBitmap(preview);
            d.draw(mCanvas);
            mCanvas.setBitmap(null);
            createItemInfo.spanX = createItemInfo.spanY = 1;
            isOffsetX = 1;
            isOffsetY = 1;
            initialDragViewScale = 1.0f;
        }

        // We use a custom alpha clip table for the default widget previews
        // Paint alphaClipPaint;
        // if (createItemInfo instanceof PendingAddWidgetInfo) {
        // if (((PendingAddWidgetInfo) createItemInfo).previewImage != 0) {
        // MaskFilter alphaClipTable = TableMaskFilter.CreateClipTable(0, 255);
        // alphaClipPaint = new Paint();
        // alphaClipPaint.setMaskFilter(alphaClipTable);
        // }
        // }

        // Save the preview for the outline generation, then dim the preview
        outline = Bitmap.createScaledBitmap(preview, preview.getWidth(),
                preview.getHeight(), false);
        mCanvas.setBitmap(preview);
        mCanvas.drawColor(mDragViewMultiplyColor, PorterDuff.Mode.MULTIPLY);
        mCanvas.setBitmap(null);

        // Start the drag
        // alphaClipPaint = null;
        mLauncher.getWorkspace().onDragStartedWithWidget(createItemInfo,
                outline, false);
        // int x = getOffset(isOffsetX,offsetX) ;
        // int y = getOffset(isOffsetY,offsetY) ;
        // mDragController.startDrag(x,y,image, preview, this,
        // createItemInfo,DragController.DRAG_ACTION_COPY, null);
        // mDragController.startDrag(preview, dragLayerX, dragLayerY, source,
        // dragInfo, dragAction, dragOffset, dragRegion, initialDragViewScale)
        mDragController.startDrag(image, preview, this, createItemInfo,
                DragController.DRAG_ACTION_COPY, null, initialDragViewScale);
        // mDragController.startDrag(image, this, createItemInfo,
        // DragController.DRAG_ACTION_COPY);
        outline.recycle();
        preview.recycle();
    }

    private Bitmap getWidgetPreview(ComponentName provider, int previewImage,
                                    int iconId, int cellHSpan, int cellVSpan, int maxWidth,
                                    int maxHeight) {
        // Load the preview image if possible
        String packageName = provider.getPackageName();
        if (maxWidth < 0)
            maxWidth = Integer.MAX_VALUE;
        if (maxHeight < 0)
            maxHeight = Integer.MAX_VALUE;
        int mCellWidth = cellLayout.getCellWidth();
        int mCellHeight = cellLayout.getCellHeight();
        Drawable drawable = null;
        if (previewImage != 0) {
            drawable = mPackageManager.getDrawable(packageName, previewImage,
                    null);
            if (drawable == null) {
                Log.w(TAG,
                        "Can't load widget preview drawable 0x"
                                + Integer.toHexString(previewImage)
                                + " for provider: " + provider);
            }
        }

        int bitmapWidth;
        int bitmapHeight;
        boolean widgetPreviewExists = (drawable != null);
        if (widgetPreviewExists) {
            bitmapWidth = drawable.getIntrinsicWidth();
            bitmapHeight = drawable.getIntrinsicHeight();

            // Cap the size so widget previews don't appear larger than the
            // actual widget
            maxWidth = Math.min(maxWidth, mCellWidth * cellHSpan);
            maxHeight = Math.min(maxHeight, mCellHeight * cellVSpan);
        } else {
            // Determine the size of the bitmap for the preview image we will
            // generate
            // TODO: This actually uses the apps customize cell layout params,
            // where as we make want
            // the Workspace params for more accuracy.
            bitmapWidth = mCellWidth * cellHSpan;
            bitmapHeight = mCellHeight * cellVSpan;
            if (cellHSpan == cellVSpan) {
                // For square widgets, we just have a fixed size for 1x1 and
                // larger-than-1x1
                int minOffset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
                if (cellHSpan <= 1) {
                    bitmapWidth = bitmapHeight = mAppIconSize + 2 * minOffset;
                } else {
                    bitmapWidth = bitmapHeight = mAppIconSize + 4 * minOffset;
                }
            }
        }

        float scale = 1f;
        if (bitmapWidth > maxWidth) {
            scale = maxWidth / (float) bitmapWidth;
        }
        if (bitmapHeight * scale > maxHeight) {
            scale = maxHeight / (float) bitmapHeight;
        }
        if (scale != 1f) {
            bitmapWidth = (int) (scale * bitmapWidth);
            bitmapHeight = (int) (scale * bitmapHeight);
        }

        Bitmap preview = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                Config.ARGB_8888);
        if (widgetPreviewExists) {
            renderDrawableToBitmap(drawable, preview, 0, 0, bitmapWidth,
                    bitmapHeight);
        } else {
            // Generate a preview image if we couldn't load one
            int minOffset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
            int smallestSide = Math.min(bitmapWidth, bitmapHeight);
            float iconScale = Math.min((float) smallestSide
                    / (mAppIconSize + 2 * minOffset), 1f);
            if (cellHSpan != 1 || cellVSpan != 1) {
                renderDrawableToBitmap(mDefaultWidgetBackground, preview, 0, 0,
                        bitmapWidth, bitmapHeight);
            }

            // Draw the icon in the top left corner
            try {
                Drawable icon = null;
                int hoffset = (int) (bitmapWidth / 2 - mAppIconSize * iconScale
                        / 2);
                int yoffset = (int) (bitmapHeight / 2 - mAppIconSize
                        * iconScale / 2);
                if (iconId > 0)
                    icon = mIconCache.getFullResIcon(packageName, iconId);
                Resources resources = mLauncher.getResources();
                if (icon == null)
                    icon = resources
                            .getDrawable(R.drawable.ic_launcher_application);

                renderDrawableToBitmap(icon, preview, hoffset, yoffset,
                        (int) (mAppIconSize * iconScale),
                        (int) (mAppIconSize * iconScale));
            } catch (Resources.NotFoundException e) {
            }
        }
        return preview;
    }

    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x,
                                        int y, int w, int h) {
        renderDrawableToBitmap(d, bitmap, x, y, w, h, 1f, 0xFFFFFFFF);
    }

    private void renderDrawableToBitmap(Drawable d, Bitmap bitmap, int x,
                                        int y, int w, int h, float scale, int multiplyColor) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            Rect oldBounds = d.copyBounds();
            d.setBounds(x, y, x + w, y + h);
            d.draw(c);
            d.setBounds(oldBounds); // Restore the bounds
            if (multiplyColor != 0xFFFFFFFF) {
                c.drawColor(mDragViewMultiplyColor, PorterDuff.Mode.MULTIPLY);
            }
            c.setBitmap(null);
        }
    }

    public boolean isEditModeDragOverDisable(float centerY) {
        return isEditMode
                && centerY > dm.heightPixels
                - mLauncher.getHotseat().getHeight();
    }

    public void showFunctions(AnimatorListenerAdapter listener) {
        executeHorizontalSwitch(edit_fun_ll, edit_list_ll, listener, true);
        setType(Type.FUNCTION);
    }

    public void showLocalWallpaper() {
        edit_list_ll.setTranslationX(0);
        setType(Type.WALLPAPER_LOCAL);
        setWallpaperFiles();
        setHwLayerEnabled(true);
        wallpaperThumbnailAdapter = new WallpaperThumbnailAdapter(mLauncher, wallpaperFiles, component_group_list);
        setWallpaperListParams();
        component_group_list.setAdapter(wallpaperThumbnailAdapter);
        showView(component_list_rl, component_group_scroll);
        executeHorizontalSwitch(null, edit_fun_ll, null, false);
        component_group_list.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // TODO Auto-generated method stub
                ChangeWallpaperTask chWallpaperTask = null;
                int count = wallpaperThumbnailAdapter.getCount();
                if (wallpaperThumbnailAdapter.isOnlyLocal() && position == 0) {
                    if (!EditModeUtils.checkNetWork(mLauncher)) {
                        return;
                    }
                    if(!onlineWallpaperEnabled){
                        Intent intent = new Intent();
                        intent.setClassName("com.lewa.themechooser",
                                "com.lewa.themechooser.custom.main.DeskTopWallpaper");
                        mLauncher.startActivity(intent);
                    }else{
                        loading_iv = (ImageView) view.findViewById(R.id.loading_iv);
                        online_cnt = 0;
                        requestWallpaperData();
                    }
                    return;
                } else if (position == count - 1) {
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.GET_CONTENT");
                    intent.setType("image/*");
                    mLauncher.startActivityForResult(intent,
                            SELECT_PICTRUE_FOR_WALLPAPER);
                    return;
                }
                if (wallpaperThumbnailAdapter.isChecked(position))
                    return;
                Object object = parent.getItemAtPosition(position);
                String path = null;
                if (object instanceof File) {
                    File file = (File) object;
                    if (file != null) {
                        chWallpaperTask = new ChangeWallpaperTask(mContext,
                                file.getAbsolutePath(), 0);
                        path = file.getPath();
                    }
                } else if (object instanceof OnlineWallpaper) {
                    if (EditModeUtils.checkNetWork(mContext)) {
                        OnlineWallpaper onlineWallpaper = (OnlineWallpaper) object;
                        if (onlineWallpaper.isDownloaded()) {
                            curDownloadedPath = EditModeUtils.getOnlineWallpaperSavedPath(onlineWallpaper);
                            chWallpaperTask = new ChangeWallpaperTask(mContext, curDownloadedPath, 0);
                        } else if (!onlineWallpaper.isDownloading()) {
                            startDownloadWallpaper(onlineWallpaper);
                        }
                        for (Entry<Long, OnlineWallpaper> entry : downloadingMap.entrySet()) {
                            if (entry.getValue().equals(onlineWallpaper)) {
                                entry.getValue().setSelected(true);
                                curDownloadedPath = EditModeUtils.getOnlineWallpaperSavedPath(onlineWallpaper);
                            } else {
                                entry.getValue().setSelected(false);
                            }
                        }
                        path = curDownloadedPath;
                    }
                } else {
                    path = wallpaperThumbnailAdapter.getWallpaperName(position);
                    chWallpaperTask = new ChangeWallpaperTask(mContext, path,
                            (Integer) object);
                }
                if (!TextUtils.isEmpty(path)) {
                    setWallpaperPath(path);
                }
                wallpaperThumbnailAdapter.setChecked(position);
                if (chWallpaperTask != null) {
                    chWallpaperTask.setPriority(Thread.MAX_PRIORITY);
                    // MyVolley.execRunnable(chWallpaperTask);
                    chWallpaperTask.start();
                }
            }


        });
    }

    public void setWallpaperPath(String path) {
//        PreferencesProvider.putStringValue(mContext,
//                EditModeUtils.PREFERENCE_WALLPAPER_PATH, path);
        Settings.System.putString(mLauncher.getContentResolver(),EditModeUtils.PREFERENCE_WALLPAPER_PATH, path);
        wallpaperThumbnailAdapter.setWallpaperPath("");
    }

    private boolean startDownloadWallpaper(OnlineWallpaper onlineWallpaper) {
        // TODO Auto-generated method stub
        if (onlineWallpaper == null) {
            return false;
        }
        mLauncher.makeToast(R.string.start_download);
        // Toast.makeText(mContext, mContext.getString(R.string.start_download), 0).show();
        String url = onlineWallpaper.getUrl();
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        if (downloadManager == null) {
            downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        }
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
        request.setShowRunningNotification(true);
        request.setVisibleInDownloadsUi(true);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        File wallpaper_file = new File(Constants.WALLPAPER_FULL_PATH);
        if (!wallpaper_file.exists() || !wallpaper_file.isDirectory()) {
            wallpaper_file.mkdirs();
        }
        try {
            request.setDestinationInExternalPublicDir(Constants.WALLPAPER_PATH, onlineWallpaper.getPackageName().concat(Constants.JPEG));
//            curDownloadedPath = EditModeUtils.getOnlineWallpaperSavedPath(onlineWallpaper);
            downloadId = downloadManager.enqueue(request);
            onlineWallpaper.setDownloading(true);
            downloadingMap.put(downloadId,onlineWallpaper);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            return false;
        }
        return true;
    }

    private void updateStatus(long downloadId,int max, int progress, int status) {
        if (progress < max && max != 0 && status == Downloads.Impl.STATUS_RUNNING) {
            return;
        } else if (max == progress && max != 0 && status == Downloads.Impl.STATUS_SUCCESS) {
            EditModeUtils.logE(TAG, "downloadChangeTask begin change wallpaper");
            OnlineWallpaper onlineWallpaper = downloadingMap.get(downloadId);
            if (onlineWallpaper != null) {
                if(onlineWallpaper.isSelected()){
                    String path = EditModeUtils.getOnlineWallpaperSavedPath(onlineWallpaper);
                    ChangeWallpaperTask downloadChangeTask = new ChangeWallpaperTask(mContext, path, 0);
                    downloadChangeTask.start();
                    setWallpaperPath(path);
                }
                onlineWallpaper.setDownloaded(true);
                onlineWallpaper.setDownloading(false);
                downloadingMap.remove(downloadId);
                checkAndUnregisterObserver();    //lqwang - pr952308 - add
                wallpaperThumbnailAdapter.notifyDataSetChanged();
            }
        } else if (max != progress && Downloads.Impl.isStatusError(status)) {
            downloadingMap.remove(downloadId);
            checkAndUnregisterObserver();    //lqwang - pr952308 - add
            downloadId = -1;
        }
    }

    private void requestWallpaperData() {
        // TODO Auto-generated method stub
        setType(Type.WALLPAPER_ONLINE);
        moved_layer.updateViewVisiable();
        moved_layer.setFriction(2.0f);
        if (mJsonArray == null) {
            RequestQueue mQueue = MyVolley.getRequestQueue();
            NetBaseParam netBaseParam = EditModeUtils.initWallpaperUrl();
            String url = netBaseParam.changeString(-1, -1);//although use page = 0 and pagesize = 5 is ok and return all papers,but it is irregular
                                                           // use -1 to get all wallpaper data and avoid server change lead to trouble
            EditModeUtils.logE(TAG, "url : " + url);
            EditModeUtils.startLoading(loading_iv);
            if (mQueue != null) {
                isRequestWallpaper = true;
                mQueue.add(new JsonArrayRequest(url, new JsonArrayRequestListener(), new JsonArrayRequestErrorListener()));
            }
        } else {
            try {
                setOnlineWallpaperData(false);
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void setFakeOnlineData() {
        // TODO Auto-generated method stub
        List<OnlineWallpaper> wallpapers = new ArrayList<OnlineWallpaper>();
        for (int i = 0; i < PAGESIZE; i++) {
            OnlineWallpaper onlineWallpaper = new OnlineWallpaper();
            wallpapers.add(onlineWallpaper);
        }
        wallpaperThumbnailAdapter.setOnlyLocal(false);
        wallpaperThumbnailAdapter.addOnlineItems(wallpapers);
        setWallpaperListParams();
    }


    private class DownloadsChangeObserver extends ContentObserver {
        public DownloadsChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            if (downloadingMap.size() <= 0) {
                return;
            }
            Set<Long> ids = downloadingMap.keySet();
//            String inArgs=ids.toString().replaceAll("\\[","(").replaceAll("\\]",")");
//            Log.e(TAG,"ids tostring: "+inArgs);
            for(Long id : ids) {
                Cursor c = mContext.getApplicationContext().getContentResolver().query(Downloads.Impl.CONTENT_URI
                        , new String[]{Downloads.Impl._ID, Downloads.Impl.COLUMN_TOTAL_BYTES
                        , Downloads.Impl.COLUMN_CURRENT_BYTES, Downloads.Impl.COLUMN_STATUS}
                        , Downloads.Impl._ID +" = ?", new String[]{String.valueOf(id)}, null);

                if (null == c) continue;
                Object[] values = new Object[4];
                if (c.moveToFirst()) {
                    values[0] = c.getLong(0);
                    values[1] = c.getInt(1);
                    values[2] = c.getInt(2);
                    values[3] = c.getInt(3);
                } else {
                    values[0] = 0;
                    values[1] = 0;
                    values[2] = 0;
                    values[3] = 0;
                }
                Message msg = Message.obtain(mHandler, Constants.ACTION_DOWNLOAD_UPDATEING, values);
                mHandler.sendMessage(msg);
                c.close();
            }
        }
    }

    private class JsonArrayRequestListener implements com.android.volley.Response.Listener<JSONArray> {

        @Override
        public void onResponse(JSONArray jsonArray) {
            // TODO Auto-generated method stub
            try {
                EditModeUtils.stopLoading(loading_iv);
                isRequestWallpaper = false;
                EditModeUtils.logE(TAG, "length: " + jsonArray.length() + "   json array : " + jsonArray.toString());
                if (jsonArray == null || jsonArray.length() == 0) {
                    return;
                }
                mJsonArray = jsonArray;
                setOnlineWallpaperData(false);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public void setOnlineWallpaperData(boolean setFakeData) throws JSONException,
            UnsupportedEncodingException {
        int length = mJsonArray.length();
        if (online_cnt >= length) {
            mLauncher.makeToast("wallpaper show done");
            // Toast.makeText(mContext, "wallpaper show done", 0).show();
            moved_layer.onRefreshComplete();
            return;
        }
        List<OnlineWallpaper> onlineWallpapers = null;
        if (setFakeData) {
            onlineWallpapers = wallpaperThumbnailAdapter.getOnlineWallpapers();
        } else {
            onlineWallpapers = new ArrayList<OnlineWallpaper>();
        }
        int pos = 0;
        for (int i = online_cnt; i < length; i++) {
            JSONObject json_obj = (JSONObject) mJsonArray.get(i);
            String pkgName = null;
            if (json_obj.has(Constants.WALLPAPER_PACKAGENAME)) {
                pkgName = json_obj.getString(Constants.WALLPAPER_PACKAGENAME);
                if (EditModeUtils.isWallpaperExists(pkgName)) {
                    online_cnt++;
                    continue;
                }
            }
            OnlineWallpaper wallpaper = null;
            if (setFakeData) {
                wallpaper = onlineWallpapers.get(pos);
            } else {
                wallpaper = new OnlineWallpaper();
            }
            wallpaper.setPackageName(pkgName);
            if (json_obj.has(Constants.WALLPAPER_ATTACHMENT)) {
                wallpaper.setUrl(URLDecoder.decode(json_obj.getString(Constants.WALLPAPER_ATTACHMENT), Constants.UTF8));
            }
            if (json_obj.has(Constants.WALLPAPER_THUMB)) {
                wallpaper.setThumbnail(URLDecoder.decode(json_obj.getString(Constants.WALLPAPER_THUMB), Constants.UTF8));
            }
            if (!setFakeData) {
                onlineWallpapers.add(wallpaper);
            }
            pos++;
            if (pos >= PAGESIZE) {
                break;
            }
        }
        online_cnt += PAGESIZE;
        if (!setFakeData) {

            wallpaperThumbnailAdapter.setOnlyLocal(false);
            wallpaperThumbnailAdapter.addOnlineItems(onlineWallpapers);
            setWallpaperListParams();
        } else {
            wallpaperThumbnailAdapter.notifyDataSetChanged();
        }
         moved_layer.onRefreshComplete();
    }

    private class JsonArrayRequestErrorListener implements ErrorListener {

        @Override
        public void onErrorResponse(VolleyError error) {
            // TODO Auto-generated method stub
            EditModeUtils.logE(TAG, "VolleyError : " + error.toString());
            isRequestWallpaper = false;
        }

    }


    public void setWallpaperListParams() {
        int count = wallpaperThumbnailAdapter.getCount();
        if (count > Constants.ONLINE_PAGESIZE * 2 + 1) {
            count = Constants.ONLINE_PAGESIZE * 2 + 1;
        }
        int layoutWidth = (int) (count * mContext.getResources().getDimensionPixelSize(R.dimen.edit_item_wallpaper_width));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                layoutWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        ((FrameLayout.LayoutParams)component_group_list_ll.getLayoutParams()).setMargins(0, 0, 0, 0);
        setHorizontalListParams(params);
    }

    public void setHorizontalListParams(LinearLayout.LayoutParams params) {
        component_group_list.setLayoutParams(params);
    }

    public void setWallpaperFiles() {
        if (wallpaperFiles != null)
            wallpaperFiles.clear();
        File in_file = INTERNAL_WALLPAPER;
        if (in_file.exists()) {
            File[] images = in_file.listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    // TODO Auto-generated method stub
                    name = name.toLowerCase();
                    return name.endsWith(".jpg") || name.endsWith(".png");
                }
            });
            if (images != null) {
                wallpaperFiles = new ArrayList(Arrays.asList(images));
            }
        }
        EditModeUtils.logE(TAG, "external_wallpaper_path : "
                + external_wallpaper_path);
//        wallpaper_path = Settings.System.getString(mContext.getContentResolver(),EditModeUtils.PREFERENCE_WALLPAPER_PATH);
        File out_file = new File(external_wallpaper_path);
        if (out_file.exists()) {
            if (wallpaperFiles == null)
                wallpaperFiles = new ArrayList<File>();
            File[] out_images = out_file.listFiles(filter);
            if (out_images != null) {
                List<File> out_files = Arrays.asList(out_images);
                wallpaperFiles.addAll(out_files);
            }
        }
        // }
        if (wallpaperFiles == null)
            wallpaperFiles = new ArrayList<File>();
    }

    public void showWidgetsGroup() {
        component_list_rl.setVisibility(View.GONE);
        //yixiao modify #70116 2015.2.12
        if (isGadgetsMapIniting || groupList==null || groupList.size() <= 0)
            return;
        final List<Object> objects;
        setHwLayerEnabled(true);
        if(!isWidgetAdded){
            component_widgets_ll.removeAllViews();
            objects = widgetInfos == null ? getAllWidgetViewInfos() : widgetInfos;
            showWidgetsGroup(objects,0,Math.min(objects.size(),Constants.LOCAL_ANIM_COUNT + 1));
        }else{
            objects = null;
        }
        setType(Type.WIDGETS_GROUP);
        component_widgets_ll.setVisibility(View.VISIBLE);
        executeHorizontalSwitch(null, edit_fun_ll, null, false);
        edit_list_ll.setTranslationX(0);
        showView(component_group_scroll, component_list_rl);
        int widget_width = getResources().getDimensionPixelSize(R.dimen.edit_widget_width);
        for (int i = 0; i < component_widgets_ll.getChildCount() && i <= Constants.LOCAL_ANIM_COUNT; i++) {
            View v = component_widgets_ll.getChildAt(i);
            WeakReference<View> ref = new WeakReference<View>(component_widgets_ll.getChildAt(i));
            v.setX(screenWidth());
            AnimatorListener listener = null;
            if(i == component_widgets_ll.getChildCount() - 1 || i == Constants.LOCAL_ANIM_COUNT){
                listener = new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if(!isWidgetAdded && objects != null){
                            showWidgetsGroup(objects,Math.min(objects.size(),Constants.LOCAL_ANIM_COUNT + 1),objects.size());
                            isWidgetAdded = true;
                        }
                        setHwLayerEnabled(false);
                    }
                };
            }
            EditModeUtils.translateAnimate(View.X, ref, Constants.ANIM_IN_DURATION * (Constants.LOCAL_ANIM_COUNT - i), (i + 1) * Constants.ANIM_IN_DURATION_DELAY, screenWidth(), i * widget_width, listener, null);
        }
    }

    private List<Object> getAllWidgetViewInfos() {
        List<Object> objects = new ArrayList<Object>();
        for (EditWidgetGroup widgetGroup : groupList) {
            boolean isExpand = isWidgetsExpand(widgetGroup);
            if (isExpand) {
                List<Object> infos = widgetGroup.getInfos();
                for (Object info : infos) {
                    objects.add(info);
                }
            } else {
                objects.add(widgetGroup);
            }
        }
        EditModeUtils.sortWidgetList(mContext,objects);
        return objects;
    }

    public void initGadgetsMap() {
        if (switchAnimators != null)
            switchAnimators.cancel();
        LauncherModel.runOnWorkerThread(new Runnable() {
            @Override
            public void run() {
                isGadgetsMapIniting = true;
                gadgetsMap = EditModeUtils.classifyGadgetByPackages(mContext);
                Set<Entry<String, ArrayList<Object>>> entrySets = gadgetsMap
                        .entrySet();
                groupList = new ArrayList<EditWidgetGroup>();
                for (Entry<String, ArrayList<Object>> entry : entrySets) {
                    EditWidgetGroup group = new EditWidgetGroup();
                    String pkgName = entry.getKey();
                    group.setPkgName(pkgName);
                    group.setInfos(entry.getValue());
                    group.setLabel(EditModeUtils.getAppName(mContext, pkgName));
                    groupList.add(group);
                }
                gadgetsMap.clear();
                isGadgetsMapIniting = false;
//                preloadAnimWidgetPreview();
            }
        });
    }

    public void showAnimItems() {
        setType(Type.ANIM_LIST);
        setHwLayerEnabled(true);
        Resources res = getResources();
        String[] entries = res
                .getStringArray(R.array.desktop_transition_effect_entries);
        String[] values = res
                .getStringArray(R.array.desktop_transition_effect_values);
        if (entries.length != values.length) {
            EditModeUtils.logE(TAG,
                    "anim entries length is not equal values length");
            return;
        }
        TransitionEffect cur_effect = PreferencesProvider
                .getTransitionEffect(mContext);
        List<Object> items = new ArrayList<Object>();
        for (int i = 0; i < entries.length; i++) {
            TransEffectItem item = new TransEffectItem();
            item.setEntry(entries[i]);
            item.setValue(values[i]);
            item.setDrawableId(EditModeUtils.getDrawableId(mContext,
                    EditModeUtils.EFFECTPREFIX + values[i].toLowerCase()));
            item.setSelected(cur_effect.toString().equals(values[i]));
            items.add(item);
        }
        final EditGridAdapter gridAdapter = initGridAdapter(
                component_group_list, items, GADGETS.EFFECTS);
        component_group_list_ll.setPadding(0, 0, 0, 0);
        int margin_bottom = res.getDimensionPixelSize(R.dimen.edit_anim_margin_bottom);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)component_group_list_ll.getLayoutParams();
        params.setMargins(0, 0, 0, margin_bottom);
        executeHorizontalSwitch(null, edit_fun_ll, null, false);
        edit_list_ll.setTranslationX(0);
        showView(component_list_rl, component_group_scroll);
        component_group_list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                // TODO Auto-generated method stub
                if (System.currentTimeMillis() - lastShowAnimTime < ANIM_DELAY_TIME) {
                    return;
                }
                lastShowAnimTime = System.currentTimeMillis();
                TransEffectItem item = (TransEffectItem) component_group_list
                        .getItemAtPosition(position);
                if (!item.isSelected()) {
                    mWorkspace.setTransitionEffect(item.getValue());
                    gridAdapter.setChecked(view, position);
                }
                final int curPage = mWorkspace.getCurrentPage();
                int totalPage = mWorkspace.getChildCount();
                final int scrollPage = curPage + 1 >= totalPage - 1 ? curPage - 1
                        : curPage + 1;
                if (scrollPage >= 0) {
                    switchRunnable = new Runnable() {
                        @Override
                        public void run() {
                            // TODO Auto-generated method stub
                            mWorkspace.snapToPage(curPage, 900);
                        }
                    };
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mWorkspace.snapToPage(scrollPage, 900);
                        }
                    }, 100);
                    mHandler.postDelayed(switchRunnable, ANIM_DELAY_TIME);
                }
            }
        });
    }

    /**
     * start =< index < end
     * @param infoList
     * @param start
     * @param end
     */
    private synchronized void showWidgetsGroup(List<Object> infoList, int start, int end) {
        int size = infoList.size();
        for (int i = start; i < end && i < size; i++) {
            addViewToGroupLayout(infoList.get(i));
        }
    }

    private boolean isWidgetsExpand(EditWidgetGroup widgetGroup) {
        boolean expand = false;
        String pkgName = widgetGroup.getPkgName();
        if (!TextUtils.isEmpty(pkgName)
                && pkgName.equals(EditModeUtils.SYSTEMGADGETS)) {
            expand = true;
        } else {
            List<Object> infos = widgetGroup.getInfos();
            expand = infos != null && infos.size() == 1;
        }
        return expand;
    }

    private void preloadAnimWidgetPreview(){
        widgetInfos = getAllWidgetViewInfos();
        PackageManager pm = mContext.getPackageManager();
        PREVIEWTYPE type;
        PendingAddItemInfo createItemInfo = null;
        for(int i = 0 ; i < Math.min(widgetInfos.size(),Constants.LOCAL_ANIM_COUNT + 1) ; i++){
            Object info = widgetInfos.get(i);
            EditWidgetGroup baseInfo = null;
            if (info instanceof EditWidgetGroup) {
                type = PREVIEWTYPE.GROUP;
                baseInfo = (EditWidgetGroup) info;
            } else {
                type = PREVIEWTYPE.WIDGET;
                createItemInfo = getCreateItemInfo(mContext, pm, info, null);
            }
            MyVolley.displayPreview(mContext, type, VIEWTYPE.GRID, baseInfo,
                    createItemInfo, null);
        }
    }

    private void addViewToGroupLayout(Object info) {
        if (info == null)
            return;
        PackageManager pm = mContext.getPackageManager();
        View view = View.inflate(mContext, R.layout.edit_widget_item, null);
        int width = getResources().getDimensionPixelSize(R.dimen.edit_widget_width);
        view.setLayoutParams(new LayoutParams(width, LayoutParams.MATCH_PARENT));
        ImageView iv = (ImageView) view.findViewById(R.id.item_iv);
        final TextView tv = (TextView) view.findViewById(R.id.item_tv);
        final ImageView item_expand = (ImageView) view
                .findViewById(R.id.item_expand);
        PREVIEWTYPE type;
        PendingAddItemInfo createItemInfo = null;
        EditWidgetGroup baseInfo = null;
        if (info instanceof EditWidgetGroup) {
            type = PREVIEWTYPE.GROUP;
            baseInfo = (EditWidgetGroup) info;
            tv.setText(baseInfo.getLabel());
            item_expand.setVisibility(View.VISIBLE);
        } else {
            type = PREVIEWTYPE.WIDGET;
            createItemInfo = getCreateItemInfo(mContext, pm, info, tv);
            item_expand.setVisibility(View.GONE);
        }

        MyVolley.displayPreview(mContext, type, VIEWTYPE.GRID, baseInfo,
                createItemInfo, new LoadPreviewListener(iv));
        load_preview_task_cnt++;
        final boolean isGroup = (type == PREVIEWTYPE.GROUP);
        if (!isGroup) {
            view.setTag(R.id.widget_list_item_tag, createItemInfo);
        } else {
            view.setTag(baseInfo);
        }
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                // TODO Auto-generated method stub
                cellLayout = mWorkspace.getCurrentCellLayout();
                if (cellLayout.mIsAddCellLayout) {
                    showAddFailToast();
                    return;
                }
                if (component_group_scroll.isScrollDisable()) {
                    setType(Type.WIDGETS_GROUP);
                    dismissWidgetList();
                    return;
                }
                if (!isGroup) {
                    final PendingAddItemInfo createItemInfo = (PendingAddItemInfo) v
                            .getTag(R.id.widget_list_item_tag);
                    int screen = mLauncher.getCurrentWorkspaceScreen();
                    CellLayout cellLayout = mLauncher.getCellLayout(0, screen);
                    final CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) cellLayout
                            .getTag();
                    int[] targetCell = new int[2];
                    int status = mLauncher.findCellForItemFromPick(createItemInfo, targetCell);
                    if (status == -1) {
                        mLauncher.makeToast(R.string.out_of_space);
                        return;
                    }
                    EditModeUtils.logE(TAG, "targetCell[0] : " + targetCell[0] + " targetCell[1]: " + targetCell[1] + " createItemInfo.spanX: " + createItemInfo.spanX + "  createItemInfo.spanY" + createItemInfo.spanY);
                    int offsetX = (int) (cellLayout.getCellWidth() * (targetCell[0] + createItemInfo.spanX * 0.5));
                    int offsetY = (int) (cellLayout.getCellHeight() * (targetCell[1] + createItemInfo.spanY * 0.5));
                    ImageView imageView = (ImageView) v.findViewById(R.id.item_iv);
                    Drawable d = imageView.getDrawable();
                    if (d != null) {
                        final ImageView iv = new ImageView(mContext);
                        iv.setLayoutParams(new LayoutParams(imageView.getWidth(), imageView.getHeight()));
                        iv.setImageDrawable(d);
                        EditModeLayer.this.addView(iv);
                        int[] location = new int[2];
                        v.getLocationOnScreen(location);
                        Animator animator = ObjectAnimator.ofPropertyValuesHolder(iv,
                                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 0),
                                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 0),
                                PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0),
                                PropertyValuesHolder.ofFloat(View.X, location[0], offsetX),
                                PropertyValuesHolder.ofFloat(View.Y, location[1], offsetY));
                        animator.setDuration(250);
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                v.setEnabled(false);
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                // TODO Auto-generated method stub
                                EditModeLayer.this.removeView(iv);
                                mLauncher.addItemFromPick(createItemInfo, cellInfo, null);
                                v.setEnabled(true);
                            }
                        });
                        animator.setInterpolator(new AccelerateInterpolator());
                        animator.start();
                    }
                } else {
                    if (widget_group_lv_ll.getVisibility() != View.VISIBLE) {
                        interceptTouch = true;//lqwang - pr69405 - modify
                        mScaleView = v;
                        widget_group_lv_ll.setVisibility(View.VISIBLE);
                        BitmapDrawable drawable = EditModeUtils
                                .getBlurScreenshot(mContext);
                        widget_group_lv_ll.setBackground(drawable);
                        EditWidgetGroup group = (EditWidgetGroup) v.getTag();
                        widget_group_lv.setTag(group);
                        List<Object> objects = group.getInfos();
                        final EditGridAdapter adapter = new EditGridAdapter(mLauncher, objects, GADGETS.WIDGETSLIST, VIEWTYPE.LIST);
                        LinearLayout.LayoutParams listview_params = (LinearLayout.LayoutParams) widget_group_lv.getLayoutParams();
                        int maxHeight = screenHeight() - mLauncher.getHotseat().getHeight();
                        listview_params.height = adapter.getWidgetListHeight() < maxHeight ? adapter.getWidgetListHeight() : maxHeight;
                        widget_group_lv.setAdapter(adapter);
                        playWidgetsGroupAnims(widget_group_lv_ll, false, 1.0f, 1.0f, 0, 1.0f, null);
                        playWidgetsGroupAnims(v, true, 1.0f, 1.0f, 1.0f, 0, null);
                        component_group_scroll.setScrollDisable(true);
                        setType(Type.WIDGETS_LIST);
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) widget_group_lv.getLayoutParams();
                        params.setMargins(0, 0, 0, mLauncher.getHotseat().getHeight());
                        widget_group_lv.setLayoutParams(params);
                        widget_group_lv.setPadding(0, 0, 0, 0);
                        item_expand.setImageResource(R.drawable.widget_close);
                        playWidgetsGroupAnims(v, false, 1.0f, GROUP_VIEW_SCALE, 1.0f, 1.0f, null);
                        tv.setVisibility(View.INVISIBLE);
                        widget_group_lv.setOnItemClickListener(new OnItemClickListener() {

                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                // TODO Auto-generated method stub
                                PendingAddItemInfo createItemInfo = (PendingAddItemInfo) view.getTag(R.id.widget_list_item_tag);
                                if (addWidgetWhenClick(createItemInfo)) {
                                    dismissWidgetList();
                                } else {
                                    mLauncher.makeToast(R.string.out_of_space);
                                    item_expand.setImageResource(R.drawable.widget_expand);
                                    dismissWidgetList();

                                }
                            }
                        });
                    } else {
                        item_expand.setImageResource(R.drawable.widget_expand);
                        dismissWidgetList();
                    }
                }
            }

        });
        view.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                // TODO Auto-generated method stub
                if (!isGroup)
                    beginDraggingWidget(v);
                return true;
            }
        });
        component_widgets_ll.addView(view);
    }


    private AnimationAdapter createAnimationAdapter(
            EditGridAdapter adapter) {
        // TODO Auto-generated method stub
        Random random = new Random();
        int anim_type = random.nextInt(ANIMATION_COUNT);
        AnimationAdapter animationAdapter = null;
        switch (anim_type) {
            case SCALE_IN:
                animationAdapter = new ScaleInAnimationAdapter(adapter);
                break;
            case BOTTOM_IN:
                animationAdapter = new SwingBottomInAnimationAdapter(adapter);
                break;
            case BOTTOM_RIGHT_IN:
//			SwingBottomInAnimationAdapter bottomInAnimationAdapter = new SwingBottomInAnimationAdapter(adapter);
//			animationAdapter = new SwingRightInAnimationAdapter(bottomInAnimationAdapter);
                animationAdapter = new SwingBottomInAnimationAdapter(adapter);
                break;
            case LEFT_IN:
                animationAdapter = new SwingLeftInAnimationAdapter(adapter);
                break;
            case RIGHT_IN:
                animationAdapter = new SwingRightInAnimationAdapter(adapter);
                break;
            default:
                animationAdapter = new SwingBottomInAnimationAdapter(adapter);

                break;
        }
        return animationAdapter;
    }

    public void showAddFailToast() {
        mLauncher.makeToast(R.string.widget_add_fail);
        // Toast.makeText(mContext, R.string.widget_add_fail, 0).show();
    }

    public void dismissWidgetList() {
        interceptTouch = false; //lqwang - pr69405 - modify
        if (widget_group_lv_ll.getVisibility() != View.VISIBLE || mScaleView == null)
            return;
        widget_group_lv.setEnabled(false);
        setType(Type.WIDGETS_GROUP);
        playWidgetsGroupAnims(mScaleView, false, GROUP_VIEW_SCALE, 1.0f, 1.0f, 1.0f, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // TODO Auto-generated method stub
                mScaleView.setEnabled(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // TODO Auto-generated method stub
                mScaleView.setEnabled(true);
            }
        });
        playWidgetsGroupAnims(widget_group_lv_ll, false, 1.0f, 1.0f, 1.0f, 0, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // TODO Auto-generated method stub
                Drawable d = widget_group_lv_ll.getBackground();
                if (d != null) {
                    EditModeUtils.recyleBitmapDrawable((BitmapDrawable) d);
                }
                d = widget_group_lv.getBackground();
                if (d != null) {
                    EditModeUtils.recyleBitmapDrawable((BitmapDrawable) d);
                }
                widget_group_lv_ll.setBackground(null);
                widget_group_lv.setBackground(null);
                widget_group_lv_ll.setVisibility(View.GONE);
                widget_group_lv.setEnabled(true);
            }
        });

        setWidgetsGroupVisiable();
        component_group_scroll.setScrollDisable(false);
    }

    private void setWidgetsGroupVisiable() {
        int childCount = component_widgets_ll.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View view = component_widgets_ll.getChildAt(i);
            TextView tv = (TextView) view.findViewById(R.id.item_tv);
            ImageView item_expand = (ImageView) view
                    .findViewById(R.id.item_expand);
            item_expand.setImageResource(R.drawable.widget_expand);
            tv.setVisibility(View.VISIBLE);
        }
        playWidgetsGroupAnims(mScaleView, true, 1.0f, 1.0f, 0.1f, 1.0f, null);
    }

    private void playWidgetsGroupAnims(final View v, boolean isViewExculde, float scaleStart, float scaleEnd, float startAlpha, float endAlpha, AnimatorListener listener) {
        ArrayList<Animator> anims = new ArrayList<Animator>();
        PropertyValuesHolder[] pvs = new PropertyValuesHolder[]{
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleStart,
                        scaleEnd),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleStart,
                        scaleEnd),
                PropertyValuesHolder.ofFloat(View.ALPHA, startAlpha,
                        endAlpha),
        };
        if (v == null) {
            int childCount = component_widgets_ll.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View view = component_widgets_ll.getChildAt(i);
                ObjectAnimator a = ObjectAnimator
                        .ofPropertyValuesHolder(view, pvs);
                anims.add(a);
            }
        } else if (v != null && isViewExculde) {
            int childCount = component_widgets_ll.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View view = component_widgets_ll.getChildAt(i);
                if (view != v) {
                    ObjectAnimator a = ObjectAnimator
                            .ofPropertyValuesHolder(view, pvs);
                    anims.add(a);
                }
            }
        } else {
            ObjectAnimator a = ObjectAnimator
                    .ofPropertyValuesHolder(v, pvs);
            if (startAlpha == endAlpha) {
                a.setInterpolator(new BounceInterpolator());
            }
            anims.add(a);
        }
        if (anims.size() > 0) {
            AnimatorSet set = new AnimatorSet();
            if (listener != null) {
                set.addListener(listener);
            } else {
                set.addListener(new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationStart(Animator arg0) {
                        // TODO Auto-generated method stub
                        v.setEnabled(false);
                    }

                    @Override
                    public void onAnimationEnd(Animator arg0) {
                        // TODO Auto-generated method stub
                        v.setEnabled(true);
                    }
                });
            }
            set.setDuration(600);
            set.playTogether(anims);
            set.start();
        }
    }

    private class LoadPreviewListener implements IIoadImageListener {
        private ImageView mIv;

        public LoadPreviewListener(ImageView iv) {
            mIv = iv;
        }

        @Override
        public void onLoadStart() {
            // TODO Auto-generated method stub

        }

        @Override
        public void onLoadComplete(Bitmap b) {
            // TODO Auto-generated method stub
            if (mIv != null && b != null) {
                mIv.setImageBitmap(b);
            }
            load_preview_task_cnt--;
        }

        @Override
        public void onLoadFail() {
            // TODO Auto-generated method stub

        }

    }

    private void showWidgetsList(ArrayList<Object> objects) {
        // TODO Auto-generated method stub
        setType(Type.WIDGETS_LIST);
        showView(component_group_scroll, edit_fun_ll, component_list_rl);
        component_widgets_ll.removeAllViews();
        PackageManager pm = mContext.getPackageManager();
        for (int i = 0; i < objects.size(); i++) {
            View view = View.inflate(mContext, R.layout.edit_grid_item, null);
            final ImageView iv = (ImageView) view.findViewById(R.id.item_iv);
            TextView tv = (TextView) view.findViewById(R.id.item_tv);
            PendingAddItemInfo createItemInfo = getCreateItemInfo(mContext, pm,
                    objects.get(i), tv);
            MyVolley.displayPreview(mContext, PREVIEWTYPE.WIDGET,
                    VIEWTYPE.GRID, null, createItemInfo,
                    new IIoadImageListener() {
                        @Override
                        public void onLoadStart() {
                            // TODO Auto-generated method stub

                        }

                        @Override
                        public void onLoadFail() {
                            // TODO Auto-generated method stub

                        }

                        @Override
                        public void onLoadComplete(Bitmap b) {
                            // TODO Auto-generated method stub
                            if (b != null)
                                iv.setImageBitmap(b);
                        }
                    });
            view.setTag(createItemInfo);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    PendingAddItemInfo createItemInfo = (PendingAddItemInfo) v
                            .getTag();
                    addWidgetWhenClick(createItemInfo);
                }
            });
            view.setOnLongClickListener(new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    // TODO Auto-generated method stub
                    beginDraggingWidget(v);
                    return true;
                }
            });
            component_widgets_ll.addView(view);
        }
    }

    private boolean addWidgetWhenClick(PendingAddItemInfo createItemInfo) {
        int screen = mLauncher.getCurrentWorkspaceScreen();
        CellLayout cellLayout = mLauncher.getCellLayout(0, screen);
        CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) cellLayout
                .getTag();
        return mLauncher.addItemFromPick(createItemInfo, cellInfo, null);
    }

    private PendingAddItemInfo getCreateItemInfo(Context context,
                                                 PackageManager pm, Object object, TextView tv) {
        PendingAddItemInfo createItemInfo = null;
        String mDimensionsFormatString = getResources().getString(
                R.string.widget_dims_format);
        if (object instanceof AppWidgetProviderInfo) {
            AppWidgetProviderInfo info = (AppWidgetProviderInfo) object;
            createItemInfo = new PendingAddWidgetInfo(info, null, null);
            int[] spanXY = Launcher.getSpanForWidget(context, info);
            createItemInfo.spanX = spanXY[0];
            createItemInfo.spanY = spanXY[1];
            int[] minSpanXY = Launcher.getMinSpanForWidget(context, info);
            createItemInfo.minSpanX = minSpanXY[0];
            createItemInfo.minSpanY = minSpanXY[1];
            if(tv != null)
                tv.setText(info.label.concat(String.format(mDimensionsFormatString,
                    spanXY[0], spanXY[1])));
        } else if (object instanceof ResolveInfo) {
            ResolveInfo info = (ResolveInfo) object;
            createItemInfo = new PendingAddShortcutInfo(
                    ((ResolveInfo) object).activityInfo);
            createItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
            createItemInfo.spanX = 1;
            createItemInfo.spanY = 1;
            createItemInfo.componentName = new ComponentName(
                    info.activityInfo.packageName, info.activityInfo.name);
            if(tv != null)
                tv.setText((info.loadLabel(pm).toString()));
        }
        return createItemInfo;
    }

    private EditGridAdapter initGridAdapter(HorizontalListView listView, List<Object> objects,
                                            GADGETS gadgets) {
        EditGridAdapter gridAdapter = new EditGridAdapter(mLauncher, objects,
                gadgets, VIEWTYPE.GRID);
        int columnWidth = res.getDimensionPixelSize(R.dimen.edit_anim_width);
        int layoutWidth = Constants.LOCAL_ANIM_COUNT * 2 * columnWidth;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                layoutWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        listView.setLayoutParams(params);
        listView.setAdapter(gridAdapter);
        return gridAdapter;
    }

    private void showView(View showView, View... hideViews) {
        // TODO Auto-generated method stub
        showView.setVisibility(View.VISIBLE);
        for (View view : hideViews) {
            view.setVisibility(View.GONE);
        }
    }

    public Intent getCropIntent(Uri uri, int width, int height) {
        //for android 4.4 gallery begin, 4.4 uri not contains path: content://com.android.providers.media.documents/document/image%3A8017
        if (EditModeUtils.isKitKat() && DocumentsContract.isDocumentUri(mContext, uri)) {
            String path = EditModeUtils.getPathFromUri(mContext, uri);
            uri = Uri.fromFile(new File(path));//change to contain path uri file:///storage/sdcard0/DCIM/Camera/IMG_20140806_033233.jpg
        }
        //for android 4.4 gallery end
        Intent intent = new Intent("com.android.camera.action.CROP");
        if (uri != null) {
            File f = new File(
//                    android.os.Environment
//                            .getExternalStorageAppCacheDirectory("com.lewa.launcher"),
                    android.os.Environment.getExternalStoragePublicDirectory("com.lewa.launcher"),
                    "cropped_image");
            try {
                if (!f.getParentFile().exists())
                    f.getParentFile().mkdirs();
                f.createNewFile();
            } catch (Exception e) {
            }
            temp_uri = Uri.fromFile(f);
            try {
                intent.setDataAndType(uri, "image/*");
                intent.putExtra("crop", "true");
                intent.putExtra("aspectX", width);
                intent.putExtra("aspectY", height);

                intent.putExtra("scaleUpIfNeeded", true);
                intent.putExtra("outputX", width);
                intent.putExtra("outputY", height);

                intent.putExtra("scale", true);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, temp_uri);
                intent.putExtra("return-data", false);
                intent.putExtra("outputFormat",
                        Bitmap.CompressFormat.JPEG.toString());
                intent.putExtra("noFaceDetection", true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return intent;
    }

    public void setWallpaperFromUri() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                try {
                    final File f = new File(external_wallpaper_path
                            + "/com.lewa.customwallpaper" + ".jpg");
                    if (!f.getParentFile().exists())
                        f.getParentFile().mkdirs();
                    final boolean isNewAdd = !f.exists();
                    if (isNewAdd) {
                        boolean iscreate = f.createNewFile();
                    }
                    EditModeUtils.copyToFile(mContext.getContentResolver()
                            .openInputStream(temp_uri), f);
                    WallpaperManager wallpaperManager = WallpaperManager
                            .getInstance(mContext);
                    int mDisplayWidth = wallpaperManager
                            .getDesiredMinimumWidth();
                    int mDisplayHeight = wallpaperManager
                            .getDesiredMinimumHeight();
                    final String path = f.getAbsolutePath();
                    InputStream is = EditModeUtils.getCalculateStream(
                            f.getAbsolutePath(), mDisplayWidth, mDisplayHeight);
                    wallpaperManager.setStream(is);

                    Settings.System.putString(mLauncher.getContentResolver(),EditModeUtils.PREFERENCE_WALLPAPER_PATH, path);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // TODO Auto-generated method stub
                            if (wallpaperFiles != null) {
                                int size = wallpaperFiles.size();
                                if (size > 0 && !isNewAdd) {
                                    wallpaperFiles.remove(size - 1);
                                }
                                wallpaperFiles.add(f);
                            }
                            if (!TextUtils.isEmpty(path)) {
                                Settings.System.putString(mLauncher.getContentResolver(),EditModeUtils.PREFERENCE_WALLPAPER_PATH, path);
                                wallpaperThumbnailAdapter.setWallpaperPath("");
                                clearCustomPaperCache(path);
                            }
                            wallpaperThumbnailAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        };
        MyVolley.execRunnable(runnable);
    }

    public void clearCustomPaperCache(final String path) {
        if (wallpaperThumbnailAdapter != null) {
            String key = wallpaperThumbnailAdapter.getCacheKey(path);
            LauncherApplication.removeCacheBitmap(key);
        }
    }

    public int screenWidth() {
        return dm.widthPixels;
    }

    public int screenHeight() {
        return dm.heightPixels;
    }

    private void executeVerticalSwitch(View in, View out, AnimatorListenerAdapter listener) {
        float in_from = in != null ? in.getHeight() * 3 : 0;
        float out_dest = out != null ? out.getHeight() : 0;
        executeSwitchAnim(View.TRANSLATION_Y,in,in_from,out,out_dest,listener);
    }
    private void executeHorizontalSwitch(View in, View out, AnimatorListenerAdapter listener, boolean leftIn) {
        int direction = leftIn ? -1 : 1;
        float in_from = in != null ? in.getWidth() * 1.2f * direction : 0;
        float out_dest = out != null ? -out.getWidth() * 1.2f * direction : 0;
        executeSwitchAnim(View.TRANSLATION_X,in,in_from,out,out_dest,listener);
    }
    private void executeSwitchAnim(Property<View, Float> property,View in,float fromIn,View out,float toOut,AnimatorListenerAdapter listener){
        switchAnimators = new AnimatorSet();
        switchAnimators.setDuration(ANIM_DURATION);
        List<Animator> anims = new ArrayList<Animator>();
        if (in != null) {
            Animator enter = ObjectAnimator.ofFloat(in,property,fromIn, 0);
            addRunningAnimator(enter);
            anims.add(enter);
        }
        if (out != null) {
            Animator exit = ObjectAnimator.ofFloat(out, property, 0,toOut);
            addRunningAnimator(exit);
            anims.add(exit);
        }
        if (listener != null) {
            switchAnimators.addListener(listener);
        }
        switchAnimators.playTogether(anims);
        switchAnimators.setInterpolator(new DecelerateInterpolator());
        switchAnimators.start();
    }

    public boolean isExecAnimRunning() {
        return switchAnimators != null && switchAnimators.isRunning();
    }

    protected void initScreenParam() {
        float density = dm.density;

        if (dm.widthPixels == 480
                && dm.heightPixels == 854) {
            isWVGA = true;
        }
        if (density == 1.0) {
            isWVGA = false;

        } else if (density == 1.5) {
            isWVGA = true;
        } else {
            isWVGA = false;
        }

        if (dm.densityDpi == 120) {
            screenDPI = "LDPI";
        } else if (dm.densityDpi == 160) {
            screenDPI = "MDPI";
        } else if (dm.densityDpi == 240) {
            screenDPI = "HDPI";
        } else if (dm.densityDpi == 320) {
            screenDPI = "XHDPI";
        } else if (dm.densityDpi == 480) {
            screenDPI = "XXHDPI";
        } else {
            screenDPI = "HDPI";
        }
    }

    Runnable switchRunnable = null;
//    private String wallpaper_path;
    private List<File> wallpaperFiles;
    private WallpaperThumbnailAdapter wallpaperThumbnailAdapter;
    private long lastShowAnimTime;
    private static final int ANIM_DELAY_TIME = 600;
    private AnimatorSet switchAnimators;
    public static boolean isWVGA = false;
    public static String screenDPI = "";
    private ImageView loading_iv;

    class ModeSwitchListener extends AnimatorListenerAdapter {
        EditModeLayer editModeLayer;
        boolean enterEditMode;

        public ModeSwitchListener(EditModeLayer edit, boolean enterEditMode) {
            editModeLayer = edit;
            this.enterEditMode = enterEditMode;
        }

        public void onAnimationEnd(Animator arg0) {
            // TODO Auto-generated method stub
            if (!enterEditMode) {
                setVisibility(View.INVISIBLE);
            } else {
                mWorkspace.setPageSwitchListener(editModeLayer);
            }
        }
    }

    public void setEditLayerVisible(boolean visible) {
        if (switchAnimators != null && switchAnimators.isRunning()) {
            switchAnimators.cancel();
        }
        if (visible) {
            setVisibility(View.VISIBLE);
            executeVerticalSwitch(edit_container, null, new ModeSwitchListener(this, true));
        } else {
            executeVerticalSwitch(null, edit_container, new ModeSwitchListener(this, false));
        }
    }

    public void addRunningAnimator(Animator animator){
        if(animator != null){
            runningAnims.add(animator);
        }
    }

    public void clearRunningAnimator(){
        for(Animator animator : runningAnims){
            if(animator.isRunning()){
                animator.cancel();
            }
        }
        runningAnims.clear();
        setHwLayerEnabled(false);
    }
}
