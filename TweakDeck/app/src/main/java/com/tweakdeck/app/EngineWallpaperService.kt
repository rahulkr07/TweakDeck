package com.tweakdeck.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.SurfaceHolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * The wallpaper engine. Modes: video, parallax photo, animated scene, solid colour.
 * Everything pauses when the wallpaper is not visible.
 */
class EngineWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = E()

    private inner class E : Engine(), SharedPreferences.OnSharedPreferenceChangeListener, SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = Prefs.engine(this@EngineWallpaperService)
        private val sm = this@EngineWallpaperService.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        private var visible = false
        private var w = 0
        private var h = 0
        private var player: MediaPlayer? = null
        private var scene: Scene? = null
        private var photo: Bitmap? = null
        private var font: Typeface? = null
        private var tx = 0f
        private var ty = 0f
        private var sx = 0f
        private var sy = 0f
        private var xOffset = 0.5f
        private var t = 0f
        private var lastFrame = 0L
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible && isCanvasMode()) handler.postDelayed(this, frameMs())
            }
        }
        private val reloadRunnable = Runnable { reload() }

        private fun mode() = prefs.getString("mode", "scene") ?: "scene"
        private fun isCanvasMode() = mode() != "video"
        private fun frameMs() = (1000 / prefs.getInt("fps", 30).coerceIn(5, 120)).toLong()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
            setOffsetNotificationsEnabled(true)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            reload()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopAll()
            releasePlayer()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) startAll() else stopAll()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int
        ) {
            this.xOffset = xOffset
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            handler.removeCallbacksAndMessages(null)
            stopAll()
            releasePlayer()
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            handler.removeCallbacks(reloadRunnable)
            handler.postDelayed(reloadRunnable, 300)
        }

        // ---- lifecycle ----------------------------------------------------

        private fun reload() {
            if (w == 0 || h == 0) return
            releasePlayer()
            scene = when (prefs.getString("scene", "aurora")) {
                "stars" -> StarsScene()
                "matrix" -> MatrixScene()
                "gradient" -> GradientScene()
                else -> AuroraScene()
            }.also { it.resize(w, h) }
            loadPhoto()
            loadFont()
            lastFrame = 0L
            if (visible) {
                stopAll()
                startAll()
            }
        }

        private fun startAll() {
            if (w == 0 || h == 0) return
            if (mode() == "video") {
                startVideo()
            } else {
                if (mode() == "parallax") {
                    sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                        sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                    }
                }
                lastFrame = 0L
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
        }

        private fun stopAll() {
            handler.removeCallbacks(tick)
            sm.unregisterListener(this)
            runCatching { player?.pause() }
        }

        // ---- video --------------------------------------------------------

        private fun startVideo() {
            player?.let {
                runCatching { it.start() }
                return
            }
            val uriString = prefs.getString("videoUri", null)
            if (uriString == null) {
                drawSolid(Color.rgb(16, 24, 38))
                return
            }
            val p = MediaPlayer()
            try {
                p.setSurface(surfaceHolder.surface)
                p.setDataSource(applicationContext, Uri.parse(uriString))
                p.isLooping = true
                val vol = if (prefs.getBoolean("muted", true)) 0f else 1f
                p.setVolume(vol, vol)
                p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                p.setOnPreparedListener { it.start() }
                p.setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    drawSolid(Color.rgb(40, 10, 10))
                    true
                }
                p.prepareAsync()
                player = p
            } catch (e: Exception) {
                runCatching { p.release() }
                player = null
                drawSolid(Color.rgb(40, 10, 10))
            }
        }

        private fun releasePlayer() {
            runCatching { player?.release() }
            player = null
        }

        // ---- resources ----------------------------------------------------

        private fun loadPhoto() {
            photo = null
            if (mode() != "parallax") return
            val s = prefs.getString("imageUri", null) ?: return
            try {
                val uri = Uri.parse(s)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= w * 1.3f && bounds.outHeight / (sample * 2) >= h * 1.3f) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                photo = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (e: Exception) {
                photo = null
            }
        }

        private fun loadFont() {
            val f = File(filesDir, "clock_font.ttf")
            font = if (f.exists()) runCatching { Typeface.createFromFile(f) }.getOrNull() else null
        }

        // ---- drawing ------------------------------------------------------

        private fun drawSolid(color: Int) {
            val holder = surfaceHolder
            var c: Canvas? = null
            try {
                c = holder.lockCanvas()
                c?.drawColor(color)
            } catch (e: Exception) {
            } finally {
                if (c != null) runCatching { holder.unlockCanvasAndPost(c) }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var c: Canvas? = null
            try {
                c = holder.lockCanvas()
                if (c != null) render(c)
            } catch (e: Exception) {
            } finally {
                if (c != null) runCatching { holder.unlockCanvasAndPost(c) }
            }
        }

        private fun render(c: Canvas) {
            val now = SystemClock.uptimeMillis()
            val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceAtMost(0.2f)
            lastFrame = now
            val speed = prefs.getInt("speed", 100) / 100f
            t += dt * speed
            val accent = prefs.getInt("accent", 0xFF4FC3F7.toInt())

            // smooth the tilt
            sx += (tx - sx) * 0.08f
            sy += (ty - sy) * 0.08f

            when (mode()) {
                "parallax" -> drawParallax(c)
                "color" -> c.drawColor(accent)
                else -> scene?.draw(c, t, dt * speed, sx, sy, accent) ?: c.drawColor(Color.BLACK)
            }

            val dim = prefs.getInt("dim", 0)
            if (dim > 0) c.drawColor(Color.argb(dim * 255 / 100, 0, 0, 0))
            if (prefs.getBoolean("clock", false)) drawClock(c)
        }

        private fun drawParallax(c: Canvas) {
            val b = photo
            if (b == null) {
                c.drawColor(Color.rgb(16, 24, 38))
                return
            }
            val cover = 1.15f
            val scale = max(w * cover / b.width, h * cover / b.height)
            val dw = b.width * scale
            val dh = b.height * scale
            val maxX = (dw - w) / 2f
            val maxY = (dh - h) / 2f
            val shiftX = (sx + (xOffset - 0.5f) * 2f).coerceIn(-1f, 1f)
            val left = -maxX - shiftX * maxX
            val top = -maxY - sy * maxY
            c.save()
            c.translate(left, top)
            c.scale(scale, scale)
            c.drawBitmap(b, 0f, 0f, bitmapPaint)
            c.restore()
        }

        private fun drawClock(c: Canvas) {
            val ctx = this@EngineWallpaperService
            val is24 = DateFormat.is24HourFormat(ctx)
            val fmt = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault())
            val dateFmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            val now = Date()
            textPaint.typeface = font ?: Typeface.DEFAULT_BOLD
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.setShadowLayer(12f, 0f, 4f, 0x99000000.toInt())
            val size = prefs.getInt("clockSize", 120).toFloat()
            val y = h * prefs.getInt("clockY", 22) / 100f
            textPaint.textSize = size
            c.drawText(fmt.format(now), w / 2f, y, textPaint)
            textPaint.textSize = size * 0.22f
            textPaint.setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
            c.drawText(dateFmt.format(now), w / 2f, y + size * 0.3f, textPaint)
        }

        // ---- tilt sensor --------------------------------------------------

        override fun onSensorChanged(e: SensorEvent) {
            tx = (-e.values[0] / 5f).coerceIn(-1f, 1f)
            ty = ((e.values[1] - 6f) / 4f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
}
