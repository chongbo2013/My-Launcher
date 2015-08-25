package lewa.os;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.view.MotionEvent;

public class FloatPanelView extends FrameLayout {
	private int mBottomOffset;
	private boolean mIsArrowUp;
	private int mPos;
	private int mTopOffset;

	public FloatPanelView(Context context) {
		super(context);
		initArrowOffset(context);
        this.setWillNotDraw(false);
	}

	public FloatPanelView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initArrowOffset(context);
        this.setWillNotDraw(false);
	}

	private void initArrowOffset(Context context) {
		mTopOffset = ((int) context.getResources().getDimension(
				com.lewa.internal.R.dimen.float_panel_arrow_top_offset));
		mBottomOffset = ((int) context.getResources().getDimension(
				com.lewa.internal.R.dimen.float_panel_arrow_bottom_offset));
	}

	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		View upArrowRight = findViewById(com.lewa.internal.R.id.arrow_up_right);
        View upArrowLeft = findViewById(com.lewa.internal.R.id.arrow_up_left);
		View downArrowRight = findViewById(com.lewa.internal.R.id.arrow_down_right);
        View downArrowLeft = findViewById(com.lewa.internal.R.id.arrow_down_left);
        LinearLayout panelUp = (LinearLayout) findViewById(com.lewa.internal.R.id.arrow_up);
		LinearLayout panelDown = (LinearLayout) findViewById(com.lewa.internal.R.id.arrow_down);
        LinearLayout panel = (LinearLayout) findViewById(com.lewa.internal.R.id.panel);
        
		if ((panelUp != null) && (panelDown != null) && (panel != null)) {
			panelDown.setVisibility(8);
			panelUp.setVisibility(8);
			int t;
			//View arrow=null;
            LinearLayout arrowPanel = null;
			if (mIsArrowUp) {
				arrowPanel = panelUp;
			} else {
			   arrowPanel = panelDown;
			}
			if (mIsArrowUp)
				t = 0;
			else
				t = panel.getMeasuredHeight() - mBottomOffset;
			arrowPanel.setVisibility(0);
			int l = mPos - arrowPanel.getMeasuredWidth() / 2;
			if (l < 0)
				l = 0;
			else if (l > right - arrowPanel.getMeasuredWidth())
				l = right - arrowPanel.getMeasuredWidth();

			arrowPanel.layout(l, t, l + arrowPanel.getMeasuredWidth(),
					t + arrowPanel.getMeasuredHeight());
			int i;
			if (mIsArrowUp)
				i = panelUp.getHeight() - mTopOffset;
			else
				i = 0;

			panel.layout(panel.getLeft(), i,
					panel.getLeft() + panel.getMeasuredWidth(),
					i + panel.getMeasuredHeight());
		} else {
			Log.e("FloatPanelView", "couldn't find view");
		}
	}

	public void setArrow(boolean isUp) {
		if (isUp != mIsArrowUp) {
			mIsArrowUp = isUp;
			requestLayout();
		}
	}

	public void setArrowPos(int pos) {
		if (pos != mPos) {
			mPos = pos;
			requestLayout();
		}
	}
    	@Override
	protected void onDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		LinearLayout panel = (LinearLayout) findViewById(com.lewa.internal.R.id.panel);
		ImageView downArrowRight = (ImageView)findViewById(com.lewa.internal.R.id.arrow_down_right);
        ImageView downArrowLeft = (ImageView)findViewById(com.lewa.internal.R.id.arrow_down_left);
        ImageView upArrowRight = (ImageView)findViewById(com.lewa.internal.R.id.arrow_up_right);
        ImageView upArrowLeft = (ImageView)findViewById(com.lewa.internal.R.id.arrow_up_left);

		Rect buttonRect = new Rect();
		Rect arrowRectRight = new Rect();
        Rect arrowRectLeft = new Rect();
		int arrawX = 0;
		int arrawY = 0;
        boolean downFlagRight = false;
        boolean downFlagLeft = false;
        boolean upFlagRight = false;
        boolean upFlagLeft = false;
		View buttonView = null;
		for (int i = 0; i < panel.getChildCount(); i++){
			buttonView = panel.getChildAt(i);
			if (buttonView != null && buttonView.isPressed()){
				buttonView.getGlobalVisibleRect(buttonRect);
				if (mIsArrowUp){
					upArrowRight.getGlobalVisibleRect(arrowRectRight);
					arrawX = arrowRectRight.left;
					arrawY = arrowRectRight.top - 15;
					if (buttonRect.contains(arrawX, arrawY)){
                        upFlagRight = true;
						upArrowRight.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_up_pressed_right);
					}
                    upArrowLeft.getGlobalVisibleRect(arrowRectLeft);
					arrawX = arrowRectLeft.left;
					arrawY = arrowRectLeft.top - 15;
                    if (buttonRect.contains(arrawX, arrawY)){
                        downFlagLeft = true;
						downArrowLeft.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_up_pressed_left);
					} 
				} else {
                    downArrowRight.getGlobalVisibleRect(arrowRectRight);
					arrawX = arrowRectRight.left;
					arrawY = arrowRectRight.top - 15;
					if (buttonRect.contains(arrawX, arrawY)){
                        downFlagRight = true;
						downArrowRight.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_down_pressed_right);
					}
                    downArrowLeft.getGlobalVisibleRect(arrowRectLeft);
					arrawX = arrowRectLeft.left;
					arrawY = arrowRectLeft.top - 15;
                    if (buttonRect.contains(arrawX, arrawY)){
                        downFlagLeft = true;
						downArrowLeft.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_down_pressed_left);
					} 
                }
                    break;
			}
		}

        if (mIsArrowUp){
            if (!downFlagRight) {
               upArrowRight.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_up_right);
            }
           if (!downFlagLeft){
               upArrowLeft.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_up_left);
            }
        } else {
           if (!downFlagRight) {
               downArrowRight.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_down_right);
            }
           if (!downFlagLeft){
               downArrowLeft.setImageResource(com.lewa.internal.R.drawable.textview_panel_arrow_down_left);
            }
        }
		super.onDraw(canvas);
        invalidate();
	}
}

