package com.lewa.launcher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.Set;
import android.app.ActivityOptions;//zwsun@letek.com 20150108 start
import android.app.WallpaperManager;
import android.os.PatternMatcher;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.animation.*;
import lewa.laml.RenderThread;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
//import android.app.ActionBar;

import com.lewa.launcher.DropTarget.DragObject;
import com.lewa.launcher.MessageModel.MessageCallbacks;
import com.lewa.launcher.constant.Constants;
import com.lewa.launcher.preference.MyLauncherSettings;
import com.lewa.launcher.preference.PreferencesProvider;
import com.lewa.launcher.pulltorefresh.PullToRefreshBase;
import com.lewa.launcher.view.MovedHotseatWrapper;
import com.lewa.reflection.Advanceable;
import com.lewa.reflection.ReflUtils;
import com.lewa.toolbox.ChangeWallpaperTask;
import com.lewa.toolbox.EditModeUtils;
import com.lewa.toolbox.MyVolley;
import com.lewa.toolbox.YLog;
import lewa.support.v7.app.ActionBar;
import lewa.support.v7.app.ActionBarActivity;
public final class Launcher extends ActionBarActivity
        implements View.OnClickListener, OnLongClickListener, LauncherModel.Callbacks,
                   View.OnTouchListener , MessageCallbacks {
    //yixiao add #70114 2015.3.17
    public static boolean hotsetMove = false;
	//yixiao add 2015.3.17
    public static boolean enterPreview = false;
    //yixiao add #70117 2015.2.13
    public static boolean exitPreview = false;
    static final String TAG = "Launcher";
    public static final boolean HARDWAREABLE = false;
    static final boolean LOGD = false;
    static final boolean PROFILE_STARTUP = false;
    static final boolean DEBUG_WIDGETS = false;
    private static final int MENU_GROUP_NORMAL = 1;
    private static final int MENU_EDITMODE = Menu.FIRST + 1;
    private static final int MENU_PREVIEW = MENU_EDITMODE + 1;
    private static final int MENU_WALLPAPER_SETTINGS = MENU_PREVIEW + 1;
    private static final int MENU_LAUNCHER_SETTINGS = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_SYSTEM_SETTINGS = MENU_LAUNCHER_SETTINGS + 1;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPLICATION = 6;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    private static final int REQUEST_BIND_APPWIDGET = 11;
    View pifView ;
    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";
    static final String EXTRA_SHORTCUT_USER_CREATED = "user_created";
    public static final String ACTION_SET_WALLPAPER = "lewa.intent.action.SET_WALLPAPER";

    private static final String PREFERENCES = "launcher.preferences";
    // To turn on these properties, type
    // adb shell setprop log.tag.PROPERTY_NAME [VERBOSE | SUPPRESS]

    // The Intent extra that defines whether to ignore the launch animation
    static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "com.android.launcher.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CONTAINER = "launcher.add_container";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cell_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cell_y";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_span_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_span_y";
    // Type: parcelable
    private static final String RUNTIME_STATE_PENDING_ADD_WIDGET_INFO = "launcher.add_widget_info";

    /** The different states that Launcher can be in. */
    private enum State { NONE, WORKSPACE, APPS_CUSTOMIZE, APPS_CUSTOMIZE_SPRING_LOADED };
    private State mState = State.WORKSPACE;

    static final int APPWIDGET_HOST_ID = 1024;

    // How long to wait before the new-shortcut animation automatically pans the workspace
    private static int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 10;

    private final BroadcastReceiver mCloseSystemDialogsReceiver = new CloseSystemDialogsIntentReceiver();
    private final ContentObserver mWidgetObserver = new AppWidgetResetObserver();

    private LayoutInflater mInflater;

    private Workspace mWorkspace;
    private DragLayer mDragLayer;
    private DragController mDragController;

    private AppWidgetManager mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private ItemInfo mPendingAddInfo = new ItemInfo();
    private AppWidgetProviderInfo mPendingAddWidgetInfo;

    private int[] mTmpAddItemCellCoordinates = new int[2];

    private FolderInfo mFolderInfo;

    // Begin, added by zhumeiquan, 20120713
    public DesktopIndicator mDesktopIndicator;
    private DeleteDropTarget mDeleteZone;
    private SystemShakeListener mShaker;
    public Hotseat mHotseat;
    public MovedHotseatWrapper movedHotseatWrapper;
    private MessageModel mMessageModel;
    private boolean isSettingLauncher;//Bug48478 Add by Fan.Yang
    private ThumbnailView mThumbnailView;
    private ThumbnailViewAdapter mThumbnailAdapter;
    private boolean isPreviewMode;
    private Hidden mHidden;
    private boolean isOpenHiddenApps;
    private AppRecommend mAppRecommend;
    private boolean isOpenAppRecommend = false;
    public static boolean isLocalChanged = false;
    public static boolean isInstallAdd = false;

    private final AccelerateInterpolator interpolator = new AccelerateInterpolator();
    private final int ENTER_DURATION = 400;
    private final int EXIT_DURATION = 600;
    private final float SCALE = 0.33f ;
    public boolean isAnimating;
    // End

//    private FolderOpenView openFolderWallpaperBg;
//    private FolderOpenView openFolderBgUp;
//    private FolderOpenView openFolderBgDown;
//    private FolderOpenView openFolderBgLeft;
//    private FolderOpenView openFolderBgRight;

    private boolean mAutoAdvanceRunning = false;

    private Bundle mSavedState;
    // We set the state in both onCreate and then onNewIntent in some cases, which causes both
    // scroll issues (because the workspace may not have been measured yet) and extra work.
    // Instead, just save the state that we need to restore Launcher to, and commit it in onResume.
    private State mOnResumeState = State.NONE;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mOnResumeNeedsLoad;
    private boolean mFloatingExited = false;

    // Keep track of whether the user has left launcher
    private static boolean sPausedFromUserAction = false;

    private LauncherModel mModel;
    private IconCache mIconCache;
    private boolean mUserPresent = true;
    private boolean mVisible = false;
    private boolean mAttached = false;

    private static LocaleConfiguration sLocaleConfiguration = null;

    private static HashMap<Long, FolderInfo> sFolders = new HashMap<Long, FolderInfo>();

    // Related to the auto-advancing of widgets
    private final int ADVANCE_MSG = 1;
    public static final int SHOW_INPUT_MSG = 2;
    private final int SHOW_APP_RECOMMENDS_MSG = 3;
    private final int HIDE_APP_RECOMMENDS_MSG = 4;
    private final int mAdvanceInterval = 20000;
    private final int mAdvanceStagger = 250;
    private long mAutoAdvanceSentTime;
    private long mAutoAdvanceTimeLeft = -1;
    private HashMap<View, AppWidgetProviderInfo> mWidgetsToAdvance =
        new HashMap<View, AppWidgetProviderInfo>();

    private final ArrayList<Integer> mSynchronouslyBoundPages = new ArrayList<Integer>();

    static final ArrayList<String> sDumpLogs = new ArrayList<String>();

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;

    // Holds the page that we need to animate to, and the icon views that we need to animate up
    // when we scroll to that page on resume.
    private int mNewShortcutAnimatePage = -1;
    private ArrayList<View> mNewShortcutAnimateViews = new ArrayList<View>();

    private BubbleTextView mWaitingForResume;
    private ProgressDialog mLoadingDialog;
    public EditModeLayer editModeLayer;

    private boolean piflowEnabled = false;
    //private Update mUpdate ;
    // for BI
    private long mOlderTime ;
    private static final String EFFECTIVE_TIME = "Effective_time" ;
    private static final long DATA_TIME = 24 * 60 * 60 * 1000;
    // end
    //yixiao add #974316
	public static String foldeName = "";
	
    private Runnable mBuildLayersRunnable = new Runnable() {
        public void run() {
            if (mWorkspace != null) {
                mWorkspace.buildPageHardwareLayers();
            }
        }
    };

    private static ArrayList<PendingAddArguments> sPendingAddList
            = new ArrayList<PendingAddArguments>();

    private static class PendingAddArguments {
        int requestCode;
        Intent intent;
        long container;
        int screen;
        int cellX;
        int cellY;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LauncherApplication app = ((LauncherApplication)getApplication());
        if (!app.LoadingProccessShowed && !isFinishing()) {
            try {
                mLoadingDialog = ProgressDialog.show(this, null, getString(R.string.loading_prompt), true, false);
            } catch(Exception e) {
                e.printStackTrace();
            }
            app.LoadingProccessShowed = true;
        }
        if (Folder.isRenameFolderWithDialog()) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        getWindow().setFlags(~WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR, WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        mSharedPrefs = PreferencesProvider.getSharedPreferences(this);
        mModel = app.setLauncher(this);
        
        mMessageModel = app.getMessageModel();
        mMessageModel.loadAndInitUnreadShortcuts();  
        
        mIconCache = app.getIconCache();
        mDragController = new DragController(this);
        mInflater = getLayoutInflater();

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        piflowEnabled = PreferencesProvider.piflowEnabled(this);


        // If we are getting an onCreate, we can actually preempt onResume and unset mPaused here,
        // this also ensures that any synchronous binding below doesn't re-trigger another
        // LauncherModel load.
        mPaused = false;

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing(
                    Environment.getExternalStorageDirectory() + "/launcher");
        }

        checkForLocaleChange();
        setContentView(R.layout.launcher);
        getWindow().getDecorView().setFitsSystemWindows(false);
        setupViews();
        registerContentObservers();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }
        //lqwang-PR62463-modify begin
        if(LauncherApplication.isScreenLandscape(getApplicationContext())){
            mDragLayer.setVisibility(View.GONE);
            //lqwang-PR62950-modify begin
            updateWallpaperVisibility(false);
            //lqwang-PR62950-modify end
        }else{
            mDragLayer.setVisibility(View.VISIBLE);
            //lqwang-PR62950-modify begin
            updateWallpaperVisibility(true);
            //lqwang-PR62950-modify end
            if (!mRestoring) {
                if (sPausedFromUserAction) {
                    // If the user leaves launcher, then we should just load items asynchronously when
                    // they return.
                    mModel.startLoader(true, -1);
                } else {
                    // We only load the page synchronously if the user rotates (or triggers a
                    // configuration change) while launcher is in the foreground
                    mModel.startLoader(true, mWorkspace.getCurrentPage());
                }
            }
        }
        //lqwang-PR62463-modify end

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter);

        initDisplay();        
        // Remove background of title bar
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setBackgroundDrawable(null);
            // Remove title of title bar
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.hide();
        }
        //lqwang - PR63468 - modify begin
        registerThemeChangeReceiver();
        //lqwang - PR63468 - modify end

        //lqwang - PR64359 - modify begin
        if (Settings.System.getInt(getContentResolver(), Constants.LAUNCHER_FIRST_RUN,Constants.FIRST_RUN) == Constants.FIRST_RUN) {
            setDefaultWallpaper();
            Settings.System.putInt(getContentResolver(),Constants.LAUNCHER_FIRST_RUN,Constants.ALREADY_RUN);
        }
        //lqwang - PR64359 - modify end
        EditModeUtils.setInEditMode(this,false);
    }
    //lqwang - PR63468 - modify begin
    private void registerThemeChangeReceiver() {
        IntentFilter filter = new IntentFilter(Constants.ACTION_CHANGE_THEME);
        filter.addDataScheme("content");
        filter.addDataAuthority("com.lewa.themechooser.themes", null);
        filter.addDataPath("/theme", PatternMatcher.PATTERN_PREFIX);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        try {
            filter.addDataType("vnd.lewa.cursor.item/theme");
            filter.addDataType("vnd.lewa.cursor.item/style");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.e(TAG, "Could not add MIME types to filter", e);
        }
        EditModeUtils.logE(TAG,"registerReceiver mThemeChangeReceiver");
        registerReceiver(mThemeChangeReceiver, filter);
    }

    private BroadcastReceiver mThemeChangeReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(Constants.ACTION_CHANGE_THEME)){
                EditModeUtils.logE(TAG,"change theme action received");
                //yixiao add #71002
                if(mAppRecommend!=null)
                mAppRecommend.saveDownloading();
            }
        }
    };
    //lqwang - PR63468 - modify end
    private void registerShakeLister() {
        EditModeUtils.logE(TAG, "registerShakeLister: " + PreferencesProvider.isSupportShake(this));
        if (PreferencesProvider.isSupportShake(this)) {
            if (mShaker == null) {
                mShaker = new SystemShakeListener(this);
            }
        }
    }

    private void unregisterShakeLister() {
        if (mShaker != null) {
            mShaker.pause();
            mShaker = null;
        }
    }

    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        sPausedFromUserAction = true;
    }

    private void checkForLocaleChange() {
        if (sLocaleConfiguration == null) {
            new AsyncTask<Void, Void, LocaleConfiguration>() {
                @Override
                protected LocaleConfiguration doInBackground(Void... unused) {
                    LocaleConfiguration localeConfiguration = new LocaleConfiguration();
                    readConfiguration(Launcher.this, localeConfiguration);
                    return localeConfiguration;
                }

                @Override
                protected void onPostExecute(LocaleConfiguration result) {
                    sLocaleConfiguration = result;
                    checkForLocaleChange();  // recursive, but now with a locale configuration
                }
            }.execute();
            return;
        }

        final Configuration configuration = getResources().getConfiguration();

        final String previousLocale = sLocaleConfiguration.locale;
        final String locale = configuration.locale.toString();

        final int previousMcc = sLocaleConfiguration.mcc;
        final int mcc = configuration.mcc;

        final int previousMnc = sLocaleConfiguration.mnc;
        final int mnc = configuration.mnc;

        boolean localeChanged = !locale.equals(previousLocale) || mcc != previousMcc || mnc != previousMnc;

        if (localeChanged) {
            sLocaleConfiguration.locale = locale;
            sLocaleConfiguration.mcc = mcc;
            sLocaleConfiguration.mnc = mnc;
            final LocaleConfiguration localeConfiguration = sLocaleConfiguration;
            new Thread("WriteLocaleConfiguration") {
                @Override
                public void run() {
                    //this method is synchronized,some times main thread will wait for iconCache lock so move it to other thread
                    mIconCache.flush();
                    writeConfiguration(Launcher.this, localeConfiguration);
                }
            }.start();
        }
    }

    private static class LocaleConfiguration {
        public String locale;
        public int mcc = -1;
        public int mnc = -1;
    }

    private static void readConfiguration(Context context, LocaleConfiguration configuration) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(context.openFileInput(PREFERENCES));
            configuration.locale = in.readUTF();
            configuration.mcc = in.readInt();
            configuration.mnc = in.readInt();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static void writeConfiguration(Context context, LocaleConfiguration configuration) {
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(context.openFileOutput(PREFERENCES, MODE_PRIVATE));
            out.writeUTF(configuration.locale);
            out.writeInt(configuration.mcc);
            out.writeInt(configuration.mnc);
            out.flush();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            context.getFileStreamPath(PREFERENCES).delete();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !mModel.isLoadingWorkspace();
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private boolean completeAdd(PendingAddArguments args) {
        boolean result = false;
        switch (args.requestCode) {
            case REQUEST_PICK_APPLICATION:
                completeAddApplication(args.intent, args.container, args.screen, args.cellX, args.cellY);
                break;
            case REQUEST_PICK_SHORTCUT:
                processShortcut(args.intent);
                break;
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(args.intent, args.container, args.screen, args.cellX, args.cellY);
                result = true;
                break;
            case REQUEST_CREATE_APPWIDGET:
                int appWidgetId = args.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                completeAddAppWidget(appWidgetId, args.container, args.screen, null, null);
                result = true;
                break;
            case REQUEST_PICK_WALLPAPER:
                // We just wanted the activity result here so we can clear mWaitingForResult
                break;
        }
        // Before adding this resetAddInfo(), after a shortcut was added to a workspace screen,
        // if you turned the screen off and then back while in All Apps, Launcher would not
        // return to the workspace. Clearing mAddInfo.container here fixes this issue
        resetAddInfo();
        return result;
    }

    @Override
    protected void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        mWaitingForResult = false;
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(appWidgetId, mPendingAddInfo, null, mPendingAddWidgetInfo);
            }
            return;
        }
        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);

        // We have special handling for widgets
        if (isWidgetDrop) {
            int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (appWidgetId < 0) {
                Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not returned from the \\" +
                        "widget configuration activity.");
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
            } else {
                completeTwoStageWidgetDrop(resultCode, appWidgetId);
            }
            return;
        }

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.
        if (resultCode == RESULT_OK && mPendingAddInfo.container != ItemInfo.NO_ID) {
            final PendingAddArguments args = new PendingAddArguments();
            args.requestCode = requestCode;
            args.intent = data;
            args.container = mPendingAddInfo.container;
            args.screen = mPendingAddInfo.screen;
            args.cellX = mPendingAddInfo.cellX;
            args.cellY = mPendingAddInfo.cellY;
            if (isWorkspaceLocked()) {
                sPendingAddList.add(args);
            } else {
                completeAdd(args);
            }
        }
        mDragLayer.clearAnimatedView();

        if (data == null) return;
        Uri uri = data.getData();
        switch (requestCode) {
            case EditModeLayer.SELECT_PICTRUE_FOR_WALLPAPER:
                try {
                    startActivityForResult(editModeLayer.getCropIntent(uri, EditModeLayer.WALLPAPER_WIDTH, EditModeLayer.WALLPAPER_HEIGHT), EditModeLayer.FROM_WALLPAPER);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
                break;
            case EditModeLayer.FROM_WALLPAPER:
                if(data.getExtras() != null){
                    editModeLayer.setWallpaperFromUri();
                }
                break;
            default:
                break;
        }
    }

    private void completeTwoStageWidgetDrop(final int resultCode, final int appWidgetId) {
        CellLayout cellLayout = mWorkspace.getCellLayout(mPendingAddInfo.screen);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mPendingAddWidgetInfo != null ? mAppWidgetHost.createView(this, appWidgetId,
                    mPendingAddWidgetInfo) : null;
            boundWidget = layout;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    completeAddAppWidget(appWidgetId, mPendingAddInfo.container,
                            mPendingAddInfo.screen, layout, null);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {

                }
            };
        }
        if (mDragLayer.getAnimatedView() != null) {
            mWorkspace.animateWidgetDrop(mPendingAddInfo, cellLayout,
                    (DragView) mDragLayer.getAnimatedView(), onCompleteRunnable,
                    animationType, boundWidget, true);
        } else {
            // The animated view may be null in the case of a rotation during widget configuration
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mFloatingExited = false;
        isSettingLauncher = false;//Bug48478 Add by Fan.Yang

        //mUpdate.checkUpdate();        
        // Restore the previous launcher state
        if (mOnResumeState == State.WORKSPACE) {
            showWorkspace(false);
        }
        mOnResumeState = State.NONE;

        // Process any items that were added while Launcher was away
        InstallShortcutReceiver.flushInstallQueue(this);

        mPaused = false;

        // Restart launcher when preferences are changed
        mWorkspace.setWallpaperScroll(PreferencesProvider.getScrollWallpaper(this));
        mWorkspace.setScreenCycle(PreferencesProvider.getScreenCycle(this));
        if (isPrefChanged(PreferencesProvider.CHANGED)) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
        sPausedFromUserAction = false;
        if (mRestoring || mOnResumeNeedsLoad) {
            mWorkspaceLoading = true;
            mModel.startLoader(true, -1);
            mRestoring = false;
            mOnResumeNeedsLoad = false;
        }

        // Reset the pressed state of icons that were locked in the press state while activities
        // were launching
        if (mWaitingForResume != null) {
            // Resets the previous workspace icon press state
            mWaitingForResume.setStayPressed(false);
        }
        // It is possible that widgets can receive updates while launcher is not in the foreground.
        // Consequently, the widgets will be inflated in the orientation of the foreground activity
        // (framework issue). On resuming, we ensure that any widgets are inflated for the current
        // orientation.
        getWorkspace().reinflateWidgetsIfNecessary();
        RenderThread.globalThread().setPausedSafety(false);
        ReflUtils.disableStatusBarBackground(this, ReflUtils.STATUSBAR_DISABLE_BACKGROUND);
    }

    public boolean isPrefChanged(String key) {
        SharedPreferences prefs = PreferencesProvider.getSharedPreferences(this);
        boolean changed = prefs.getBoolean(key, false);
        if (changed) {
            prefs.edit().putBoolean(key, false).commit();
        }
        return changed;
    }

    @Override
    protected void onPause() {
        // NOTE: We want all transitions from launcher to act as if the wallpaper were enabled
        // to be consistent.  So re-enable the flag here, and we will re-disable it as necessary
        // when Launcher resumes and we are still in AllApps.
        mWorkspace.removeRenderResumeCallback();
        //lqwang-PR62950-modify begin
        if(!LauncherApplication.isScreenLandscape(getApplicationContext()))
            updateWallpaperVisibility(true);
        //lqwang-PR62950-modify end
        super.onPause();
        mPaused = true;
        mDragController.cancelDrag();
        ReflUtils.disableStatusBarBackground(this, ReflUtils.STATUSBAR_DISABLE_NONE);
        mDragController.resetLastGestureUpTime();
        RenderThread.globalThread().setPausedSafety(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mWorkspace.removeAddScreenView(false);
        closeFolder();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        // Flag the loader to stop early before switching
        mModel.stopLoader();
        return Boolean.TRUE;
    }

    // We can't hide the IME if it was forced open.  So don't bother
    /*
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            final InputMethodManager inputManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            inputManager.hideSoftInputFromWindow(lp.token, 0, new android.os.ResultReceiver(new
                        android.os.Handler()) {
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            Log.d(TAG, "ResultReceiver got resultCode=" + resultCode);
                        }
                    });
            Log.d(TAG, "called hideSoftInputFromWindow from onWindowFocusChanged");
        }
    }
    */

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int uniChar = event.getUnicodeChar();
        final boolean handled = super.onKeyDown(keyCode, event);
        final boolean isKeyNotWhitespace = uniChar > 0 && !Character.isWhitespace(uniChar);
        if (!handled && acceptFilter() && isKeyNotWhitespace) {
            boolean gotKey = TextKeyListener.getInstance().onKeyDown(mWorkspace, mDefaultKeySsb,
                    keyCode, event);
            if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
                // something usable has been typed - start a search
                // the typed text will be retrieved and cleared by
                // showSearchDialog()
                // If there are multiple keystrokes before the search dialog takes focus,
                // onSearchRequested() will be called for every keystroke,
                // but it is idempotent, so it's fine.
                return onSearchRequested();
            }
        }
        // Eat the long press event so the keyboard doesn't come up.
        //zwsun@lewatek.com PR945916 2015.03.24 start
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            mWorkspace.exitWidgetResizeMode();
            if(event.isLongPress())
            {
               return true;
            }
        }
       //zwsun@lewatek.com PR945916 2015.03.24 end
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        //zwsun@lewatek.com PR69251 2015.02.09 start
	    if(Hidden.isShowApps){
               mHidden.isIntentEdit();
               Hidden.isShowApps = false;
               return false;
	       }
        //zwsun@lewatek.com PR69251 2015.02.09 start
            if (editModeLayer != null && editModeLayer.isEditMode()
                    && isEditModeVisible())
                editModeLayer.onBack();
	    //yixiao@lewatek.com add for #935265 begin
            if(!mModel.mIsLoaderTaskRunning && isPiflowPage()) {
               //mWorkspace.moveToDefaultScreen(true);
               mWorkspace.snapToPage(1);
               return true;
	    }
	    //yixiao@lewatek.com add for #935265 end
        }
        return handled;
    }

    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    private void clearTypedText() {
        mDefaultKeySsb.clear();
        mDefaultKeySsb.clearSpans();
        Selection.setSelection(mDefaultKeySsb, 0);
    }

    /**
     * Given the integer (ordinal) value of a State enum instance, convert it to a variable of type State
     */
    private static State intToState(int stateOrdinal) {
        State state = State.WORKSPACE;
        final State[] stateValues = State.values();
        for (State stateValue : stateValues) {
            if (stateValue.ordinal() == stateOrdinal) {
                state = stateValue;
                break;
            }
        }
        return state;
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            //yixiao add pr67907 2015.1.16 begin
            mWorkspace.setCurrentPage(mWorkspace.getDefaultPage());
            mDesktopIndicator.fullIndicate(mWorkspace.getDefaultPage());
            //yixiao add pr67907 2015.1.16 end
            return;
        }

        /*int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN, -1);
        if (currentScreen > -1) {
            currentScreen = Math.min(currentScreen, mWorkspace.getChildCount() - 1);
            mWorkspace.setCurrentPage(currentScreen);
            int count = mWorkspace.getChildCount();
            if(currentScreen < count)
                mDesktopIndicator.fullIndicate(currentScreen);
        }*/
      //yixiao modify for piflow 2015.1.11 begin
        long currentScreenId = savedState.getLong(RUNTIME_STATE_CURRENT_SCREEN, -1);
        if (currentScreenId > -1) {
            int currentScreen = LauncherModel.getScreenIndexById(currentScreenId);
            currentScreen = Math.min(currentScreen, mWorkspace.getChildCount() - 1);
            mWorkspace.setCurrentPage(currentScreen);
            int count = mWorkspace.getChildCount();
            if(currentScreen < count)
                mDesktopIndicator.fullIndicate(currentScreen);
        }
      //yixiao modify for piflow 2015.1.11 end
        final long pendingAddContainer = savedState.getLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, -1);
        final int pendingAddScreen = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);

        if (pendingAddContainer != ItemInfo.NO_ID && pendingAddScreen > -1) {
            mPendingAddInfo.container = pendingAddContainer;
            mPendingAddInfo.screen = pendingAddScreen;
            mPendingAddInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            mPendingAddInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            mPendingAddInfo.spanX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
            mPendingAddInfo.spanY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
            mPendingAddWidgetInfo = savedState.getParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO);
            mWaitingForResult = true;
            mRestoring = true;
        }

        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = mModel.getFolderById(this, sFolders, id);
            mRestoring = true;
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        final DragController dragController = mDragController;
        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        mWorkspace = (Workspace) findViewById(R.id.workspace);

        mDesktopIndicator = (DesktopIndicator) findViewById(R.id.desktop_indicator);
        mDesktopIndicator.setItems(mWorkspace.getChildCount(), mWorkspace.getCurrentPage());
        mWorkspace.setIndicator(mDesktopIndicator);

        // Setup the drag layer
        mDragLayer.setup(this, dragController);

        // Setup the hotseat
        mFloating = (FloatingLayer) findViewById(R.id.workspace_floating);
        mFloating.setup(dragController);

        if (editModeLayer == null) {
            editModeLayer = (EditModeLayer) findViewById(R.id.workspace_edit);
            editModeLayer.setDragController(mDragController);
            editModeLayer.initViews();
            View v = findViewById(R.id.edit_background);
            editModeLayer.setEditBackground(v);
        }

        // Setup the workspace
        mWorkspace.setHapticFeedbackEnabled(false);
        mWorkspace.setOnLongClickListener(this);
        mWorkspace.setup(dragController);
        mWorkspace.setPageSwitchListener(mFloating);
        mWorkspace.setOverScrollListener(mWorkspace);
        dragController.addDragListener(mWorkspace);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        dragController.setDragScoller(mWorkspace);
        dragController.setScrollView(mDragLayer);
        dragController.setMoveTarget(mWorkspace);
        dragController.addDropTarget(mWorkspace);

        movedHotseatWrapper = (MovedHotseatWrapper) findViewById(R.id.hotseat);
        movedHotseatWrapper.setMode(PullToRefreshBase.Mode.PULL_FROM_END);
        movedHotseatWrapper.setFriction(1.0f);//set touch easily
        movedHotseatWrapper.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener<Hotseat>() {
            @Override
            public void onRefresh(PullToRefreshBase<Hotseat> refreshView) {
                enterEditMode(EditModeLayer.ANIMORIENTATION.HORIZONTAL);
//                refreshView.setState(PullToRefreshBase.State.RESET, false);
            }
        });
        mHotseat = movedHotseatWrapper.getRefreshableView();
        mDragController.addDragListener(mHotseat);
        mDragController.addDropTarget(mHotseat);

        mDeleteZone = (DeleteDropTarget) findViewById(R.id.delete_zone);
        mDeleteZone.setup(this, mDragController);

        mThumbnailView = (ThumbnailView) findViewById(R.id.workspace_thumb);
        mThumbnailAdapter = new ThumbnailViewAdapter(this);
        //yixiao delete for piflow 2015.1.15 at first load piflow screen is null
        //mThumbnailView.setAdapter(mWorkspace, mThumbnailAdapter);
        //lqwang - remove smart sort and recommend - modify begin
        if(PreferencesProvider.isRecommendOn(Launcher.this)){
            mAppRecommend = (AppRecommend) mInflater.inflate(R.layout.recommend_layout, null);
            mAppRecommend.initialize((LauncherApplication) getApplication(), this);
        }
        //lqwang - remove smart sort and recommend - modify end
        initExplodeInterpolator();
    }

    public DesktopIndicator getDesktopIndicator() {
        return mDesktopIndicator;
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     * @return A View inflated from R.layout.application.
     */
    View createShortcut(ShortcutInfo info) {
        return createShortcut(R.layout.application,
                (ViewGroup) mWorkspace.getCurrentCellLayout(), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param layoutResId The id of the XML layout used to create the shortcut.
     * @param parent      The group the shortcut belongs to.
     * @param info        The data structure describing the shortcut.
     * @return A View inflated from layoutResId.
     */
    View createShortcut(int layoutResId, ViewGroup parent, ShortcutInfo info) {
        ShortcutIcon shortcut = (ShortcutIcon) mInflater.inflate(layoutResId, parent, false);
        shortcut.applyFromShortcutInfo(info, mIconCache);
        shortcut.mFavorite.setOnClickListener(this);
        shortcut.mFavorite.setOnLongClickListener(this);
        return shortcut;
    }

    /**
     * Add an application shortcut to the workspace.
     *
     * @param data     The intent describing the application.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    void completeAddApplication(Intent data, long container, int screen, int cellX, int cellY) {
        final int[] cellXY = mTmpAddItemCellCoordinates;
        final CellLayout layout = getCellLayout(container, screen);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
        } else if (!layout.findCellForSpan(cellXY, 1, 1)) {
            showOutOfSpaceMessage();
            return;
        }

        final ShortcutInfo info = mModel.getShortcutInfo(getPackageManager(), data, this);

        if (info != null) {
            info.setActivity(data.getComponent(), Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            info.container = ItemInfo.NO_ID;
            mWorkspace.addApplicationShortcut(info, layout, container, screen, cellXY[0], cellXY[1],
                    isWorkspaceLocked(), cellX, cellY);
        } else {
            Log.e(TAG, "Couldn't find ActivityInfo for selected application: " + data);
        }
    }

    /**
     * Add a shortcut to the workspace.
     *
     * @param data     The intent describing the shortcut.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    private void completeAddShortcut(Intent data, long container, int screen, int cellX,
                                     int cellY) {
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        CellLayout layout = getCellLayout(container, screen);

        boolean foundCellSpan = false;

        ShortcutInfo info = mModel.infoFromShortcutIntent(this, data, null);
        if (info == null) {
            return;
        }
        final View view = createShortcut(info);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
            foundCellSpan = true;

            // If appropriate, either create a folder or add to an existing folder
            if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                    true, null, null)) {
                return;
            }
            DragObject dragObject = new DragObject();
            dragObject.dragInfo = info;
            if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                    true)) {
                return;
            }
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(touchXY[0], touchXY[1], 1, 1, cellXY);
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
        }

        if (!foundCellSpan) {
            showOutOfSpaceMessage();
            return;
        }

        LauncherModel.addItemToDatabase(this, info, container, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            mWorkspace.addInScreen(view, container, screen, cellXY[0], cellXY[1], 1, 1,
                    isWorkspaceLocked());
            if (isEditMode()) {
                EditModeUtils.animScaleView(view, 0, 0.8f, 0, null);
            }
        }
    }

    static int[] getSpanForWidget(Context context, ComponentName component, int minWidth,
                                  int minHeight) {
        Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(context, component, null);
        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested
        int requiredWidth = minWidth + padding.left + padding.right;
        int requiredHeight = minHeight + padding.top + padding.bottom;
        return CellLayout.rectToCell(context.getResources(), requiredWidth, requiredHeight, null);
    }

    public static int[] getSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minResizeWidth, info.minResizeHeight);
    }

    static int[] getSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minResizeWidth,
                info.minResizeHeight);
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo    The position on screen where to create the widget.
     */
    private void completeAddAppWidget(final int appWidgetId, long container, int screen,
                                      AppWidgetHostView hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null) {
            appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        }

        // Calculate the grid spans needed to fit this widget
        CellLayout layout = getCellLayout(container, screen);
        if (layout == null) {
            return;
        }

        int[] minSpanXY = getMinSpanForWidget(this, appWidgetInfo);
        int[] spanXY = getSpanForWidget(this, appWidgetInfo);

        // Try finding open space on Launcher screen
        // We have saved the position to which the widget was dragged-- this really only matters
        // if we are placing widgets on a "spring-loaded" screen
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        int[] finalSpan = new int[2];
        boolean foundCellSpan = false;
        if (mPendingAddInfo.cellX >= 0 && mPendingAddInfo.cellY >= 0) {
            cellXY[0] = mPendingAddInfo.cellX;
            cellXY[1] = mPendingAddInfo.cellY;
            spanXY[0] = mPendingAddInfo.spanX;
            spanXY[1] = mPendingAddInfo.spanY;
            foundCellSpan = true;
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(
                    touchXY[0], touchXY[1], minSpanXY[0], minSpanXY[1], spanXY[0],
                    spanXY[1], cellXY, finalSpan);
            spanXY[0] = finalSpan[0];
            spanXY[1] = finalSpan[1];
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, minSpanXY[0], minSpanXY[1]);
        }

        if (!foundCellSpan) {
            if (appWidgetId != -1) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    }
                }.start();
            }
            showOutOfSpaceMessage();
            return;
        }

        // Build Launcher-specific widget info and save to database
        final LauncherAppWidgetInfo launcherInfo = new LauncherAppWidgetInfo(appWidgetId,
                appWidgetInfo.provider);
        launcherInfo.spanX = spanXY[0];
        launcherInfo.spanY = spanXY[1];
        launcherInfo.minSpanX = mPendingAddInfo.minSpanX;
        launcherInfo.minSpanY = mPendingAddInfo.minSpanY;
