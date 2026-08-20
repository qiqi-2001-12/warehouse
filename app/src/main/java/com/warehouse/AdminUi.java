package com.warehouse;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AdminUi {
    static final int PAGE_BG = Color.rgb(4, 29, 43);
    static final int PANEL_BG = Color.rgb(7, 45, 66);
    static final int PANEL_STROKE = Color.rgb(20, 113, 136);
    static final int CARD_BG = Color.rgb(9, 57, 80);
    static final int CARD_STROKE = Color.rgb(36, 132, 154);
    static final int TEXT_PRIMARY = Color.rgb(224, 246, 248);
    static final int TEXT_SECONDARY = Color.rgb(133, 191, 201);
    static final int ACCENT = Color.rgb(36, 200, 217);
    static final int INPUT_BG = Color.rgb(176, 170, 255);

    private AdminUi() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable bg(int color, int radiusDp, int strokeColor, int strokeDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(context, strokeDp), strokeColor);
        }
        return drawable;
    }

    static TextView text(Context context, String value, int sizeSp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        if (style != Typeface.NORMAL) {
            view.setTypeface(Typeface.DEFAULT, style);
        }
        return view;
    }

    static TextView label(Context context, String value) {
        TextView view = text(context, value, 18, TEXT_PRIMARY, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    static TextView valueBox(Context context, String value) {
        TextView view = text(context, value, 16, Color.rgb(25, 28, 52), Typeface.BOLD);
        view.setBackground(bg(INPUT_BG, 1, Color.TRANSPARENT, 0, context));
        return view;
    }

    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    static LinearLayout.LayoutParams weightedLp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    static TextView button(Context context, String value) {
        TextView button = text(context, value, 16, TEXT_PRIMARY, Typeface.BOLD);
        button.setBackground(bg(Color.rgb(19, 72, 94), 6, PANEL_STROKE, 1, context));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    static void setSelected(TextView button, boolean selected) {
        Context context = button.getContext();
        button.setTextColor(selected ? Color.rgb(5, 34, 45) : TEXT_PRIMARY);
        button.setBackground(bg(
                selected ? ACCENT : Color.rgb(19, 72, 94),
                6,
                selected ? ACCENT : PANEL_STROKE,
                1,
                context));
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(Color.rgb(18, 95, 116));
        return view;
    }
}
