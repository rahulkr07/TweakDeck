package com.tweakdeck.app

import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Some changes (display density, resolution, disabling system apps) can only be made by the
 * ADB shell, not by an app. This screen builds the exact commands for your PC.
 */
class CommandsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "ADB command lab"
        val (sv, col) = page()
        setContentView(sv)

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)

        col.section("DISPLAY DENSITY (DPI)")
        col.body("Current: ${dm.densityDpi} dpi on a ${dm.widthPixels}×${dm.heightPixels} screen.")
        col.note("Lower dpi = smaller UI, more on screen. Very low values can make Settings unreadable, so `wm density reset` is below.")
        val densityCmd = col.mono("adb shell wm density ${dm.densityDpi}")
        col.slider("Density", 240, 640, dm.densityDpi, { "$it dpi" }) {
            densityCmd.text = "adb shell wm density $it"
        }
        col.button("Copy density command") { copyToClipboard("adb", densityCmd.text.toString()) }
        col.button("Copy: reset density") { copyToClipboard("adb", "adb shell wm density reset") }

        col.section("RESOLUTION")
        col.note("Native size is ${dm.widthPixels}×${dm.heightPixels}. A lower resolution can save battery and boost game FPS.")
        val sizeCmd = col.mono("adb shell wm size 720x1608")
        col.choice(
            "Preset",
            listOf(
                "HD+  720x1608" to "720x1608",
                "FHD+ 1080x2412" to "1080x2412",
                "Lower 900x2010" to "900x2010"
            ),
            "720x1608"
        ) { sizeCmd.text = "adb shell wm size $it" }
        col.button("Copy resolution command") { copyToClipboard("adb", sizeCmd.text.toString()) }
        col.button("Copy: reset resolution") { copyToClipboard("adb", "adb shell wm size reset") }

        col.section("DEBLOAT / APP CONTROL")
        col.note("Type a package name (for example com.example.bloat). Nothing is run on the phone; you paste the command into your PC terminal.")
        val pkg = EditText(this).apply {
            hint = "package.name"
            setSingleLine()
        }
        col.addView(pkg)
        fun p() = pkg.text.toString().trim().ifEmpty { "<package>" }
        col.button("Copy: remove for current user (reversible)") {
            copyToClipboard("adb", "adb shell pm uninstall -k --user 0 ${p()}")
        }
        col.button("Copy: disable") { copyToClipboard("adb", "adb shell pm disable-user --user 0 ${p()}") }
        col.button("Copy: restore / enable") {
            copyToClipboard("adb", "adb shell cmd package install-existing ${p()}")
        }
        col.button("Copy: revoke a permission") {
            copyToClipboard("adb", "adb shell pm revoke ${p()} android.permission.<PERMISSION>")
        }

        col.section("USEFUL ONE-LINERS")
        oneLiner(col, "List third-party apps", "adb shell pm list packages -3")
        oneLiner(col, "List ALL packages", "adb shell pm list packages")
        oneLiner(col, "Show current foreground app", "adb shell dumpsys window | findstr mCurrentFocus")
        oneLiner(col, "List UI overlays", "adb shell cmd overlay list")
        oneLiner(col, "Battery stats", "adb shell dumpsys battery")
        oneLiner(col, "Take a screenshot to the PC", "adb exec-out screencap -p > screen.png")
        oneLiner(col, "Record the screen (Ctrl+C to stop)", "adb shell screenrecord /sdcard/rec.mp4")
        oneLiner(col, "Set a global setting", "adb shell settings put global <key> <value>")
        oneLiner(col, "Read a setting", "adb shell settings get secure <key>")

        col.section("ONE-TIME PERMISSION GRANTS")
        oneLiner(col, "Unlock Secure + Global settings", Sx.GRANT_SECURE)
        oneLiner(col, "Unlock System settings", Sx.GRANT_SYSTEM)
    }

    private fun oneLiner(col: LinearLayout, label: String, cmd: String) {
        col.body(label, 13f, 0.8f)
        col.mono(cmd)
        col.button("Copy") { copyToClipboard("adb", cmd) }
    }
}
