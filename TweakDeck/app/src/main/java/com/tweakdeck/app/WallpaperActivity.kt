package com.tweakdeck.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/** Settings for the live wallpaper engine. Also opened from the system wallpaper picker. */
class WallpaperActivity : BaseActivity() {

    private val prefs by lazy { Prefs.engine(this) }
    private lateinit var status: TextView

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keep(uri)
            prefs.edit().putString("videoUri", uri.toString()).putString("mode", "video").apply()
            refreshStatus()
        }
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keep(uri)
            prefs.edit().putString("imageUri", uri.toString()).putString("mode", "parallax").apply()
            refreshStatus()
        }
    }
    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val dest = File(filesDir, "clock_font.ttf")
                contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                Typeface.createFromFile(dest)
                prefs.edit().putLong("fontStamp", System.currentTimeMillis()).putBoolean("clock", true).apply()
                toast("Font loaded")
            } catch (e: Exception) {
                toast("That file is not a usable font")
            }
            refreshStatus()
        }
    }

    private fun keep(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Live wallpaper engine"
        val (sv, col) = page()
        setContentView(sv)

        status = col.body("", 13f, 0.8f)
        col.button("Apply as my live wallpaper") { applyWallpaper() }

        col.section("SOURCE")
        col.choice(
            "Wallpaper type",
            listOf(
                "Animated scene" to "scene",
                "Video" to "video",
                "Parallax photo (tilts with the phone)" to "parallax",
                "Solid colour" to "color"
            ),
            prefs.getString("mode", "scene")
        ) { prefs.edit().putString("mode", it).apply(); refreshStatus() }
        col.choice(
            "Animated scene",
            listOf("Aurora" to "aurora", "Starfield" to "stars", "Matrix rain" to "matrix", "Colour flow" to "gradient"),
            prefs.getString("scene", "aurora")
        ) { prefs.edit().putString("scene", it).putString("mode", "scene").apply(); refreshStatus() }
        col.button("Pick a video (MP4 / WebM)") { pickVideo.launch(arrayOf("video/*")) }
        col.button("Pick a photo for parallax") { pickImage.launch(arrayOf("image/*")) }

        col.section("LOOK")
        col.choice("Accent colour", accentOptions, prefs.getInt("accent", 0xFF4FC3F7.toInt()).toString()) {
            prefs.edit().putInt("accent", it.toInt()).apply()
        }
        col.slider("Dim", 0, 80, prefs.getInt("dim", 0), { "$it%" }) { prefs.edit().putInt("dim", it).apply() }
        col.slider("Animation speed", 25, 300, prefs.getInt("speed", 100), { "$it%" }) {
            prefs.edit().putInt("speed", it).apply()
        }
        col.note("Dim applies to scenes and parallax. Video plays at full brightness.")

        col.section("VIDEO")
        col.switchRow("Play video sound", !prefs.getBoolean("muted", true)) {
            prefs.edit().putBoolean("muted", !it).apply()
        }
        col.note("The video is paused automatically whenever the wallpaper isn't visible, which saves battery.")

        col.section("BATTERY")
        col.slider("Frame rate (scenes and parallax)", 10, 60, prefs.getInt("fps", 30), { "$it fps" }) {
            prefs.edit().putInt("fps", it).apply()
        }

        col.section("CLOCK OVERLAY (scenes and parallax)")
        col.switchRow("Show clock on wallpaper", prefs.getBoolean("clock", false)) {
            prefs.edit().putBoolean("clock", it).apply()
        }
        col.slider("Clock size", 40, 220, prefs.getInt("clockSize", 120), { "$it" }) {
            prefs.edit().putInt("clockSize", it).apply()
        }
        col.slider("Clock vertical position", 5, 80, prefs.getInt("clockY", 22), { "$it%" }) {
            prefs.edit().putInt("clockY", it).apply()
        }
        col.button("Load clock font (.ttf / .otf)") { pickFont.launch(arrayOf("*/*")) }
        col.button("Use default clock font") {
            File(filesDir, "clock_font.ttf").delete()
            prefs.edit().putLong("fontStamp", System.currentTimeMillis()).apply()
            refreshStatus()
        }

        refreshStatus()
    }

    private val accentOptions = listOf(
        "Sky blue" to 0xFF4FC3F7.toInt().toString(),
        "Mint" to 0xFF69F0AE.toInt().toString(),
        "Amber" to 0xFFFFC107.toInt().toString(),
        "Coral" to 0xFFFF6E6E.toInt().toString(),
        "Violet" to 0xFFB388FF.toInt().toString(),
        "Pink" to 0xFFFF80AB.toInt().toString(),
        "White" to Color.WHITE.toString()
    )

    private fun refreshStatus() {
        val mode = prefs.getString("mode", "scene")
        val video = prefs.getString("videoUri", null)
        val image = prefs.getString("imageUri", null)
        val font = File(filesDir, "clock_font.ttf").exists()
        status.text = "Mode: $mode\nVideo: ${if (video != null) "selected" else "none"}   " +
                "Photo: ${if (image != null) "selected" else "none"}   Custom font: ${if (font) "loaded" else "default"}"
    }

    private fun applyWallpaper() {
        val comp = ComponentName(this, EngineWallpaperService::class.java)
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp)
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                toast("Open Settings → Wallpaper → Live wallpapers and choose TweakDeck")
            }
        }
    }
}
