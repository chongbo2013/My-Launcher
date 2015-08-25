package com.lewa.launcher;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class UninstallComfirmDialog {
    private Context mContext;
    private Handler mHandler;
    private CharSequence mLabel;
    private static final String TAG = "UninstallComfirmDialog";
    private PackageManager mPackageManager;
    private View mContentView;
    private AlertDialog mDialog;
    private String mPackageName;
    private ComponentName mComponentName;
    
    public static void show(Context context, ComponentName cn) {
        UninstallComfirmDialog dialog = new UninstallComfirmDialog(context, cn);
        dialog.mDialog.show();
    }
    
    public UninstallComfirmDialog(Context context, ComponentName cn) {
        mContext = context;
        mComponentName = cn;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        mContentView = LayoutInflater.from(context).inflate(R.layout.uninstall_confirm, null);
        boolean success = setupContentView(builder, mContentView);
       
        if (success) {
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    if (mHandler == null) {
                        mHandler = new Handler();
                    }
                    final Runnable r = new Runnable(){
                        @Override
                        public void run() {
                            Toast.makeText(mContext, mContext.getString(R.string.uninstall_failed_msg, mLabel), Toast.LENGTH_LONG).show();
                        }
                    };
                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            //yixiao modify #70189 2015.2.13 begin
                            Looper.prepare();
                            if (Utilities.uninstallSilent(mContext, mPackageName)) {
                                Toast.makeText(mContext, mContext.getString(R.string.uninstall_done, mLabel), Toast.LENGTH_LONG).show();
                            } else {
                                mHandler.post(r);
                            }
                            Looper.loop();
                          //yixiao modify #70189 2015.2.13 end
                        }
                    };
                    t.start();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);
        } else {
            
            builder.setNegativeButton(android.R.string.cancel, null);
        }
        builder.setView(mContentView);
        mDialog = builder.create();
    }
    
    private boolean setupContentView(AlertDialog.Builder builder, View contentView) {
        TextView confirm = (TextView) contentView.findViewById(R.id.uninstall_confirm_text);
        TextView activityText = (TextView) contentView.findViewById(R.id.app_name);
        ImageView iconView = (ImageView) contentView.findViewById(R.id.app_icon);
        RelativeLayout app_snippet = (RelativeLayout)contentView.findViewById(R.id.app_snippet);
        builder.setTitle(R.string.uninstall_application_title);
        if (mComponentName == null) {
            Log.e(TAG, "Invalid package name");
            confirm.setText(R.string.app_not_found_dlg_text);
            app_snippet.setVisibility(View.GONE);
            return false;
        }
        
        String packageName = mComponentName.getPackageName();
        mPackageName = packageName;
        mPackageManager = mContext.getPackageManager();
        boolean errFlag = false;
        android.content.pm.ApplicationInfo appInfo = null;
        try {
            appInfo = mPackageManager.getApplicationInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException e) {
            errFlag = true;
        }

        // The class name may have been specified (e.g. when deleting an app from all apps)
        String className = mComponentName.getClassName();
        ActivityInfo activityInfo = null;
        if (className != null) {
            try {
                activityInfo = mPackageManager.getActivityInfo(mComponentName, 0);
            } catch (NameNotFoundException e) {
                errFlag = true;
            }
        }

        if (appInfo == null || errFlag) {
            Log.e(TAG, "Invalid packageName or componentName in " + mComponentName);
            confirm.setText(R.string.app_not_found_dlg_text);
            app_snippet.setVisibility(View.GONE);
            return false;
        } else {
            boolean isUpdate = ((appInfo.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
            if (isUpdate) {
                builder.setTitle(R.string.uninstall_update_title);
                confirm.setText(R.string.uninstall_update_text);
            } else {
                builder.setTitle(R.string.uninstall_application_title);
                confirm.setText(R.string.uninstall_application_text);
            }
            
            Drawable icon = appInfo.loadIcon(mPackageManager);
            iconView.setImageDrawable(icon);
            
            CharSequence applabel = appInfo.loadLabel(mPackageManager);
            // If an activity was specified (e.g. when dragging from All Apps to trash can),
            // give a bit more info if the activity label isn't the same as the package label.
            if (activityInfo != null) {
                CharSequence activityLabel = activityInfo.loadLabel(mPackageManager);
                if (!activityLabel.equals(appInfo.loadLabel(mPackageManager))) {
                    
                    CharSequence text = mContext.getString(R.string.uninstall_activity_text,
                            activityLabel);
                    //activityText.setText(text);
                    confirm.setText(text);
                }
                mLabel = activityLabel;
            } else {
                mLabel = applabel;
            }
            activityText.setText(mLabel);
        }
        return true;
    }
}
