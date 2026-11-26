package com.ailauncher.data.packages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.ailauncher.domain.model.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Draws icons in the shape chosen in settings, and composes icons on an icon pack's back. */
internal object IconShapes {

    /** The outline of [shape] in a [size] × [size] square (null for [IconShape.SYSTEM]). */
    fun path(shape: IconShape, size: Float): Path? = when (shape) {
        IconShape.SYSTEM -> null
        IconShape.CIRCLE -> Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        IconShape.SQUIRCLE -> superellipse(size, exponent = 4.0)
        IconShape.ROUNDED_SQUARE -> Path().apply {
            val r = size * 0.22f
            addRoundRect(0f, 0f, size, size, r, r, Path.Direction.CW)
        }
        IconShape.TEARDROP -> Path().apply {
            val big = size / 2f
            val small = size * 0.12f
            // Corners: top-left, top-right, bottom-right (the point), bottom-left.
            addRoundRect(0f, 0f, size, size, floatArrayOf(big, big, big, big, small, small, big, big), Path.Direction.CW)
        }
    }

    /** |x|^n + |y|^n = 1, the "squircle" many phones use. */
    private fun superellipse(size: Float, exponent: Double): Path {
        val half = size / 2.0
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val t = 2.0 * Math.PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = half + half * sign(c) * abs(c).pow(2.0 / exponent)
            val y = half + half * sign(s) * abs(s).pow(2.0 / exponent)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }

    /**
     * Adaptive icons get their background and foreground layers clipped to [shape]; any other
     * drawable (legacy icons, icon pack icons) is drawn as it is.
     */
    fun render(drawable: Drawable, shape: IconShape, width: Int, height: Int): Bitmap {
        val path = path(shape, width.toFloat())
        if (path == null || drawable !is AdaptiveIconDrawable) return drawable.toBitmap(width, height)
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        canvas.clipPath(path)
        // Setting the bounds lays the layers out the way the system does (72 of 108 dp visible).
        drawable.setBounds(0, 0, width, height)
        drawable.background?.draw(canvas)
        drawable.foreground?.draw(canvas)
        return out
    }

    /** An app the icon pack doesn't cover: its icon, scaled, on the pack's back, mask and overlay. */
    fun compose(icon: Bitmap, recipe: PackIcon.Composed, width: Int, height: Int): Bitmap {
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        recipe.back.setBounds(0, 0, width, height)
        recipe.back.draw(canvas)
        val layer = createBitmap(width, height)
        Canvas(layer).apply {
            val w = width * recipe.scale
            val h = height * recipe.scale
            val left = (width - w) / 2f
            val top = (height - h) / 2f
            drawBitmap(icon, null, android.graphics.RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            recipe.mask?.let { mask ->
                // The mask's opaque pixels cut the icon away (the convention of ADW-style packs).
                val maskBitmap = mask.toBitmap(width, height)
                drawBitmap(maskBitmap, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
            }
        }
        canvas.drawBitmap(layer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        recipe.upon?.let {
            it.setBounds(0, 0, width, height)
            it.draw(canvas)
        }
        return out
    }
}
