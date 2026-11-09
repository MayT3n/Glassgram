package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.liquidglass.BackdropGlass;
import org.telegram.messenger.AndroidUtilities;

/**
 * Filled capsule button background after LiquidButton from the Kyant0/AndroidLiquidGlass
 * catalog: a capsule tinted with the button color, the library's default highlight along the
 * outline and a soft shadow below.
 * <p>
 * The tint is kept opaque. In the catalog the button floats over a picture and the tint is
 * translucent; Telegram's buttons sit on a flat background and carry white text, so a
 * translucent tint would only wash the button out.
 */
public class LiquidButtonBackground extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ShadowLayerCache shadowLayer = new ShadowLayerCache();
    private final RectF rect = new RectF();
    private final float[] radii = new float[4];
    private Object glass;
    private int color;
    private int alpha = 255;

    public LiquidButtonBackground(int color) {
        setColor(color);
    }

    public void setColor(int color) {
        this.color = color;
        invalidateSelf();
    }

    public int getColor() {
        return color;
    }

    // The library's LiquidButton is drawn in its place (see ButtonWithCounterView)
    private boolean skipDraw;

    public void setSkipDraw(boolean skipDraw) {
        this.skipDraw = skipDraw;
    }

    public static float radius(Rect bounds) {
        return Math.min(bounds.width(), bounds.height()) / 2f;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty() || skipDraw) {
            return;
        }
        final float r = radius(b);
        final int fill = ColorUtils.setAlphaComponent(color, (int) (android.graphics.Color.alpha(color) * alpha / 255f));
        rect.set(b);

        // Shadow(radius = 12.dp, color = Black 10%), drawn only around the capsule.
        final float shadow = AndroidUtilities.dpf2(12);
        shadowPaint.setColor(fill);
        shadowLayer.set(shadowPaint, shadow, 0, shadow / 6f, ColorUtils.setAlphaComponent(0xFF000000, (int) (0x1A * alpha / 255f)));
        canvas.drawRoundRect(rect, r, r, shadowPaint);

        paint.setColor(fill);
        canvas.drawRoundRect(rect, r, r, paint);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alpha > 0) {
            if (glass == null) {
                glass = new BackdropGlass();
            }
            final BackdropGlass g = (BackdropGlass) glass;
            radii[0] = radii[1] = radii[2] = radii[3] = r;
            final boolean dark = ColorUtils.calculateLuminance(color | 0xFF000000) < 0.5f;
            g.updateEffect(b.width(), b.height(), radii, AndroidUtilities.density,
                BackdropGlass.COLOR_NONE, 0f, 0f, 0f, false, false, dark);
            canvas.save();
            canvas.translate(b.left, b.top);
            g.drawHighlight(canvas, alpha / 255f, 1f, false);
            canvas.restore();
        }
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        final Rect b = getBounds();
        outline.setRoundRect(b, radius(b));
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
