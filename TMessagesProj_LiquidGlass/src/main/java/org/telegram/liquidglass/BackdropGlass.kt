package org.telegram.liquidglass

import android.graphics.BlurMaskFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.ceil
import kotlin.math.min

/**
 * Liquid Glass of one Telegram glass surface, built with Kyant0/AndroidLiquidGlass.
 *
 * The look follows shashachkaaa/ward: the backdrop gets the library's color controls
 * (contrast/saturation tuned per theme), blur and edge lens, and the outline gets the
 * library's ambient highlight. Small surfaces (bars, pills, buttons) refract with
 * dispersion; large ones (sheets, menus) use the wider, dispersion-free panel lens with
 * the depth effect, like ward's `glassPanel`.
 *
 * The caller owns the RenderNodes: it sets [renderEffect] on the node that records the
 * content behind the glass, enlarges that node by [padding] on every side, and calls
 * [drawHighlight] after the tint.
 */
class BackdropGlass {

    private val scope: Any = GlassEffectScope.create()
    private val drawScope = CanvasDrawScope()
    private val emptyCanvas = Canvas(android.graphics.Canvas())

    private val highlightPaint = Paint().apply { style = PaintingStyle.Stroke }
    private val clipPath = Path()
    private var highlightBlurRadius = -1f

    private var width = -1f
    private var height = -1f
    private var radii = FloatArray(4)
    private var density = 0f
    private var colorMode = -1
    private var blurRadius = -1f
    private var lensHeight = -1f
    private var lensAmount = -1f
    private var depthEffect = false
    private var chromaticAberration = false
    private var dark = false
    private var built = false

    private var shape = AbsoluteRoundedCornerShape(0f)

    /** Effect for the node that records the content behind the glass, or null below Android 12. */
    var renderEffect: RenderEffect? = null
        private set

    /** How far the recorded content has to extend past the glass bounds, in px. */
    var padding: Float = 0f
        private set

    /**
     * Glass surface preset (bars, pills, buttons, sheets): color controls for the theme,
     * blur and an edge lens, following ward's `glassBackground` / `glassPanel`.
     *
     * @param radii corner radii in px: top-left, top-right, bottom-right, bottom-left
     * @param refractionHeight width of the refracting band at the edge, px
     * @param refractionAmount how far the band bends the backdrop, px
     * @param dark whether the glass sits on a dark theme
     * @param blur false when the content behind is blurred already, so only the lens bends it
     * @return true if [renderEffect] or [padding] changed
     */
    @JvmOverloads
    fun update(
        width: Float,
        height: Float,
        radii: FloatArray,
        density: Float,
        refractionHeight: Float,
        refractionAmount: Float,
        dark: Boolean,
        blur: Boolean = true
    ): Boolean {
        val panel = min(width, height) >= PanelMinSize * density
        return updateEffect(
            width, height, radii, density,
            if (dark) COLOR_CONTROLS_DARK else COLOR_CONTROLS_LIGHT,
            if (blur) (if (panel) PanelBlurRadius else BlurRadius) * density else 0f,
            if (panel) refractionHeight * 2f else refractionHeight,
            refractionAmount,
            depthEffect = panel,
            chromaticAberration = !panel,
            dark = dark
        )
    }

    /**
     * Builds the effect chain in the order the library requires: color filter, blur, lens.
     * Zero [blurRadius] or lens values skip that effect.
     *
     * @param colorMode one of [COLOR_NONE], [COLOR_CONTROLS_DARK], [COLOR_CONTROLS_LIGHT], [COLOR_VIBRANCY]
     * @return true if [renderEffect] or [padding] changed
     */
    fun updateEffect(
        width: Float,
        height: Float,
        radii: FloatArray,
        density: Float,
        colorMode: Int,
        blurRadius: Float,
        lensHeight: Float,
        lensAmount: Float,
        depthEffect: Boolean,
        chromaticAberration: Boolean,
        dark: Boolean
    ): Boolean {
        // Adaptive rendering: the blur radius shrinks only under real thermal pressure
        val effectiveBlur = blurRadius * GlassQuality.blurScale
        if (built &&
            this.width == width && this.height == height &&
            this.radii.contentEquals(radii) && this.density == density &&
            this.colorMode == colorMode && this.blurRadius == effectiveBlur &&
            this.lensHeight == lensHeight && this.lensAmount == lensAmount &&
            this.depthEffect == depthEffect && this.chromaticAberration == chromaticAberration &&
            this.dark == dark
        ) {
            return false
        }
        this.width = width
        this.height = height
        this.radii = radii.copyOf()
        this.density = density
        this.colorMode = colorMode
        this.blurRadius = effectiveBlur
        this.lensHeight = lensHeight
        this.lensAmount = lensAmount
        this.depthEffect = depthEffect
        this.chromaticAberration = chromaticAberration
        this.dark = dark
        built = true

        shape = AbsoluteRoundedCornerShape(radii[0], radii[1], radii[2], radii[3])
        val size = Size(width, height)
        drawScope.draw(Density(density), LayoutDirection.Ltr, emptyCanvas, size) {
            GlassEffectScope.update(scope, this)
        }
        GlassEffectScope.setShape(scope, shape)
        GlassEffectScope.reset(scope)

        with(GlassEffectScope.asScope(scope)) {
            // Under the glass there is the app's own background: on dark themes the blurred
            // copy turns into a flat grey, so lift and stretch it; on light ones tone it
            // down, otherwise the glass washes out to white.
            when (colorMode) {
                COLOR_CONTROLS_DARK -> colorControls(brightness = 0.03f, contrast = 1.12f, saturation = 1.15f)
                COLOR_CONTROLS_LIGHT -> colorControls(brightness = -0.03f, contrast = 1.06f, saturation = 1.5f)
                COLOR_VIBRANCY -> vibrancy()
            }
            if (effectiveBlur > 0f) {
                blur(effectiveBlur)
            }
            if (lensHeight > 0f && lensAmount > 0f) {
                lens(lensHeight, lensAmount, depthEffect = depthEffect, chromaticAberration = chromaticAberration)
            }
        }

        val scopeApi = GlassEffectScope.asScope(scope)
        renderEffect = scopeApi.renderEffect
        padding = scopeApi.padding
        return true
    }

