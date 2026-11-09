package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.liquidglass.BackdropGlass;
import org.telegram.liquidglass.GlassLayer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;

/**
 * Glass capsule under the action bar buttons of screens without a glass backdrop (profile,
 * settings): a translucent tint, a soft shadow on light glass and the library's ambient
 * highlight along the outline, like the catalog's LiquidButton.
 * <p>
 * Over light icons (a colored header or a photo) the glass is a light veil; over dark icons
 * (a plain light header) it is the frosted white of iOS buttons.
 * <p>
 * Given a {@link #setBackdrop backdrop} (what is under the button), it is real glass: that
 * backdrop blurred and refracted at the edges by the library's lens, under the tint. Without
 * one, or where the lens is not there, the capsule is only the tint and the highlight.
 */
public class LiquidGlassButtonBackground extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ShadowLayerCache shadowLayer = new ShadowLayerCache();
    private final RectF rect = new RectF();
    private final float[] radii = new float[4];
    private Object glass;
    private int iconColor = 0xFFFFFFFF;
    private int surfaceColor = 0xFFFFFFFF;
    private int alpha = 255;

    private GlassLayer.Backdrop backdrop;
    private Object glassLayer;
    private final Path clip = new Path();

    /** What is under the button, with (0, 0) at its top-left corner; null for a flat capsule. */
    public void setBackdrop(GlassLayer.Backdrop backdrop) {
        this.backdrop = backdrop;
    }

    /** Picks the glass for icons of iconColor over a screen of surfaceColor. */
    public void setColors(int iconColor, int surfaceColor) {
        if (this.iconColor != iconColor || this.surfaceColor != surfaceColor) {
            this.iconColor = iconColor;
            this.surfaceColor = surfaceColor;
            invalidateSelf();
        }
    }

    /**
     * The tint of the glass over what it blurs, for the library's LiquidButton drawn in place of
     * this: frosted for dark icons, a light veil for light ones, as {@link #draw} tints its own.
     */
    public int getGlassTint() {
        final float t = veilFactor();
        final boolean dark = ColorUtils.calculateLuminance(surfaceColor | 0xFF000000) < 0.5f;
        final int frosted = ColorUtils.setAlphaComponent(surfaceColor, (int) (255 * BackdropGlass.tintAlpha(dark)));
        final int light = ColorUtils.setAlphaComponent(0xFFFFFFFF, 0x2E);
        return ColorUtils.blendARGB(frosted, light, t);
    }

    /**
     * 0 for dark icons (frosted white glass), 1 for light ones (a light veil), in between while
     * the icons change color, as they do when the profile header opens.
     */
    private float veilFactor() {
        final float lum = (float) ColorUtils.calculateLuminance(iconColor | 0xFF000000);
        return Math.max(0f, Math.min(1f, (lum - 0.25f) / 0.5f));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty() || alpha <= 0) {
            return;
        }
        final float r = Math.min(b.width(), b.height()) / 2f;
        final float t = veilFactor();
        final boolean veil = t >= 0.5f;
        rect.set(b);

        if (drawLiquidGlass(canvas, b, r, t, veil)) {
            return;
        }

        final int frosted = ColorUtils.setAlphaComponent(surfaceColor, 0xE0);
        final int light = ColorUtils.setAlphaComponent(0xFFFFFFFF, 0x2E);
        final int blended = ColorUtils.blendARGB(frosted, light, t);
        final int fill = ColorUtils.setAlphaComponent(blended, (int) (android.graphics.Color.alpha(blended) * alpha / 255f));
        if (t < 1f) {
            final float shadow = AndroidUtilities.dpf2(10);
            shadowPaint.setColor(fill);
            shadowLayer.set(shadowPaint, shadow, 0, shadow / 5f, ColorUtils.setAlphaComponent(0xFF000000, (int) (0x18 * (1f - t) * alpha / 255f)));
            canvas.drawRoundRect(rect, r, r, shadowPaint);
        }
        paint.setColor(fill);
        canvas.drawRoundRect(rect, r, r, paint);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (glass == null) {
                glass = new BackdropGlass();
            }
            final BackdropGlass g = (BackdropGlass) glass;
            radii[0] = radii[1] = radii[2] = radii[3] = r;
            g.updateEffect(b.width(), b.height(), radii, AndroidUtilities.density,
                BackdropGlass.COLOR_NONE, 0f, 0f, 0f, false, false, veil);
            canvas.save();
            canvas.translate(b.left, b.top);
            g.drawHighlight(canvas, (veil ? 0.8f : 1f) * alpha / 255f, 1f, true);
            canvas.restore();
        }
    }

    private boolean drawLiquidGlass(Canvas canvas, Rect b, float r, float t, boolean veil) {
        if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) || !canvas.isHardwareAccelerated()) {
            return false;
        }
        if (glassLayer == null) {
            glassLayer = new GlassLayer();
        }
        final boolean dark = ColorUtils.calculateLuminance(surfaceColor | 0xFF000000) < 0.5f;
        // As light a tint as the other glass, or it hides what the lens bends
        final int frosted = ColorUtils.setAlphaComponent(surfaceColor, (int) (255 * BackdropGlass.tintAlpha(dark)));
        final int light = ColorUtils.setAlphaComponent(0xFFFFFFFF, 0x2E);
        final int tint = ColorUtils.blendARGB(frosted, light, t);

        if (t < 1f) {
            // The shadow around the capsule only, so it does not darken the glass
            final float shadow = AndroidUtilities.dpf2(10);
            shadowPaint.setColor(0xFF000000);
            shadowLayer.set(shadowPaint, shadow, 0, shadow / 5f, ColorUtils.setAlphaComponent(0xFF000000, (int) (0x18 * (1f - t) * alpha / 255f)));
            clip.rewind();
            clip.addRoundRect(rect, r, r, Path.Direction.CW);
            canvas.save();
            canvas.clipOutPath(clip);
            canvas.drawRoundRect(rect, r, r, shadowPaint);
            canvas.restore();
        }

        final float density = AndroidUtilities.density;
        final int width = b.width(), height = b.height();
        // The same lens as the bars' glass: a narrow band with dispersion. Over a photo the veil
        // stays clear, on a plain header it is frosted
        final float lensHeight = Math.max(1, Math.min(AndroidUtilities.dp(12), Math.min(width, height) / 4));
        final float blur = (veil ? 2f : BackdropGlass.BlurRadius) * density;
        final int restore = alpha != 255
            ? canvas.saveLayerAlpha(b.left, b.top, b.right, b.bottom, alpha)
            : canvas.save();
        canvas.translate(b.left, b.top);
        ((GlassLayer) glassLayer).draw(canvas, width, height, r, density,
            dark ? BackdropGlass.COLOR_CONTROLS_DARK : BackdropGlass.COLOR_CONTROLS_LIGHT,
            blur, lensHeight, AndroidUtilities.dpf2(24), false, true, dark,
            backdrop, tint, veil ? 0.8f : 1f, 1f);
        canvas.restoreToCount(restore);
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
