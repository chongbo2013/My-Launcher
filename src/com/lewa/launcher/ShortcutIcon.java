package com.lewa.launcher;

import lewa.content.res.IconCustomizer;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import lewa.graphics.BitmapFilterFactory;
import lewa.graphics.BitmapFilter;
import lewa.graphics.GaussianBlurBitmapFilter;

public class ShortcutIcon extends FrameLayout {
    BubbleTextView mFavorite;

    TextView mUnread;
    TextView mAppName;
    ImageView mCheck;
    ShortcutInfo mInfo;
    float mShadowRadius = IconCustomizer.getIconConfig().shadowRadius;
    int mShadowColor = IconCustomizer.getIconConfig().shadowColor;
    private final static PorterDuffXfermode sDstInMode = new PorterDuffXfermode(Mode.DST_IN);
    private static final String TAG = "ShortcutIcon";
    private boolean mHiddenMode;
    private boolean mIsDraging;
    private Bitmap blurReflect;

    public ShortcutIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFavorite = (BubbleTextView) findViewById(R.id.app_icon_title);
        mUnread = (TextView) findViewById(R.id.app_unread);
        mCheck = (ImageView) findViewById(R.id.app_check);
        mAppName = (TextView) findViewById(R.id.app_name);
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        mFavorite.setTag(tag);
        mUnread.setTag(tag);
        mInfo = (ShortcutInfo) tag;
    }

    public void applyFromShortcutInfo(ShortcutInfo info, IconCache iconCache) {
        mFavorite.applyFromShortcutInfo(info, iconCache);
        setTag(info);
        updateShortcutUnreadNum();
        setIsNewAdded(info.isNewAdded);
    }

    public void setIcon(Drawable paramDrawable) {
        mFavorite.setCompoundDrawablesWithIntrinsicBounds(null, paramDrawable, null, null);
    }

    public Drawable getFavoriteCompoundDrawable() {
        return mFavorite.getCompoundDrawables()[1];
    }

    public void updateShortcutUnreadNum() {
        if (mHiddenMode) {
            return;
        }
        if (mInfo.unreadNum <= 0) {
            mUnread.setVisibility(View.GONE);
        } else {
            mUnread.setVisibility(View.VISIBLE);
            if (MessageModel.isLewaUpdater(mInfo.intent.getComponent())) {
                mUnread.setText(R.string.new_rom_hint);
            } else {
                mUnread.setText(MessageModel.getDisplayText(mInfo.unreadNum));
            }
        }
    }

    public void setText(CharSequence text) {
        mFavorite.setText(text);
    }

    public void updateShortcutUnreadNum(int unreadNum) {
        if (unreadNum <= 0) {
            mInfo.unreadNum = 0;
            mUnread.setVisibility(View.GONE);
        } else {
            mInfo.unreadNum = unreadNum;
            if (mCheck == null || mCheck.getVisibility() != View.VISIBLE) {
                mUnread.setVisibility(View.VISIBLE);
            }
            if (MessageModel.isLewaUpdater(mInfo.intent.getComponent())) {
                mUnread.setText(R.string.new_rom_hint);
            } else {
                mUnread.setText(MessageModel.getDisplayText(unreadNum));
            }
        }
        setTag(mInfo);
    }

    //for new app tip-lqwang-add begin
    public void setIsNewAdded(boolean newAdded) {
        if (mFavorite != null) {
            mFavorite.setNewAdded(newAdded);
            mInfo.title = mFavorite.getText();
        }
    }
    //for new app tip-lqwang-add end

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        canvas.save();
        boolean isHotseat = mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