    /** Draws the library's ambient highlight along the glass outline, at (0, 0). */
    fun drawHighlight(canvas: android.graphics.Canvas) {
        drawHighlight(canvas, if (dark) 0.7f else 1f, 1f)
    }

    /**
     * Draws the library's ambient highlight along the glass outline, at (0, 0).
     *
     * @param alpha highlight opacity
     * @param widthScale scale of the highlight width and blur, like `Highlight.Ambient.width / 1.5f` in the catalog toggle
     */
    fun drawHighlight(canvas: android.graphics.Canvas, alpha: Float, widthScale: Float) {
        drawHighlight(canvas, alpha, widthScale, true)
    }

    /**
     * Draws the library's highlight along the glass outline, at (0, 0).
     *
     * @param ambient `Highlight.Ambient` if true, otherwise `Highlight.Default` (the one
     *   `drawBackdrop` uses unless told otherwise, as on the catalog's LiquidButton)
     */
    fun drawHighlight(canvas: android.graphics.Canvas, alpha: Float, widthScale: Float, ambient: Boolean) {
        if (!built || width <= 0f || height <= 0f || alpha <= 0f || !canvas.isHardwareAccelerated) return
        val base = if (ambient) Highlight.Ambient else Highlight.Default
        val highlight = base.copy(
            width = base.width * widthScale,
            blurRadius = base.blurRadius * widthScale,
            alpha = alpha.coerceIn(0f, 1f)
        )
        val size = Size(width, height)
        drawScope.draw(Density(density), LayoutDirection.Ltr, Canvas(canvas), size) {
            val outline = shape.createOutline(size, layoutDirection, this)

            highlightPaint.color = highlight.style.color
            highlightPaint.alpha = highlight.style.color.alpha * highlight.alpha
            highlightPaint.blendMode = highlight.style.blendMode
            highlightPaint.strokeWidth = ceil(min(highlight.width.toPx(), size.minDimension / 2f)) * 2f
            val blurRadius = highlight.blurRadius.toPx()
            if (blurRadius != highlightBlurRadius) {
                highlightBlurRadius = blurRadius
                highlightPaint.asFrameworkPaint().maskFilter =
                    if (blurRadius > 0f) BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL) else null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                highlightPaint.shader = with(highlight.style) {
                    createShader(shape, GlassEffectScope.asShaderCache(scope))
                }
            }

            val c = drawContext.canvas
            c.save()
            when (outline) {
                is Outline.Rounded -> {
                    clipPath.reset()
                    clipPath.addRoundRect(outline.roundRect)
                    c.clipPath(clipPath)
                }
                is Outline.Rectangle -> c.clipRect(outline.rect)
                is Outline.Generic -> c.clipPath(outline.path)
            }
            c.drawOutline(outline, highlightPaint)
            c.restore()
        }
    }

    companion object {
        /** Blur radius under bars, pills and buttons, dp. */
        const val BlurRadius = 8f

        /** Blur radius under sheets and menus, dp: they cover content that should stay readable through them. */
        const val PanelBlurRadius = 6f

        /** Surfaces at least this tall and wide are treated as panels, dp. */
        const val PanelMinSize = 120f

        /** Blur and color controls need RenderEffect (Android 12); the lens needs RuntimeShader (Android 13). */
        @JvmStatic
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        const val COLOR_NONE = 0
        const val COLOR_CONTROLS_DARK = 1
        const val COLOR_CONTROLS_LIGHT = 2
        const val COLOR_VIBRANCY = 3

        /** Alpha of the tint over the glass, so the backdrop stays visible like in ward. */
        @JvmStatic
        fun tintAlpha(dark: Boolean): Float = if (dark) 0.35f else 0.45f
    }
}

