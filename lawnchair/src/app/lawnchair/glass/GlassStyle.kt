package app.lawnchair.glass

import android.graphics.Color
import app.lawnchair.preferences.PreferenceManager
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/** One place that turns the user's glass settings into LiquidGlassView properties. */
object GlassStyle {
    /**
     * @param bevelDp lens edge width at default strength
     * @param refractionDp refraction depth at default strength
     */
    fun apply(
        view: LiquidGlassView,
        prefs: PreferenceManager,
        density: Float,
        bevelDp: Float = 22f,
        refractionDp: Float = 16f,
    ) {
        val k = 0.4f + 1.2f * prefs.glassRefraction.get()
        view.material = if (prefs.glassClear.get()) GlassMaterial.CLEAR else GlassMaterial.REGULAR
        view.bevelWidth = bevelDp * density * k
        view.refractionHeight = refractionDp * density * k
        view.dispersionStrength = prefs.glassDispersion.get()
        view.enableSensorHighlight = prefs.glassSensorHighlight.get()
        view.enableAdaptiveTint = true
        view.setGlassTint(Color.WHITE, prefs.glassTintStrength.get())
        view.invalidate()
    }

    fun configureSurface(view: LiquidGlassView) {
        view.isClickable = false
        view.isFocusable = false
        view.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        view.enablePressEffect = false
        view.enableDynamicBackground = true
        view.enableAdaptiveTint = true
        view.visibility = android.view.View.INVISIBLE
    }
}
