package app.lawnchair.glass

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.DragLayer
import com.example.liquidglass.LiquidGlassView

/**
 * Keeps a [LiquidGlassView] glued to a rectangle inside another view, wherever that view is on
 * screen right now (page scroll, open animation, hotseat slide). The glass is laid out once at
 * the rectangle's own size, then moved and scaled with translation, so following costs no layout.
 */
class GlassFollower(private val dragLayer: DragLayer, val glass: LiquidGlassView) {
    private val point = FloatArray(2)
    private var baseW = 0
    private var baseH = 0
    private var baseRadius = -1f

    /** [local] is the rectangle in [target]'s own coordinates; [radius] is in the same units. */
    fun follow(target: View, local: Rect, radius: Float, alpha: Float = 1f) {
        val w = local.width()
        val h = local.height()
        if (w <= 0 || h <= 0) {
            hide()
            return
        }
        if (w != baseW || h != baseH) {
            baseW = w
            baseH = h
            val lp = glass.layoutParams as InsettableFrameLayout.LayoutParams
            lp.width = w
            lp.height = h
            lp.leftMargin = 0
            lp.topMargin = 0
            glass.layoutParams = lp
        }
        if (radius != baseRadius) {
            baseRadius = radius
            glass.cornerRadius = radius
        }
        point[0] = local.left.toFloat()
        point[1] = local.top.toFloat()
        val scale = Utilities.getDescendantCoordRelativeToAncestor(target, dragLayer, point, false)
        glass.pivotX = 0f
        glass.pivotY = 0f
        glass.translationX = point[0]
        glass.translationY = point[1]
        glass.scaleX = scale
        glass.scaleY = scale
        glass.alpha = alpha
        if (glass.visibility != View.VISIBLE) glass.visibility = View.VISIBLE
    }

    fun hide() {
        if (glass.visibility != View.INVISIBLE) glass.visibility = View.INVISIBLE
    }

    companion object {
        fun layoutParams() = InsettableFrameLayout.LayoutParams(1, 1).apply {
            ignoreInsets = true
            gravity = Gravity.TOP or Gravity.START
        }
    }
}
