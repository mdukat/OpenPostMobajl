package com.openpostmobajl;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App preferences backed by the framework {@link SharedPreferences} (no
 * AndroidX). Currently only the background parcel monitor toggle.
 */
final class AppSettings {

    private static final String PREFS = "openpostmobajl_settings";
    private static final String KEY_MONITOR = "monitor_enabled";

    private AppSettings() {
    }

    /** Background parcel monitoring is off by default. */
    static boolean isMonitorEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_MONITOR, false);
    }

    static void setMonitorEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_MONITOR, enabled).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
