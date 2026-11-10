package com.openpostmobajl;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * Base class for every activity. It applies the selected language to the
 * activity's resources before they load, and recreates itself if the language
 * changed while it was on the back stack (so the whole app switches instantly
 * when the user picks a language in Settings).
 */
public abstract class BaseActivity extends Activity {

    private String attachedLanguage;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attachedLanguage = LocaleHelper.getLanguage(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (attachedLanguage != null
                && !attachedLanguage.equals(LocaleHelper.getLanguage(this))) {
            recreate();
        }
    }
}
