package com.lewa.launcher.preference;


import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.MenuItem;
import lewa.support.v7.app.ActionBar;
import lewa.support.v7.app.ActionBarActivity;

public class MyLauncherSettings extends ActionBarActivity{
    LauncherPreferenceFragment launcherPreferenceFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //yixiao add #67932 2015.1.16 begin
        ActionBar actionBar=getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayUseLogoEnabled(false);   
        }

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction =
                fragmentManager.beginTransaction();
        launcherPreferenceFragment = new LauncherPreferenceFragment();
        fragmentTransaction.replace(android.R.id.content, launcherPreferenceFragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO Auto-generated method stub
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
//                overridePendingTransition(lewa.R.anim.android_activity_close_enter, lewa.R.anim.android_activity_close_exit);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        getWindow().setBackgroundDrawable(null);
    }

}
