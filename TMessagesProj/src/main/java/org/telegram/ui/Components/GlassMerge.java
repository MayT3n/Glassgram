package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * glassGram: as on iOS, glass pieces side by side flow into each other when one of them swells
 * or is pulled towards the other: a liquid bridge grows between them, narrowest in the middle,
 * like two drops about to join (the metaball of Hiroyuki Sato's paper.js example). The bridge
 * is glass too: what is under it, blurred, a tinted piece's color poured into it, and a light rim.
 */
public final class GlassMerge {

    private static final float HANDLE_SIZE = 2.4f;
    /** How near the pieces have to come for a bridge to start: iOS joins them only nearly touching. */
    private static final float JOIN_GAP_DP = 8;

    private final Path path = new Path();
    private final Path edges = new Path();
    private final Path pieces = new Path();
    private final RectF bounds = new RectF();
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int gradientFrom, gradientTo;
    private boolean gradientTintedLeft;
    private float gradientX1 = Float.NaN, gradientX2;

    public GlassMerge() {
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Draws the bridge between two capsules lying in a row, left before right, as far as their
     * gap has shrunk from restGap (their gap at rest, when no bridge shows). The glass is drawn
     * clipped to the bridge (its own drawable, not one drawn elsewhere this frame); a tinted piece
     * (0 for clear glass) pours its color into the bridge, as iOS's do, fading only near the other.
     * cutLeft and cutRight leave a piece already drawn out of the bridge: a piece drawn over it
     * afterwards is not cut, or a ring of the bridge's darker glass showed between them.
     */
    public boolean draw(Canvas canvas, RectF left, RectF right, float restGap, BlurredBackgroundDrawable glass,
                        int tintLeft, int tintRight, boolean cutLeft, boolean cutRight) {
        if (restGap <= 0 || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        final float gap = right.left - left.right;
        // Nothing at rest, nor until they come nearly together; fully joined as they touch
        final float joinGap = Math.min(AndroidUtilities.dpf2(JOIN_GAP_DP), restGap - AndroidUtilities.dpf2(1));
        if (joinGap <= 0f) {
            return false;
        }
        final float strength = Math.max(0f, Math.min(1f, (joinGap - gap) / joinGap));
        if (strength <= 0f) {
            return false;
        }
        final float r1 = left.height() / 2f, r2 = right.height() / 2f;
        final float c1x = left.right - r1, c2x = right.left + r2;
        // A full neck soon after it starts, as iOS's liquid pieces have
        if (!metaball(path, edges, c1x, left.centerY(), r1, c2x, right.centerY(), r2, 0.3f + 0.5f * strength)) {
            return false;
        }
        if (cutLeft || cutRight) {
            pieces.rewind();
            if (cutLeft) {
                pieces.addRoundRect(left, r1, r1, Path.Direction.CW);
            }
            if (cutRight) {
                pieces.addRoundRect(right, r2, r2, Path.Direction.CW);
            }
            path.op(pieces, Path.Op.DIFFERENCE);
        }
        path.computeBounds(bounds, false);
        if (bounds.isEmpty()) {
            return false;
        }
        final float alpha = Math.min(1f, strength * 3f);
        canvas.save();
        canvas.clipPath(path);
        if (glass != null) {
            glass.setBounds(Math.round(bounds.left) - 1, Math.round(bounds.top) - 1, Math.round(bounds.right) + 1, Math.round(bounds.bottom) + 1);
            glass.setRadius(0);
            glass.setAlpha((int) (255 * alpha));
            glass.draw(canvas);
        }
        if (tintLeft != 0 || tintRight != 0) {
            // The tinted piece's color fills the bridge and fades only near the clear one
            final boolean tintedLeft = tintLeft != 0 && tintRight == 0;
            final int color = tintedLeft ? tintLeft : tintRight;
            if (gradientFrom != color || gradientTintedLeft != tintedLeft || gradientX1 != c1x || gradientX2 != c2x || (tintLeft != 0 && tintRight != 0)) {
                gradientFrom = color;
                gradientTintedLeft = tintedLeft;
                gradientX1 = c1x;
                gradientX2 = c2x;
                final int clear = color & 0x00FFFFFF;
                if (tintLeft != 0 && tintRight != 0) {
                    tintPaint.setShader(new LinearGradient(c1x, 0, c2x, 0, tintLeft, tintRight, Shader.TileMode.CLAMP));
                } else if (tintedLeft) {
                    tintPaint.setShader(new LinearGradient(c1x, 0, c2x, 0, new int[]{color, color, clear}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                } else {
                    tintPaint.setShader(new LinearGradient(c1x, 0, c2x, 0, new int[]{clear, color, color}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                }
            }
            tintPaint.setAlpha((int) (255 * alpha));
            canvas.drawRect(bounds, tintPaint);
        }
        // The glass's light rim along the bridge's edges, as along the pieces'
        rimPaint.setStrokeWidth(AndroidUtilities.dpf2(2f));
        rimPaint.setColor(0xFFFFFFFF);
        rimPaint.setAlpha((int) (70 * alpha));
        canvas.drawPath(edges, rimPaint);
        canvas.restore();
        return true;
    }

    /** As above, cutting both pieces out: for a bridge drawn over both. */
    public boolean draw(Canvas canvas, RectF left, RectF right, float restGap, BlurredBackgroundDrawable glass, int tintLeft, int tintRight) {
        return draw(canvas, left, right, restGap, glass, tintLeft, tintRight, true, true);
    }

    private static float clamp1(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    /** The bridge between two circles, spreading over them by v (0 to 1); false when there is none. */
    private static boolean metaball(Path path, Path edges, float c1x, float c1y, float r1, float c2x, float c2y, float r2, float v) {
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
        edges.rewind();
        edges.moveTo(p1x, p1y);
        edges.cubicTo(h1x, h1y, h3x, h3y, p3x, p3y);
        edges.moveTo(p4x, p4y);
        edges.cubicTo(h4x, h4y, h2x, h2y, p2x, p2y);
        return true;
    }
}
