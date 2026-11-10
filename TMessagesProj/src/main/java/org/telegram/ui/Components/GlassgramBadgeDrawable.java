package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stars.StarsReactionsSheet;

/**
 * A Glassgram badge like exteraGram's: a translucent rosette in the color of the name it
 * follows, the glyph in that color, and sparkles twinkling around it.
 *
 * As a drawable (next to names in SimpleTextView) it animates through its callback; the same
 * drawing is used by {@link GlassgramBadgeSpan} inside text.
 *
 * A badge can be a picture (a bitmap, like the "peter" face) instead of a glyph: it keeps its own
 * colors and is cut to the rosette, which shows in the name's color around it.
 */
public class GlassgramBadgeDrawable extends Drawable {

    private static final int FPS = 30;
    public static final float SIZE_TO_TEXT = 1.12f;

    private final Drawable shape;
    private final Drawable glyph;
    private final StarsReactionsSheet.Particles particles;
    private final Rect particleBounds = new Rect();
    private final Runnable invalidate = this::invalidateSelf;
    private int size;
    private int color = 0xFFFFFFFF;
    private int lastColor, lastGlyphColor;
    private long lastProcess;
    /** Called once per frame drawn, for hosts that are not this drawable's callback. */
    Runnable onFrame;

    public GlassgramBadgeDrawable(int glyphResId, int sizePx) {
        size = sizePx;
        shape = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.glassgram_badge_shape).mutate();
        glyph = ContextCompat.getDrawable(ApplicationLoader.applicationContext, glyphResId).mutate();
        picture = glyph instanceof BitmapDrawable;
        if (picture) {
            ((BitmapDrawable) glyph).getPaint().setFilterBitmap(true);
            ((BitmapDrawable) glyph).getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        }
        particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 8);
    }

    private final boolean picture;

    /** Whether the badge's glyph is a picture, drawn in its own colors (see the class). */
    public static boolean isPicture(int glyphResId) {
        try {
            return ContextCompat.getDrawable(ApplicationLoader.applicationContext, glyphResId) instanceof BitmapDrawable;
        } catch (Exception e) {
            return false;
        }
    }

    /** The color of the name next to the badge. */
    public void setColor(int color) {
        this.color = color;
    }

    /** Runs on the next frame after each draw, to redraw hosts that are not the callback. */
    public void setOnFrame(Runnable onFrame) {
        this.onFrame = onFrame;
    }

    public void setSize(int sizePx) {
        size = sizePx;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        int badgeSize = Math.min(b.width(), b.height());
        if (badgeSize <= 0) {
            badgeSize = size;
        }
        draw(canvas, b.centerX() - badgeSize / 2, b.centerY() - badgeSize / 2, badgeSize, color);
        if (getCallback() != null) {
            Choreographer60FpsContent.getInstance().addFrameCallbackOnce(invalidate, FPS);
        }
    }

    /**
     * The badge's colors follow the theme the way Telegram's verified mark does: the theme's
     * verified color with a white glyph next to plain names. A colored host (an accent passed on
     * purpose, a secret chat's green name) keeps its color, and over a cover or a photo, where a
     * light theme draws the name white, the badge is white with the glyph cut out.
     */
    private static boolean isCutout(int nameColor) {
        return isCutout(nameColor, isColored(nameColor));
    }

    private static boolean isCutout(int nameColor, boolean colored) {
        return !Theme.isCurrentThemeDark() && !colored && AndroidUtilities.computePerceivedBrightness(nameColor) > 0.75f;
    }

    private static boolean isColored(int color) {
        final float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[1] > 0.25f && hsv[2] > 0.2f;
    }

    private static int fillColor(int nameColor, boolean colored, boolean cutout) {
        if (colored || cutout) {
            return nameColor;
        }
        return Theme.getColor(Theme.key_chats_verifiedBackground);
    }

    private final Paint cutoutPaint = new Paint();
    {
        cutoutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }
    private boolean lastCutout;

    /** Draws the badge of badgeSize at left, top, next to a name in nameColor. */
    public void draw(Canvas canvas, int left, int top, int badgeSize, int nameColor) {
        nameColor |= 0xFF000000;
        // the HSV test (an array and a conversion) once per draw, not three times
        final boolean colored = isColored(nameColor);
        final boolean cutout = isCutout(nameColor, colored);
        final int color = fillColor(nameColor, colored, cutout) | 0xFF000000;
        final int glyphColor = Theme.getColor(Theme.key_chats_verifiedCheck) | 0xFF000000;
        if (color != lastColor || cutout != lastCutout || glyphColor != lastGlyphColor) {
            lastColor = color;
            lastCutout = cutout;
            lastGlyphColor = glyphColor;
            shape.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            if (!picture) {
                glyph.setColorFilter(new PorterDuffColorFilter(cutout ? 0xFF000000 : glyphColor, PorterDuff.Mode.SRC_IN));
            }
        }
        shape.setBounds(left, top, left + badgeSize, top + badgeSize);
        glyph.setBounds(left, top, left + badgeSize, top + badgeSize);
        if (picture) {
            // The picture over the rosette, cut to it (its paint draws SRC_ATOP)
            final int layer = canvas.saveLayer(left, top, left + badgeSize, top + badgeSize, null);
            shape.draw(canvas);
            glyph.draw(canvas);
            canvas.restoreToCount(layer);
        } else if (cutout) {
            // A white rosette with the glyph showing the cover through it
            final int layer = canvas.saveLayer(left, top, left + badgeSize, top + badgeSize, null);
            shape.draw(canvas);
            canvas.saveLayer(left, top, left + badgeSize, top + badgeSize, cutoutPaint);
            glyph.draw(canvas);
            canvas.restoreToCount(layer);
        } else {
            shape.draw(canvas);
            glyph.draw(canvas);
        }

        // Sparkles in the badge's own coordinates: the same badge can be drawn in several
        // places at once (the profile draws the name twice) and they all must match
        int spread = badgeSize / 3;
        int area = badgeSize + spread * 2;
        particleBounds.set(0, 0, area, area);
        particles.setBounds(particleBounds);
        long now = SystemClock.uptimeMillis();
        if (now - lastProcess >= 12) {
            lastProcess = now;
            particles.process();
        }
        canvas.save();
        canvas.translate(left - spread, top - spread);
        particles.draw(canvas, color);
        canvas.restore();
        if (onFrame != null) {
            Choreographer60FpsContent.getInstance().addFrameCallbackOnce(onFrame, FPS);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        shape.setAlpha(alpha);
        glyph.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /**
     * The badge after another drawable (the one SimpleTextView shows after the name's status,
     * like the verified check or the muted icon), drawn as one.
     */
    public static class Row extends Drawable implements Drawable.Callback {

        public final GlassgramBadgeDrawable badge;
        public final Drawable after;
        private final int gap;

        public Row(GlassgramBadgeDrawable badge, Drawable after, int gap) {
            this.badge = badge;
            this.after = after;
            this.gap = gap;
            badge.setCallback(this);
            if (after != null) {
                after.setCallback(this);
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return badge.getIntrinsicWidth() + (after != null ? gap + Math.max(0, after.getIntrinsicWidth()) : 0);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.max(badge.getIntrinsicHeight(), after != null ? after.getIntrinsicHeight() : 0);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float scale = getIntrinsicWidth() > 0 ? b.width() / (float) getIntrinsicWidth() : 1f;
            int bw = (int) (badge.getIntrinsicWidth() * scale);
            int bh = (int) (badge.getIntrinsicHeight() * scale);
            int cy = b.centerY();
            badge.setBounds(b.left, cy - bh / 2, b.left + bw, cy + bh / 2);
            badge.draw(canvas);
            if (after != null) {
                int aw = (int) (after.getIntrinsicWidth() * scale);
                int ah = (int) (after.getIntrinsicHeight() * scale);
                int ax = b.left + bw + (int) (gap * scale);
                after.setBounds(ax, cy - ah / 2, ax + aw, cy + ah / 2);
                after.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            badge.setAlpha(alpha);
            if (after != null) {
                after.setAlpha(alpha);
            }
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            if (after != null) {
                after.setColorFilter(colorFilter);
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }
    }
}
