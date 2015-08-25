package com.lewa.launcher.view;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;

import com.lewa.launcher.Hotseat;
import com.lewa.launcher.R;
import com.lewa.launcher.pulltorefresh.PullToRefreshBase;

/**
 * Created by lqwang on 14-9-24.
 */
public class MovedHotseatWrapper extends PullToRefreshBase<Hotseat> {
    //lqwang - PR65502 - add begin
    private Paint sPaint = new Paint();
    //lqwang - PR65502 - add end

    public MovedHotseatWrapper(Context context) {
        super(context);
    }

    public MovedHotseatWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MovedHotseatWrapper(Context context, Mode mode) {
        super(context, mode);
    }

    public MovedHotseatWrapper(Context context, Mode mode, AnimationStyle animStyle) {
        super(context, mode, animStyle);
    }

    @Override
    public Orientation getPullToRefreshScrollDirection() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected Hotseat createRefreshableView(Context context, AttributeSet attrs) {
        Hotseat hotseat = new Hotseat(context, attrs);
        return hotseat;
    }

    @Override
    protected boolean isReadyForPullEnd() {
        return true;
    }

    @Override
    protected boolean isReadyForPullStart() {
        return true;
    }

    @Override
    public boolean hideFooterView() {
        return true;
    }

    @Override
    public boolean stayPosOnRefreshing() {
        return true;
    }
    //lqwang - PR65502 - add begin
    public void setHwLayerEnable(boolean enable){
        setLayerType(enable ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE , sPaint);
    }
    //lqwang - PR65502 - add end
}