//        boolean isSameExists = LauncherModel.isSameWidgetExists(launcherInfo.providerName);
        LauncherModel.addItemToDatabase(this, launcherInfo,
                container, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            if (hostView == null) {
                // Perform actual inflation because we're live
                launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
                launcherInfo.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            } else {
                // The AppWidgetHostView has already been inflated and instantiated
                launcherInfo.hostView = hostView;
            }

            launcherInfo.hostView.setTag(launcherInfo);
            launcherInfo.hostView.setVisibility(View.VISIBLE);
            launcherInfo.notifyWidgetSizeChanged(this);

            mWorkspace.addInScreen(launcherInfo.hostView, container, screen, cellXY[0], cellXY[1],
                    launcherInfo.spanX, launcherInfo.spanY, isWorkspaceLocked());
            if (isEditMode()) {
                //lqwang - PR62973 - modify begin
                final boolean isDelay;
                if(launcherInfo.providerName != null){
                    isDelay = isWidgetUpdateDelay(Launcher.this,launcherInfo.providerName.getPackageName());
                }else {
                    isDelay = false;
                }
                EditModeUtils.logE(TAG,"update widget delay : "+isDelay+"  component: " + launcherInfo.providerName);
                EditModeUtils.animScaleView(launcherInfo.hostView, 0, 1.0f, 0, new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if(isDelay){
                            ((LauncherAppWidgetHostView)launcherInfo.hostView).setUpdateDelay(true);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if(isDelay){
                            ((LauncherAppWidgetHostView)launcherInfo.hostView).setUpdateDelay(false);
                            LauncherModel.updateWidgets();
                        }
                    }
                });
                //lqwang - PR62973 - modify end
            }
            addWidgetToAutoAdvanceIfNeeded(launcherInfo.hostView, appWidgetInfo);
        }
        resetAddInfo();
    }
    //lqwang - PR62973 - add begin
    private boolean isWidgetUpdateDelay(Context context,String pkgName){
        boolean isDelay = false;
        String[] update_delays = context.getResources().getStringArray(R.array.widget_update_delays);
        if(pkgName != null){
            for(String s : update_delays){
                if(s.equals(pkgName)){
                    isDelay = true;
                    break;
                }
            }
        }
        return isDelay;
    }
    //lqwang - PR62973 - add end

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mUserPresent = false;
                mDragLayer.clearAllResizeFrames();
                updateRunning();
                if (isFloating()) {
                    mFloating.endFloating(true, false);
                } else if (isEditMode()) {
                    editModeLayer.endEditMode(false);
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mUserPresent = true;
                updateRunning();
//                EditModeUtils.batchSetCellScale(Launcher.this,getCurrentWorkspaceScreen(),true,true,EditModeUtils.ANIM_OUT_DELAY,1.0f,1.05f,1.0f);
            } else if (ACTION_SET_WALLPAPER.equals(action)) {
                startWallpaper();
            }
            //lqwang - PR63179 - add begin
            else if(Constants.START_APPLICATION_ACTION.equals(action)){
                String pkgName = intent.getStringExtra("packageName");
                String clsName = intent.getStringExtra("className");
                  if(!TextUtils.isEmpty(pkgName) && !TextUtils.isEmpty(clsName)){
                      ComponentName componentName = new ComponentName(pkgName,clsName);
                      resetNewAddShortcut(componentName);
                  }
            }
            //lqwang - PR63179 - add end
        }
    };
    //lqwang - PR63179 - add begin
    private void resetNewAddShortcut(ComponentName componentName) {
        boolean found = false;
        for(int i = mWorkspace.getChildCount() - 1 ; i >= 0 ; i--){
           CellLayout layout = mWorkspace.getCellLayout(i);
           ShortcutAndWidgetContainer container = layout.getShortcutsAndWidgets();
           for(int j = container.getChildCount() - 1 ; j >= 0 ; j --){
                View v = container.getChildAt(j);
                if(v instanceof ShortcutIcon){
                    ShortcutInfo info = (ShortcutInfo) v.getTag();
                    if(info.intent != null){
                        ComponentName mComPonentName = info.intent.getComponent();
                        if(mComPonentName != null && mComPonentName.equals(componentName)){
                            Utilities.updateNewAddSymbol(v, info.getPackageName());
                            found = true;
                            break;
                        }
                    }
                }
           }
           if(found)break;
        }
        LauncherModel.removeNewAdded(getApplicationContext(),componentName.getPackageName());
    }
    //lqwang - PR63179 - add end

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Listen for broadcasts related to user-presence
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(ACTION_SET_WALLPAPER);
        filter.addAction(Constants.START_APPLICATION_ACTION);//lqwang - PR63179 - add
        registerReceiver(mReceiver, filter);

        mAttached = true;
        mVisible = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;

        if (mAttached) {
            unregisterReceiver(mReceiver);
            mAttached = false;
        }
        updateRunning();
    }

    public void onWindowVisibilityChanged(int visibility) {
        mVisible = visibility == View.VISIBLE;
        updateRunning();
        // The following code used to be in onResume, but it turns out onResume is called when
        // you're in All Apps and click home to go to the workspace. onWindowVisibilityChanged
        // is a more appropriate event to handle
        if (mVisible) {
            if (!mWorkspaceLoading) {
                final ViewTreeObserver observer = mWorkspace.getViewTreeObserver();
                // We want to let Launcher draw itself at least once before we force it to build
                // layers on all the workspace pages, so that transitioning to Launcher from other
                // apps is nice and speedy. Usually the first call to preDraw doesn't correspond to
                // a true draw so we wait until the second preDraw call to be safe
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    public boolean onPreDraw() {
                        // We delay the layer building a bit in order to give
                        // other message processing a time to run.  In particular
                        // this avoids a delay in hiding the IME if it was
                        // currently shown, because doing that may involve
                        // some communication back with the app.
                        mWorkspace.postDelayed(mBuildLayersRunnable, 500);

                        observer.removeOnPreDrawListener(this);
                        return true;
                    }
                });
            }
            clearTypedText();
        }
    }

    private void sendAdvanceMessage(long delay) {
        mHandler.removeMessages(ADVANCE_MSG);
        Message msg = mHandler.obtainMessage(ADVANCE_MSG);
        mHandler.sendMessageDelayed(msg, delay);
        mAutoAdvanceSentTime = System.currentTimeMillis();
    }

    private void updateRunning() {
        boolean autoAdvanceRunning = mVisible && mUserPresent && !mWidgetsToAdvance.isEmpty();
        if (autoAdvanceRunning != mAutoAdvanceRunning) {
            mAutoAdvanceRunning = autoAdvanceRunning;
            if (autoAdvanceRunning) {
                long delay = mAutoAdvanceTimeLeft == -1 ? mAdvanceInterval : mAutoAdvanceTimeLeft;
                sendAdvanceMessage(delay);
            } else {
                if (!mWidgetsToAdvance.isEmpty()) {
                    mAutoAdvanceTimeLeft = Math.max(0, mAdvanceInterval -
                            (System.currentTimeMillis() - mAutoAdvanceSentTime));
                }
                mHandler.removeMessages(ADVANCE_MSG);
                mHandler.removeMessages(0); // Remove messages sent using postDelayed()
            }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ADVANCE_MSG) {
                int i = 0;
                for (View key : mWidgetsToAdvance.keySet()) {
                    final View v = key.findViewById(mWidgetsToAdvance.get(key).autoAdvanceViewId);
                    final int delay = mAdvanceStagger * i;
                    if (ReflUtils.isInstance("android.widget.Advanceable", v)) {
                        postDelayed(new Runnable() {
                            public void run() {
                                Advanceable.advance(v);
                            }
                        }, delay);
                    }
                    i++;
                }
                sendAdvanceMessage(mAdvanceInterval);
            } else if (msg.what == SHOW_INPUT_MSG) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(0, InputMethodManager.SHOW_FORCED);
            } else if (msg.what == SHOW_APP_RECOMMENDS_MSG) {
                showAppRecommend();
            } else if (msg.what == HIDE_APP_RECOMMENDS_MSG) {
                hideAppRecommend();
            }
        }
    };

    void addWidgetToAutoAdvanceIfNeeded(View hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null || appWidgetInfo.autoAdvanceViewId == -1) return;
        View v = hostView.findViewById(appWidgetInfo.autoAdvanceViewId);
        if (ReflUtils.isInstance("android.widget.Advanceable", v)) {
            mWidgetsToAdvance.put(hostView, appWidgetInfo);
            Advanceable.fyiWillBeAdvancedByHostKThx(v);
            updateRunning();
        }
    }

    void removeWidgetToAutoAdvance(View hostView) {
        if (mWidgetsToAdvance.containsKey(hostView)) {
            mWidgetsToAdvance.remove(hostView);
            updateRunning();
        }
    }

    public void removeAppWidget(LauncherAppWidgetInfo launcherInfo) {
        removeWidgetToAutoAdvance(launcherInfo.hostView);
        launcherInfo.hostView = null;
    }

    void showOutOfSpaceMessage() {
        makeToast(R.string.out_of_space);
        // Toast.makeText(this, getString(R.string.out_of_space), Toast.LENGTH_SHORT).show();
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    void closeSystemDialogs() {
        getWindow().closeAllPanels();
        closeActionMenu();

        // Whatever we were doing is hereby canceled.
        mWaitingForResult = false;
    }


    int findCellForItemFromPick(PendingAddItemInfo itemInfo, int[] result) {
        int screen = -1;
        int curPage = mWorkspace.getCurrentPage();
        CellLayout layout = mWorkspace.getCellLayout(curPage);
        if (layout.findCellForSpan(result, itemInfo.spanX, itemInfo.spanY)) {
            screen = curPage;
        }
//        if (!found) {
//            CellLayout newLayout = mWorkspace.addScreen(screenCount);
//            if (newLayout != null && newLayout.findCellForSpan(result, itemInfo.spanX, itemInfo.spanY)) {
//                screen = screenCount;
//                found = true;
//            }
//        }
        return screen;
    }

    boolean addItemFromPick(final PendingAddItemInfo itemInfo,
                            final CellLayout.CellInfo longClickCell,
                            final WidgetsAdapter.OnWidgetAddedListener addedListener) {
        int[] targetCell = new int[2];
        int screen = findCellForItemFromPick(itemInfo, targetCell);
        if (screen == -1) {
            Log.i(TAG, "can't find cell for:" + itemInfo.componentName);
            makeToast(R.string.out_of_space);
            //Toast.makeText(Launcher.this, R.string.out_of_space, 0).show();
            return false;
        }

        if (DEBUG_WIDGETS)
            Log.i(TAG, "find cell in screen:" + screen + " X:" + targetCell[0]
                    + " Y:" + targetCell[1]);
        switch (itemInfo.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                int span[] = new int[2];
                span[0] = itemInfo.spanX;
                span[1] = itemInfo.spanY;
                addAppWidgetFromDrop((PendingAddWidgetInfo) itemInfo,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                        targetCell, span, null);
                break;

            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                processShortcutFromDrop(itemInfo.componentName,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                        targetCell, null);
                break;
            default:
                throw new IllegalStateException("Unknown item type: "
                        + itemInfo.itemType);
        }
        final int newScreen = screen;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (addedListener != null) {
                    addedListener.onAdded(itemInfo);
                }
                mWorkspace.snapToPage(newScreen);
            }
        };
        if (mHandler != null) {
            mHandler.post(r);
        }
        return true;
    }

    // Begin , added for adding widget in installed Launcher
    public void addAppWidgetFromPick() {
        resetAddInfo();
        mPendingAddInfo.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        mPendingAddInfo.screen = mWorkspace.getCurrentPage();
        mPendingAddInfo.dropPos = null;

        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }
    // End

    void openHiddenApps() {
        if (isPreviewMode || isEditMode() || isAnimating || isWorkspaceLocked()
                || mWorkspace.getOpenFolder() != null || mDragController.isDragging()
                || isFloating() || isOpenHiddenApps) {
            return;
        }

        isOpenHiddenApps = true;
        if (mHidden == null) {
            mHidden = (Hidden) mInflater.inflate(R.layout.hidden_layout, null);
            mDragLayer.addView(mHidden);
            mHidden.layout(0, displayHeight, displayWidth, displayHeight * 2);
        }
        mHidden.setVisibility(View.VISIBLE);
        mHidden.setupContentItems(true);
        mDragController.setDragScoller(mHidden.getHiddenLayout());
        mDragController.addDropTarget(mHidden.getHiddenLayout());
        ObjectAnimator.ofFloat(mHidden, View.TRANSLATION_Y, mHidden.getHeight(), 0).setDuration(400).start();
    }

    void closeHiddenApps() {
        if (!isOpenHiddenApps || isAnimating) {
            return;
        }
        mDragController.setDragScoller(mWorkspace);
        mDragController.removeDropTarget(mHidden.getHiddenLayout());
        ObjectAnimator closeAnimation = ObjectAnimator.ofFloat(mHidden, View.TRANSLATION_Y, 0, mHidden.getHeight());
        closeAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mHidden.setVisibility(View.GONE);
                isOpenHiddenApps = false;
                isAnimating = false;//lqwang - pr954601 - modify begin
            }

            @Override
            public void onAnimationStart(Animator animation) {
                isAnimating = true;//lqwang - pr954601 - modify end
            }
        });
        closeAnimation.setDuration(400);
        closeAnimation.start();
    }

    boolean isHiddenOpened() {
        return isOpenHiddenApps;
    }

    boolean isRecommendOpened() {
        return isOpenAppRecommend;
    }

    public ThumbnailView getThumbnailView() {
        return mThumbnailView;
    }

    public boolean inPreviewMode() {
        return isPreviewMode;
    }

    public Handler getHandler() {
        return mHandler;
    }

    public void updatePreviews() {
        if (isPreviewMode) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mThumbnailAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    void enterPreviews() {
        if (isPreviewMode || isEditMode() || isAnimating || isWorkspaceLocked()
                || mWorkspace.getOpenFolder() != null || mDragController.isDragging()
                || isFloating() || isOpenHiddenApps || mWorkspace.isPageMoving()) {
            return;
        }
		//yixiao add 2015.3.17
        enterPreview = true;
	//yixiao add for piflow 2015.1.15 begin
        if(mThumbnailView.getAdapter() == null){
            mThumbnailView.setAdapter(mWorkspace, mThumbnailAdapter);
        }
	//yixiao add for piflow 2015.1.15 begin
        isAnimating = true;
        isPreviewMode = true;

        closeOptionsMenu(); //lqwang - PR955032 - add

        mThumbnailAdapter.notifyDataSetChanged();
        final int curPage = mWorkspace.getCurrentPage();
        int scrIndex = mThumbnailView.getScrIdx(curPage);
        mThumbnailView.snapToPage(scrIndex);

        int cnt = mThumbnailView.getChildCount();
        for (int i = 0; i < cnt; i++) {
            mThumbnailView.getChildAt(i).clearAnimation();
        }

        final float[] center = getScrCenter(curPage % ThumbnailScreen.CNT_PER_SCREEN, true);
        Animation anim = getScaleAnimation(1 / SCALE, 1, center[0], center[1], ENTER_DURATION);
        mThumbnailView.getChildAt(scrIndex).startAnimation(anim);

        ObjectAnimator preEnterAnim = ObjectAnimator.ofFloat(mHotseat,
                View.TRANSLATION_Y, 0, mHotseat.getHeight());
        preEnterAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
                mDesktopIndicator.setVisibility(View.GONE);
                mDragController.removeDragListener(mDeleteZone);
                mDragController.addDropTarget(mThumbnailView);
                mDragController.setDragScoller(mThumbnailView);
                mThumbnailView.setVisibility(View.VISIBLE);
                mWorkspace.setVisibility(View.INVISIBLE);
                //lqwang - PR64701 - modify begin
                movedHotseatWrapper.setMode(PullToRefreshBase.Mode.DISABLED);
                //lqwang - PR64701 - modify end
            }
        });
        preEnterAnim.setDuration(ENTER_DURATION).start();
    }

    private float[] getScrCenter(int relPage, boolean in) {
        float pivotX = (float) (relPage % 3) / 2;
        float pivotY = (float) (relPage / 3) / 2;
        return new float[]{pivotX, pivotY + (in ? 0 : 0.03f)}; // 0.03f for the status bar padding
    }

    public Animation getScaleAnimation(float from, float to, float cnterX,
                                       float cnterY, int durationTime) {
        ScaleAnimation scaleAnimation = new ScaleAnimation(from, to, from, to,
                Animation.RELATIVE_TO_SELF, cnterX,
                Animation.RELATIVE_TO_SELF, cnterY);
        scaleAnimation.setDuration(durationTime);
        scaleAnimation.setInterpolator(interpolator);
        return scaleAnimation;
    }

    void exitPreviews(boolean showDock) {
        if (!isPreviewMode || isAnimating) {
            return;
        }
        isAnimating = true;
        mThumbnailView.updateScreenMap();
        mWorkspace.setVisibility(View.VISIBLE);

        int curPage = mWorkspace.getCurrentPage();
        curPage = Math.max(curPage, 0);
        int scrIndex = mThumbnailView.getScrIdx(curPage);
        mThumbnailView.snapToPage(scrIndex, 10);
        mThumbnailView.setBackgroundColor(0x00000000);
        mThumbnailView.getItemAt(curPage).setVisibility(View.GONE);

        final View view = mWorkspace.getChildAt(curPage);
        view.clearAnimation();
        int cnt = mThumbnailView.getChildCount();
        for (int i = 0; i < cnt; i++) {
            mThumbnailView.getChildAt(i).clearAnimation();
        }

        final float[] center = getScrCenter(curPage % ThumbnailScreen.CNT_PER_SCREEN, false);
        view.startAnimation(getScaleAnimation(SCALE, 1, center[0], center[1], EXIT_DURATION));

        final Animation animation = getScaleAnimation(1, 1 / SCALE, center[0], center[1], EXIT_DURATION);
        animation.setFillEnabled(true);
        animation.setFillAfter(true);
        mThumbnailView.getChildAt(scrIndex).startAnimation(animation);

        ObjectAnimator preExitAnim = ObjectAnimator.ofFloat(mHotseat,
                View.TRANSLATION_Y, mHotseat.getHeight(), showDock ? 0 : mHotseat.getHeight());
        preExitAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
                isPreviewMode = false;
                if (mDragController != null) {
                    mDragController.setDragScoller(mWorkspace);
                    mDragController.removeDropTarget(mThumbnailView);
                    mDragController.addDragListener(mDeleteZone);
                }
                mDesktopIndicator.setVisibility(View.VISIBLE);
                mThumbnailView.setVisibility(View.INVISIBLE);
                mThumbnailView.recycleBitmap();
				//zwsun@letek.com 20150108 start
                    mWorkspace.updateWorkspace();
			    //zwsun@letek.com 20150108 end
                //lqwang - PR64701 - modify begin
                movedHotseatWrapper.setMode(PullToRefreshBase.Mode.PULL_FROM_END);
                //lqwang - PR64701 - modify end
                //yixiao add #70117 2015.2.13
                exitPreview = false;
				//yixiao 2015.3.17
                enterPreview = false;
            }
          //yixiao add #70117 2015.2.13 begin
            @Override
            public void onAnimationStart(Animator animation) {
                exitPreview = true;
            }
          //yixiao add #70117 2015.2.13 end
        });
        preExitAnim.setDuration(EXIT_DURATION).start();
    }

    void exitPreviews() {
        exitPreviews(!isEditMode());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mFloatingExited) {
            return;
        }
        final boolean alreadyOnHome = (isSettingLauncher) ? false
                : ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        // Close the menu
        final Folder openFolder = mWorkspace.getOpenFolder();
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            if (isFloating()) {
                if (openFolder == null) {
                    mFloating.endFloating(true, true);
                } else {
                    closeFolder();
                }
                return;
            } else if (isEditMode()) {
                editModeLayer.endEditMode(true);
                return;
            }
            exitPreviews();
            closeHiddenApps();
            // also will cancel mWaitingForResult.
            closeSystemDialogs();
            Runnable processIntent = new Runnable() {
                public void run() {
                    if (mWorkspace == null) {
                        // Can be cases where mWorkspace is null, this prevents a NPE
                        return;
                    }

                    // In all these cases, only animate if we're already on home
                    mWorkspace.exitWidgetResizeMode();
                    if (alreadyOnHome && mState == State.WORKSPACE && !mWorkspace.isTouchActive() &&
                            openFolder == null) {
                        mWorkspace.moveToDefaultScreen(true);
                    }

                    closeFolder();

                    // If we are already on home, then just animate back to the workspace,
                    // otherwise, just wait until onResume to set the state back to Workspace
                    if (alreadyOnHome) {
                        showWorkspace(true);
                    } else {
                        mOnResumeState = State.WORKSPACE;
                    }

                    final View v = getWindow().peekDecorView();
                    if (v != null && v.getWindowToken() != null) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(
                                INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            };

            if (alreadyOnHome && !mWorkspace.hasWindowFocus()) {
                // Delay processing of the intent to allow the status bar animation to finish
                // first in order to avoid janky animations.
                mWorkspace.postDelayed(processIntent, 350);
            } else {
                // Process the intent immediately.
                processIntent.run();
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        for (int page : mSynchronouslyBoundPages) {
            mWorkspace.restoreInstanceStateForChild(page);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //yixiao modify for piflow 2015.1.11 begin
        //outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getNextPage());
        outState.putLong(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getRunPage());
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE, mState.ordinal());
        // We close any open folder since it will not be re-opened, and we need to make sure
        // this state is reflected.
        closeFolder();

        if (mPendingAddInfo.container != ItemInfo.NO_ID && mPendingAddInfo.screen > -1 &&
                mWaitingForResult) {
            outState.putLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, mPendingAddInfo.container);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SCREEN, mPendingAddInfo.screen);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, mPendingAddInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, mPendingAddInfo.cellY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X, mPendingAddInfo.spanX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y, mPendingAddInfo.spanY);
            outState.putParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO, mPendingAddWidgetInfo);
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LauncherApplication.clearCache();
        //lqwang - PR63468 - modify begin
        unregisterReceiver(mThemeChangeReceiver);
        //lqwang - PR63468 - modify end
        // Remove all pending runnables
        mHandler.removeMessages(ADVANCE_MSG);
        mHandler.removeMessages(0);
        mWorkspace.removeCallbacks(mBuildLayersRunnable);

        // Stop callbacks from LauncherModel
        LauncherApplication app = ((LauncherApplication) getApplication());
        mModel.stopLoader();
        app.setLauncher(null);

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHost = null;

        mWidgetsToAdvance.clear();

        TextKeyListener.getInstance().release();

        // Disconnect any of the callbacks and drawables associated with ItemInfos on the workspace
        // to prevent leaking Launcher activities on orientation change.
        if (mModel != null) {
            mModel.unbindItemInfosAndClearQueuedBindRunnables();
        }

        getApplicationContext().getContentResolver().unregisterContentObserver(mWidgetObserver);
        unregisterReceiver(mCloseSystemDialogsReceiver);

        mDragLayer.clearAllResizeFrames();
        ((ViewGroup) mWorkspace.getParent()).removeAllViews();
        mWorkspace.removeAllViews();
        mWorkspace = null;
        mDragController = null;

        LauncherAnimUtils.onDestroyActivity();
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode >= 0) mWaitingForResult = true;
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isWorkspaceLocked() || isFloating() || isEditMode()) {
            return false;
        }
        //yixiao add intercept menu_key at T-show screen 2015.1.15 begin
        if(mWorkspace.getCurrentPage()==0 && isPiflowPageEnable()){
            return false;
        }
        //yixiao add intercept menu_key at T-show screen 2015.1.15 end
        super.onCreateOptionsMenu(menu);

        inflateOptionMenu(menu);

        return true;
    }

    private void inflateOptionMenu(Menu menu) {
        Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        menu.add(MENU_GROUP_NORMAL, MENU_EDITMODE, 0, R.string.menu_edit)
                .setAlphabeticShortcut('E');

        menu.add(MENU_GROUP_NORMAL, MENU_PREVIEW, 0, R.string.menu_preview)
                .setAlphabeticShortcut('V');

        menu.add(MENU_GROUP_NORMAL, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
                .setAlphabeticShortcut('W');

        menu.add(MENU_GROUP_NORMAL, MENU_LAUNCHER_SETTINGS, 0, R.string.menu_desktop_settings)
                .setAlphabeticShortcut('X');

        menu.add(MENU_GROUP_NORMAL, MENU_SYSTEM_SETTINGS, 0, R.string.menu_settings)
                .setIntent(settings)
                .setAlphabeticShortcut('P');
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //yixiao add intercept menu_key at T-show screen 2015.1.15 begin
        if(mWorkspace.getCurrentPage()==0 && isPiflowPageEnable()){
            return false;
        }
        //yixiao add intercept menu_key at T-show screen 2015.1.15 end
        if (isEditMode() || inPreviewMode() || isFloating() || isOpenHiddenApps
                || mWorkspace.getOpenFolder() != null) {
            menu.clear();
            return false;
        }else if(!menu.hasVisibleItems()){
            inflateOptionMenu(menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        closeFolder();
        // yixiao add #982477
        mWorkspace.exitWidgetResizeMode();
        switch (item.getItemId()) {
            case MENU_EDITMODE:
                enterEditMode(EditModeLayer.ANIMORIENTATION.VERTICAL);
                return true;
            case MENU_PREVIEW:
                enterPreviews();
                return true;
            case MENU_WALLPAPER_SETTINGS:
                Intent intent = new Intent();
                intent.setClassName("com.lewa.themechooser",
                        "com.lewa.themechooser.custom.main.DeskTopWallpaper");
              //zwsun@lewatek.com 2015.02.04 PR69224 start
                try{
                startActivity(intent);
                }catch(ActivityNotFoundException e)
                {
                // TODO: handle exception
                }
              //zwsun@lewatek.com 2015.02.04 PR69224 end
                return true;
            case MENU_LAUNCHER_SETTINGS:
                //lqwang - PR64141 - modify begin
                Intent i = new Intent(Launcher.this, MyLauncherSettings.class);
                i.putExtra(Constants.WALLPAPER_FINALX,mWorkspace.mWallpaperOffset.getCurrX());
                startActivity(i);
                //lqwang - PR64141 - modify end
                isSettingLauncher = true;//Bug48478 Add by Fan.Yang
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mWaitingForResult;
    }

    private void resetAddInfo() {
        mPendingAddInfo.container = ItemInfo.NO_ID;
        mPendingAddInfo.screen = -1;
        mPendingAddInfo.cellX = mPendingAddInfo.cellY = -1;
        mPendingAddInfo.spanX = mPendingAddInfo.spanY = -1;
        mPendingAddInfo.minSpanX = mPendingAddInfo.minSpanY = -1;
        mPendingAddInfo.dropPos = null;
    }

    void addAppWidgetImpl(final int appWidgetId, ItemInfo info, AppWidgetHostView boundWidget,
                          AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo.configure != null) {
            mPendingAddWidgetInfo = appWidgetInfo;

            // Launch over to configure widget, if needed
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResultSafely(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            // Otherwise just add it
            completeAddAppWidget(appWidgetId, info.container, info.screen, boundWidget,
                    appWidgetInfo);
        }
    }

    /**
     * Process a shortcut drop.
     *
     * @param componentName The name of the component
     * @param screen        The screen where it should be added
     * @param cell          The cell it should be added to, optional
     * @param position      The location on the screen where it was dropped, optional
     */
    void processShortcutFromDrop(ComponentName componentName, long container, int screen,
                                 int[] cell, int[] loc) {
        resetAddInfo();
        mPendingAddInfo.container = container;
        mPendingAddInfo.screen = screen;
        mPendingAddInfo.dropPos = loc;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }

        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createShortcutIntent.setComponent(componentName);
        createShortcutIntent.putExtra(EXTRA_SHORTCUT_USER_CREATED, true);
        processShortcut(createShortcutIntent);
    }

    /**
     * Process a widget drop.
     *
     * @param info     The PendingAppWidgetInfo of the widget being added.
     * @param screen   The screen where it should be added
     * @param cell     The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void addAppWidgetFromDrop(PendingAddWidgetInfo info, long container, int screen,
                              int[] cell, int[] span, int[] loc) {
        resetAddInfo();
        mPendingAddInfo.container = info.container = container;
        mPendingAddInfo.screen = info.screen = screen;
        mPendingAddInfo.dropPos = loc;
        mPendingAddInfo.minSpanX = info.minSpanX;
        mPendingAddInfo.minSpanY = info.minSpanY;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }
        if (span != null) {
            mPendingAddInfo.spanX = span[0];
            mPendingAddInfo.spanY = span[1];
        }

        AppWidgetHostView hostView = info.boundWidget;
        int appWidgetId;
        if (hostView != null) {
            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetImpl(appWidgetId, info, hostView, info.info);
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            appWidgetId = getAppWidgetHost().allocateAppWidgetId();
            Bundle options = info.bindOptions;

            boolean success = false;
            if (Build.VERSION.SDK_INT >= 15) {
                if (options != null) {
                    success = ReflUtils.bindAppWidgetIdIfAllowed(mAppWidgetManager, appWidgetId, info.componentName, options);
                } else {
                    success = ReflUtils.bindAppWidgetIdIfAllowed(mAppWidgetManager, appWidgetId, info.componentName);
                }
            } else {
                success = ReflUtils.bindAppWidgetId(mAppWidgetManager, appWidgetId, info.componentName);
            }
            if (success) {
                addAppWidgetImpl(appWidgetId, info, null, info.info);
            } else {
                mPendingAddWidgetInfo = info.info;
                Intent intent = new Intent(ReflUtils.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(ReflUtils.EXTRA_APPWIDGET_PROVIDER, info.componentName);
                // TODO: we need to make sure that this accounts for the options bundle.
                // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
            }
        }
    }

    void processShortcut(Intent intent) {
        // Handle case where user selected "Applications"
        String applicationName = getResources().getString(R.string.group_applications);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (applicationName != null && applicationName.equals(shortcutName)) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
            pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
            pickIntent.putExtra(Intent.EXTRA_TITLE, getText(R.string.title_select_application));
            startActivityForResultSafely(pickIntent, REQUEST_PICK_APPLICATION);
        } else {
            startActivityForResultSafely(intent, REQUEST_CREATE_SHORTCUT);
        }
    }

    void processWallpaper(Intent intent) {
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    FolderIcon addFolder(CellLayout layout, long container, final int screen, int cellX,
                         int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getResources().getResourceName(R.string.folder_name);

        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, container, screen, cellX, cellY,
                false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder =
                FolderIcon.fromXml(R.layout.folder_icon_lewa, this, layout, folderInfo, mIconCache);
        mWorkspace.addInScreen(newFolder, container, screen, cellX, cellY, 1, 1,
                isWorkspaceLocked());
        return newFolder;
    }

    FolderInfo addFolder(CellLayout layout, final int screen, int cellX, int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getResources().getResourceName(R.string.folder_name);

        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, -100, screen, cellX, cellY,
                false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon_lewa, this, layout, folderInfo,
                mIconCache);
        mWorkspace.addInScreen(newFolder, -100, screen, cellX, cellY, 1, 1, isWorkspaceLocked());
        return folderInfo;
    }

    void removeFolder(FolderInfo folder) {
        sFolders.remove(folder.id);
    }

    void showStatusBar(boolean bShow) {
        if ((isFloating() || isEditMode()) && bShow) {
            return;
        }
        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        int flag;
        if (bShow) {
            flag = lp.flags & ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
        } else {
            flag = lp.flags | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        }
        lp.flags = flag;
        window.setAttributes(lp);
    }

    private void startWallpaper() {
        showWorkspace(true);
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        Intent chooser = Intent.createChooser(pickWallpaper,
                getText(R.string.chooser_wallpaper));
        startActivityForResult(chooser, REQUEST_PICK_WALLPAPER);
    }

    /**
     * Registers various content observers. The current implementation registers only a favorites observer to keep track
     * of the favorites applications.
     */
    private void registerContentObservers() {
        ContentResolver resolver = getApplicationContext().getContentResolver();
        resolver.registerContentObserver(LauncherProvider.CONTENT_APPWIDGET_RESET_URI,
                true, mWidgetObserver);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
                // remove temp for volue_down disabled, by luoyongxing
//                case KeyEvent.KEYCODE_VOLUME_DOWN:
//                    if (SystemProperties.getInt("debug.launcher2.dumpstate", 0) != 0) {
//                        dumpState();
//                        return true;
//                    }
//                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        closeHiddenApps();
        exitPreviews();
        if (mWorkspace.getOpenFolder() != null) {
            Folder openFolder = mWorkspace.getOpenFolder();
            if (openFolder.isEditingName()) {
                openFolder.dismissEditingName();
            } else /*if(!isFloating())*/ {
                closeFolder();
            }
            return;
        } else {
            mWorkspace.exitWidgetResizeMode();
            if(!isEditMode()){
                // Back button is a no-op here, but give at least some feedback for the button press
                mWorkspace.showOutlinesTemporarily();
            }
            if (isFloating()) {
                mFloating.endFloating(false, true);
            }
        }
    }

    /**
     * Re-listen when widgets are reset.
     */
    private void onAppWidgetReset() {
        if (mAppWidgetHost != null) {
            mAppWidgetHost.startListening();
        }
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) {
            return;
        }
        if (!mWorkspace.isFinishedSwitchingState() || isPreviewMode) {
            return;
        }

        if (v.getTag() instanceof FolderInfo && v.getParent() instanceof FolderIcon) {
            handleFolderClick((FolderIcon) v.getParent());
            return;
        }

        if (isEditMode() && !isFloating() && !isEditModeSwitching()) {
            CellLayout.CellInfo cellInfo = getCellInfo(v);
            mFloating.startFloating(cellInfo);
            return;
        } else if (isFloating()) {
            CellLayout.CellInfo cellInfo = getCellInfo(v);
            if (cellInfo != null && FloatingLayer.allowFloat(cellInfo.cell)) {
                mFloating.toggleCell(cellInfo);
            }
            return;
        }

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            // Open shortcut
            final Intent intent = ((ShortcutInfo) tag).intent;
            LauncherModel.isRecommendShortcut(this, intent, (String) ((ShortcutInfo) tag).title);
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));
            boolean success = startActivitySafely(v, intent, tag);

            if (success && v instanceof BubbleTextView) {
                mWaitingForResume = (BubbleTextView) v;
                mWaitingForResume.setStayPressed(true);
                if (v.getParent() instanceof ShortcutIcon) {
                    Utilities.updateNewAddSymbol((ShortcutIcon) v.getParent(), ((ShortcutInfo) tag).getPackageName());
                }
            }
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                FolderIcon fi = (FolderIcon) v;
                handleFolderClick(fi);
            }
        }
    }

    private CellLayout.CellInfo getCellInfo(View v) {
        while (!(v instanceof CellLayout)) {
            ViewParent p = v.getParent();
            if (!(p instanceof View)) {
                break;
            }
            v = (View) v.getParent();
        }
        CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) v.getTag();
        return cellInfo;
    }

    public boolean onTouch(View v, MotionEvent event) {
        // this is an intercepted event being forwarded from mWorkspace;
        // clicking anywhere on the workspace causes the customization drawer to slide down
        showWorkspace(true);
        return false;
    }

    private boolean isUninstallApp;

    public void setIsUninstallApp(boolean isUninstall) {
        isUninstallApp = isUninstall;
    }

    public boolean getIsUninstallApp() {
        return isUninstallApp;
    }

    void startApplicationUninstallActivity(ApplicationInfo appInfo) {
        if ((appInfo.flags & ApplicationInfo.DOWNLOADED_FLAG) == 0) {
            Toast.makeText(this, R.string.uninstall_system_app_text, Toast.LENGTH_SHORT).show();
        } else {
            setIsUninstallApp(true);
            String packageName = appInfo.componentName.getPackageName();
            String className = appInfo.componentName.getClassName();
            Intent intent = new Intent(Intent.ACTION_DELETE,
                    Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    void startShortcutUninstallActivity(ShortcutInfo shortcutInfo) {
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(shortcutInfo.intent, 0);
        if ((resolveInfo.activityInfo.applicationInfo.flags &
                android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
            //Toast.makeText(Launcher.this, R.string.uninstall_system_app_text, Toast.LENGTH_SHORT).show();
            makeToast(R.string.uninstall_system_app_text);
        } else {
            setIsUninstallApp(true);
            if (LauncherApplication.isSystemApp()) {
                ComponentName cn = shortcutInfo.intent.getComponent();
                UninstallComfirmDialog.show(Launcher.this, cn);
            } else {
                String packageName = shortcutInfo.intent.getComponent().getPackageName();
                String className = shortcutInfo.intent.getComponent().getClassName();
                Intent intent = new Intent(Intent.ACTION_DELETE,
                        Uri.fromParts("package", packageName, className));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(intent);
            }
        }
    }

    boolean startActivity(View v, Intent intent, Object tag) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            // Only launch using the new animation if the shortcut has not opted out (this is a
            // private contract between launcher and may be ignored in the future).
            if (Build.VERSION.SDK_INT >= 16) {
                boolean useLaunchAnimation = (v != null) &&
                        !intent.hasExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION);
                if (useLaunchAnimation) {
                    Bundle bundle = ReflUtils.makeScaleUpAnimationBundle(v, 0, 0,
                            v.getMeasuredWidth(), v.getMeasuredHeight());
                    ReflUtils.startActivity(this, intent, bundle);
                } else {
                    startActivity(intent);
                }
            } else {
                startActivity(intent);
            }
            return true;
        } catch (SecurityException e) {
            makeToast(R.string.activity_not_found);
            //Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag=" + tag + " intent=" + intent, e);
        }
        return false;
    }

    boolean startActivitySafely(View v, Intent intent, Object tag) {
        boolean success = false;
        try {
            success = startActivity(v, intent, tag);
        } catch (ActivityNotFoundException e) {
            makeToast(R.string.activity_not_found);
            // Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        }
        return success;
    }

    void startActivityForResultSafely(Intent intent, int requestCode) {
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            makeToast(R.string.activity_not_found);
            //Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            makeToast(R.string.activity_not_found);
            //Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    private void handleFolderClick(FolderIcon folderIcon) {
        final FolderInfo info = folderIcon.getFolderInfo();
        Folder openFolder = mWorkspace.getFolderForTag(info);

        // If the folder info reports that the associated folder is open, then verify that
        // it is actually opened. There have been a few instances where this gets out of sync.
        if (info.opened && openFolder == null) {
            Log.d(TAG, "Folder info marked as open, but associated folder is not open. Screen: "
                    + info.screen + " (" + info.cellX + ", " + info.cellY + ")");
            info.opened = false;
        }

        if (!info.opened && !folderIcon.getFolder().isDestroyed()) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderIcon);
        } else {
            // Find the open folder...
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getPageForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentPage()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderIcon);
                }
            }
        }
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder is animated relative to the
     * specified View. If the View is null, no animation is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    public void openFolder(FolderIcon folderIcon) {
        if (mFloating.forbiddenOpenFolder() || mWorkspace.getOpenFolder() != null ||
                mDragLayer.isDropAnimRunning() || mWorkspace.isPageMoving()) {
            return;
        }

        //#61461 Add by Fan.Yang
        if(!mBindingWorkspaceFinished){
            return;
        }
        Folder folder = folderIcon.getFolder();
        if (folder.getState() == Folder.STATE_ANIMATING) {
            return;
        }

        FolderInfo info = folder.mInfo;
        info.opened = true;
		//yixiao add #974316
        foldeName = info.title.toString();
        folderIcon.initParams();
        openFolderAnimation(folderIcon, true);
    }

    private void initDisplay() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        displayHeight = size.y;
        displayWidth = size.x;

        display.getRealSize(size);
        int deviceHeight = size.y;
        getWindow().getDecorView().setPadding(0, 0, 0, deviceHeight - displayHeight);

        openFolderDuration = getResources().getInteger(R.integer.config_folderAnimDuration);
    }

    private int folderIconLeft, folderIconTop, folderIconRight, folderIconBottom;
    private int displayHeight, displayWidth, openFolderDuration, cellHeight;
    private float openLeftWidth, openRightWidth;
    private Rect currentScreenRect, folderIconBgRect;
    private final float maxAlpha = 1f, minAlpha = -1.5f;
    private float mFolderScale = 0.18823f;
    private float mIconBgScale = 2.5f;
    private Interpolator explodeInterpolator;

    public boolean isNormalModel() {
        return !(isEditMode() ||isFloating());
    }

    /**
     * open folder animation
     * <ul>
     * <li>If bottom of folder(folderBottom) plus height of folder(folderHeight) smaller than lowest y(loewstY), only
     * split down, height need to split down is height of folder(folderHeight).</li>
     * <li>else split up and down at the same time, height need to split up is (folderHeight - (loewstY -
     * folderBottom)), height need to split down is (loewstY - folderBottom)</li>
     * <li>Note that the coordinate system is different below, so folderBottom should be folderIcon.getBottom() plus
     * currentScreen.getTop(), because coordinate of folderIcon.getBottom() is currenScreen, but coordinate of
     * currentScreen.getTop() is display</li>
     * </ul>
     *
     * @param folderIcon
     */
    void openFolderAnimation(final FolderIcon folderIcon, boolean animator) {
        final Folder folder = folderIcon.getFolder();
        if(folder.getInfo() != null && folder.getInfo().screen != mWorkspace.getCurrentPage()){
            Log.e(TAG,"screen param is incorrect when open folder info: "+folder.getInfo().toString()+"  workspace screen: "+mWorkspace.getCurrentPage());
        }
        CellLayout currentScreen = mWorkspace.getCurrentCellLayout();
        final int curPage = mWorkspace.getCurrentPage();
        if (folder == null || currentScreen == null || folder.getParent() != null) {
            return;
        }

        int folderTop = (displayHeight - folder.getFolderHeight()) / 2;
        int folderLeft = (displayWidth - folder.getFolderWidth()) / 2;
        if (isFloating()) {
            mFloating.bringToFront();
        }
        if (animator) {
            computePivot(currentScreen, folderIcon, folderLeft, folderTop);
            currentScreen.setAlpha(0);
            mHotseat.setVisibility(isNormalModel() ? View.VISIBLE : View.INVISIBLE);
            mDragLayer.addView(folder);
            mDragController.setDragScoller(folder.getFolderLayout());
            mDragController.setScrollView(folder);
            mDragController.addDropTarget((DropTarget) folder.getFolderLayout());
            folder.animateOpen(folderTop, -1);

            PropertyValuesHolder bgAlpha = PropertyValuesHolder.ofFloat("alpha", maxAlpha, minAlpha);
            PropertyValuesHolder bgScaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, mIconBgScale);
            PropertyValuesHolder bgScaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, mIconBgScale);
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", mFolderScale, 1f);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", mFolderScale, 1f);
            PropertyValuesHolder leftImageTranslationX = PropertyValuesHolder.ofFloat(
                    "translationX", 0f, -openLeftWidth);
            PropertyValuesHolder rightImageTranslationX = PropertyValuesHolder.ofFloat(
                    "translationX", 0f, openRightWidth);

            // create folder animation
            ObjectAnimator oFolderAnimatorOpen = ObjectAnimator.ofPropertyValuesHolder(folder, scaleX,
                    scaleY);
            // create folder bg animation
            ObjectAnimator leftImageAnimator = ObjectAnimator.ofPropertyValuesHolder(folderSideLeft,
                    bgAlpha, leftImageTranslationX, bgScaleX, bgScaleY);
            ObjectAnimator upImageAnimator = ObjectAnimator.ofPropertyValuesHolder(folderSideUp,
                    bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator rightImageAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    folderSideRight, bgAlpha, rightImageTranslationX, bgScaleX, bgScaleY);
            ObjectAnimator downImageAnimator = ObjectAnimator.ofPropertyValuesHolder(folderSideDown,
                    bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator hotSeatAnimator = ObjectAnimator.ofPropertyValuesHolder(getHotseat(),
                    bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator indicatorAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    mDesktopIndicator, bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator editLayerAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    editModeLayer, bgAlpha, bgScaleX, bgScaleY);

            oFolderAnimatorOpen.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // TODO Auto-generated method stub
                    folder.openAnimationEnd();
                    setHotseatAndIndicatorHwLayerEnabled(false);
                    mWorkspace.setCurrentPage(curPage);
                    mHandler.sendEmptyMessage(SHOW_APP_RECOMMENDS_MSG);
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    // TODO Auto-generated method stub
                    setHotseatAndIndicatorHwLayerEnabled(true);
                    folder.openAnimationStart();
                    BlurWallpaperManager.getInstance(getApplicationContext()).setBlur(true);
                }
            });

            oFolderAnimatorOpen.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float factor = isFloating() ? 1f : (Float) animation.getAnimatedValue();
                    BlurWallpaperManager.getInstance(getApplicationContext())
                            .setBlurAlpha(factor, !isFloating());
                }
            });
            AnimatorSet animSet = LauncherAnimUtils.createAnimatorSet();
            animSet.playTogether(editLayerAnimator, leftImageAnimator, upImageAnimator,
                    rightImageAnimator, downImageAnimator, hotSeatAnimator, indicatorAnimator,
                    oFolderAnimatorOpen);
            explodeInterpolator = createExplodeInterpolator(folderIconBgRect, currentScreenRect.width(),
                    currentScreenRect.height());
            animSet.setInterpolator(explodeInterpolator);
            animSet.start();
        } else {
            currentScreen.setAlpha(0);
            mDragLayer.addView(folder);
            mDragController.setDragScoller(folder.getFolderLayout());
            mDragController.addDropTarget((DropTarget) folder.getFolderLayout());
            folder.animateOpen(folderTop, -1);
            showAppRecommend();
        }
    }

    int velocityLevel = 4;
    Interpolator[] interpolators = new Interpolator[4];

    void initExplodeInterpolator(){
        interpolators[0] = AnimationUtils.loadInterpolator(this, R.anim.folder_interpolator1);
        interpolators[1] = AnimationUtils.loadInterpolator(this, R.anim.folder_interpolator2);
        interpolators[2] = AnimationUtils.loadInterpolator(this, R.anim.folder_interpolator3);
        interpolators[3] = AnimationUtils.loadInterpolator(this, R.anim.folder_interpolator4);
    }

    Interpolator createExplodeInterpolator(Rect iconRect, int width, int height) {
        int rectX = iconRect.centerX();
        int rectY = iconRect.centerY();
        int centerX = width / 2;
        int centerY = height / 2;

        double length = Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2));
        length = length / (velocityLevel * 2);

        int level = -1;
        double distance = Math.sqrt(Math.pow(rectX - centerX, 2) + Math.pow(rectY - centerY, 2));
        level = (int) (distance / length);

        if (level >= 0 && level < interpolators.length) {
            return interpolators[level];
        } else {
            return interpolator;
        }
    }

    /**
     * close folder animation
     */
    private void closeFolderAnimation(boolean animate) {
        final Folder folder = mWorkspace.getOpenFolder();
        final CellLayout currentScreen = (CellLayout) mWorkspace.getChildAt(mWorkspace.getCurrentPage());
        if (folder == null || currentScreen == null) {
            return;
        }

        if(animate){
            folder.animateClosed();

            //create folder close animator
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, mFolderScale);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, mFolderScale);
            ObjectAnimator oFolderAnimatorClosed = ObjectAnimator.ofPropertyValuesHolder(folder,
                    scaleX, scaleY).setDuration(openFolderDuration);

            // create folder bg animation
            PropertyValuesHolder bgAlpha = PropertyValuesHolder.ofFloat("alpha", minAlpha,maxAlpha);
            PropertyValuesHolder bgScaleX = PropertyValuesHolder.ofFloat("scaleX", mIconBgScale,1f);
            PropertyValuesHolder bgScaleY = PropertyValuesHolder.ofFloat("scaleY", mIconBgScale,1f);
            PropertyValuesHolder leftImageTranslationX =
                    PropertyValuesHolder.ofFloat("translationX", -openLeftWidth, 0f);
            PropertyValuesHolder rightImageTranslationX =
                    PropertyValuesHolder.ofFloat("translationX", openRightWidth, 0f);

            ObjectAnimator leftImageAnimatorClosed = ObjectAnimator.ofPropertyValuesHolder(
                    folderSideLeft, bgAlpha, leftImageTranslationX, bgScaleX, bgScaleY);
            ObjectAnimator oUpImageAnimatorClosed = ObjectAnimator.ofPropertyValuesHolder(
                    folderSideUp, bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator rightImageAnimatorClosed = ObjectAnimator.ofPropertyValuesHolder(
                    folderSideRight, bgAlpha, rightImageTranslationX, bgScaleX, bgScaleY);
            ObjectAnimator oDownImageAnimatorClosed = ObjectAnimator.ofPropertyValuesHolder(
                    folderSideDown, bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator hotSeatAnimator = ObjectAnimator.ofPropertyValuesHolder(getHotseat(),
                    bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator indicatorAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    mDesktopIndicator, bgAlpha, bgScaleX, bgScaleY);
            ObjectAnimator editLayerAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    editModeLayer, bgAlpha, bgScaleX, bgScaleY);

            AnimatorSet animSet = LauncherAnimUtils.createAnimatorSet();
            animSet.setDuration(openFolderDuration);
            animSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mWorkspace == null || mDragLayer == null) {
                        return;
                    }
                    folder.getFolderLayout().onDropCompletedCallback();
                    setHotseatAndIndicatorHwLayerEnabled(false);
                    BlurWallpaperManager.getInstance(getApplicationContext()).setBlur(isFloating());
                    currentScreen.setAlpha(1f);
                    currentScreen.setDrawingCacheEnabled(false);
                    removeExplosionDebris();
                    folder.closeAnimationEnd();
                    //lqwang - remove smart sort and recommend - modify begin
                    if(mAppRecommend != null){
                        mAppRecommend.clearRecomContent();
                    }
                    //lqwang - remove smart sort and recommend - modify end
                    mHotseat.setVisibility(View.VISIBLE);
                    //lqwang - PR65454 - add begin
                    if(mDragController.getDragView() != null){
                        mWorkspace.scrollFolderPreview(null, false);
                    }
                    //lqwang - PR65454 - add end
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    setHotseatAndIndicatorHwLayerEnabled(true);
                    mHandler.removeMessages(SHOW_APP_RECOMMENDS_MSG);
                    mHandler.sendEmptyMessage(HIDE_APP_RECOMMENDS_MSG);
                    folder.closeAnimationStart();
                }
            });

            ValueAnimator translationAnimator = LauncherAnimUtils.ofFloat(1f, 0f);
            translationAnimator.addUpdateListener(new AnimatorUpdateListener() {
                FolderLayout folderLayout = (FolderLayout) folder.getFolderLayout();
                int currentScreen = folderLayout.getCurrentPage();

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    // TODO Auto-generated method stub
                    float factor = (Float) animation.getAnimatedValue();
                    folder.setChildrenTranslationAndScale(factor, currentScreen,
                            folderIconBgRect, mFolderScale);
                    BlurWallpaperManager.getInstance(getApplicationContext())
                            .setBlurAlpha(factor, !isFloating());
                }
            });

            ValueAnimator alphaAnimator = LauncherAnimUtils.ofFloat(0f, 1f);
            alphaAnimator.setStartDelay(openFolderDuration / 2);
            alphaAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float factor = (Float) animation.getAnimatedValue();
                    folder.setFolderBgAlpha(factor);
                }
            });
            animSet.playTogether(editLayerAnimator,leftImageAnimatorClosed, oUpImageAnimatorClosed,
                    rightImageAnimatorClosed, oDownImageAnimatorClosed, oFolderAnimatorClosed,
                    translationAnimator, alphaAnimator, hotSeatAnimator, indicatorAnimator);
            animSet.setInterpolator(
                    explodeInterpolator != null ? explodeInterpolator : interpolator);
            animSet.start();
        } else {
            currentScreen.setAlpha(1f);
            mHotseat.setVisibility(View.VISIBLE);
            hideAppRecommend();
            folder.closeAnimationEnd();
        }
    }

    private void computePivot(CellLayout currentScreen,FolderIcon folderIcon, int folderLeft, int folderTop){
        Folder folder = folderIcon.getFolder();
        Rect folderIconRect = new Rect();
        folderIcon.getFolderIconZone().getGlobalVisibleRect(folderIconRect);
        currentScreenRect = new Rect();
        currentScreen.getGlobalVisibleRect(currentScreenRect);
        TextView folderName = folderIcon.getFolderName();
        Rect folderNameRect = new Rect();
        folderName.getGlobalVisibleRect(folderNameRect);
        View folderIconBg = folderIcon.getFolderPreviewBg();
        folderIconBgRect = new Rect();
        folderIconBg.getGlobalVisibleRect(folderIconBgRect);

        int folderHeight = folder.getFolderHeight();

        mFolderScale = FolderIcon.PREVIEW_SCALE *
                (isFloating() || isEditMode() ? folderIcon.getScaleX() : 1f);
        mIconBgScale = 1f / mFolderScale;
        cellHeight = currentScreen.getCellHeight();

        folderIconLeft = folderIconRect.left;
        folderIconTop = folderIconRect.top;
        folderIconRight = folderIconRect.right;
        folderIconBottom = folderNameRect.bottom;

        int vSpacing = (cellHeight - folderIconBottom + folderIconBgRect.top) / 2;

        openFolderExplodeView(currentScreen, vSpacing);
        // set scale pivotX and pivotY
        int leftOffset = folderIconBgRect.left - folderLeft;
        int topOffset = folderIconBgRect.top - folderTop;
        float height = folderHeight * mFolderScale;
        float delta = ((float) folderHeight) / (folderHeight - height);
        float folderPivotX2 = leftOffset * delta;
        float folderPivotY2 = topOffset * delta;

        folder.setPivotX(folderPivotX2);
        folder.setPivotY(folderPivotY2);

        openLeftWidth = folderIconLeft;
        openRightWidth = displayWidth - folderIconRight;

        float leftBgPivotX = folderIconLeft;
        float leftBgPivotY = vSpacing + mFolderScale * folderPivotY2;
        folderSideLeft.setPivotX(leftBgPivotX);
        folderSideLeft.setPivotY(leftBgPivotY);

        float upBgPivotX = folderIconBgRect.left + mFolderScale * folderPivotX2;
        float upBgPivotY = folderIconBgRect.top - currentScreenRect.top + mFolderScale * folderPivotY2;
        folderSideUp.setPivotX(upBgPivotX);
        folderSideUp.setPivotY(upBgPivotY);

        float rightBgPivotY = vSpacing + mFolderScale * folderPivotY2;
        folderSideRight.setPivotX(0);
        folderSideRight.setPivotY(rightBgPivotY);

        float downBgPivotY =
                (folderIconRect.height() - mFolderScale * folderPivotY2) + folderNameRect.height();
        folderSideDown.setPivotX(upBgPivotX);
        folderSideDown.setPivotY(-downBgPivotY);

        Rect hotSeatRect = new Rect();
        mHotseat.getGlobalVisibleRect(hotSeatRect);
        float hotSeatPivotY = hotSeatRect.top - folderIconBgRect.top - mFolderScale * folderPivotY2;
        mHotseat.setPivotX(upBgPivotX);
        mHotseat.setPivotY(-hotSeatPivotY);

        Rect indicatorRect = new Rect();
        mDesktopIndicator.getGlobalVisibleRect(indicatorRect);
        float indicatorPivotX = upBgPivotX - indicatorRect.left;
        float indicatorPivotY = indicatorRect.top - folderIconBgRect.top - mFolderScale * folderPivotY2;
        mDesktopIndicator.setPivotX(indicatorPivotX);
        mDesktopIndicator.setPivotY(-indicatorPivotY);

        editModeLayer.setPivotX(upBgPivotX);
        editModeLayer.setPivotY(folderIconBgRect.top + mFolderScale * folderPivotY2);
    }

    private ImageView folderSideLeft;
    private ImageView folderSideUp;
    private ImageView folderSideRight;
    private ImageView folderSideDown;

    private Bitmap folderLeftBitmap;
    private Bitmap folderUpBitmap;
    private Bitmap folderRightBitmap;
    private Bitmap folderDownBitmap;

    private void openFolderExplodeView(View parent, int spacing) {
        parent.setDrawingCacheEnabled(true);
        Bitmap viewBmp = parent.getDrawingCache();
        if (viewBmp == null) {
            return;
        }

        //left
        if (folderSideLeft == null) {
            folderSideLeft = new ImageView(getApplicationContext());
            FrameLayout.LayoutParams leftParams =
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            folderSideLeft.setScaleType(ImageView.ScaleType.MATRIX);
            folderSideLeft.setLayoutParams(leftParams);
        }
        int hTop = folderIconTop - currentScreenRect.top - spacing;
        int leftWidth = Math.max(folderIconLeft - currentScreenRect.left, 1);
        folderLeftBitmap = getFolderAnimBitmap(viewBmp, 0, hTop,
                leftWidth, cellHeight);
        folderSideLeft.setImageBitmap(folderLeftBitmap);
        ((LayoutParams) folderSideLeft.getLayoutParams()).topMargin = folderIconTop - spacing;
        ((LayoutParams) folderSideLeft.getLayoutParams()).leftMargin = currentScreenRect.left;
        mDragLayer.addView(folderSideLeft);

        //top
        if (folderSideUp == null) {
            folderSideUp = new ImageView(getApplicationContext());
            FrameLayout.LayoutParams upParams =
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            folderSideUp.setScaleType(ImageView.ScaleType.MATRIX);
            folderSideUp.setLayoutParams(upParams);
        }
        if (hTop > 0) {
            folderUpBitmap = getFolderAnimBitmap(viewBmp, 0, 0,
                    viewBmp.getWidth(), hTop);
            folderSideUp.setImageBitmap(folderUpBitmap);
            ((LayoutParams) folderSideUp.getLayoutParams()).topMargin = currentScreenRect.top;
            ((LayoutParams) folderSideUp.getLayoutParams()).leftMargin = currentScreenRect.left;
        } else {
            folderSideUp.setImageBitmap(null);
        }
        mDragLayer.addView(folderSideUp);

        //right
        if (folderSideRight == null) {
            folderSideRight = new ImageView(getApplicationContext());
            FrameLayout.LayoutParams rightParams =
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            folderSideRight.setScaleType(ImageView.ScaleType.MATRIX);
            folderSideRight.setLayoutParams(rightParams);
        }
        int rightX = folderIconRight - currentScreenRect.left;
        folderRightBitmap = getFolderAnimBitmap(viewBmp, rightX, hTop
                , viewBmp.getWidth() - rightX, cellHeight);
        folderSideRight.setImageBitmap(folderRightBitmap);
        ((LayoutParams) folderSideRight.getLayoutParams()).topMargin = folderIconTop - spacing;
        ((LayoutParams) folderSideRight.getLayoutParams()).leftMargin = folderIconRight;
        mDragLayer.addView(folderSideRight);

        //down
        if (folderSideDown == null) {
            folderSideDown = new ImageView(getApplicationContext());
            FrameLayout.LayoutParams downParams =
                    new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            folderSideDown.setScaleType(ImageView.ScaleType.MATRIX);
            folderSideDown.setLayoutParams(downParams);
        }
        int downY = folderIconBottom - currentScreenRect.top;
        if (downY > 0 && viewBmp.getHeight() > downY) {
            folderDownBitmap = getFolderAnimBitmap(viewBmp, 0, downY,
                    viewBmp.getWidth(), viewBmp.getHeight() - downY);
            folderSideDown.setImageBitmap(folderDownBitmap);
            ((LayoutParams) folderSideDown.getLayoutParams()).topMargin = folderIconBottom;
            ((LayoutParams) folderSideDown.getLayoutParams()).leftMargin = currentScreenRect.left;
        } else {
            folderSideDown.setImageBitmap(null);
        }

        mDragLayer.addView(folderSideDown);
    }

    private Bitmap getFolderAnimBitmap(Bitmap source,int x,int y,int width,int height){
        Bitmap b = null;
        try {
            b = Bitmap.createBitmap(source, x, y,width,height);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG,"getFolderAnimBitmap error x: "+x+"  y: "+y+" width: "+width+"  height: "+height);
        } catch (OutOfMemoryError e){
            e.printStackTrace();
        }
        return b;
    }

    private void removeExplosionDebris() {
        mDragLayer.removeView(folderSideDown);
        mDragLayer.removeView(folderSideRight);
        mDragLayer.removeView(folderSideUp);
        mDragLayer.removeView(folderSideLeft);
        folderSideLeft.setImageBitmap(null);
        folderSideUp.setImageBitmap(null);
        folderSideRight.setImageBitmap(null);
        folderSideDown.setImageBitmap(null);
        folderLeftBitmap = null;
        folderUpBitmap = null;
        folderRightBitmap = null;
        folderDownBitmap = null;
    }

