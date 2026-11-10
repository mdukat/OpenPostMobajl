package com.openpostmobajl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows only delivered parcels from the encrypted cache.
 */
public class DeliveredParcelsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean simplified = AppSettings.isSimplified(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new Button(this);
        back.setText(R.string.common_back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.delivered_title);
        title.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setPadding(dp(8), 0, 0, 0);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(this);
        status.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ApiClient.ParcelResult res = ParcelCache.load(this);
        List<JSONObject> items = res == null
                ? new ArrayList<JSONObject>()
                : ParcelUi.sorted(res.parcels, true);

        status.setText(getString(R.string.delivered_count, items.size()));
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.delivered_empty);
            empty.setTextColor(Color.rgb(0x11, 0x11, 0x11));
            empty.setPadding(0, dp(8), 0, dp(8));
            list.addView(empty);
        } else {
            for (int i = 0; i < items.size(); i++) {
                final JSONObject p = items.get(i);
                TextView tv = new TextView(this);
                if (simplified) {
                    String sender = ParcelUi.senderName(p);
                    if (sender == null || sender.length() == 0) {
                        sender = ParcelUi.last6(p);
                    }
                    tv.setText(getString(R.string.main_parcel_simplified, sender));
                } else {
                    tv.setText(ParcelUi.rowText(p));
                }
                tv.setTextColor(Color.rgb(0x11, 0x11, 0x11));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                if (simplified) {
                    Ui.applyBorderedRow(this, tv);
                } else {
                    tv.setPadding(0, dp(12), 0, dp(12));
                }
                tv.setClickable(true);
                tv.setFocusable(true);
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(
                                DeliveredParcelsActivity.this, ParcelDetailActivity.class);
                        intent.putExtra("parcel", p.toString());
                        startActivity(intent);
                    }
                });
                list.addView(tv);

                if (!simplified) {
                    View divider = new View(this);
                    divider.setBackgroundColor(Color.LTGRAY);
                    list.addView(divider, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1));
                }
            }
        }

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
