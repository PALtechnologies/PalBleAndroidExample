package com.palt.examplepalble;

import android.app.Application;
import android.content.Context;

import com.paltechnologies.pal8.PalBle;

public class ExampleApplication extends Application {
    private PalBle palBle;

    public static PalBle getPalBle(Context context) {
        ExampleApplication application = (ExampleApplication) context.getApplicationContext();
        return application.palBle;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        palBle = new PalBle(this);
    }
}
