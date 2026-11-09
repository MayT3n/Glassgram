package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import androidx.core.graphics.ColorUtils;

import org.telegram.liquidglass.BackdropGlass;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundDrawableRenderNode extends BlurredBackgroundDrawable {
    private final BlurredBackgroundSource source;
    private final Outline outline = new Outline();
    private final Rect outlineRect = new Rect();

    private final RenderNode renderNode;
    private final RenderNode renderNodeFill;

    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean renderNodeInvalidated;
    private int shadowPaintColor;
    private float shadowPaintRadius = Float.NaN, shadowPaintDx, shadowPaintDy;

    public BlurredBackgroundDrawableRenderNode(BlurredBackgroundSource source) {
        this.renderNode = new RenderNode("BlurredNode");
        this.renderNodeFill = new RenderNode("BlurredFill");
        this.renderNode.setClipToOutline(true);
        this.renderNode.setClipToBounds(true);

        this.source = source;

        this.paintShadow.setColor(0);
        this.paintStrokeTop.setStyle(Paint.Style.STROKE);
        this.paintStrokeBottom.setStyle(Paint.Style.STROKE);

        // Every glass surface, frosted ones included, is rendered by the library, like in ward
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
            setLiquidGlassEffectAllowed();
        }
    }

    @Override
    public BlurredBackgroundDrawable setClipToOutline(boolean clipToOutline) {
        renderNode.setClipToOutline(clipToOutline);
        return super.setClipToOutline(clipToOutline);
    }

    /** Liquid Glass rendered by Kyant0/AndroidLiquidGlass; null for plain frosted blur. */
    private BackdropGlass backdropGlass;
    private final float[] glassRadii = new float[4];

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void setLiquidGlassEffectAllowed() {
        if (backdropGlass == null) {
            backdropGlass = new BackdropGlass();
        }
    }

    // glassGram: the source draws a blurred picture already (the photo viewer's), so the glass
    // only bends it instead of blurring it a second time on every frame
    private boolean sourceBlurred;

    public void setSourceBlurred(boolean sourceBlurred) {
        if (this.sourceBlurred != sourceBlurred) {
            this.sourceBlurred = sourceBlurred;
            renderNodeInvalidated = true;
        }
    }


    /*
     * How far around the glass the source must be captured. Telegram's own liquid shader
     * reads only inside the shape, so its screens capture with a few dp of outset; the
     * library's blur and lens read past the edge, and with that outset they ran into the
     * border of the captured copy and showed it as a thin dotted rectangle. Capture as much
     * as frosted blur does.
     */
    private static final int LIBRARY_GLASS_OUTSET_DP = 48;

    @Override
    public int getOutsetX() {
        return backdropGlass != null ? Math.max(super.getOutsetX(), dp(LIBRARY_GLASS_OUTSET_DP)) : super.getOutsetX();
    }

    @Override
    public int getOutsetY() {
        return backdropGlass != null ? Math.max(super.getOutsetY(), dp(LIBRARY_GLASS_OUTSET_DP)) : super.getOutsetY();
    }

    @Override
    public BlurredBackgroundSource getSource() {
        return source;
    }

    @Override
    protected void onBoundPropsChanged() {
        super.onBoundPropsChanged();

        paintStrokeTop.setStrokeWidth(boundProps.strokeWidthTop);
        paintStrokeBottom.setStrokeWidth(boundProps.strokeWidthBottom);

        outlineRect.set(0, 0,
            boundProps.boundsWithPadding.width(),
            boundProps.boundsWithPadding.height()
        );
        getOutline(outline, outlineRect, boundProps.radii);
        outline.setAlpha(1);

        if (!boundProps.boundsWithPadding.isEmpty()) {
            renderNodeFill.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setOutline(outline);

            renderNodeInvalidated = true;
        }
    }

    @Override
    protected void onSourceOffsetChange(float sourceOffsetX, float sourceOffsetY) {
        super.onSourceOffsetChange(sourceOffsetX, sourceOffsetY);
        renderNodeInvalidated = true;
    }

    @Override
    public boolean hasDisplayList() {
        return renderNode.hasDisplayList();
    }

    @Override
    public void updateDisplayList() {
        if (backdropGlass != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Color.alpha(backgroundColor) != 255) {
            updateDisplayListBackdropGlass();
            return;
        }

        final float offsetX = sourceOffsetX;
        final float offsetY = sourceOffsetY;

        Canvas c;

        renderNodeFill.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());

        final float sL = boundProps.boundsWithPadding.left + offsetX;
        final float sT = boundProps.boundsWithPadding.top + offsetY;
        final float sR = boundProps.boundsWithPadding.right + offsetX;
        final float sB = boundProps.boundsWithPadding.bottom + offsetY;

        c = renderNodeFill.beginRecording();
        c.save();
        c.translate(-sL, -sT);
        source.draw(c, sL, sT, sR, sB);
        c.save();
        renderNodeFill.endRecording();


        c = renderNode.beginRecording();
        if (Color.alpha(backgroundColor) == 255) {
            c.drawColor(backgroundColor);
        } else {
            c.drawRenderNode(renderNodeFill);
            if (Color.alpha(backgroundColor) != 0) {
                c.drawColor(backgroundColor);
            }
        }
        drawStrokes(c);
        renderNode.endRecording();
    }

    /**
     * Liquid Glass: the content behind is recorded into {@link #renderNodeFill} with the
     * library's color controls, blur and lens as its RenderEffect, then tinted and outlined
     * with the library's highlight. The fill node is enlarged by the effect padding so the
     * blur and the lens can sample past the glass edge.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private void updateDisplayListBackdropGlass() {
        final int width = boundProps.boundsWithPadding.width();
        final int height = boundProps.boundsWithPadding.height();
        final boolean dark = ColorUtils.calculateLuminance(backgroundColor | 0xFF000000) < 0.5f;

        final int thickness = Math.max(Math.min(
            boundProps.liquidThickness <= 0 ? dp(12) : boundProps.liquidThickness,
            Math.min(width, height) / 4), 1);
        glassRadii[0] = boundProps.shaderRadii[0];
        glassRadii[1] = boundProps.shaderRadii[2];
        glassRadii[2] = boundProps.shaderRadii[4];
        glassRadii[3] = boundProps.shaderRadii[6];
        if (backdropGlass.update(width, height, glassRadii, AndroidUtilities.density,
                thickness, dp(24) * boundProps.liquidIntensity / 0.75f, dark, !sourceBlurred)) {
            renderNodeFill.setRenderEffect(backdropGlass.getRenderEffect());
        }
        final int pad = (int) Math.ceil(backdropGlass.getPadding());

        final float sL = boundProps.boundsWithPadding.left + sourceOffsetX - pad;
        final float sT = boundProps.boundsWithPadding.top + sourceOffsetY - pad;
        final float sR = boundProps.boundsWithPadding.right + sourceOffsetX + pad;
        final float sB = boundProps.boundsWithPadding.bottom + sourceOffsetY + pad;

        renderNodeFill.setPosition(-pad, -pad, width + pad, height + pad);
        Canvas c = renderNodeFill.beginRecording();
        c.translate(-sL, -sT);
        source.draw(c, sL, sT, sR, sB);
        renderNodeFill.endRecording();

        c = renderNode.beginRecording();
        c.drawRenderNode(renderNodeFill);
        final float tintAlpha = Math.min(Color.alpha(backgroundColor) / 255f, BackdropGlass.tintAlpha(dark));
        if (tintAlpha > 0) {
            c.drawColor(ColorUtils.setAlphaComponent(backgroundColor, (int) (tintAlpha * 255)));
        }
        backdropGlass.drawHighlight(c);
        renderNode.endRecording();
    }

    private void drawStrokes(Canvas c) {
        if (strokeColorTop != 0) {
            drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                    boundProps.boundsWithPadding.height(), boundProps.radii,
                    boundProps.strokeWidthTop, true, paintStrokeTop);
        }
        if (strokeColorBottom != 0) {
            drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                    boundProps.boundsWithPadding.height(), boundProps.radii,
                    boundProps.strokeWidthBottom, false, paintStrokeBottom);
        }
    }

    @Override
    public void updateColors() {
        super.updateColors();

        paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, shadowColor);
        // the paint no longer holds what draw() last set
        shadowPaintRadius = Float.NaN;
        paintStrokeTop.setColor(strokeColorTop);
        paintStrokeBottom.setColor(strokeColorBottom);

        renderNodeInvalidated = true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundProps.boundsWithPadding.isEmpty()) {
            return;
        }

        if (!canvas.isHardwareAccelerated()) {
            drawSource(canvas, source);
            return;
        }

        if (!renderNode.hasDisplayList()) {
            source.dispatchOnDrawablesRelativePositionChange();
            updateDisplayList();
        } else if (renderNodeInvalidated) {
            updateDisplayList();
        }
        renderNodeInvalidated = false;

        int color = Theme.multAlpha(shadowColor, renderNode.getAlpha() * shadowAlpha);
        if (Color.alpha(color) != 0) {
            if (color != shadowPaintColor || shadowLayerRadius != shadowPaintRadius || shadowLayerDx != shadowPaintDx || shadowLayerDy != shadowPaintDy) {
                // each call makes a native looper: only when something changed
                shadowPaintColor = color;
                shadowPaintRadius = shadowLayerRadius;
                shadowPaintDx = shadowLayerDx;
                shadowPaintDy = shadowLayerDy;
                paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, color);
            }
            boundProps.drawShadows(canvas, paintShadow, inAppKeyboardOptimization);
        }

        canvas.save();
        canvas.translate(boundProps.boundsWithPadding.left, boundProps.boundsWithPadding.top);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
    }

    public void invalidateDisplayList() {
        renderNodeInvalidated = true;
    }

    @Override
    public void setAlpha(int alpha) {
        final int oldAlpha = getAlpha();

        super.setAlpha(alpha);
        renderNode.setAlpha(alpha / 255f);

        // What is recorded does not depend on the alpha (the node applies it when drawn), so a
        // fade does not record the glass for each of its frames. Only coming back from invisible,
        // when the window behind may have changed unseen, records it anew
        if (oldAlpha == 0 && alpha > 0) {
            renderNodeInvalidated = true;
            source.dispatchOnDrawablesRelativePositionChange();
        }
    }

    @Override
    protected void onSourceRelativePositionChanged(RectF position) {
        super.onSourceRelativePositionChanged(position);
        source.dispatchOnDrawablesRelativePositionChange();
    }
}
