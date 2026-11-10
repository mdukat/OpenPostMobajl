package com.openpostmobajl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view helpers shared by the list screens. */
final class Ui {

    private Ui() {
    }

    static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }

    /** White, rounded, bordered background so a row clearly looks tappable. */
    static GradientDrawable borderedRow(Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.WHITE);
        d.setStroke(dp(ctx, 1), Color.rgb(0x99, 0x99, 0x99));
        d.setCornerRadius(dp(ctx, 6));
        return d;
    }

    /** Applies the bordered-row style: padding, border and a small bottom gap. */
    static void applyBorderedRow(Context ctx, TextView tv) {
        tv.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
        tv.setBackground(borderedRow(ctx));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 8);
        tv.setLayoutParams(lp);
    }
}
