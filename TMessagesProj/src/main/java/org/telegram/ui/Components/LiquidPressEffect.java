package org.telegram.ui.Components;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.liquidglass.GlassLayer;
import org.telegram.liquidglass.LiquidButtonView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;

/**
 * A glass button of the Kyant0/AndroidLiquidGlass catalog's LiquidButton for a plain View.
 * <p>
 * Where Liquid Glass is on, the button is the library's LiquidButton itself ({@link LiquidButtonView}):
 * {@link #drawGlass} draws its glass with its highlight in place of the view's own glass, and
 * the press is the catalog's InteractiveHighlight with LiquidButton's layer: the surface swells a
 * little and leans towards the finger, and a light spot follows the finger. Elsewhere this view's
 * own copy of that motion runs, with the catalog's spring(0.5f, 300f), and {@link #drawGlow}
 * draws the light.
 * <p>
 * Call {@link #onTouchEvent} from {@code dispatchTouchEvent}, wrap the content's {@code draw} in
 * {@link #transform} / {@code restore}, and draw the glass with {@link #drawGlass}, or call
 * {@link #drawGlow} after the background when it returns false.
 */
public class LiquidPressEffect {

    private static final float DAMPING = 0.5f;
    private static final float STIFFNESS = 300f;

    private final View view;
    private final FloatValueHolder press = new FloatValueHolder(0f);
    private final FloatValueHolder offsetX = new FloatValueHolder(0f);
    private final FloatValueHolder offsetY = new FloatValueHolder(0f);
    private final SpringAnimation pressAnimation;
    private final SpringAnimation offsetXAnimation;
    private final SpringAnimation offsetYAnimation;

    private float startX, startY, touchX, touchY;

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // The light spot's gradient, made again only when its size or color changes; the finger moves it by matrix
    private RadialGradient glowGradient;
    private float glowSpot;
    private int glowColor;
    private final android.graphics.Matrix glowMatrix = new android.graphics.Matrix();
    private final Path clip = new Path();

    // The library's LiquidButton, where there is one
    private LiquidButtonView button;
    private BlurredBackgroundDrawable buttonGlass;
    private GlassLayer.Backdrop buttonBackdrop;
    private float buttonLeft, buttonTop, buttonWidth, buttonHeight;
    // Whether the button was drawn last time: it has its own highlight then
    private boolean buttonDrawn;

