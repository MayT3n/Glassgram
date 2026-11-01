package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * glassGram: as on iOS, glass pieces side by side flow into each other when one of them swells
 * or is pulled towards the other: a liquid bridge grows between them, narrowest in the middle,
 * like two drops about to join (the metaball of Hiroyuki Sato's paper.js example). The bridge
 * is glass too: what is under it, blurred, and the pieces' tints running from one to the other.
 */
public final class GlassMerge {

    private static final float HANDLE_SIZE = 2.4f;

    private final Path path = new Path();
    private final Path pieces = new Path();
    private final RectF bounds = new RectF();
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int gradientFrom, gradientTo;
    private float gradientX1 = Float.NaN, gradientX2;

    /**
     * Draws the bridge between two capsules lying in a row, left before right, as far as their
     * gap has shrunk from restGap (their gap at rest, when no bridge shows). The glass is drawn
     * clipped to the bridge (its own drawable, not one drawn elsewhere this frame); the tints
     * (0 for clear glass) run from the left piece to the right one. False when there is no bridge.
     */
    public boolean draw(Canvas canvas, RectF left, RectF right, float restGap, BlurredBackgroundDrawable glass, int tintLeft, int tintRight) {
        if (restGap <= 0 || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        final float gap = right.left - left.right;
        // Nothing at rest; fully joined once half the gap is gone
        final float strength = Math.max(0f, Math.min(1f, (restGap - gap) / (restGap * 0.5f)));
        if (strength <= 0f) {
            return false;
        }
        final float r1 = left.height() / 2f, r2 = right.height() / 2f;
        if (!metaball(path, left.right - r1, left.centerY(), r1, right.left + r2, right.centerY(), r2, 0.55f * strength)) {
            return false;
        }
        // Only what is between the pieces: their own glass, rims and all, stays as drawn
        pieces.rewind();
        pieces.addRoundRect(left, r1, r1, Path.Direction.CW);
        pieces.addRoundRect(right, r2, r2, Path.Direction.CW);
        path.op(pieces, Path.Op.DIFFERENCE);
        path.computeBounds(bounds, false);
        if (bounds.isEmpty()) {
            return false;
        }
        canvas.save();
        canvas.clipPath(path);
        if (glass != null) {
            glass.setBounds(Math.round(bounds.left) - 1, Math.round(bounds.top) - 1, Math.round(bounds.right) + 1, Math.round(bounds.bottom) + 1);
            glass.setRadius(0);
            glass.setAlpha((int) (255 * Math.min(1f, strength * 2f)));
            glass.draw(canvas);
        }
        if (tintLeft != 0 || tintRight != 0) {
            final float x1 = left.right - r1, x2 = right.left + r2;
            if (gradientFrom != tintLeft || gradientTo != tintRight || gradientX1 != x1 || gradientX2 != x2) {
                gradientFrom = tintLeft;
                gradientTo = tintRight;
                gradientX1 = x1;
                gradientX2 = x2;
                tintPaint.setShader(new LinearGradient(x1, 0, x2, 0, tintLeft, tintRight, Shader.TileMode.CLAMP));
            }
            tintPaint.setAlpha((int) (255 * Math.min(1f, strength * 2f)));
            canvas.drawRect(bounds, tintPaint);
        }
        canvas.restore();
        return true;
    }

    private static float clamp1(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    /** The bridge between two circles, spreading over them by v (0 to 1); false when there is none. */
    private static boolean metaball(Path path, float c1x, float c1y, float r1, float c2x, float c2y, float r2, float v) {
        final float dx = c2x - c1x, dy = c2y - c1y;
        final float d = (float) Math.hypot(dx, dy);
        if (r1 <= 0 || r2 <= 0 || v <= 0 || d <= Math.abs(r1 - r2) || d > r1 + r2 * 2.5f) {
            return false;
        }
        final float u1, u2;
        if (d < r1 + r2) {
            u1 = (float) Math.acos(clamp1((r1 * r1 + d * d - r2 * r2) / (2 * r1 * d)));
            u2 = (float) Math.acos(clamp1((r2 * r2 + d * d - r1 * r1) / (2 * r2 * d)));
        } else {
            u1 = 0;
            u2 = 0;
        }
        final float angle = (float) Math.atan2(dy, dx);
        final float maxSpread = (float) Math.acos(clamp1((r1 - r2) / d));
        final float pi = (float) Math.PI;
        final float a1 = angle + u1 + (maxSpread - u1) * v;
        final float a2 = angle - u1 - (maxSpread - u1) * v;
        final float a3 = angle + pi - u2 - (pi - u2 - maxSpread) * v;
        final float a4 = angle - pi + u2 + (pi - u2 - maxSpread) * v;
        final float p1x = c1x + r1 * (float) Math.cos(a1), p1y = c1y + r1 * (float) Math.sin(a1);
        final float p2x = c1x + r1 * (float) Math.cos(a2), p2y = c1y + r1 * (float) Math.sin(a2);
        final float p3x = c2x + r2 * (float) Math.cos(a3), p3y = c2y + r2 * (float) Math.sin(a3);
        final float p4x = c2x + r2 * (float) Math.cos(a4), p4y = c2y + r2 * (float) Math.sin(a4);
        final float totalRadius = r1 + r2;
        float d2 = Math.min(v * HANDLE_SIZE, (float) Math.hypot(p3x - p1x, p3y - p1y) / totalRadius);
        d2 *= Math.min(1f, d * 2f / totalRadius);
        final float r1h = r1 * d2, r2h = r2 * d2;
        final float h1x = p1x + r1h * (float) Math.cos(a1 - pi / 2), h1y = p1y + r1h * (float) Math.sin(a1 - pi / 2);
        final float h2x = p2x + r1h * (float) Math.cos(a2 + pi / 2), h2y = p2y + r1h * (float) Math.sin(a2 + pi / 2);
        final float h3x = p3x + r2h * (float) Math.cos(a3 + pi / 2), h3y = p3y + r2h * (float) Math.sin(a3 + pi / 2);
        final float h4x = p4x + r2h * (float) Math.cos(a4 - pi / 2), h4y = p4y + r2h * (float) Math.sin(a4 - pi / 2);
        path.rewind();
        path.moveTo(p1x, p1y);
        path.cubicTo(h1x, h1y, h3x, h3y, p3x, p3y);
        path.lineTo(p4x, p4y);
        path.cubicTo(h4x, h4y, h2x, h2y, p2x, p2y);
        path.close();
        return true;
    }
}
