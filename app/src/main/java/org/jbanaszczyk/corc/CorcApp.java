package org.jbanaszczyk.corc;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import org.jbanaszczyk.corc.db.CorcDatabase;

public class CorcApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        CorcDatabase.getInstance(this);
    }
}
