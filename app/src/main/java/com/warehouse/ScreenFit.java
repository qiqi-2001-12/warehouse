package com.warehouse;

import android.view.View;

/** Keeps a fixed-design panel fully visible on displays smaller than its design size. */
final class ScreenFit {
    private ScreenFit() {
    }

    static void bind(View host, View content) {
        host.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                fitInside(host, content));
        host.post(() -> fitInside(host, content));
    }

    private static void fitInside(View host, View content) {
        int availableWidth = host.getWidth() - host.getPaddingLeft() - host.getPaddingRight();
        int availableHeight = host.getHeight() - host.getPaddingTop() - host.getPaddingBottom();
        if (availableWidth <= 0 || availableHeight <= 0 || content.getWidth() <= 0 || content.getHeight() <= 0) {
            return;
        }

        float scale = Math.min(1f, Math.min(
                availableWidth / (float) content.getWidth(),
                availableHeight / (float) content.getHeight()));
        content.setPivotX(content.getWidth() / 2f);
        content.setPivotY(content.getHeight() / 2f);
        content.setScaleX(scale);
        content.setScaleY(scale);
    }
}
