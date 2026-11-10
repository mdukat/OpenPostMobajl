package com.openpostmobajl;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * App preferences backed by the framework {@link SharedPreferences} (no
 * AndroidX): the app language (see {@link LocaleHelper}) and the simplified
 * mode for larger text and simpler screens.
 */
final class AppSettings {

    private static final String PREFS = "openpostmobajl_settings";
    private static final String KEY_SIMPLIFIED = "simplified_mode";

    /** Extra font scale applied in simplified mode (relative to the system). */
    private static final float SIMPLIFIED_FONT_SCALE = 1.3f;

    private AppSettings() {
    }

    static boolean isSimplified(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SIMPLIFIED, false);
    }

    static void setSimplified(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SIMPLIFIED, enabled).apply();
    }

    /**
     * Wraps a base context with the selected language and, in simplified mode,
     * a larger {@code fontScale}. Every {@code COMPLEX_UNIT_SP} text size is
     * therefore scaled up app-wide without touching each call site.
     */
    static Context wrap(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        Locale locale = new Locale(LocaleHelper.getLanguage(base));
        Locale.setDefault(locale);
        config.setLocale(locale);
        if (isSimplified(base)) {
            config.fontScale = config.fontScale * SIMPLIFIED_FONT_SCALE;
        }
        return base.createConfigurationContext(config);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
