package com.openpostmobajl;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Settings screen: language picker and the simplified mode for older users.
 * Both changes are applied immediately (this activity recreates itself and the
 * others follow on resume).
 */
public class SettingsActivity extends BaseActivity {

    private Spinner languageSpinner;

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
        title.setText(R.string.common_settings);
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

        // ---- language picker ----
        TextView languageLabel = new TextView(this);
        languageLabel.setText(R.string.settings_language);
        languageLabel.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        languageLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        languageLabel.setPadding(0, dp(8), 0, dp(8));
        content.addView(languageLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        languageSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, LocaleHelper.LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
        int index = LocaleHelper.indexOf(LocaleHelper.getLanguage(this));
        if (index >= 0) {
            languageSpinner.setSelection(index);
        }
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String code = LocaleHelper.CODES[position];
                if (!code.equals(LocaleHelper.getLanguage(SettingsActivity.this))) {
                    LocaleHelper.setLanguage(SettingsActivity.this, code);
                    recreate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        content.addView(languageSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(this);
        description.setText(R.string.settings_language_description);
        description.setTextColor(Color.rgb(0x55, 0x55, 0x55));
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        description.setPadding(0, dp(8), 0, dp(8));
        content.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View separator = new View(this);
        separator.setBackgroundColor(Color.LTGRAY);
        content.addView(separator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // ---- simplified mode ----
        final CheckBox simplified = new CheckBox(this);
        simplified.setText(R.string.settings_simplified);
        simplified.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        simplified.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        simplified.setChecked(AppSettings.isSimplified(this));
        simplified.setPadding(0, dp(16), 0, dp(4));
        simplified.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (isChecked != AppSettings.isSimplified(SettingsActivity.this)) {
                    AppSettings.setSimplified(SettingsActivity.this, isChecked);
                    recreate();
                }
            }
        });
        content.addView(simplified, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView simplifiedDescription = new TextView(this);
        simplifiedDescription.setText(R.string.settings_simplified_description);
        simplifiedDescription.setTextColor(Color.rgb(0x55, 0x55, 0x55));
        simplifiedDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        simplifiedDescription.setPadding(0, 0, 0, dp(8));
        content.addView(simplifiedDescription, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