//    private void showOpenFolderView(FolderIcon folderIcon,View view, int paddingX, int spacing) {
//        folderIcon.setTextVisible(false);
//        view.setDrawingCacheEnabled(true);
//        Bitmap b = view.getDrawingCache();
//        if (b == null) {
//            return;
//        }
//        int notiBarHeight = 0;
//        if(isFloating()){
//            notiBarHeight = currentScreenRect.top;
//        }
//        // up image
//        if (openFolderBgUp == null || openFolderBgUp.getParent() == null) {
//            RelativeLayout.LayoutParams screenImageParams = new RelativeLayout.LayoutParams(
//                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
//            screenImageParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
//            openFolderBgUp = new FolderOpenView(this, screenImageParams);
//            mDragLayer.addView(openFolderBgUp, new FrameLayout.LayoutParams(
//                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));
//            openFolderBgUp.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        }
//        openFolderBgUp.setScreenImagePadding(paddingX);
//        //openFolderBgUp.setVisibility(View.VISIBLE);
//        ImageView upImageView = openFolderBgUp.getScreenImage();
//        upImageView.setPadding(upImageView.getPaddingLeft(), upImageView.getPaddingTop(),
//                upImageView.getPaddingRight(), folderIconTop - spacing);
//        LayoutParams openFolderBgUpParams = (LayoutParams) openFolderBgUp.getLayoutParams();
//        openFolderBgUpParams.topMargin = notiBarHeight;
//        // openFolderBgUpParams.height = folderBottom;
//        openFolderBgUpParams.height = folderIconTop - spacing - notiBarHeight;
//        openFolderBgUp.setBitmap(b, null);
//
//        // down image
//        if (openFolderBgDown == null || openFolderBgDown.getParent() == null) {
//            RelativeLayout.LayoutParams screenImageParams = new RelativeLayout.LayoutParams(
//                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
//            screenImageParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
//            openFolderBgDown = new FolderOpenView(this, screenImageParams);
//            mDragLayer.addView(openFolderBgDown, new FrameLayout.LayoutParams(
//                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
//            openFolderBgDown.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        }
//        openFolderBgDown.setScreenImagePadding(paddingX);
//        //openFolderBgDown.setVisibility(View.VISIBLE);
//        LayoutParams openFolderBgDownParams = (LayoutParams) openFolderBgDown.getLayoutParams();
//        openFolderBgDownParams.height = displayHeight - folderIconBottom;
//        ImageView downImage = openFolderBgDown.getScreenImage();
//        downImage.setPadding(downImage.getPaddingLeft(), -folderIconBottom + notiBarHeight,
//                downImage.getPaddingRight(), downImage.getPaddingBottom());
//        openFolderBgDown.setBitmap(b, null);
//
//        // left image
//        if (openFolderBgLeft == null || openFolderBgLeft.getParent() == null) {
//            RelativeLayout.LayoutParams screenImageParams = new RelativeLayout.LayoutParams(
//                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//            screenImageParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
//            openFolderBgLeft = new FolderOpenView(this, screenImageParams);
//            mDragLayer.addView(openFolderBgLeft, new FrameLayout.LayoutParams(
//                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT));
//            openFolderBgLeft.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        }
//        openFolderBgLeft.setScreenImagePadding(paddingX);
//        //openFolderBgLeft.getLayoutParams().width =
//        //openFolderBgLeft.setVisibility(View.VISIBLE);
//        ImageView leftImage = openFolderBgLeft.getScreenImage();
//        leftImage.setPadding(leftImage.getPaddingLeft(), -folderIconTop + spacing + notiBarHeight,
//                leftImage.getPaddingRight(), leftImage.getPaddingBottom());
//        LayoutParams openFolderBgLeftParams = (LayoutParams) openFolderBgLeft.getLayoutParams();
//        openFolderBgLeftParams.width = folderIconLeft - currentScreenRect.left;
//        openFolderBgLeftParams.height = cellLayoutHeight;
//        openFolderBgLeftParams.topMargin = folderIconTop - spacing;
//        openFolderBgLeft.setBitmap(b, null);
//
//        // right image
//        if (openFolderBgRight == null || openFolderBgRight.getParent() == null) {
//            RelativeLayout.LayoutParams screenImageParams = new RelativeLayout.LayoutParams(
//                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//            screenImageParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
//            openFolderBgRight = new FolderOpenView(this, screenImageParams);
//            mDragLayer.addView(openFolderBgRight, new FrameLayout.LayoutParams(
//                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.RIGHT));
//            openFolderBgRight.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        }
//        openFolderBgRight.setScreenImagePadding(paddingX);
//        //openFolderBgRight.setVisibility(View.VISIBLE);
//        ImageView rightImage = openFolderBgRight.getScreenImage();
//        rightImage.setPadding(-folderIconRight, -folderIconTop + spacing + notiBarHeight,
//                rightImage.getPaddingRight(), rightImage.getPaddingBottom());
//        LayoutParams openFolderBgRightParams = (LayoutParams) openFolderBgRight.getLayoutParams();
//        openFolderBgRightParams.width = currentScreenRect.right - folderIconRight;
//        openFolderBgRightParams.height = cellLayoutHeight;
//        openFolderBgRightParams.topMargin = folderIconTop - spacing;
//        openFolderBgRight.setBitmap(b, null);
//    }

