package com.captainsss.usershub.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small shared visual system for the platform-widget UI. */
public final class UiKit {
    public static final int BACKGROUND = 0xfff6f8fc;
    public static final int SURFACE = Color.WHITE;
    public static final int TEXT = 0xff172033;
    public static final int MUTED = 0xff667085;
    public static final int BORDER = 0xffe1e7f0;
    public static final int PRIMARY = 0xff2f6fed;
    public static final int PRIMARY_CONTAINER = 0xffeaf1ff;
    public static final int SUCCESS = 0xff168a55;
    public static final int SUCCESS_CONTAINER = 0xffe8f7ef;
    public static final int DANGER = 0xffc63e48;
    public static final int DANGER_CONTAINER = 0xffffedef;
    public static final int WARNING = 0xffae691d;
    public static final int WARNING_CONTAINER = 0xfffff3df;
    public static final int NEUTRAL = 0xff7a8495;
    public static final int NEUTRAL_CONTAINER = 0xffeef1f6;

    private UiKit() {}

    public static void configureWindow(Activity activity) {
        activity.getWindow().setStatusBarColor(BACKGROUND);
        activity.getWindow().setNavigationBarColor(BACKGROUND);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, int sp, boolean bold, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, 2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static TextView sectionTitle(Context context, String value) {
        TextView view = text(context, value, 17, true, TEXT);
        view.setPadding(0, dp(context, 8), 0, dp(context, 7));
        return view;
    }

    public static LinearLayout card(Context context, int radiusDp, int paddingDp) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, paddingDp);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(background(context, SURFACE, radiusDp, BORDER));
        return card;
    }

    public static Button button(Context context, String label, int fill, int textColor, int stroke) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setLetterSpacing(0.01f);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setStateListAnimator(null);
        button.setBackground(interactive(context, fill, stroke, 14));
        return button;
    }

    public static TextView pill(Context context, String value, int fill, int textColor) {
        TextView view = text(context, value, 11, true, textColor);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        view.setBackground(background(context, fill, 99, Color.TRANSPARENT));
        return view;
    }

    public static GradientDrawable background(Context context, int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (Color.alpha(stroke) > 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static Drawable interactive(Context context, int fill, int stroke, int radiusDp) {
        GradientDrawable content = background(context, fill, radiusDp, stroke);
        GradientDrawable mask = background(context, Color.WHITE, radiusDp, Color.TRANSPARENT);
        return new RippleDrawable(ColorStateList.valueOf(0x22365f91), content, mask);
    }
}
