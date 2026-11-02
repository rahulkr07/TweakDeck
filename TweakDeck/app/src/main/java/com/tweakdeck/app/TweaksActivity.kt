package com.tweakdeck.app

import android.os.Bundle
import android.widget.LinearLayout
import java.util.Locale
import kotlin.math.roundToInt

class TweaksActivity : BaseActivity() {

    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "System tweaks"
        val (sv, c) = page()
        col = c
        setContentView(sv)

        if (!Sx.canSecure(this)) {
            col.note("Secure/Global tweaks need: ${Sx.GRANT_SECURE}")
        }
        if (!Sx.canSystem(this)) {
            col.note("System tweaks need: Allow modifying system settings (hub screen) or ${Sx.GRANT_SYSTEM}")
        }

        col.section("FONTS & DISPLAY")
        col.note("Realme UI only lets the Themes store replace the system font itself. Font size and weight below work everywhere.")
        floatSlider("Font size", Sx.Table.SYSTEM, "font_scale", 0.5f, 2.0f, 0.05f, 1.0f)
        intSlider("Bold text strength", Sx.Table.SECURE, "font_weight_adjustment", 0, 500, 50, 0)
        toggle("Auto-rotate screen", Sx.Table.SYSTEM, "accelerometer_rotation")
        pick("Clock format", Sx.Table.SYSTEM, "time_12_24", listOf("Follow system" to "", "12-hour" to "12", "24-hour" to "24"))
        pick(
            "Minimum refresh rate (device dependent)", Sx.Table.SYSTEM, "min_refresh_rate",
            listOf("Default" to "0.0", "60 Hz" to "60.0", "90 Hz" to "90.0", "120 Hz" to "120.0")
        )
        pick(
            "Peak refresh rate (device dependent)", Sx.Table.SYSTEM, "peak_refresh_rate",
            listOf("Default" to "0.0", "60 Hz" to "60.0", "90 Hz" to "90.0", "120 Hz" to "120.0")
        )

        col.section("ANIMATIONS")
        floatSlider("Window animation scale", Sx.Table.GLOBAL, "window_animation_scale", 0f, 2f, 0.25f, 1f)
        floatSlider("Transition animation scale", Sx.Table.GLOBAL, "transition_animation_scale", 0f, 2f, 0.25f, 1f)
        floatSlider("Animator duration scale", Sx.Table.GLOBAL, "animator_duration_scale", 0f, 2f, 0.25f, 1f)

        col.section("SCREEN")
        toggle("Adaptive brightness", Sx.Table.SYSTEM, "screen_brightness_mode")
        intSlider("Brightness (turn adaptive off first)", Sx.Table.SYSTEM, "screen_brightness", 1, 255, 1, 128)
        pick(
            "Screen timeout", Sx.Table.SYSTEM, "screen_off_timeout",
            listOf(
                "15 seconds" to "15000", "30 seconds" to "30000", "1 minute" to "60000",
                "2 minutes" to "120000", "5 minutes" to "300000", "10 minutes" to "600000", "30 minutes" to "1800000"
            )
        )
        pick(
            "Stay awake while charging", Sx.Table.GLOBAL, "stay_on_while_plugged_in",
            listOf("Never" to "0", "AC" to "1", "USB" to "2", "AC + USB" to "3", "AC + USB + wireless" to "7")
        )

        col.section("TOUCH & INPUT")
        toggle("Show taps", Sx.Table.SYSTEM, "show_touches")
        toggle("Pointer location overlay", Sx.Table.SYSTEM, "pointer_location")
        pick(
            "Touch & hold delay", Sx.Table.SECURE, "long_press_timeout",
            listOf("Short (300 ms)" to "300", "Medium (400 ms)" to "400", "Long (500 ms)" to "500", "Longer (1000 ms)" to "1000")
        )

        col.section("SOUND & HAPTICS")
        toggle("Touch vibration", Sx.Table.SYSTEM, "haptic_feedback_enabled")
        toggle("Touch sounds", Sx.Table.SYSTEM, "sound_effects_enabled")
        toggle("Lock screen sounds", Sx.Table.SYSTEM, "lockscreen_sounds_enabled")
        toggle("Vibrate when ringing", Sx.Table.SYSTEM, "vibrate_when_ringing")

        col.section("ACCESSIBILITY & COLOUR")
        toggle("Colour inversion", Sx.Table.SECURE, "accessibility_display_inversion_enabled")
        toggle("High contrast text", Sx.Table.SECURE, "high_text_contrast_enabled")
        toggle("Colour correction", Sx.Table.SECURE, "accessibility_display_daltonizer_enabled")
        pick(
            "Colour correction mode", Sx.Table.SECURE, "accessibility_display_daltonizer",
            listOf("Greyscale" to "0", "Red-weak (protanomaly)" to "11", "Green-weak (deuteranomaly)" to "12", "Blue-weak (tritanomaly)" to "13")
        )

        col.section("DEVELOPER & NETWORK")
        toggle("Force activities to be resizable", Sx.Table.GLOBAL, "force_resizable_activities")
        pick(
            "Private DNS", Sx.Table.GLOBAL, "private_dns_mode",
            listOf("Off" to "off", "Automatic" to "opportunistic", "Hostname (set below)" to "hostname")
        )
        col.note("Set the private DNS hostname in Settings explorer → Global → private_dns_specifier.")

        col.section("MORE")
        col.note("Anything not listed here is in Settings explorer: every key in System, Secure and Global can be viewed and changed.")
    }

    private fun apply(t: Sx.Table, key: String, v: String) {
        val err = Sx.putTracked(this, t, key, v)
        if (err != null) toast(err)
    }

    private fun toggle(title: String, t: Sx.Table, key: String) {
        col.switchRow(title, Sx.get(this, t, key) == "1") { apply(t, key, if (it) "1" else "0") }
    }

    private fun pick(title: String, t: Sx.Table, key: String, options: List<Pair<String, String>>) {
        col.choice(title, options, Sx.get(this, t, key)) { apply(t, key, it) }
    }

    private fun floatSlider(title: String, t: Sx.Table, key: String, lo: Float, hi: Float, step: Float, def: Float) {
        val n = ((hi - lo) / step).roundToInt()
        val cur = Sx.get(this, t, key)?.toFloatOrNull() ?: def
        val idx = ((cur - lo) / step).roundToInt().coerceIn(0, n)
        col.slider(title, 0, n, idx, { String.format(Locale.US, "%.2f", lo + it * step) }) {
            apply(t, key, String.format(Locale.US, "%.2f", lo + it * step))
        }
    }

    private fun intSlider(title: String, t: Sx.Table, key: String, lo: Int, hi: Int, step: Int, def: Int) {
        val n = (hi - lo) / step
        val cur = Sx.get(this, t, key)?.toIntOrNull() ?: def
        val idx = ((cur - lo) / step.toFloat()).roundToInt().coerceIn(0, n)
        col.slider(title, 0, n, idx, { (lo + it * step).toString() }) {
            apply(t, key, (lo + it * step).toString())
        }
    }
}
