package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * A view's background as the library's LiquidButton (Kyant0/AndroidLiquidGlass): a glass circle
 * or capsule made of what the given glass drawable's source has under the view, toned with that
 * glass's tint. Where there is no Liquid Glass, the glass drawable itself is drawn.
 */
public class LiquidGlassBackground extends Drawable {

    private final LiquidPressEffect button;
    private final BlurredBackgroundDrawable glass;
    private final int inset;
    private int alpha = 255;

    /** host: the view this is the background of; inset: the glass's distance from its bounds. */
    public LiquidGlassBackground(View host, BlurredBackgroundDrawable glass, int inset) {
        this.button = new LiquidPressEffect(host);
        // Its shadow lies around it
        this.button.setUnclipParents(1);
        this.glass = glass;
        this.inset = inset;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty() || alpha <= 0) {
            return;
        }
        glass.setBounds(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        glass.setAlpha(alpha);
        final float l = b.left + inset, t = b.top + inset, r = b.right - inset, bottom = b.bottom - inset;
        if (!button.drawGlass(canvas, l, t, r, bottom, Math.min(r - l, bottom - t) / 2f, glass, alpha)) {
            glass.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) {
            return;
        }
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
