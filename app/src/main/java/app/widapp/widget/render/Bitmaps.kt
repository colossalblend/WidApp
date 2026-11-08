package app.widapp.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/** Small bitmaps for things RemoteViews cannot draw natively: gradients and progress rings. */
object Bitmaps {
    private const val MAX = 480

    fun gradient(colors: List<Int>, angleDeg: Float, widthPx: Int, heightPx: Int): Bitmap {
        val ratio = minOf(1f, MAX.toFloat() / maxOf(widthPx, heightPx, 1))
        val w = (widthPx * ratio).toInt().coerceAtLeast(8)
        val h = (heightPx * ratio).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val a = Math.toRadians(angleDeg.toDouble())
        val cx = w / 2f; val cy = h / 2f
        val dx = (cos(a) * w / 2).toFloat(); val dy = (sin(a) * h / 2).toFloat()
        val list = if (colors.size == 1) listOf(colors[0], colors[0]) else colors
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, list.toIntArray(), null, Shader.TileMode.CLAMP)
            isDither = true
        }
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    fun ring(sizePx: Int, thicknessPx: Float, fraction: Float, color: Int, track: Int): Bitmap {
        val s = sizePx.coerceIn(16, MAX)
        val t = thicknessPx * (s.toFloat() / sizePx.coerceAtLeast(1))
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inset = t / 2f + 1f
        val rect = RectF(inset, inset, s - inset, s - inset)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = t; strokeCap = Paint.Cap.ROUND }
        p.color = track
        c.drawArc(rect, 0f, 360f, false, p)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            p.color = color
            c.drawArc(rect, -90f, 360f * f, false, p)
        }
        return bmp
    }
}
