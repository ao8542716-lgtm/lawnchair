package app.lawnchair.glass

import android.content.Context
import android.graphics.Outline
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.liquidglass.LiquidGlassView

/** iOS-style vertical slider: a glass pill with a white fill that rises with the value. */
class GlassSlider(
    context: Context,
    iconRes: Int,
    initial: Float,
    backdrop: View?,
    private val canStart: () -> Boolean,
    private val onProgress: (Float) -> Unit,
) : FrameLayout(context) {
    val glass = LiquidGlassView(context)
    private val fill = View(context)
    private var progress = initial.coerceIn(0f, 1f)

    init {
        GlassStyle.configureSurface(glass)
        glass.visibility = View.VISIBLE
        glass.backdropSource = backdrop
        addView(glass, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fill.setBackgroundColor(0xE6FFFFFF.toInt())
        addView(fill, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val density = resources.displayMetrics.density
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(0xB3000000.toInt())
        }
        addView(icon, LayoutParams((26 * density).toInt(), (26 * density).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (18 * density).toInt()
        })

        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.width / 2f)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glass.cornerRadius = w / 2f
        fill.pivotX = w / 2f
        fill.pivotY = h.toFloat()
        applyProgress()
        invalidateOutline()
    }

    private fun applyProgress() {
        fill.scaleY = progress
    }

    private fun setFromTouch(y: Float) {
        if (height <= 0) return
        progress = (1f - y / height).coerceIn(0f, 1f)
        applyProgress()
        onProgress(progress)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!canStart()) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                setFromTouch(event.y)
            }
            MotionEvent.ACTION_MOVE -> setFromTouch(event.y)
        }
        return true
    }
}
