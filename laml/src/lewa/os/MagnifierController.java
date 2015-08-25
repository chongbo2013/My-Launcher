// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) fieldsfirst 
// Source File Name:   MagnifierController.java

package lewa.os;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.*;

// Referenced classes of package android.widget:

public class MagnifierController
{

    public static final String LOG_TAG = "MagnifierController";
    private final Editor mEditor;
    private int mLongClickX;
    private int mLongClickY;
    private int mOffset;
    private boolean mShowing;
    private final TextView mTextView;

    public MagnifierController(Context context, Editor editor)
    {
        mOffset = -1;
        mEditor = editor;
        mTextView = editor.textview();
        mShowing = false;
    }

    private void hide()
    {
        mShowing = false;
        if(mTextView.getParent() != null)
            mTextView.getParent().requestDisallowInterceptTouchEvent(false);
        mEditor.makeBlinkWrap();
    }


    private void showMagnifier()
    {
        mEditor.stopBlink();
        mTextView.getContext().sendBroadcast(new Intent("android.intent.action.SHOW_MAGNIFIER"));
        Log.i("wangliqiang", "SEND BROADCAST android.intent.action.SHOW_MAGNIFIER");
    }

    private void updatePosition(boolean isFirst)
    {
        int offset = mTextView.getOffsetForPosition(mLongClickX, mLongClickY);
        if(offset != mOffset)
        {
            mEditor.setTextSelectionWrap(offset);
            mOffset = offset;
            if(!isFirst)
                showMagnifier();
        }
    }

    public boolean isShowing()
    {
        return mShowing;
    }

    public void onParentChanged()
    {
        if(mShowing)
            showMagnifier();
    }

    public boolean onTouchEvent(MotionEvent event)
    {
        boolean _tmp = false;
        mLongClickX = (int)event.getX();
        mLongClickY = (int)event.getY();
        boolean flag = isShowing();
        boolean flag1 = false;
        if(flag)
        {
            int i = event.getActionMasked();
            flag1 = false;
            switch(i)
            {
            case MotionEvent.ACTION_UP: // '\001'
            case MotionEvent.ACTION_CANCEL: // '\003'
                hide();
                flag1 = false;
                break;

            case MotionEvent.ACTION_MOVE: // '\002'
                updatePosition(false);
                flag1 = true;
                break;
            }
            mEditor.getInsertionController().onHandleTouchEvent(null, event);
        }
        return flag1;
    }

    public void show()
    {
        mShowing = true;
        if(mTextView.getParent() != null)
            mTextView.getParent().requestDisallowInterceptTouchEvent(true);
        updatePosition(true);
        showMagnifier();
    }
}
