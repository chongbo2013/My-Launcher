package com.lewa.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;

import lewa.graphics.drawable.BitmapBlurDrawable;
import lewa.graphics.drawable.BlurOptions;

/**
 * Created by ivonhoe on 14-9-24.
 */
public class BlurWallpaperManager {

    private static final boolean isDebug = false;

    private Context mContext;
    private static BlurWallpaperManager instance ;

    private WallpaperManager mWallpaperManager;
    private BitmapBlurDrawable blurredWallpaper;
    private Rect blurredOutRect;
    private boolean isBlur = false;
    private int wallpaperWidth;
    private int wallpaperHeight;
    private float alphaFactor = 1f;

    private View attachedView;

    public static BlurWallpaperManager getInstance(Context context) {
        if (instance == null) {
            instance = new BlurWallpaperManager(context);
        }

        return instance;
    }

    private BlurWallpaperManager(Context context) {
        mContext = context;
        mWallpaperManager = WallpaperManager.getInstance(context);
        getBlurredWallpaper();
    }

    private void getBlurredWallpaper() {
        if (mWallpaperManager.getWallpaperInfo() == null) {
            BitmapDrawable d = (BitmapDrawable) mWallpaperManager.getDrawable();
            if (d != null) {
                Bitmap wallpaper = ((BitmapDrawable) d).getBitmap();
                wallpaperWidth = wallpaper.getWidth();
                wallpaperHeight = wallpaper.getHeight();
                if (wallpaper != null) {
                    BlurOptions options = new BlurOptions();
                    options.strength = BlurOptions.STRENGTH_LOW;
                    blurredWallpaper = new BitmapBlurDrawable(mContext, wallpaper, options);
                    blurredWallpaper.setBounds(0, 0, blurredWallpaper.getIntrinsicWidth(),
                            blurredWallpaper.getIntrinsicHeight());
                    blurredOutRect = new Rect();
                }
            }
            mWallpaperManager.forgetLoadedWallpaper();
        } else {
            blurredWallpaper = null;
        }
    }

    public void wallpaperChanged() {
        getBlurredWallpaper();
    }

    private boolean isLiveWallpaper() {
        if (mWallpaperManager.getWallpaperInfo() != null) {
            return true;
        }
        return false;
    }

    public boolean isBlur() {
        return isBlur && blurredWallpaper != null;
    }

    public void setBlur(boolean blur) {
        if (isLiveWallpaper()) {
            blurredWallpaper = null;
        }
        isBlur = blur;
        alphaFactor = blur ? 1f : 0f;
        if (attachedView != null && blurredWallpaper != null) {
            attachedView.invalidate();
        }
    }

    public BitmapBlurDrawable getBlurredWallpaperDrawable() {
        return blurredWallpaper;
    }

    public void setBlurAlpha(float alphaFactor, boolean invalidate) {
        this.alphaFactor = alphaFactor;
        if (attachedView != null && blurredWallpaper != null && invalidate) {
            attachedView.invalidate();
        }
    }

    public void setAttachedView(View attachedView) {
        this.attachedView = attachedView;
    }

    public void draw(Canvas canvas, float scrollX, float offsetX, int screenWidth, int screenHeight) {
        if (blurredOutRect == null || blurredWallpaper == null) {
            return;
        }
        /*
        blurredOutRect.left = (int) (scrollX - ((wallpaperWidth - screenWidth) * offsetX));
        blurredOutRect.top = 0;
        blurredOutRect.right = wallpaperWidth + blurredOutRect.left;
        blurredOutRect.bottom = screenHeight;
        */

        blurredOutRect.left = (int) (scrollX - ((Math.max(wallpaperWidth, screenWidth) - screenWidth) * offsetX));
        blurredOutRect.top = 0;
        blurredOutRect.right = Math.max(wallpaperWidth, screenWidth) + blurredOutRect.left;
        blurredOutRect.bottom = Math.max(wallpaperHeight, screenHeight);

        if (isDebug) {
//            debugDrawable.setAlpha((int) (255 * alphaFactor));
//            debugDrawable.setBounds(blurredOutRect);
//            debugDrawable.draw(canvas);
        } else {
            blurredWallpaper.setAlpha((int) (255 * alphaFactor));
            blurredWallpaper.setBounds(blurredOutRect);
            blurredWallpaper.draw(canvas);
        }

    }

    class BlurredWallpaperDrawable extends TransitionDrawable {

        /**
         * Create a new transition drawable with the specified list of layers. At least
         * 2 layers are required for this drawable to work properly.
         *
         * @param layers
         */
        public BlurredWallpaperDrawable(Drawable[] layers) {
            super(layers);
        }
    }

}
