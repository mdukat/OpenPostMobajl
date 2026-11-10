package com.openpostmobajl;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * Base class for every activity. It applies the selected language (and, in
 * simplified mode, a larger font scale) to the activity's resources before they
 * load, and recreates itself if either setting changed while it was on the back
 * stack. This way the whole app switches instantly when the user changes
 * something in Settings.
 */
public abstract class BaseActivity extends Activity {

    private String attachedLanguage;
    private boolean attachedSimplified;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachedLanguage = LocaleHelper.getLanguage(this);
        attachedSimplified = AppSettings.isSimplified(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean languageChanged = attachedLanguage != null
                && !attachedLanguage.equals(LocaleHelper.getLanguage(this));
        boolean simplifiedChanged = attachedSimplified != AppSettings.isSimplified(this);
        if (languageChanged || simplifiedChanged) {
            recreate();
        }
    }
}
