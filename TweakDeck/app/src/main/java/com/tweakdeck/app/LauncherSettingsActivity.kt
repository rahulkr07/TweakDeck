package com.tweakdeck.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LauncherSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Launcher settings"
        val (sv, col) = page()
        setContentView(sv)
        val p = Prefs.launcher(this)

        col.section("GRID")
        col.slider("Columns", 3, 8, p.getInt("cols", 4)) { p.edit().putInt("cols", it).apply() }
        col.slider("Icon size", 36, 100, p.getInt("iconSize", 56), { "$it dp" }) { p.edit().putInt("iconSize", it).apply() }
        col.switchRow("Show app names", p.getBoolean("labels", true)) { p.edit().putBoolean("labels", it).apply() }
        col.slider("Dark overlay behind apps", 0, 80, p.getInt("scrim", 20), { "$it%" }) { p.edit().putInt("scrim", it).apply() }

        col.section("ICON PACK")
        val current = p.getString("iconpack", null)
        col.body("Current: ${current ?: "system icons"}")
        col.button("Choose icon pack") {
            val packs = IconPacks.list(packageManager)
            if (packs.isEmpty()) {
                toast("No icon packs installed. Install any ADW/Nova-compatible pack from the Play Store.")
            } else {
                val names = (listOf("System icons") + packs.map { it.second }).toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Icon pack")
                    .setItems(names) { _, which ->
                        if (which == 0) p.edit().remove("iconpack").apply()
                        else p.edit().putString("iconpack", packs[which - 1].first).apply()
                        toast("Applied. Go home to see it.")
                        recreate()
                    }
                    .show()
            }
        }

        col.section("APPS")
        col.button("Unhide all apps") { p.edit().remove("hidden").apply(); toast("All apps visible") }
        col.button("Clear dock") { p.edit().remove("dock").apply(); toast("Dock cleared") }
        col.note("Long-press any app on the home screen to hide it, add it to the dock, open app info, or uninstall it.")

        col.section("DEFAULT LAUNCHER")
        col.button("Choose default home app") {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                toast("Open Settings → Apps → Default apps → Home app")
            }
        }
    }
}
