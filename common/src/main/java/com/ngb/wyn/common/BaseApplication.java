package com.ngb.wyn.common;

import android.app.Application;
import android.content.Context;

public class BaseApplication extends Application {
    private static Application sApplication;
    private static BaseApplication sContext;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sContext = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;
        doOnCreate(this);
    }

    public void doOnCreate(Application app) {

    }

    public static Application getInstance() {
        return sApplication;
    }

    public static Context getContext() {
        return sContext;
    }

}
