package com.openpostmobajl;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fullscreen QR code with maximum screen brightness and the system bars hidden.
 * Tap anywhere to close.
 */
public class QrFullscreenActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Maximum brightness for this window only; restored when it closes.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1.0f;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        String qr = getIntent().getStringExtra("qr");
        if (qr == null) {
            qr = "";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Bitmap bmp = QrCode.generate(qr, 1024);
        if (bmp != null) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(bmp);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.WHITE);
            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            root.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        TextView hint = new TextView(this);
        hint.setText("Tap anywhere to close");
        hint.setTextColor(Color.rgb(0x55, 0x55, 0x55));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, dp(8));
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
