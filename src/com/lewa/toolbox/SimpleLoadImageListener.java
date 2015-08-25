package com.lewa.toolbox;

import java.lang.ref.WeakReference;

import com.lewa.launcher.view.RoundedDrawable;

import android.graphics.Bitmap;
import android.widget.ImageView;
import com.lewa.launcher.R;
public class SimpleLoadImageListener implements IIoadImageListener {
	private WeakReference<ImageView> ref;
    private String mTag;
	public SimpleLoadImageListener(WeakReference<ImageView> ref,String tag) {
		this.ref = ref;
        this.mTag = tag;
	}
	@Override
	public void onLoadStart() {
		// TODO Auto-generated method stub
	}


	@Override
	public void onLoadFail() {
		// TODO Auto-generated method stub

	}


	@Override
	public void onLoadComplete(Bitmap b) {
		// TODO Auto-generated method stub
		ImageView v = ref.get();
		if(v!=null&&b!=null && v.getTag().equals(mTag)){
			v.setImageBitmap(b);
            v.setTag("");
        }
	}

}
