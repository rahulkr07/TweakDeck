package com.tweakdeck.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** A procedural animated wallpaper drawn with Canvas. */
interface Scene {
    fun resize(w: Int, h: Int)

    /** t = animation time in seconds (already speed-scaled), dt = seconds since last frame, tx/ty = tilt -1..1. */
    fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, accent: Int)
}

private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

class StarsScene : Scene {
    private var w = 0
    private var h = 0
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var zs = FloatArray(0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun resize(w: Int, h: Int) {
        this.w = w
        this.h = h
        val n = 240
        xs = FloatArray(n) { Random.nextFloat() * w }
        ys = FloatArray(n) { Random.nextFloat() * h }
        zs = FloatArray(n) { 0.15f + Random.nextFloat() * 0.85f }
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, accent: Int) {
        c.drawColor(Color.rgb(4, 6, 16))
        for (i in xs.indices) {
            ys[i] += dt * zs[i] * 70f
            if (ys[i] > h) {
                ys[i] = 0f
                xs[i] = Random.nextFloat() * w
            }
            val px = xs[i] + tx * zs[i] * 40f
            val py = ys[i] + ty * zs[i] * 40f
            val twinkle = 0.6f + 0.4f * sin(t * 3f + i)
            paint.color = if (i % 7 == 0) withAlpha(accent, (255 * twinkle).toInt()) else Color.argb((255 * twinkle * zs[i]).toInt(), 255, 255, 255)
            c.drawCircle(px, py, 0.8f + zs[i] * 2.2f, paint)
        }
    }
}

class AuroraScene : Scene {
    private var w = 0
    private var h = 0
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shaders: List<Shader> = emptyList()
    private var cachedAccent = 0

    override fun resize(w: Int, h: Int) {
        this.w = w
        this.h = h
        shaders = emptyList()
    }

    private fun buildShaders(accent: Int) {
        cachedAccent = accent
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        shaders = (0 until 4).map { i ->
            val hh = floatArrayOf((hsv[0] + i * 35f) % 360f, 0.7f, 1f)
            val col = Color.HSVToColor(hh)
            LinearGradient(0f, h * 0.15f, 0f, h * 0.85f, withAlpha(col, 170), withAlpha(col, 0), Shader.TileMode.CLAMP)
        }
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, accent: Int) {
        if (shaders.isEmpty() || cachedAccent != accent) buildShaders(accent)
        c.drawColor(Color.rgb(5, 8, 20))
        for (i in shaders.indices) {
            val base = h * (0.28f + 0.1f * i) + ty * 30f
            val amp = h * (0.06f + 0.015f * i)
            val phase = t * (0.35f + 0.12f * i) + i * 1.7f
            path.reset()
            path.moveTo(0f, h.toFloat())
            val steps = 48
            for (s in 0..steps) {
                val x = w * s / steps.toFloat()
                val y = base + amp * sin(2.0 * PI * s / steps * (1.2 + 0.3 * i) + phase).toFloat() + tx * 25f
                path.lineTo(x, y)
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            paint.shader = shaders[i]
            c.drawPath(path, paint)
        }
        paint.shader = null
    }
}

class MatrixScene : Scene {
    private var w = 0
    private var h = 0
    private var colY = FloatArray(0)
    private var colSpeed = FloatArray(0)
    private val cell = 34f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }
    private val chars = "01ABCDEFXYZ$#@%&*+=<>"

    override fun resize(w: Int, h: Int) {
        this.w = w
        this.h = h
        val cols = (w / cell).toInt() + 1
        colY = FloatArray(cols) { Random.nextFloat() * h }
        colSpeed = FloatArray(cols) { 140f + Random.nextFloat() * 260f }
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, accent: Int) {
        c.drawColor(Color.BLACK)
        val trail = 14
        for (i in colY.indices) {
            colY[i] += colSpeed[i] * dt
            if (colY[i] - trail * cell > h) {
                colY[i] = 0f
                colSpeed[i] = 140f + Random.nextFloat() * 260f
            }
            val x = i * cell + tx * 10f
            for (j in 0 until trail) {
                val y = colY[i] - j * cell
                if (y < 0f || y > h) continue
                val ch = chars[((i * 31 + j * 17 + (t * 6f).toInt() * (j + 1)) and 0x7fffffff) % chars.length]
                paint.color = if (j == 0) Color.WHITE else withAlpha(accent, 255 - j * 255 / trail)
                c.drawText(ch.toString(), x, y, paint)
            }
        }
    }
}

class GradientScene : Scene {
    private var w = 0
    private var h = 0
    private val paint = Paint()

    override fun resize(w: Int, h: Int) {
        this.w = w
        this.h = h
    }

    override fun draw(c: Canvas, t: Float, dt: Float, tx: Float, ty: Float, accent: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        val h1 = (hsv[0] + t * 12f) % 360f
        val h2 = (h1 + 70f) % 360f
        val c1 = Color.HSVToColor(floatArrayOf(h1, 0.75f, 0.9f))
        val c2 = Color.HSVToColor(floatArrayOf(h2, 0.8f, 0.45f))
        val a = t * 0.2f
        val cx = w / 2f
        val cy = h / 2f
        val dx = kotlin.math.cos(a) * w
        val dy = kotlin.math.sin(a) * h
        paint.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, c1, c2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }
}
