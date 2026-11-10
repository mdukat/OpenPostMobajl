package com.openpostmobajl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Detail view for a logical "MULTISKRYTKA" group: one QR code taken from the
 * first parcel in scan order, followed by the list of parcels the group
 * contains. Each contained parcel can be tapped to open its own detail view.
 */
public class MultiCompartmentActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String json = getIntent().getStringExtra("parcels");
        String uuid = getIntent().getStringExtra("uuid");
        if (uuid == null) {
            uuid = "";
        }

        JSONArray parcels = new JSONArray();
        try {
            parcels = new JSONArray(json == null ? "[]" : json);
        } catch (Exception ignored) {
        }

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
        title.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setPadding(dp(8), 0, 0, 0);
        title.setText(ParcelUi.multiCompartmentName(uuid));
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final JSONObject first = parcels.optJSONObject(0);

        // ---- single QR code, from the first parcel in scan order ----
        if (first != null) {
            final String qr = first.optString("qrCode", "");
            if (qr.length() > 0) {
                Bitmap bmp = QrCode.generate(qr, 600);
                if (bmp != null) {
                    TextView qrLabel = new TextView(this);
                    qrLabel.setText("QR code");
                    qrLabel.setTextColor(Color.rgb(0x11, 0x11, 0x11));
                    qrLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
                    content.addView(qrLabel, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));

                    ImageView qrView = new ImageView(this);
                    qrView.setImageBitmap(bmp);
                    qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    qrView.setBackgroundColor(Color.WHITE);
                    qrView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(
                                    MultiCompartmentActivity.this, QrFullscreenActivity.class);
                            intent.putExtra("qr", qr);
                            startActivity(intent);
                        }
                    });
                    LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                            dp(240), dp(240));
                    qrLp.gravity = Gravity.CENTER_HORIZONTAL;
                    qrLp.topMargin = dp(8);
                    qrLp.bottomMargin = dp(8);
                    content.addView(qrView, qrLp);

                    TextView qrText = new TextView(this);
                    qrText.setText(qr);
                    qrText.setTextColor(Color.rgb(0x55, 0x55, 0x55));
                    qrText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                    qrText.setTypeface(android.graphics.Typeface.MONOSPACE);
                    qrText.setTextIsSelectable(true);
                    content.addView(qrText, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }
        }

        // ---- contained parcels ----
        TextView section = new TextView(this);
        section.setText(parcels.length() == 1 ? "1 parcel" : parcels.length() + " parcels");
        section.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        section.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        section.setPadding(0, dp(8), 0, dp(8));
        content.addView(section, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < parcels.length(); i++) {
            final JSONObject p = parcels.optJSONObject(i);
            if (p == null) {
                continue;
            }
            TextView tv = new TextView(this);
            tv.setText(ParcelUi.rowText(p));
            tv.setTextColor(Color.rgb(0x11, 0x11, 0x11));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            tv.setPadding(0, dp(12), 0, dp(12));
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(
                            MultiCompartmentActivity.this, ParcelDetailActivity.class);
                    intent.putExtra("parcel", p.toString());
                    startActivity(intent);
                }
            });
            content.addView(tv);

            View divider = new View(this);
            divider.setBackgroundColor(Color.LTGRAY);
            content.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
