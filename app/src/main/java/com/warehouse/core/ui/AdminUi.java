package com.warehouse.core.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AdminUi {
    public static final int PAGE_BG = Color.rgb(4, 29, 43);
    public static final int PANEL_BG = Color.rgb(7, 45, 66);
    public static final int PANEL_STROKE = Color.rgb(20, 113, 136);
    public static final int CARD_BG = Color.rgb(9, 57, 80);
    public static final int CARD_STROKE = Color.rgb(36, 132, 154);
    public static final int TEXT_PRIMARY = Color.rgb(224, 246, 248);
    public static final int TEXT_SECONDARY = Color.rgb(133, 191, 201);
    public static final int ACCENT = Color.rgb(36, 200, 217);
    public static final int INPUT_BG = Color.rgb(176, 170, 255);
    public static final int CONTROL_BG = Color.argb(198, 7, 48, 88);
    public static final int CONTROL_STROKE = Color.rgb(60, 154, 190);
    public static final int SELECTED_CONTROL_BG = Color.rgb(15, 196, 225);
    public static final int SELECTED_CONTROL_STROKE = Color.rgb(143, 238, 250);

    private AdminUi() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int mm(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_MM,
                value,
                context.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable bg(int color, int radiusDp, int strokeColor, int strokeDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(context, strokeDp), strokeColor);
        }
        return drawable;
    }

    public static TextView text(Context context, String value, int sizeSp, int color, int style) {
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

    public static TextView label(Context context, String value) {
        TextView view = text(context, value, 18, TEXT_PRIMARY, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    public static TextView valueBox(Context context, String value) {
        TextView view = text(context, value, 16, Color.rgb(25, 28, 52), Typeface.BOLD);
        view.setBackground(bg(INPUT_BG, 1, Color.TRANSPARENT, 0, context));
        return view;
    }

    public static void styleCompactNumericInput(EditText input) {
        Context context = input.getContext();
        input.setSoundEffectsEnabled(false);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_SECONDARY);
        input.setTextSize(24);
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setBackground(bg(Color.rgb(5, 35, 52), 4, ACCENT, 1, context));
        input.setPadding(dp(context, 12), 0, dp(context, 12), 0);
    }

    public static int compactNumericInputWidth(Context context) {
        return dp(context, 176);
    }

    public static int compactNumericInputHeight(Context context) {
        return dp(context, 52);
    }

    public static int compactDialogWidth(Context context) {
        return dp(context, 280);
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams weightedLp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    public static TextView button(Context context, String value) {
        TextView button = text(context, value, 16, TEXT_PRIMARY, Typeface.BOLD);
        button.setBackground(bg(CONTROL_BG, 6, CONTROL_STROKE, 1, context));
        button.setClickable(true);
        button.setFocusable(true);
        addPressFeedback(button);
        return button;
    }

    /** Adds a theme-colored pressed overlay without changing the control's background. */
    public static void addPressFeedback(View view) {
        if (view == null) {
            return;
        }
        Drawable mask = null;
        Drawable background = view.getBackground();
        if (background != null && background.getConstantState() != null) {
            mask = background.getConstantState().newDrawable().mutate();
        }
        view.setForeground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(120, 36, 200, 217)), null, mask));
    }

    public static void setSelected(TextView button, boolean selected) {
        Context context = button.getContext();
        button.setTextColor(selected ? Color.rgb(5, 34, 45) : TEXT_PRIMARY);
        button.setBackground(bg(
                selected ? SELECTED_CONTROL_BG : CONTROL_BG,
                6,
                selected ? SELECTED_CONTROL_STROKE : CONTROL_STROKE,
                1,
                context));
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(Color.rgb(18, 95, 116));
        return view;
    }
}
