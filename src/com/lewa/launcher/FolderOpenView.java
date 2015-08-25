package com.lewa.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class FolderOpenView extends RelativeLayout implements OnEditorActionListener {

    private ImageView screenImage;
    private ImageView wallpaperBgImage;
    private ImageView mBlurredWallpaperImage;
    private LinearLayout folderNameLayout;
    private FolderEditText folderName;
    private Folder mFolder;
    private int folderNameHeight;

    public FolderOpenView(Context context, LayoutParams screenImageParams) {
        super(context);
        screenImage = new ImageView(context);
        screenImage.setScaleType(ScaleType.MATRIX);
        addView(screenImage, screenImageParams);
    }

    public FolderOpenView(Context context, boolean isFolderBg) {
        super(context);
        // if(!isFloating){
        mBlurredWallpaperImage = new ImageView(context);
        mBlurredWallpaperImage.setScaleType(ScaleType.FIT_XY);
        RelativeLayout.LayoutParams screenImageParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        screenImageParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        screenImageParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        addView(mBlurredWallpaperImage, screenImageParams);
        // }

        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.user_folder_name, null);
        folderNameLayout = (LinearLayout) layout.findViewById(R.id.folder_name_layout);
        folderName = (FolderEditText) layout.findViewById(R.id.folder_name);

        folderName.getBackground().setAlpha(0);
        int measureSpec = MeasureSpec.UNSPECIFIED;
        folderName.measure(measureSpec, measureSpec);
        folderNameHeight = folderName.getMeasuredHeight();
        folderName.setOnEditorActionListener(this);
        folderName.setSelectAllOnFocus(true);
        folderName.selectAll();
        folderName.setInputType(folderName.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        folderName.setClickable(true);
        folderName.setFocusableInTouchMode(true);
        folderName.setFocusable(true);
        folderName.setImeOptions(EditorInfo.IME_ACTION_DONE);
        folderName.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                if (mFolder != null) {
                    mFolder.startEditingName();
                }
                return false;
            }
        });
        addView(folderNameLayout);
    }

    public FolderOpenView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FolderOpenView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setWallpaperBgImage(Bitmap wallpaper) {
        mBlurredWallpaperImage.setImageBitmap(wallpaper);
    }

    public FolderEditText getFolderName() {
        return folderName;
    }

    public void setFolderNameInfo(Folder folder, boolean animator, int folderTop, int folderLeft) {
        mFolder = folder;
        folderName.setFolder(folder);
        folderName.setText(Utilities.convertStr(getContext(), folder.getInfo().title.toString()));
        if (animator) {
            folderName.setAlpha(0f);
        }

        RelativeLayout.LayoutParams folderNameParams = (RelativeLayout.LayoutParams) folderNameLayout
                .getLayoutParams();
        folderNameParams.topMargin = folderTop - folderNameHeight;
        folderNameParams.leftMargin = folderLeft;
        folderNameParams.width = folder.getFolderWidth();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        // TODO Auto-generated method stub
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            if (mFolder != null) {
                mFolder.dismissEditingName();
                return false;
            }
        }
        return false;
    }

    public void setBitmap(Bitmap fronBitmap, Bitmap folderIcon) {
        screenImage.setImageBitmap(fronBitmap);
    }

    public void setScreenImagePadding(int paddingX) {
        screenImage.setPadding(paddingX, screenImage.getPaddingTop(),
                paddingX, screenImage.getPaddingBottom());
    }

    public ImageView getScreenImage() {
        return screenImage;
    }

    /*
    public void setFolderScrollCallback(FolderScrollCallbacks callback){
        mfolderScroller = callback;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        if (mfolderScroller == null
                || mFolder.getFolderLayout().getChildCount() < 1) {
            return true;
        }
        YLog.d("FolderOpenView---envent="+event.getAction());
//        if (mFolder != null) {
//            Rect rect = new Rect();
//            mLauncher.getDragLayer().getDescendantRectRelativeToSelf(folderName, rect);
//            boolean contain = rect.contains((int) event.getX(), (int) event.getY());
//            YLog.d("top=" + rect.top + ",bottom=" + rect.bottom + ",contain=" + contain + ",folder=" + mFolder);
//            if (contain) {
//                mFolder.startEditingName();
//            }
//        }
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mfolderScroller.onActionDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                mfolderScroller.onActionMove(event);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mfolderScroller.onActionUp(event);
                break;
        }
        return true;
    }

    public interface FolderScrollCallbacks{
        public void onActionDown(MotionEvent event);
        public void onActionMove(MotionEvent event);
        public void onActionUp(MotionEvent event);
    }*/
}