//    public FolderEditText getFolderEditName() {
//        if (openFolderWallpaperBg != null) {
//            return openFolderWallpaperBg.getFolderName();
//        }
//        return null;
//    }

    private int[] bubbleSize;

    public int[] getBubbleSize() {
        if (bubbleSize != null) {
            return bubbleSize;
        }

        bubbleSize = new int[2];
        mWorkspace.getOneBubble(bubbleSize);
        return bubbleSize;
    }

    private void showAppRecommend() {
        if (!PreferencesProvider.isRecommendOn(this) || isFloating() || isEditMode()) {
            return;
        }
        Folder folder = mWorkspace.getOpenFolder();
        FolderInfo info = folder.getInfo();
        Log.d(TAG, "showAppRecommend,size=" + info.recommendApps.size());
        if (info.recommendApps.size() > 0 && mAppRecommend != null && mAppRecommend.getParent() == null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            mAppRecommend.recommendIndex = mAppRecommend.recommendIndex - 4 < 0 ? 0
                    : mAppRecommend.recommendIndex - 4;
            mAppRecommend.refreshUI(info, true);
            mDragLayer.addView(mAppRecommend, lp);
            ObjectAnimator.ofFloat(mAppRecommend, View.TRANSLATION_Y, 300, 0).setDuration(300)
                    .start();
            isOpenAppRecommend = true;
        }
        LauncherApplication app = ((LauncherApplication) getApplication());
        app.mAppRecommendHelper.startUpdater(true, null, false, null, false);
    }

    private void hideAppRecommend() {
        isOpenAppRecommend = false;
        if (!PreferencesProvider.isRecommendOn(this) || isFloating() || mWorkspace == null) {
            return;
        }

        if (mAppRecommend != null && mAppRecommend.getParent() != null) {
            ObjectAnimator anim = ObjectAnimator.ofFloat(mAppRecommend, View.TRANSLATION_Y, 0, 300);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mDragLayer.removeView(mAppRecommend);
                }
            });
            anim.setDuration(200);
            anim.start();
        }
    }

    public void closeFolder() {
        Folder folder = mWorkspace.getOpenFolder();
        if (folder != null) {
            if (folder.getState() == Folder.STATE_ANIMATING || folder.isPageMoving() ||
                    mFloating.forbiddenCloseFolder()) {
                return;
            }
            if (folder.isEditingName()) {
                folder.dismissEditingName();
            //zwsun@lewatek.com PR949568 2014.03.25 start
            }else {
			    //yixiao add #974316
                if(!foldeName.equals(folder.mInfo.title.toString())){
                    folder.doneEditingFolderName(true);
                }
            }
            //zwsun@lewatek.com PR949568 2014.03.25 end
            mDragController.setDragScoller(mWorkspace);
            mDragController.setScrollView(mDragLayer);
            closeFolderAnimation(true);
        }
    }

    void closeFolder(Folder folder) {
        if (folder.getState() == Folder.STATE_ANIMATING) {
            return;
        }
        folder.getInfo().opened = false;
        folder.animateClosed();
    }

    private FloatingLayer mFloating;

    FloatingLayer getFloating() {
        return mFloating;
    }

    DeleteDropTarget getDeleteZone() {
        return mDeleteZone;
    }

    boolean isFloating() {
        return mFloating.isFloating();
    }

    public boolean onLongClick(View v) {
        //yixiao add for #71170 2015.3.25
        if (!isDraggingEnabled() || isWorkspaceLocked()
                || mState != State.WORKSPACE || isPreviewMode || isOpenHiddenApps) {
            return false;
        }

//        Folder folder = mWorkspace.getOpenFolder();
//        if ((folder != null && folder.isOpening())) {
//            return true;
//        }

        boolean isFloating = isFloating();
        if (isFloating && mFloating.isEmptyCell(v) && !(v instanceof AppWidgetHostView)) {
            mFloating.endFloating(true, true);
            return true;
        }

        while (!(v instanceof CellLayout)) {
            v = (View) v.getParent();
        }

        resetAddInfo();
        CellLayout.CellInfo longClickCellInfo = (CellLayout.CellInfo) v.getTag();
        // This happens when long clicking an item with the dpad/trackball
        if (longClickCellInfo == null) {
            return true;
        }
        if (isFloating) {
            if (!mWorkspace.isPageMoving()) {
                if (!mFloating.isFloatingCellLayout(v) && !mDragController.isDragging()) {
                    // is dragging workspace cell
                    mFloating.beforeFloatingDrag(longClickCellInfo);
                    mWorkspace.startDrag(longClickCellInfo);
                    // if (FloatingLayer.allowFloat(longClickCellInfo.cell)) {
                    // // drag shortcut and folder in floating mode
                    // mFloating.toggleCell(longClickCellInfo);
                    // mFloating.startDrag(longClickCellInfo, true);
                    // } else if (mWorkspace.allowLongPress()) {
                    // // drag widget in floating mode
                    // mFloating.beforeFloatingDrag(longClickCellInfo);
                    // mWorkspace.startDrag(longClickCellInfo);
                    // }
                } else {
                    // is dragging floating cell
                    mFloating.isDragFloatingCell = true;
                    mFloating.startDrag(longClickCellInfo, false);
                }
            }
            return true;
        }

        // The hotseat touch handling does not go through Workspace, and we always allow long press on hotseat items.
        final View itemUnderLongClick = longClickCellInfo.cell;
        boolean allowLongPress = mWorkspace.allowLongPress();
        if (allowLongPress && !mDragController.isDragging()) {
            if (itemUnderLongClick == null) {
                // User long pressed on empty space
                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                //lqwang - PR951628 - modify begin
                if(!mWorkspace.getCurrentCellLayout().isPifLowPage()){
                    enterEditMode(EditModeLayer.ANIMORIENTATION.VERTICAL);
                }
                //lqwang - PR951628 - modify end
            } else {
                if (!(itemUnderLongClick instanceof Folder)) {
                    // User long pressed on an item
                    mWorkspace.startDrag(longClickCellInfo);
                }
            }
        }
        return true;
    }

    public void enterEditMode(EditModeLayer.ANIMORIENTATION animorientation) {
        mWorkspace.refreshFolderIconHat(true);
        editModeLayer.startEditMode(animorientation);
    }

    public EditModeLayer getEditModeLayer() {
        return editModeLayer;
    }

    public MovedHotseatWrapper getMovedHotseatWrapper(){
        return movedHotseatWrapper;
    }

    public boolean isEditModeVisible() {
        return editModeLayer.getVisibility() == View.VISIBLE;
    }

    public boolean isEditMode() {
        return editModeLayer != null && editModeLayer.isEditMode();
    }

    public boolean isPreviewMode(){
        return isPreviewMode;
    }

    public boolean isEditModeSwitching() {
        return editModeLayer != null && editModeLayer.isExecAnimRunning();
    }

    Hotseat getHotseat() {
        return mHotseat;
    }

    AppRecommend getAppRecommend() {
        return mAppRecommend;
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    public CellLayout getCellLayout(long container, int screen) {
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            return null;
        } else {
            return mWorkspace.getCellLayout(screen);
        }
    }

    public Workspace getWorkspace() {
        return mWorkspace;
    }

    // Now a part of LauncherModel.Callbacks. Used to reorder loading steps.
    @Override
    public boolean isAllAppsVisible() {
        return false;
    }

    void updateWallpaperVisibility(boolean visible) {
        int wpflags = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int curflags = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (wpflags != curflags) {
            getWindow().setFlags(wpflags, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);}

        if (!hasFocus) {
            // When another window occludes launcher (like the notification shade, or recents),
            // ensure that we enable the wallpaper flag so that transitions are done correctly.
            updateWallpaperVisibility(true);

            unregisterShakeLister();
        } else {
            registerShakeLister();
        }
    }

    void showWorkspace(boolean animated) {
        mWorkspace.setVisibility(View.VISIBLE);
        // Change the state *after* we've called all the transition code
        mState = State.WORKSPACE;

        // Resume the auto-advance of widgets
        mUserPresent = true;
        updateRunning();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    /**
     * Add an item from all apps or customize onto the given workspace screen.
     * If layout is null, add to the current screen.
     */
    void addExternalItemToScreen(ItemInfo itemInfo, final CellLayout layout) {
        if (!mWorkspace.addExternalItemToScreen(itemInfo, layout)) {
            showOutOfSpaceMessage();
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        return result;
    }

    /**
     * Receives notifications when system dialogs are to be closed.
     */
    private class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeSystemDialogs();
        }
    }

    /**
     * Receives notifications whenever the appwidgets are reset.
     */
    private class AppWidgetResetObserver extends ContentObserver {
        public AppWidgetResetObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onAppWidgetReset();
        }
    }

    /**
     * If the activity is currently paused, signal that we need to re-run the loader
     * in onResume.
     * <p/>
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     * <p/>
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    public boolean setLoadOnResume() {
        if (mPaused) {
            Log.i(TAG, "setLoadOnResume");
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public int getCurrentWorkspaceScreen() {
        if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return -1;
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     * <p/>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        //#61461 Add by Fan.Yang
        mBindingWorkspaceFinished = false;
        mNewShortcutAnimatePage = -1;
        mNewShortcutAnimateViews.clear();
        mWorkspace.clearDropTargets();
        int count = mWorkspace.getChildCount();
        for (int i = 0; i < count; i++) {
            // Use removeAllViewsInLayout() to avoid an extra requestLayout() and invalidate().
            mWorkspace.getCellLayout(i).removeAllViewsInLayout();
        }
        mWidgetsToAdvance.clear();
        if (mHotseat != null) {
            mHotseat.resetLayout();
        }
        SharedPreferences sp = PreferencesProvider.getSharedPreferences(this);
        if (sp.getBoolean("add_recommend_folder", false)) {
            sp.edit().putBoolean("add_recommend_folder", false).commit();
            mModel.addRecommendFolderInWorkspace(getApplicationContext());
        }
        //lqwang - remove smart sort and recommend - modify begin
        if(PreferencesProvider.isRecommendOrSmartSortOn(Launcher.this)){
            LauncherApplication app = ((LauncherApplication) getApplication());
            app.mAppRecommendHelper.startUpdater(true, null, true, null, false);
            app.mAppRecommendHelper.setFinishLoading(true);
        }
        //lqwang - remove smart sort and recommend - modify end
    }

    /**
     * Bind the items start-end from the list.
     * <p/>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end) {
        setLoadOnResume();

        // Get the list of added shortcuts and intersect them with the set of shortcuts here
        Set<String> newApps = new HashSet<String>();
        newApps = mSharedPrefs.getStringSet(InstallShortcutReceiver.NEW_APPS_LIST_KEY, newApps);

        Workspace workspace = mWorkspace;
        mWorkspace.setIsFirstBind(true);
        for (int i = start; i < end; i++) {
            final ItemInfo item = shortcuts.get(i);
            if (item instanceof ShortcutInfo) {
                int state = ((ShortcutInfo) item).state;
                if (state != ShortcutInfo.STATE_OK || item.isHidden()) {
                    continue;
                }
            }
            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                mHotseat.setSeat(item);
                continue;
            }
            // auto add screen by luoyongxing
            int screenCnt = mWorkspace.getChildCount();
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP
                    && item.screen >= screenCnt) {
                while (item.screen >= screenCnt) {
                    workspace.addScreen(screenCnt);
                    screenCnt++;
                }
            }
            // end

            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    ShortcutInfo info = (ShortcutInfo) item;
                    String uri = info.intent.toUri(0).toString();
                    View shortcut = createShortcut(info);
                    workspace.addInScreen(shortcut, item.container, item.screen, item.cellX,
                            item.cellY, 1, 1, false);
                    boolean animateIconUp = false;
                    synchronized (newApps) {
                        if (newApps.contains(uri)) {
                            animateIconUp = newApps.remove(uri);
                        }
                    }
                    if (animateIconUp) {
                        // Prepare the view to be animated up
                        shortcut.setAlpha(0f);
                        shortcut.setScaleX(0f);
                        shortcut.setScaleY(0f);
                        mNewShortcutAnimatePage = item.screen;
                        if (!mNewShortcutAnimateViews.contains(shortcut)) {
                            mNewShortcutAnimateViews.add(shortcut);
                        }
                    }
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon_lewa, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item, mIconCache);
                    workspace.addInScreen(newFolder, item.container, item.screen, item.cellX,
                            item.cellY, 1, 1, false);
                    break;
            }
        }
        mWorkspace.setIsFirstBind(false);
        workspace.requestLayout();
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindFolders(HashMap<Long, FolderInfo> folders) {
        setLoadOnResume();
        sFolders.clear();
        sFolders.putAll(folders);
    }

    /**
     * Add the views for a widget to the workspace.
     * <p/>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppWidget(LauncherAppWidgetInfo item) {
        setLoadOnResume();

        final Workspace workspace = mWorkspace;
        mWorkspace.setIsFirstBind(true);
        final int appWidgetId = item.appWidgetId;
        final AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        item.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
        item.hostView.setTag(item);
        item.onBindAppWidget(this);

        int scrCnt = workspace.getChildCount();
        if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP
                && item.screen >= scrCnt && item.screen < PreferencesProvider.MAX_SCREEN_COUNT) {
            while (item.screen >= scrCnt) {
                workspace.addScreen(scrCnt);
            }
        }
        workspace.addInScreen(item.hostView, item.container, item.screen, item.cellX,
                item.cellY, item.spanX, item.spanY, false);
        addWidgetToAutoAdvanceIfNeeded(item.hostView, appWidgetInfo);

        workspace.requestLayout();
        mWorkspace.setIsFirstBind(false);
    }

    public void onPageBoundSynchronously(int page) {
        mSynchronouslyBoundPages.add(page);
    }

    /**
     * Callback saying that there aren't any more items to bind.
     * <p/>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems() {
        setLoadOnResume();

        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getCurrentCellLayout().requestFocus();
            }
            mSavedState = null;
        }

        // Begin, modified by zhumeiquan, 20130730
        //ADW: sometimes on rotating the phone, some widgets fail to restore its states.... so... damn.
        try {
            mWorkspace.restoreInstanceStateForRemainingPages();
        } catch (Exception e) {

        }
        // End

        // If we received the result of any pending adds while the loader was running (e.g. the
        // widget configuration forced an orientation change), process them now.
        for (int i = 0; i < sPendingAddList.size(); i++) {
            completeAdd(sPendingAddList.get(i));
        }
        sPendingAddList.clear();

        // Animate up any icons as necessary
        if (mVisible || mWorkspaceLoading) {
            Runnable newAppsRunnable = new Runnable() {
                @Override
                public void run() {
                    runNewAppsAnimation(false);
                }
            };

            boolean willSnapPage = mNewShortcutAnimatePage > -1 &&
                    mNewShortcutAnimatePage != mWorkspace.getCurrentPage();
            if (canRunNewAppsAnimation()) {
                // If the user has not interacted recently, then either snap to the new page to show
                // the new-apps animation or just run them if they are to appear on the current page
                if (willSnapPage) {
                    mWorkspace.snapToPage(mNewShortcutAnimatePage, newAppsRunnable);
                } else {
                    runNewAppsAnimation(false);
                }
            } else {
                // If the user has interacted recently, then just add the items in place if they
                // are on another page (or just normally if they are added to the current page)
                runNewAppsAnimation(willSnapPage);
            }
        }

        if (mUnreadLoadCompleted) {
            bindWorkspaceUnreadInfo();
        }
        mBindingWorkspaceFinished = true;
        mWorkspaceLoading = false;
        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
            isLocalChanged = false;
        }
		//zwsun@letek.com 20150108 start
        if(isPiflowPageEnable() && mWorkspace.getCurrentPage() == 0){
                mWorkspace.snapToPage(1);
            }
        if (isLocalChanged) {
            //lqwang - PR64369 - modify begin
            if(mWorkspace.getCurrentPage() == 0){
                mWorkspace.moveToDefaultScreen(true);
            }
            //lqwang - PR64369 - modify end
            isLocalChanged = false;
        }
        if (mModel.isSortingWorkspace) {
            mWorkspace.moveToDefaultScreen(true);
            mOnResumeNeedsLoad = false;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent();
                    intent.setClassName(getPackageName(), "com.lewa.launcher.Launcher");
                    startActivity(intent);
                }
            }, 300);
            mModel.isSortingWorkspace = false;
        }
    }

    private boolean canRunNewAppsAnimation() {
        long diff = System.currentTimeMillis() - mDragController.getLastGestureUpTime();
        return diff > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
    }

    /**
     * Runs a new animation that scales up icons that were added while Launcher was in the
     * background.
     *
     * @param immediate whether to run the animation or show the results immediately
     */
    private void runNewAppsAnimation(boolean immediate) {
        AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
        Collection<Animator> bounceAnims = new ArrayList<Animator>();

        // Order these new views spatially so that they animate in order
        Collections.sort(mNewShortcutAnimateViews, new Comparator<View>() {
            @Override
            public int compare(View a, View b) {
                CellLayout.LayoutParams alp = (CellLayout.LayoutParams) a.getLayoutParams();
                CellLayout.LayoutParams blp = (CellLayout.LayoutParams) b.getLayoutParams();
                int cellCountX = LauncherModel.getCellCountX();
                return (alp.cellY * cellCountX + alp.cellX) - (blp.cellY * cellCountX + blp.cellX);
            }
        });

        // Animate each of the views in place (or show them immediately if requested)
        if (immediate) {
            for (View v : mNewShortcutAnimateViews) {
                v.setAlpha(1f);
                v.setScaleX(1f);
                v.setScaleY(1f);
            }
        } else {
            for (int i = 0; i < mNewShortcutAnimateViews.size(); ++i) {
                View v = mNewShortcutAnimateViews.get(i);
                ValueAnimator bounceAnim = LauncherAnimUtils.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat("alpha", 1f),
                        PropertyValuesHolder.ofFloat("scaleX", 1f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f));
                bounceAnim.setDuration(InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
                bounceAnim.setStartDelay(i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
                bounceAnim.setInterpolator(new SmoothPagedView.OvershootInterpolator());
                bounceAnims.add(bounceAnim);
            }
            anim.playTogether(bounceAnims);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mWorkspace != null) {
                        mWorkspace.postDelayed(mBuildLayersRunnable, 500);
                    }
                }
            });
            anim.start();
        }

        // Clean up
        mNewShortcutAnimatePage = -1;
        mNewShortcutAnimateViews.clear();
        new Thread("clearNewAppsThread") {
            public void run() {
                mSharedPrefs.edit()
                        .putInt(InstallShortcutReceiver.NEW_APPS_PAGE_KEY, -1)
                        .putStringSet(InstallShortcutReceiver.NEW_APPS_LIST_KEY, null)
                        .commit();
            }
        }.start();
    }

    /**
     * Add the icons for all apps. Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(final ArrayList<ApplicationInfo> apps) {
        // Bind all applications
    }

    /**
     * A package was installed. Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsAdded(ArrayList<ApplicationInfo> apps) {
        // Begin, added by zhumeiquan, 20130926, if installing apps background, dragging must be cancelled
        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
        }
        // End

        // exit floating when adding apps. by luoyongxing
        if (mFloating != null && mFloating.isFloating()) {
            mFloating.endFloating(false, false);
        }
        // end

        checkIsRecommend(apps);
        refreshRecommend(apps);
        setLoadOnResume();
    }

    private void checkIsRecommend(ArrayList<ApplicationInfo> apps) {
        //lqwang - remove smart sort and recommend - modify begin
        if(!PreferencesProvider.isRecommendOn(getApplicationContext())){
            return;
        }
        //lqwang - remove smart sort and recommend - modify end

        LauncherApplication app = ((LauncherApplication) getApplication());
        for (final ApplicationInfo info : apps) {
            Utilities.mAddedPackages.add(info.componentName.getPackageName());
            FolderInfo folderInfo = Utilities.isRecommendPackage(getApplicationContext(), info);
            if (folderInfo != null) {
                if (LauncherModel.shortcutExists(getApplicationContext(), info.componentName)) {
                    continue;
                }
                Intent intent = new Intent();
                Parcelable shortIcon = mIconCache.getIcon(info.intent);
                intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, info.intent);
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, info.title);
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, shortIcon);
                final ShortcutInfo shortcutInfo = app.getModel().infoFromShortcutIntent(
                        getApplicationContext(), intent, null);
                shortcutInfo.id = app.getLauncherProvider().generateNewId();
                shortcutInfo.container = folderInfo.id;
                shortcutInfo.cellX = -1;
                shortcutInfo.cellY = 0;
                shortcutInfo.screen = 0;
                shortcutInfo.itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION;
                app.getModel().insertItem2DB(getApplicationContext(), shortcutInfo,
                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION);
                folderInfo.add(shortcutInfo, 0, true);
                Utilities.mDownloadedPackages.remove(info.componentName.getPackageName());
            }
        }
        app.getRecommendHelper().startUpdater(false, null, true, null, false);
    }

    private void refreshRecommend(ArrayList<ApplicationInfo> apps) {
        if (PreferencesProvider.isRecommendOn(this)) {
            HashSet<String> packageNames = new HashSet<String>();
            for (ApplicationInfo aInfo : apps) {
                packageNames.add(aInfo.getPackageName());
            }

            Iterator<Entry<Long, FolderInfo>> iter = sFolders.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<Long, FolderInfo> entry = iter.next();
                Object val = entry.getValue();
                if (val instanceof FolderInfo) {
                    FolderInfo fInfo = (FolderInfo) val;
                    for (ShortcutInfo sInfo : fInfo.recommendApps) {
                        if (packageNames.contains(sInfo.recommendPkgName)) {
                            int index = mAppRecommend.recommendIndex;
                            int count = mAppRecommend.MAX_RECOMMEND_CNT;
                            mAppRecommend.recommendIndex = index - count < 0 ? 0 : index - count;
                            mAppRecommend.refreshUI(fInfo);
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean isFolderOpening() {
        return mWorkspace.getOpenFolder() != null;
    }

    /**
     * A package was updated. Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(ArrayList<ApplicationInfo> apps) {
        // Begin, added by zhumeiquan, 20130926, if installing apps background, dragging must be cancelled
        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
        }
        // End

        // exit floating when updating apps. by luoyongxing
        if (mFloating != null && mFloating.isFloating()) {
            mFloating.endFloating(false, false);
        }
        // end

        setLoadOnResume();
        if (mWorkspace != null) {
            mWorkspace.updateShortcuts(apps);
            mWorkspace.updateFolders(apps);
        }
        checkIsRecommend(apps);
    }

    /**
     * A package was uninstalled. Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsRemoved(ArrayList<ApplicationInfo> apps, boolean permanent) {
        // Begin, added by zhumeiquan, 20130926, if installing apps background, dragging must be cancelled
        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
        }
        // End

        // exit floating when removing apps. by luoyongxing
        if (mFloating != null && mFloating.isFloating()) {
            mFloating.endFloating(false, false);
        }
        // end

        mWorkspace.removeItems(apps, permanent);
        
        // Notify the drag controller
        mDragController.onAppsRemoved(apps, this);
    }

    /**
     * A number of packages were updated.
     */
    public void bindPackagesUpdated() {

    }

    // Begin, added by zhumeiquan, 20130227
    private boolean mUnreadLoadCompleted = false;
    private boolean mBindingWorkspaceFinished = false;

    public void bindComponentUnreadChanged(final ComponentName component, final int unreadNum) {
        mHandler.post(new Runnable() {
            public void run() {
                if (mWorkspace != null) {
                    mWorkspace.updateComponentUnreadChanged(component, unreadNum);
                }
            }
        });
    }

    public void bindUnreadInfoIfNeeded() {
        if (mBindingWorkspaceFinished) {
            bindWorkspaceUnreadInfo();
        }
        mUnreadLoadCompleted = true;
    }

    private void bindWorkspaceUnreadInfo() {
        mHandler.post(new Runnable() {
            public void run() {
                if (mWorkspace != null) {
                    mWorkspace.updateShortcutsAndFoldersUnread();
                }
            }
        });
    }

    	 //yixiao@lewatek.com add for piflow begin
    public boolean isPiflowPage(){
        return isPiflowPageEnable() && mWorkspace.isPiflowPage();
    }

    public boolean isPiflowPageEnable(){
        return piflowEnabled;
    }

    public View getPiflow(){
        //View pifView = null;
        if(pifView!=null){
          return pifView;
        }
        try {
            Context c = createPackageContext("com.inveno.newpiflow", Context.CONTEXT_INCLUDE_CODE |
                    Context.CONTEXT_IGNORE_SECURITY);
            int id = c.getResources().getIdentifier("pi_flow_new", "layout", "com.inveno.newpiflow");
            LayoutInflater inflater = LayoutInflater.from(c);
            pifView = inflater.inflate(id, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pifView;
    }

    public void showHotseat(boolean anim, final boolean isShow) {
        if (anim) {
            movedHotseatWrapper.setVisibility(View.VISIBLE);
            ValueAnimator va = LauncherAnimUtils.ofFloat(0, 1f);
            final float oldA = mHotseat.getAlpha();
            va.setDuration(300);

            va.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float r = ((Float) animation.getAnimatedValue()).floatValue();
                    float newA = 0f;
                    if (isShow) {
                        newA = 1f;
                    } else {
                        newA = 0f;
                    }
                    mHotseat.setAlpha((1 - r) * oldA + r * newA);
                }
            });
            va.addListener(new AnimatorListenerAdapter() {

                public void onAnimationEnd(Animator animation) {
                    if (isShow) {
                        movedHotseatWrapper.setVisibility(View.VISIBLE);
                    } else {
                        movedHotseatWrapper.setVisibility(View.GONE);
                    }
                }

                public void onAnimationCancel(Animator animation) {

                }
            });
            va.start();
        }else{
            if (isShow) {
                movedHotseatWrapper.setVisibility(View.VISIBLE);
            } else {
                movedHotseatWrapper.setVisibility(View.GONE);
            }
        }
    }
    //yixiao@lewatek.com add for piflow end

    public void makeToast(int resId) {
        CharSequence text = getResources().getText(resId);
        makeToast(text);
    }

    public void makeToast(CharSequence text) {
        mDeleteZone.makeToastEx(text);
    }
    // End

    /**
     * Prints out out state for debugging.
     */
    public void dumpState() {
        Log.d(TAG, "BEGIN launcher2 dump state for launcher " + this);
        Log.d(TAG, "mSavedState=" + mSavedState);
        Log.d(TAG, "mWorkspaceLoading=" + mWorkspaceLoading);
        Log.d(TAG, "mRestoring=" + mRestoring);
        Log.d(TAG, "mWaitingForResult=" + mWaitingForResult);
        Log.d(TAG, "sFolders.size=" + sFolders.size());
        mModel.dumpState();

        Log.d(TAG, "END launcher2 dump state");
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println(" ");
        writer.println("Debug logs: ");
        for (int i = 0; i < sDumpLogs.size(); i++) {
            writer.println("  " + sDumpLogs.get(i));
        }
    }

    public static void dumpDebugLogsToConsole() {
        Log.d(TAG, "");
        Log.d(TAG, "*********************");
        Log.d(TAG, "Launcher debug logs: ");
        for (int i = 0; i < sDumpLogs.size(); i++) {
            Log.d(TAG, "  " + sDumpLogs.get(i));
        }
        Log.d(TAG, "*********************");
        Log.d(TAG, "");
    }

    private void setDefaultWallpaper() {
        if(EditModeLayer.INTERNAL_WALLPAPER != null){
            File[] files = EditModeLayer.INTERNAL_WALLPAPER.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    return s.toLowerCase().equalsIgnoreCase("1.jpg");
                }
            });
            if(files != null && files.length > 0){
               ChangeWallpaperTask task = new ChangeWallpaperTask(this,files[0].getPath(),0);
               MyVolley.execRunnable(task);
//               PreferencesProvider.putStringValue(this,EditModeUtils.PREFERENCE_WALLPAPER_PATH, files[0].getAbsolutePath());
                Settings.System.putString(getContentResolver(),EditModeUtils.PREFERENCE_WALLPAPER_PATH, files[0].getAbsolutePath());
            }
        }
    }
	
    
	private void deleteCharAtLaste(StringBuilder sb) {
        if (sb != null && sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
        }
    }
    //lqwang - PR65502 - add begin
    public void setHotseatAndIndicatorHwLayerEnabled(boolean enabled){
        if(movedHotseatWrapper != null){
            movedHotseatWrapper.setHwLayerEnable(enabled);
        }
        if(mDesktopIndicator != null){
            mDesktopIndicator.setHwLayerEnable(enabled);
        }
    }
