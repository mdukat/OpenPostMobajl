package com.openpostmobajl;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Per-app language selection.
 *
 * Each supported code maps to an Android resource folder {@code values-<code>}
 * (for example {@code values-pl}). To add a language, add its code and label
 * to {@link #CODES}/{@link #LABELS} and add the matching
 * {@code app/res/values-<code>/strings.xml} file. See docs/LANGUAGES.md.
 *
 * The default language is Polish.
 */
final class LocaleHelper {

    private static final String PREFS = "openpostmobajl_settings";
    private static final String KEY_LANGUAGE = "language";

    static final String DEFAULT_LANGUAGE = "pl";

    /** Language codes, in the same order as {@link #LABELS}. */
    static final String[] CODES = {"en", "pl"};
    /** Picker labels, shown exactly as written here (in their own language). */
    static final String[] LABELS = {"English", "Polski"};

    private LocaleHelper() {
    }

    /** Selected language code, falling back to {@link #DEFAULT_LANGUAGE}. */
    static String getLanguage(Context ctx) {
        String code = prefs(ctx).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
        return indexOf(code) >= 0 ? code : DEFAULT_LANGUAGE;
    }

    static void setLanguage(Context ctx, String code) {
        prefs(ctx).edit().putString(KEY_LANGUAGE, code).apply();
    }

    /** Index of a code in {@link #CODES}, or -1. */
    static int indexOf(String code) {
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(code)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Wraps a base context so its resources use the selected language. Used from
     * {@code Activity.attachBaseContext} so {@code getString(R.string...)} and
     * every activity pick up the choice.
     */
    static Context wrap(Context base) {
        Locale locale = new Locale(getLanguage(base));
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
