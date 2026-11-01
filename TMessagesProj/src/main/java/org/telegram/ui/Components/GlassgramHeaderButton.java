package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

/**
 * glassGram: a Liquid Glass button of the chats list's iOS-like header, made of what is under the
 * header as the bar's own pieces are: a capsule with a text (Edit, Done) or a circle with an icon,
 * clear glass or tinted with a color (the new message button, in the theme's color). It swells
 * under the finger like the bar's pieces (LiquidPressEffect).
 */
public class GlassgramHeaderButton extends FrameLayout {

    /** The inset of the glass drawable around the glass, as the bar's own pieces have it. */
    private static final int GLASS_PADDING_DP = 6;
    /** The room on each side of a text. */
    private static final int TEXT_PADDING_DP = 16;

    private final BlurredBackgroundDrawable glass;
    private final LiquidPressEffect press;
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedFloat glassWidth = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private int tint;
    private AnimatedTextView textView;
    private ImageView iconView;
    private CharSequence text;
    private int anchor = Gravity.CENTER_HORIZONTAL;

    /** A glass piece beside this button that the button flows into when it swells towards it. */
    public interface MergeNeighbor {
        /** The neighbor's glass in this button's coordinates as drawn now; false when it shows none. */
        boolean getGlass(android.graphics.RectF out);
    }

    private MergeNeighbor mergeNeighbor;
    private GlassMerge merge;
    private BlurredBackgroundDrawable mergeGlass;
    private float mergeRestGap = -1;
    private final android.graphics.RectF mergeMine = new android.graphics.RectF();
    private final android.graphics.RectF mergeOther = new android.graphics.RectF();

    /** As on iOS, pressed, the button flows into the glass beside it (GlassMerge); null for none. */
    public void setMergeNeighbor(MergeNeighbor neighbor, BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colors) {
        mergeNeighbor = neighbor;
        if (neighbor != null && merge == null) {
            merge = new GlassMerge();
            mergeGlass = factory.create(this, colors);
        }
    }

    private void drawMerge(Canvas canvas, float left, float right, float h) {
        if (mergeNeighbor == null || !mergeNeighbor.getGlass(mergeOther)) {
            return;
        }
        press.mapRect(left, 0, right, h, mergeMine);
        final boolean neighborFirst = mergeOther.centerX() < mergeMine.centerX();
        final float gap = neighborFirst ? mergeMine.left - mergeOther.right : mergeOther.left - mergeMine.right;
        if (!press.isActive()) {
            // The gap at rest: the bridge grows as it shrinks from it
            mergeRestGap = gap;
            return;
        }
        if (neighborFirst) {
            merge.draw(canvas, mergeOther, mergeMine, mergeRestGap, mergeGlass, 0, tint);
        } else {
            merge.draw(canvas, mergeMine, mergeOther, mergeRestGap, mergeGlass, tint, 0);
        }
    }

    public GlassgramHeaderButton(Context context, BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colors) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        glass = factory.create(this, colors);
        glass.setPadding(dp(GLASS_PADDING_DP));
        press = new LiquidPressEffect(this);
        // The library's button has its shadow around it, past the bar
        press.setUnclipParents(2);
        tintPaint.setStyle(Paint.Style.FILL);
    }

    /** Which edge the capsule keeps while its width changes with the text: Gravity.LEFT, RIGHT or CENTER_HORIZONTAL. */
    public void setAnchor(int gravity) {
        anchor = gravity;
    }

    /** A capsule with a text: its width follows the text, changing with an animation. */
    public void setText(CharSequence text, boolean animated) {
        if (textView == null) {
            textView = new AnimatedTextView(getContext(), true, true, false);
            textView.setAnimationProperties(.6f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            // iOS's bar buttons: 17pt, regular
            textView.setTextSize(dp(16));
            textView.setGravity(Gravity.CENTER);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        this.text = text;
        textView.setText(text, animated && isLaidOut());
        if (!animated) {
            glassWidth.set(targetWidthFor(getMeasuredHeight() > 0 ? getMeasuredHeight() : dp(46)), true);
        }
        requestLayout();
        invalidate();
    }

    public CharSequence getText() {
        return text;
    }

    /** A circle with an icon. */
    public void setIcon(int resId) {
        setIcon(getContext().getResources().getDrawable(resId).mutate());
    }

    public void setIcon(Drawable drawable) {
        if (iconView == null) {
            iconView = new ImageView(getContext());
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        iconView.setImageDrawable(drawable);
    }

    public ImageView getIconView() {
        return iconView;
    }

    /** The color of the text or the icon. */
    public void setContentColor(int color) {
        if (textView != null) {
            textView.setTextColor(color);
        }
        if (iconView != null) {
            iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
    }

    /** Fills the glass with a color, 0 for clear glass. */
    public void setTint(int color) {
        if (tint != color) {
            tint = color;
            invalidate();
        }
    }

    public void updateColors() {
        glass.updateColors();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int width = textView != null ? Math.max(height, targetWidthFor(height)) : height;
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private int targetWidthFor(int height) {
        if (textView == null || text == null) {
            return height;
        }
        final float width = textView.getPaint().measureText(text, 0, text.length());
        return Math.max(height, (int) Math.ceil(width) + dp(TEXT_PADDING_DP) * 2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        press.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float h = getHeight();
        if (h <= 0) {
            return;
        }
        // The capsule goes from the text's old width to its new one; the button keeps its start
        final float w = textView != null ? glassWidth.set(getWidth()) : getWidth();
        final float left = anchor == Gravity.LEFT ? 0 : anchor == Gravity.RIGHT ? getWidth() - w : (getWidth() - w) / 2f;
        if (textView != null) {
            // The text in the middle of the capsule as it changes
            final float tx = left + w / 2f - getWidth() / 2f;
            if (textView.getTranslationX() != tx) {
                textView.setTranslationX(tx);
            }
        }
        final float right = left + w;
        final float radius = h / 2f;
        drawMerge(canvas, left, right, h);
        final int p = dp(GLASS_PADDING_DP);
        glass.setBounds(Math.round(left) - p, -p, Math.round(right) + p, Math.round(h) + p);
        glass.setRadius(radius);
        final boolean liquid = press.drawGlass(canvas, left, 0, right, h, radius, glass, 255, tint);
        if (!liquid) {
            glass.setAlpha(255);
            glass.draw(canvas);
            if (tint != 0) {
                tintPaint.setColor(tint);
                canvas.drawRoundRect(left, 0, right, h, radius, radius, tintPaint);
            }
            if (press.isActive()) {
                canvas.save();
                canvas.translate(left, 0);
                press.drawGlow(canvas, w, h, radius);
                canvas.restore();
            }
        }
        canvas.save();
        if (press.isActive()) {
            // What the button carries follows its press
            canvas.translate(left, 0);
            press.transform(canvas, w, h);
            canvas.translate(-left, 0);
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
