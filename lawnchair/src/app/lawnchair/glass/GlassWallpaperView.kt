package app.lawnchair.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.View
import kotlin.math.max

/** Draws the wallpaper centre-cropped. This is the backdrop every glass surface refracts. */
class GlassWallpaperView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        updateMatrix()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    private fun updateMatrix() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = max(width / b.width.toFloat(), height / b.height.toFloat())
        matrix.setScale(scale, scale)
        matrix.postTranslate((width - b.width * scale) / 2f, (height - b.height * scale) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, matrix, paint) }
    }
}