private String getIntentPackageName(String intentDescription) {
        if (intentDescription == null || intentDescription.equals("")) {
            return null;
        }
        Intent intent = null;
        try {
            intent = Intent.parseUri(intentDescription, 0);
            if (intent != null) {
                return intent.getComponent().getPackageName();
            }
        } catch (Exception e) {
        }
        return null;
    }
private String getInstalledWidgets() {
        final StringBuilder sbBuilder = new StringBuilder();
        final List<AppWidgetProviderInfo> widgets = mAppWidgetManager.getInstalledProviders();
        for (AppWidgetProviderInfo widget : widgets) {
            if (widget.minWidth > 0 && widget.minHeight > 0) {
                sbBuilder.append(widget.provider.getPackageName()).append(",");
            }
        }
        if (sbBuilder.length() > 1) {
            sbBuilder.deleteCharAt(sbBuilder.length() - 1);
            return sbBuilder.toString();
        }
        return null;
    }

    public void disableHwLayer(){
        mHandler.removeCallbacks(disableHw);
        mHandler.postDelayed(disableHw,3000);
    }
    Runnable disableHw = new Runnable() {
        @Override
        public void run() {
            if(!mWorkspace.isPageMoving()){
                for (int i = 0; i < mWorkspace.getPageCount(); i++) {
                    final CellLayout cl = (CellLayout) mWorkspace.getChildAt(i);
                    cl.disableHardwareLayers();
                }
                setHotseatAndIndicatorHwLayerEnabled(false);
            }
        }
    };
    //lqwang - PR65502 - add end

}
