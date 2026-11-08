package org.telegram.liquidglass

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Adaptive rendering: scales the glass's blur radius down under real device heat, and only then.
 *
 * Blur is the one effect whose cost scales with its radius, so it is the only thing reduced; the
 * lens, refraction, dispersion, color controls and highlight stay as they are. Below
 * [PowerManager.THERMAL_STATUS_MODERATE] the scale is 1, i.e. full quality. The system's own
 * thermal status already has hysteresis, so there is no extra debounce here.
 *
 * A change applies the next time a glass surface updates its effect (on its next draw).
 */
object GlassQuality {

    @Volatile
    private var scale = 1f

    /** Multiplier for the blur radius, 0.5..1. */
    @JvmStatic
    val blurScale: Float
        get() = scale

    @JvmStatic
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            scale = scaleFor(pm.currentThermalStatus)
            pm.addThermalStatusListener(context.mainExecutor) { status -> scale = scaleFor(status) }
        } catch (ignore: Throwable) {
            // no thermal info: stay at full quality
        }
    }

    private fun scaleFor(status: Int): Float = when {
        status >= PowerManager.THERMAL_STATUS_CRITICAL -> 0.5f
        status >= PowerManager.THERMAL_STATUS_SEVERE -> 0.6f
        status >= PowerManager.THERMAL_STATUS_MODERATE -> 0.75f
        else -> 1f
    }
}
