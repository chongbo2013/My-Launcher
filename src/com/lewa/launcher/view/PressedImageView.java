package com.lewa.launcher.view;

import com.lewa.toolbox.EditModeUtils;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

public class PressedImageView extends ImageView {
	private boolean mShowPressed;
	private static final String TAG = "PressedImageView";
	private Drawable mDrawable;

	public PressedImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
		init();
	}

	private void init() {
		mDrawable = getDrawable();
	}

	@Override
	protected void drawableStateChanged() {
		// TODO Auto-generated method stub
		if (isPressed()) {
			setImagePressed(true);
		} else {
			setImagePressed(false);
		}
		super.drawableStateChanged();
	}
	@Override
	protected void onAttachedToWindow() {
		// TODO Auto-generated method stub
		super.onAttachedToWindow();
		if (mDrawable != null)
			mDrawable.setCallback(this);
	}

	@Override
	protected void onDetachedFromWindow() {
		// TODO Auto-generated method stub
		super.onDetachedFromWindow();
		if (mDrawable != null)
			mDrawable.setCallback(null);
	}

	void setImagePressed(boolean pressed) {
		if (pressed) {
			if(mDrawable == null)
				mDrawable = getDrawable();
			if (!mShowPressed && mDrawable != null) {
				mDrawable.setColorFilter(0x66000000, PorterDuff.Mode.SRC_ATOP);
				invalidate();
				mShowPressed = true;
			}
		} else {
			if (mShowPressed && mDrawable != null) {
				mDrawable.clearColorFilter();
				invalidate();
				mShowPressed = false;
			}
		}
	}

}
