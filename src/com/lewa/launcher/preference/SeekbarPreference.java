
package com.lewa.launcher.preference;

import java.math.BigDecimal;

import android.R.integer;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.lewa.launcher.R;

public class SeekbarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

    private Context mContext;
    private AlertDialog.Builder builder;
    private SeekBar mSeekBar;
    private TextView mTextView;
    private float mProgress;
    private int mType;
    public static int TYPE_SCALE = 0x01;
    public static int TYPE_DURATION = 0x02;
    private boolean hasError = false;

    public SeekbarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
        mContext = context;
        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.seekbar_dialog, null);
        initDialog(context, layout);
        mSeekBar = (SeekBar) layout.findViewById(R.id.seekBar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mTextView = (TextView) layout.findViewById(R.id.progress);
    }

    private void initDialog(Context context, View view) {
        builder = new AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        if (mType == TYPE_SCALE) {
                            PreferencesProvider.setAnimScale(mContext, mProgress);
                        } else if (mType == TYPE_DURATION) {
                            PreferencesProvider.setAnimDuration(mContext, (int) mProgress);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub

                    }

                });
        mType = -1;
    }

    public void setPreferenceType(int type) {
        if (builder != null) {
            if (type == TYPE_DURATION) {
                mType = TYPE_DURATION;
                builder.setTitle(mContext.getResources().getString(
                        R.string.pref_title_anim_duration));
            } else if (type == TYPE_SCALE) {
                mType = TYPE_SCALE;
                builder.setTitle(mContext.getResources().getString(R.string.pref_title_anim_scale));
            } else {
                hasError = true;
            }
        }
    }

    @Override
    protected void onClick() {
        // TODO Auto-generated method stub
        if (mType == -1) {
            return;
        }
        String progress = "";
        if (mType == TYPE_SCALE) {
            progress = String.valueOf(PreferencesProvider.getAnimScale(mContext));
        } else if (mType == TYPE_DURATION) {
            progress = String.valueOf(PreferencesProvider.getAnimDuration(mContext));
        }
        mTextView.setText(progress);
        builder.create().show();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // TODO Auto-generated method stub
        if (mType == TYPE_SCALE) {
            mProgress = round((float) (seekBar.getProgress() * 0.1), 2, BigDecimal.ROUND_UP);
        } else if (mType == TYPE_DURATION) {
            mProgress = progress * 20;
        }
        mTextView.setText(String.valueOf(mProgress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
    }

    public float round(float value, int scale, int roundingMode) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(scale, roundingMode);
        float f = bd.floatValue();
        bd = null;
        return f;
    }
}
