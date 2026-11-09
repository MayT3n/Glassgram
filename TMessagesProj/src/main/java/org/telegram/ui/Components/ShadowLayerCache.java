package org.telegram.ui.Components;

import android.graphics.Paint;

/**
 * Paint.setShadowLayer makes a native looper on every call; a glass draws its shadow with the
 * same few parameters frame after frame, so the paint is told only when one of them changes.
 * The paint must not get its shadow layer from anywhere else.
 */
final class ShadowLayerCache {
    private float radius = Float.NaN;
    private float dx;
    private float dy;
    private int color;

    void set(Paint paint, float radius, float dx, float dy, int color) {
        if (radius == this.radius && dx == this.dx && dy == this.dy && color == this.color) {
            return;
        }
        this.radius = radius;
        this.dx = dx;
        this.dy = dy;
        this.color = color;
        paint.setShadowLayer(radius, dx, dy, color);
    }
}
