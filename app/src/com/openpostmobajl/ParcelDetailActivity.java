package com.openpostmobajl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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

import org.json.JSONObject;

/**
 * Full parcel view. Shows a locally generated QR code at the top for delivered
 * and ready-to-pickup parcels, a short summary, and the complete backend JSON.
 */
public class ParcelDetailActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String json = getIntent().getStringExtra("parcel");
        JSONObject parcel = null;
        try {
            parcel = new JSONObject(json == null ? "{}" : json);
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
        title.setText("Parcel " + ParcelUi.last6(parcel));
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (parcel != null) {
            // ---- QR code for delivered / ready-to-pickup parcels ----
            String group = parcel.optString("statusGroup", "");
            String status = parcel.optString("status", "");
            String qr = parcel.optString("qrCode", "");
            boolean readyToPickup = "TO_PICKUP".equalsIgnoreCase(group)
                    || "READY_TO_PICKUP".equalsIgnoreCase(status);
            if (("DELIVERED".equalsIgnoreCase(group) || readyToPickup) && qr.length() > 0) {
                Bitmap bmp = QrCode.generate(qr, 600);
                if (bmp != null) {
                    TextView qrLabel = new TextView(this);
                    qrLabel.setText("QR code");
                    qrLabel.setTextColor(Color.rgb(0x11, 0x11, 0x11));
                    qrLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
                    content.addView(qrLabel, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    ImageView qrView = new ImageView(this);
                    qrView.setImageBitmap(bmp);
                    qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    qrView.setBackgroundColor(Color.WHITE);
                    qrView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(
                                    ParcelDetailActivity.this, QrFullscreenActivity.class);
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
                    qrText.setTypeface(Typeface.MONOSPACE);
                    qrText.setTextIsSelectable(true);
                    content.addView(qrText, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }

            // ---- summary ----
            TextView summary = new TextView(this);
            summary.setTextColor(Color.rgb(0x11, 0x11, 0x11));
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            summary.setText(
                    "Shipment: " + parcel.optString("shipmentNumber",
                            parcel.optString("primaryParcelNumber", "")) + "\n"
                    + "Status: " + parcel.optString("status", "")
                    + " (" + parcel.optString("statusGroup", "") + ")\n"
                    + "Pickup point: " + ParcelUi.pointName(parcel) + "\n"
                    + "Sender: " + ParcelUi.senderName(parcel));
            summary.setPadding(0, dp(8), 0, dp(8));
            content.addView(summary, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View divider = new View(this);
            divider.setBackgroundColor(Color.LTGRAY);
            content.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));

            // ---- full JSON ----
            TextView dump = new TextView(this);
            dump.setTextColor(Color.rgb(0x11, 0x11, 0x11));
            dump.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            dump.setTypeface(Typeface.MONOSPACE);
            dump.setTextIsSelectable(true);
            dump.setPadding(0, dp(8), 0, dp(8));
            try {
                dump.setText(parcel.toString(2));
            } catch (Exception e) {
                dump.setText(String.valueOf(parcel));
            }
            content.addView(dump, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            TextView error = new TextView(this);
            error.setText("Could not read parcel data.");
            error.setTextColor(Color.rgb(0x11, 0x11, 0x11));
            content.addView(error);
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
