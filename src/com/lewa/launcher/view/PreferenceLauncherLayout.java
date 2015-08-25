package com.lewa.launcher.view;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.lewa.launcher.R;
import com.lewa.launcher.preference.PreferencesProvider;

public class PreferenceLauncherLayout extends Preference implements
        OnClickListener {
    private Context mContext;
    private TextView screen_4x4;
    private TextView screen_4x5;
    private String screen_layout;
    private String old_screen_layout;

    public PreferenceLauncherLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
        mContext = context;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        // TODO Auto-generated method stub
        final LayoutInflater layoutInflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final View layout = layoutInflater.inflate(
                R.layout.preference_screen_layout, parent, false);
        screen_4x4 = (TextView) layout.findViewById(R.id.screen_4x4);
        screen_4x5 = (TextView) layout.findViewById(R.id.screen_4x5);
        String[] layout_array = mContext.getResources().getStringArray(
                R.array.large_screen_layout);
        screen_layout = PreferencesProvider.getStringValue(mContext,
                "pref_screen_layout", PreferencesProvider.LARGE_SCREEN_LAYOUT);
        old_screen_layout = screen_layout;
        if (screen_layout.endsWith(PreferencesProvider.LARGE_SCREEN_LAYOUT)) {
            screen_4x5.setCompoundDrawablesWithIntrinsicBounds(0,
                    R.drawable.screen_4x5_on, 0, 0);
            screen_4x5.setSelected(true);
        } else {
            screen_4x4.setCompoundDrawablesWithIntrinsicBounds(0,
                    R.drawable.screen_4x4_on, 0, 0);
            screen_4x4.setSelected(true);
        }
        screen_4x4.setText(layout_array[0]);
        screen_4x5.setText(layout_array[1]);
        screen_4x4.setOnClickListener(this);
        screen_4x5.setOnClickListener(this);
        return layout;
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch (v.getId()) {
            case R.id.screen_4x4:
                toggleView((TextView) v, R.drawable.screen_4x4_on,
                        R.drawable.screen_4x4_off);
                toggleView(screen_4x5, R.drawable.screen_4x5_on,
                        R.drawable.screen_4x5_off);
                setPreference(v, PreferencesProvider.DEFAULT_SCREEN_LAYOUT, PreferencesProvider.LARGE_SCREEN_LAYOUT);
                break;

            case R.id.screen_4x5:
                toggleView((TextView) v, R.drawable.screen_4x5_on,
                        R.drawable.screen_4x5_off);
                toggleView(screen_4x4, R.drawable.screen_4x4_on,
                        R.drawable.screen_4x4_off);
                setPreference(v, PreferencesProvider.LARGE_SCREEN_LAYOUT, PreferencesProvider.DEFAULT_SCREEN_LAYOUT);
                break;
        }
        PreferencesProvider.putStringValue(mContext, "pref_screen_layout", screen_layout);
        if (screen_layout.endsWith(old_screen_layout)) {
            PreferencesProvider.getSharedPreferences(mContext).edit().putBoolean(PreferencesProvider.CHANGED, false).commit();
        } else {
            PreferencesProvider.getSharedPreferences(mContext).edit().putBoolean(PreferencesProvider.CHANGED, true).commit();
        }
    }

    private void setPreference(View v, String select_layout, String unselect_layout) {
        if (v.isSelected()) {
            screen_layout = select_layout;
        } else {
            screen_layout = unselect_layout;
        }
    }

    private void toggleView(TextView tv, int drawable_on, int drawable_off) {
        if (tv.isSelected()) {
            tv.setSelected(false);
            tv.setCompoundDrawablesWithIntrinsicBounds(0, drawable_off, 0, 0);
        } else {
            tv.setSelected(true);
            tv.setCompoundDrawablesWithIntrinsicBounds(0, drawable_on, 0, 0);
        }
    }

}
