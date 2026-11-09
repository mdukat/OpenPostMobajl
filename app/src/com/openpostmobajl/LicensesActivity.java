package com.openpostmobajl;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Shows the project MIT license and the third-party license notices.
 * Both files are bundled in the APK as assets by the build scripts.
 */
public class LicensesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

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
        title.setText("Licenses");
        title.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setPadding(dp(8), 0, 0, 0);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setPadding(0, dp(8), 0, dp(8));
        text.setText(
                "PROJECT LICENSE (MIT)\n\n"
                + readAsset("LICENSE.md")
                + "\n\n\nTHIRD-PARTY LICENSES\n\n"
                + readAsset("THIRD-PARTY-LICENSES.md"));
        scroll.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private String readAsset(String name) {
        try {
            InputStream in = getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "Could not load " + name + ": " + e.getMessage();
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
