package com.lewa.launcher.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.lewa.launcher.pulltorefresh.PullToRefreshBase;

/**
 * Created by lqwang on 14-10-23.
 */
public class MovedFrameLayoutWrapper extends PullToRefreshBase<FrameLayout> {
    private MovedStatusImpl movedStatusImpl;
    public MovedFrameLayoutWrapper(Context context) {
        super(context);
    }

    public MovedFrameLayoutWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MovedFrameLayoutWrapper(Context context, Mode mode) {
        super(context, mode);
    }

    public MovedFrameLayoutWrapper(Context context, Mode mode, AnimationStyle animStyle) {
        super(context, mode, animStyle);
    }

    @Override
    public Orientation getPullToRefreshScrollDirection() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected FrameLayout createRefreshableView(Context context, AttributeSet attrs) {
        FrameLayout layout = new FrameLayout(context,attrs);
        return layout;
    }

    @Override
    protected boolean isReadyForPullEnd() {
        if(movedStatusImpl != null){
            return movedStatusImpl.isReadyForPullEndImpl();
        }
        return false;
    }

    @Override
    protected boolean isReadyForPullStart() {
        if(movedStatusImpl != null){
            return movedStatusImpl.isReadyForPullStartImpl();
        }
        return false;
    }

    @Override
    public boolean hideHeaderView() {
        if(movedStatusImpl != null){
            return movedStatusImpl.hideHeaderViewImpl();
        }
        return true;
    }

    public void setMovedStatusImpl(MovedStatusImpl movedStatusImpl) {
        this.movedStatusImpl = movedStatusImpl;
    }

    @Override
    public View getScrollView() {
        if(movedStatusImpl != null){
            return movedStatusImpl.getScrollViewImpl();
        }
        return super.getScrollView();
    }

    @Override
    public boolean stayPosOnRefreshing() {
        return true;
    }

    public interface MovedStatusImpl{
        boolean isReadyForPullStartImpl();
        boolean isReadyForPullEndImpl();
        boolean hideHeaderViewImpl();
        View getScrollViewImpl();
    }
}
