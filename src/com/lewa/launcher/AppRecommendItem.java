
package com.lewa.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class AppRecommendItem extends LinearLayout {

    public AppRecommendItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void drawableStateChanged() {
        // TODO Auto-generated method stub
        if (isPressed()) {
            setAlpha(0.6f);
        } else {
            setAlpha(1f);
        }
    }
}
