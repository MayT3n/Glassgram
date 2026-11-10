package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * A Glassgram badge inside text (the chat list names), sized from the text and drawn in its
 * color; see {@link GlassgramBadgeDrawable}.
 *
 * Views showing the text register with {@link #attach} so the sparkles keep moving; without a
 * host the badge is drawn still.
 */
public class GlassgramBadgeSpan extends ReplacementSpan {

    private final GlassgramBadgeDrawable badge;
    private final int fallbackSize;
    private final WeakHashMap<View, Boolean> hosts = new WeakHashMap<>();

    public GlassgramBadgeSpan(int glyphResId, int sizePx) {
        fallbackSize = sizePx;
        badge = new GlassgramBadgeDrawable(glyphResId, sizePx);
        badge.onFrame = this::invalidateHosts;
    }

    /** Lets the views drawing text with badges animate their sparkles. */
    public static void attach(View host, CharSequence text) {
        if (host == null || !(text instanceof Spanned)) {
            return;
        }
        Spanned spanned = (Spanned) text;
        GlassgramBadgeSpan[] spans = spanned.getSpans(0, spanned.length(), GlassgramBadgeSpan.class);
        for (GlassgramBadgeSpan span : spans) {
            span.hosts.put(host, Boolean.TRUE);
        }
    }

    private int sizeFor(Paint paint) {
        return paint != null && paint.getTextSize() > 0 ? Math.round(paint.getTextSize() * GlassgramBadgeDrawable.SIZE_TO_TEXT) : fallbackSize;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            Paint.FontMetricsInt paintFm = paint.getFontMetricsInt();
            fm.ascent = paintFm.ascent;
            fm.descent = paintFm.descent;
            fm.top = paintFm.top;
            fm.bottom = paintFm.bottom;
        }
        return sizeFor(paint);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        int badgeSize = sizeFor(paint);
        int centerY = y + (fm.descent + fm.ascent) / 2;
        badge.draw(canvas, (int) x, centerY - badgeSize / 2, badgeSize, paint.getColor());
    }

    private final ArrayList<View> scratch = new ArrayList<>();

    private void invalidateHosts() {
        if (hosts.isEmpty()) {
            return;
        }
        // one list for all frames: the hosts are a WeakHashMap that attach() can change while the
        // views are invalidated
        scratch.clear();
        scratch.addAll(hosts.keySet());
        for (int i = 0; i < scratch.size(); i++) {
            final View view = scratch.get(i);
            if (view != null && view.isAttachedToWindow()) {
                view.invalidate();
            }
        }
        scratch.clear();
    }
}