    public LiquidPressEffect(View view) {
        this.view = view;
        if (LiquidButtonView.isSupported() && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
            button = new LiquidButtonView(view.getContext());
            button.setSource(this::drawButtonSource);
            button.setInvalidateListener(() -> {
                view.invalidate();
                restoreClipping();
            });
            // The button needs a window: it goes, out of sight, into the root of the view's own,
            // after the frame being drawn (the root must not get a child while it draws)
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    attachButton();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    button.detach();
                }
            });
            if (view.isAttachedToWindow()) {
                attachButton();
            }
        }
        pressAnimation = spring(press, 0.001f);
        offsetXAnimation = spring(offsetX, 0.5f);
        offsetYAnimation = spring(offsetY, 0.5f);
        // Taken out of the window mid-press, the view gets no end of its gesture: let go
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                cancelPress();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            glowPaint.setBlendMode(BlendMode.PLUS);
        } else {
            glowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        }
    }

    private void attachButton() {
        view.post(() -> {
            if (view.isAttachedToWindow()) {
                button.attach(view);
                view.invalidate();
            }
        });
    }

    /** Whether the library's LiquidButton was drawn last time, so its owner keeps room for its shadow. */
    public boolean isLibraryButton() {
        return isButton() && buttonDrawn;
    }

    /** Whether the library's LiquidButton is drawn (and pressed) for this view right now. */
    private boolean isButton() {
        return button != null && button.isAttachedToWindow();
    }

    /** What is under the button, from the view's glass, with (0, 0) at the button's top-left corner. */
    private void drawButtonSource(Canvas canvas) {
        if (buttonBackdrop != null) {
            buttonBackdrop.draw(canvas);
            return;
        }
        final BlurredBackgroundDrawable glass = buttonGlass;
        if (glass == null) {
            return;
        }
        final BlurredBackgroundSource source = glass.getUnwrappedSource();
        if (source == null) {
            return;
        }
        // The glass's source is laid out like the canvas it is drawn in, moved by its offset
        final float sx = buttonLeft + glass.getSourceOffsetX();
        final float sy = buttonTop + glass.getSourceOffsetY();
        // Pressed, the button swells and leans past its place, and shows what is there (see
        // sourceMargin)
        final float m = sourceMargin(0);
        canvas.save();
        canvas.translate(-sx, -sy);
        source.draw(canvas, sx - m, sy - m, sx + buttonWidth + m, sy + buttonHeight + m);
        canvas.restore();
    }

    /**
     * Draws the library's LiquidButton for the glass (left, top, right, bottom, in the canvas) in
     * place of glass: what is under it comes from the glass's source, and its surface is the
     * glass's tint. False when there is none here (no Liquid Glass, no window yet, a software
     * canvas): the caller draws its glass itself, and {@link #drawGlow} over it.
     */
    public boolean drawGlass(Canvas canvas, float left, float top, float right, float bottom, float radius, BlurredBackgroundDrawable glass, int alpha) {
        return drawGlass(canvas, left, top, right, bottom, radius, glass, alpha, 0);
    }

    /** Like the one above, the glass tinted with a color (a filled button, as the chats list's new message one); 0 for none. */
    public boolean drawGlass(Canvas canvas, float left, float top, float right, float bottom, float radius, BlurredBackgroundDrawable glass, int alpha, int tint) {
        if (!isButton() || glass == null) {
            return false;
        }
        final BlurredBackgroundSource unwrapped = glass.getUnwrappedSource();
        if (unwrapped instanceof org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode) {
            final org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode node = (org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode) unwrapped;
            if (node.inRecording() || node.isRecordingCanvas(canvas)) {
                // Drawn into the very picture it is made of (a story's buttons lie on the story): not there
                return true;
            }
        }
        buttonGlass = glass;
        buttonBackdrop = null;
        // The source records only around glass that has been drawn: the glass, not drawn now,
        // still has to count, as it would on its first draw
        if (glass instanceof org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode && !glass.hasDisplayList()) {
            final BlurredBackgroundSource source = glass.getSource();
            if (source != null) {
                source.dispatchOnDrawablesRelativePositionChange();
            }
            glass.updateDisplayList();
        }
        return drawButton(canvas, left, top, right, bottom, radius, surfaceOf(glass.getBackgroundColor()), tint, alpha);
    }

    /**
     * Like {@link #drawGlass}, with what is under the button from a backdrop drawn from the
     * button's top-left corner, a surface color over the glass and a tint (the filled buttons),
     * 0 for none.
     */
    public boolean drawGlass(Canvas canvas, float left, float top, float right, float bottom, float radius, GlassLayer.Backdrop backdrop, int surfaceColor, int tint, int alpha) {
        if (!isButton()) {
            return false;
        }
        buttonGlass = null;
        buttonBackdrop = backdrop;
        return drawButton(canvas, left, top, right, bottom, radius, surfaceColor, tint, alpha);
    }

    private boolean drawButton(Canvas canvas, float left, float top, float right, float bottom, float radius, int surfaceColor, int tint, int alpha) {
        if (alpha <= 0) {
            return true;
        }
        buttonLeft = left;
        buttonTop = top;
        buttonWidth = right - left;
        buttonHeight = bottom - top;
        button.setSurfaceColor(surfaceColor);
        button.setTint(tint);
        button.setCornerRadius(radius >= Math.min(buttonWidth, buttonHeight) / 2f - 0.5f ? Float.POSITIVE_INFINITY : radius);
        final boolean layer = alpha < 255;
        if (layer) {
            final float o = AndroidUtilities.dp(LiquidButtonView.OVERFLOW_DP);
            canvas.saveLayerAlpha(left - o, top - o, right + o, bottom + o, alpha);
        }
        final boolean drawn = button.drawInto(canvas, left, top, Math.round(buttonWidth), Math.round(buttonHeight));
        if (layer) {
            canvas.restore();
        }
        buttonDrawn = drawn;
        if (drawn) {
            // The library's shadow lies around the button: its parents must not cut it
            unclipParents();
        }
        return drawn;
    }

    /** The glass's tint as the surface of the button: as light as the library's other glass. */
    private static int surfaceOf(int backgroundColor) {
        if (Color.alpha(backgroundColor) == 0) {
            return 0;
        }
        final boolean dark = androidx.core.graphics.ColorUtils.calculateLuminance(backgroundColor | 0xFF000000) < 0.5f;
        final float alpha = Math.min(Color.alpha(backgroundColor) / 255f, org.telegram.liquidglass.BackdropGlass.tintAlpha(dark));
        return androidx.core.graphics.ColorUtils.setAlphaComponent(backgroundColor, (int) (alpha * 255));
    }

    private SpringAnimation spring(FloatValueHolder holder, float threshold) {
        SpringAnimation animation = new SpringAnimation(holder);
        animation.setSpring(new SpringForce().setDampingRatio(DAMPING).setStiffness(STIFFNESS));
        animation.setMinimumVisibleChange(threshold);
        animation.addUpdateListener((a, value, velocity) -> view.invalidate());
        animation.addEndListener((a, canceled, value, velocity) -> restoreClipping());
        return animation;
    }

    // The swelling surface goes past its view; these ancestors stop clipping it meanwhile
    private int unclipLevels;
    private final java.util.ArrayList<android.view.ViewGroup> unclipped = new java.util.ArrayList<>();
    // Groups that also clipped to their padding: a button in a padded container was cut at the
    // padding's edge while it swelled, with clipChildren already off
    private final java.util.ArrayList<android.view.ViewGroup> unclippedPadding = new java.util.ArrayList<>();

    /** Lets the surface swell past its view: the given number of ancestors stop clipping while it is pressed. */
    public void setUnclipParents(int levels) {
        unclipLevels = levels;
    }

    private void unclipParents() {
        if (unclipLevels <= 0 || !unclipped.isEmpty() || !unclippedPadding.isEmpty()) {
            return;
        }
        android.view.ViewParent parent = view.getParent();
        for (int i = 0; i < unclipLevels && parent instanceof android.view.ViewGroup; i++) {
            final android.view.ViewGroup group = (android.view.ViewGroup) parent;
            if (group.getClipChildren()) {
                group.setClipChildren(false);
                unclipped.add(group);
            }
            // Not lists and scrolls: off, their rows would show in the padding while it is pressed
            final boolean scrolls = group instanceof androidx.recyclerview.widget.RecyclerView
                || group instanceof android.widget.AbsListView
                || group instanceof android.widget.ScrollView
                || group instanceof androidx.core.widget.NestedScrollView;
            final boolean padded = group.getPaddingLeft() != 0 || group.getPaddingTop() != 0
                || group.getPaddingRight() != 0 || group.getPaddingBottom() != 0;
            if (padded && !scrolls && group.getClipToPadding()) {
                group.setClipToPadding(false);
                unclippedPadding.add(group);
            }
            parent = group.getParent();
        }
    }

    private void restoreClipping() {
        if (unclipped.isEmpty() && unclippedPadding.isEmpty() || pressAnimation.isRunning() || offsetXAnimation.isRunning() || offsetYAnimation.isRunning() || isActive() && getPressProgress() > 0.01f) {
            return;
        }
        if (isButton() && (button.isActive() || buttonDrawn)) {
            return;
        }
        for (android.view.ViewGroup group : unclipped) {
            group.setClipChildren(true);
            group.invalidate();
        }
        unclipped.clear();
        for (android.view.ViewGroup group : unclippedPadding) {
            group.setClipToPadding(true);
            group.invalidate();
        }
        unclippedPadding.clear();
    }

    public float getPressProgress() {
        if (isButton()) {
            return button.getPressProgress();
        }
        return press.getValue();
    }

    /** Whether the surface is pressed or still springing back, so it has to be drawn transformed. */
    public boolean isActive() {
        if (isButton()) {
            return button.isActive();
        }
        return press.getValue() != 0f || offsetX.getValue() != 0f || offsetY.getValue() != 0f;
    }

    public void onTouchEvent(MotionEvent event) {
        // Only a press starts on whether the view takes clicks: the one under way ends whatever
        // the click made of the view (a button that turns itself off kept swelling, lit)
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && (!view.isEnabled() || !view.isClickable())) {
            return;
        }
        onTouch(event.getActionMasked(), event.getX(), event.getY());
    }

    // The presses under way, each until its gesture ends
    private static final java.util.ArrayList<LiquidPressEffect> touching = new java.util.ArrayList<>();
    private boolean touched;

    /**
     * Lets go of the presses of views in this window still waiting for their gesture's end, as
     * a new gesture starts (call before it is dispatched): when nothing in a view took the first
     * touch, Android sends it no more, and its press stayed swollen and lit.
     */
    public static void releaseStale(View root) {
        for (int i = touching.size() - 1; i >= 0; i--) {
            final LiquidPressEffect effect = touching.get(i);
            if (!effect.view.isAttachedToWindow() || effect.view.getRootView() == root) {
                effect.cancelPress();
            }
        }
    }

    /** Ends a press under way as if the gesture had been cancelled; nothing when there is none. */
    public void cancelPress() {
        if (touched) {
            onTouch(MotionEvent.ACTION_CANCEL, touchX, touchY);
        }
    }

    /**
     * Feeds a touch at x, y of a surface that is only a part of the view (the glass pieces of
     * the action bar); the view's own clickable state does not matter.
     */
    public void onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (!touched) {
                touched = true;
                touching.add(this);
            }
        } else if (!touched) {
            // the rest of a gesture whose press never started, or has ended already
            return;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touched = false;
            touching.remove(this);
        }
        if (isButton()) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    unclipParents();
                    touchX = x;
                    touchY = y;
                    button.press(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    touchX = x;
                    touchY = y;
                    button.move(x, y);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    button.release();
                    break;
            }
            return;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                unclipParents();
                startX = touchX = x;
                startY = touchY = y;
                offsetXAnimation.cancel();
                offsetYAnimation.cancel();
                offsetX.setValue(0);
                offsetY.setValue(0);
                pressAnimation.animateToFinalPosition(1f);
                break;
            case MotionEvent.ACTION_MOVE:
                touchX = x;
                touchY = y;
                offsetXAnimation.cancel();
                offsetYAnimation.cancel();
                offsetX.setValue(touchX - startX);
                offsetY.setValue(touchY - startY);
                view.invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressAnimation.animateToFinalPosition(0f);
                offsetXAnimation.animateToFinalPosition(0f);
                offsetYAnimation.animateToFinalPosition(0f);
                break;
        }
    }

    // The current press transform: translation and scale about the surface's center
    private float curTx, curTy, curScaleX = 1f, curScaleY = 1f;

    /** Works out the press transform for a surface of this size; false when there is none. */
    private boolean compute(float width, float height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (isButton()) {
            // The library's own: LiquidButton's layer
            if (!button.isActive()) {
                return false;
            }
            button.computeMotion(width, height);
            curTx = button.getContentTranslationX();
            curTy = button.getContentTranslationY();
            curScaleX = button.getContentScaleX();
            curScaleY = button.getContentScaleY();
            return true;
        }
        final float progress = press.getValue();
        final float ox = offsetX.getValue(), oy = offsetY.getValue();
        if (progress == 0f && ox == 0f && oy == 0f) {
            return false;
        }
        // The catalog swells its small buttons by 4dp of their height in both directions; a wide
        // button would grow by tens of dp sideways that way, so each side gets its own 4dp
        final float swell = AndroidUtilities.dpf2(4) * progress;
        final float scaleBaseX = 1f + swell / width;
        final float scaleBaseY = 1f + swell / height;

        final float maxOffset = Math.min(width, height);
        final float initialDerivative = 0.05f;
        curTx = maxOffset * (float) Math.tanh(initialDerivative * ox / maxOffset);
        curTy = maxOffset * (float) Math.tanh(initialDerivative * oy / maxOffset);

        // The drag stretch too is 4dp at most along each side, whatever the surface's size
        final float maxStretch = AndroidUtilities.dpf2(4);
        final float maxDimension = Math.max(width, height);
        final double angle = Math.atan2(oy, ox);
        // The stretch grows with the drag up to one size of the surface, then holds
        curScaleX = scaleBaseX + maxStretch / width * Math.min(1f, Math.abs((float) Math.cos(angle) * ox / maxDimension));
        curScaleY = scaleBaseY + maxStretch / height * Math.min(1f, Math.abs((float) Math.sin(angle) * oy / maxDimension));
        return true;
    }

    /** Applies the catalog's LiquidButton layerBlock: swell by 4dp and stretch towards the drag. */
    public void transform(Canvas canvas, float width, float height) {
        if (!compute(width, height)) {
            return;
        }
        canvas.translate(curTx, curTy);
        canvas.scale(curScaleX, curScaleY, width / 2f, height / 2f);
    }

    /**
     * Where the surface (left, top, right, bottom, in its parent) is with the press transform
     * applied. Glass is drawn at that place rather than through {@link #transform}, so it blurs
     * what is under it now instead of stretching the picture taken where it was.
     */
    public void mapRect(float left, float top, float right, float bottom, android.graphics.RectF out) {
        out.set(left, top, right, bottom);
        final float width = right - left, height = bottom - top;
        if (!compute(width, height)) {
            return;
        }
        final float cx = (left + right) / 2f + curTx, cy = (top + bottom) / 2f + curTy;
        final float hw = width * curScaleX / 2f, hh = height * curScaleY / 2f;
        out.set(cx - hw, cy - hh, cx + hw, cy + hh);
    }

    /**
     * How much the corner radii of glass drawn at {@link #mapRect} grow with it. A canvas scale
     * stretched the corners along with the surface; new bounds keep the old radius, and a circle
     * or a capsule whose half size outgrows it turns into a rounded square.
     */
    public static float radiusScale(float width, float height, android.graphics.RectF moved) {
        if (width <= 0 || height <= 0) {
            return 1f;
        }
        return Math.max(1f, Math.max(moved.width() / width, moved.height() / height));
    }

    /**
     * How far around its glass a source that keeps only what is around the glass (the chat's)
     * must keep it: the library's button, pulled, swells and leans past its place and shows what
     * is there, and was empty, flat, past Telegram's own margin.
     */
    public static int sourceMargin(int margin) {
        if (LiquidButtonView.isSupported() && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
            return Math.max(margin, AndroidUtilities.dp(LiquidButtonView.OVERFLOW_DP));
        }
        return margin;
    }

    /**
     * This view's copy of the catalog's InteractiveHighlight: a faint wash and a light spot under
     * the finger. The library's LiquidButton draws its own (see {@link #drawGlass}).
     */
    public void drawGlow(Canvas canvas, float width, float height, float radius) {
        final float progress = getPressProgress();
        if (isButton() && buttonDrawn) {
            return;
        }
        if (progress <= 0f || width <= 0 || height <= 0) {
            return;
        }
        canvas.save();
        clip.rewind();
        clip.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);

        glowPaint.setShader(null);
        glowPaint.setColor(Color.argb((int) (255 * 0.08f * Math.min(progress, 1f)), 255, 255, 255));
        canvas.drawRect(0, 0, width, height, glowPaint);

        final float spot = Math.min(width, height) * 1.5f;
        final float x = Math.max(0, Math.min(width, touchX));
        final float y = Math.max(0, Math.min(height, touchY));
        final int color = Color.argb((int) (255 * 0.15f * Math.min(progress, 1f)), 255, 255, 255);
        if (glowGradient == null || glowSpot != spot || glowColor != color) {
            glowGradient = new RadialGradient(0, 0, spot, new int[]{color, color, 0}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            glowSpot = spot;
            glowColor = color;
        }
        glowMatrix.setTranslate(x, y);
        glowGradient.setLocalMatrix(glowMatrix);
        glowPaint.setShader(glowGradient);
        canvas.drawRect(0, 0, width, height, glowPaint);
        glowPaint.setShader(null);
        canvas.restore();
    }
}
