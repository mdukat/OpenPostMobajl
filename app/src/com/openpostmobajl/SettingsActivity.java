package com.openpostmobajl;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Settings screen. Currently a single option: enable/disable the background
 * parcel monitor (foreground service). Disabled by default.
 */
public class SettingsActivity extends Activity {

    private static final int REQ_POST_NOTIFICATIONS = 1002;

    private Switch monitorSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        // ---- top bar ----
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new Button(this);
        back.setText("\u2190 Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setPadding(dp(8), 0, 0, 0);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- background monitoring toggle ----
        monitorSwitch = new Switch(this);
        monitorSwitch.setText("Background parcel monitoring");
        monitorSwitch.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        monitorSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        monitorSwitch.setPadding(0, dp(8), 0, dp(8));
        monitorSwitch.setChecked(AppSettings.isMonitorEnabled(this));
        monitorSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMonitorToggled(monitorSwitch.isChecked());
            }
        });
        content.addView(monitorSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView caveats = new TextView(this);
        caveats.setTextColor(Color.rgb(0x55, 0x55, 0x55));
        caveats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        caveats.setLineSpacing(dp(2), 1f);
        caveats.setText(
                "When enabled, a foreground service checks the parcel list every "
                + "5 minutes and notifies you when a parcel's status changes.\n\n"
                + "Caveats\n"
                + "\u2022 A permanent \"Monitoring parcels\" notification is shown "
                + "the whole time it is enabled.\n"
                + "\u2022 Checks are best effort: Android may delay them while the "
                + "device is dozing or under memory pressure.\n"
                + "\u2022 Status-change notifications require notification "
                + "permission on Android 13 and newer.\n"
                + "\u2022 Off by default; it keeps running until you turn it off or "
                + "sign out.");
        content.addView(caveats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void onMonitorToggled(boolean enabled) {
        AppSettings.setMonitorEnabled(this, enabled);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS);
            }
            ParcelMonitorService.start(this);
        } else {
            ParcelMonitorService.stop(this);
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