//        if (mShadowRadius > 0 && child == mFavorite && !mHiddenMode && !isHotseat) {
//            drawShadow(canvas, mShadowRadius);
//        }
        if (isHotseat && child == mFavorite && !mHiddenMode && !mIsDraging) {
//            drawReflect(canvas);
        }
        mFavorite.setShowNewIndicator(!isHotseat);
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int paddingTop = getPaddingTop();
        int t = mFavorite.getTop();
        //int r = mFavorite.getRight();
        int r = (getMeasuredWidth() + IconCustomizer.sCustomizedIconWidth) / 2;
        int l = mFavorite.getLeft();
        int b = mFavorite.getBottom();
        int favHeight = mFavorite.getMeasuredHeight();
        LayoutParams lp = (LayoutParams) mFavorite.getLayoutParams();

        mShadowTop = paddingTop + (getMeasuredHeight() - paddingTop - favHeight) / 2 +
                lp.topMargin - lp.bottomMargin;

        if (mUnread != null) {
            int uwidth = mUnread.getMeasuredWidth();
            int uheight = mUnread.getMeasuredHeight();
            mUnread.layout(r - uwidth * 2 / 3, t - uheight / 4, r + uwidth / 3, t + uheight * 3 / 4);
        }

        if (mCheck != null) {
            int cwidth = mCheck.getMeasuredWidth();
            int cheight = mCheck.getMeasuredHeight();
            mCheck.layout(r - cwidth * 2 / 3, t - cheight / 4, r + cwidth / 3, t + cheight * 3 / 4);
        }

        if (mAppName != null) {
            LayoutParams params = (LayoutParams) mAppName.getLayoutParams();
            params.width = right - left;
            int iconHeight = IconCustomizer.sCustomizedIconHeight;
            mAppName.layout(0, t + iconHeight, getWidth(), b);
        }
    }

    public void setChecked(boolean checked) {
        if (checked) {
            mCheck.setImageResource(mHiddenMode ? R.drawable.hidden_selector : R.drawable.floating_selector);
            mCheck.setVisibility(VISIBLE);
            mUnread.setVisibility(GONE);
            ObjectAnimator.ofFloat(mCheck, View.ALPHA, 0, 1).setDuration(100).start();
        } else {
            mCheck.setVisibility(GONE);
            updateShortcutUnreadNum();
        }
    }

    public void setHiddenMode(boolean isHiddenMode) {
        mHiddenMode = isHiddenMode;
    }

    boolean isHiddenSelected() {
        return mHiddenMode && mCheck.getVisibility() == VISIBLE;
    }

    void drawReflect(Canvas c) {
        Drawable d = getFavoriteCompoundDrawable();
        int width = d.getIntrinsicWidth();
        int height = d.getIntrinsicHeight();
        int reflectGap = getResources().getDimensionPixelSize(R.dimen.icon_blur_gap);
        try {
            if (blurReflect == null) {
                Bitmap original;
                if (d instanceof FastBitmapDrawable) {
                    original = ((FastBitmapDrawable) d).getBitmap();
                } else {
                    original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas ca = new Canvas(original);
                    d.draw(ca);
                }
                Matrix matrix = new Matrix();
                matrix.preScale(1, -1);
                Bitmap reflectImg = Bitmap.createBitmap(original, 0, height / 4 * 1, width, height / 4 * 3, matrix, false);
                Bitmap blurImg = Bitmap.createBitmap((int) (reflectImg.getWidth() * 1.4f), (int) (reflectImg.getHeight() * 1.4f), Bitmap.Config.ARGB_8888);
                Canvas blurCanvas = new Canvas(blurImg);
                matrix.reset();
                matrix.postTranslate((int) (reflectImg.getWidth() * 0.2f), (int) (reflectImg.getHeight() * 0.1f));
                Paint paint = new Paint();
                blurCanvas.drawBitmap(reflectImg, matrix, paint);
                GaussianBlurBitmapFilter filter = (GaussianBlurBitmapFilter) BitmapFilterFactory.createFilter(BitmapFilterFactory.GAUSSIAN_BLUR);
                filter.setFilterStrength(BitmapFilter.FILTER_STRENGTH_HIGH);
                blurReflect = filter.filterBitmap(blurImg, 8);
                reflectImg.recycle();
                blurImg.recycle();
            }
            if(blurReflect != null) {
                Paint paint = new Paint();
                Bitmap blur = Bitmap.createBitmap(blurReflect);
                Canvas canvas = new Canvas(blur);
                paint.setXfermode(sDstInMode);
                LinearGradient shader = new LinearGradient(0, 0, 0, blur.getHeight(), 0x95ffffff, 0x00ffffff, TileMode.CLAMP);
                paint.setShader(shader);
                canvas.drawRect(0, 0, blur.getWidth(), blur.getHeight(), paint);
                c.drawBitmap(blur, (getWidth() - blur.getWidth()) / 2, height + reflectGap, null);
            }
        } catch (OutOfMemoryError e) {

        }
    }

    private Paint mBlurPaint;
    private Paint mShadowPaint;
    private Canvas mCanvas;
    private Bitmap mShadow;
    private int mShadowTop;

    private void drawShadow(Canvas c, float shadow) {
        int dropShadow = Math.round(shadow);
        int height = IconCustomizer.sCustomizedIconHeight;
        int width = IconCustomizer.sCustomizedIconWidth;
        if (mShadow == null) {
            try {
                if (mBlurPaint == null) {
                    mBlurPaint = new Paint();
                    mBlurPaint.setMaskFilter(new BlurMaskFilter(shadow * 2, BlurMaskFilter.Blur.INNER));
                }
                if (mCanvas == null) {
                    mCanvas = new Canvas();
                    mCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG, Paint.FILTER_BITMAP_FLAG));
                }
                Drawable d = getFavoriteCompoundDrawable();
                Bitmap alpha;
                if (d instanceof FastBitmapDrawable) {
                    alpha = ((FastBitmapDrawable) d).getBitmap();
                    alpha = alpha.extractAlpha(mBlurPaint, null);
                    width = alpha.getWidth();
                    height = alpha.getHeight();
                } else {
                    alpha = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    mCanvas.setBitmap(alpha);
                    d.draw(mCanvas);
                    alpha = alpha.extractAlpha(mBlurPaint, null);
                }
                if (mShadowPaint == null) {
                    mShadowPaint = new Paint();
                    mShadowPaint.setColor(Color.TRANSPARENT);
                    mShadowPaint.setShadowLayer(shadow, 0, 0, mShadowColor);
                }

                int doubleShadow = Math.round(shadow * 2);
                mShadow = Bitmap.createBitmap(width + doubleShadow, height + doubleShadow, alpha.getConfig());
                mCanvas.setBitmap(mShadow);
                mCanvas.drawBitmap(alpha, dropShadow, dropShadow, mShadowPaint);
                alpha.recycle();
            } catch (Exception e) {
            } catch (OutOfMemoryError e) {
            }
        }
        if (mShadow != null) {
            c.drawBitmap(mShadow, (getWidth() - width) / 2 - dropShadow, mShadowTop + dropShadow / 2, null);
        }
    }

    public void setIsDraging(boolean isDraging) {
        this.mIsDraging = isDraging;
    }

    public void clearReflectCache() {
        if (blurReflect != null && !blurReflect.isRecycled()) {
            blurReflect.recycle();
        }
        blurReflect = null;
    }
}

