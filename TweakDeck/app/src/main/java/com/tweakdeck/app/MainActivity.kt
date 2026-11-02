package com.tweakdeck.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var col: LinearLayout

    private val createBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            Thread {
                val result = try {
                    val json = Sx.exportJson(this)
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    "Backup saved"
                } catch (e: Exception) {
                    "Backup failed: ${e.message}"
                }
                runOnUiThread { toast(result) }
            }.start()
        }

    private val pickRestore =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            MaterialAlertDialogBuilder(this)
                .setTitle("Restore settings?")
                .setMessage("Every value in the file that differs from the current one will be written back. Settings that are unsafe to restore (device id, ADB switches) are skipped.")
                .setPositiveButton("Restore") { _, _ ->
                    Thread {
                        val msg = try {
                            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                            val (ok, bad) = Sx.importJson(this, text)
                            "Restored $ok values, $bad failed"
                        } catch (e: Exception) {
                            "Restore failed: ${e.message}"
                        }
                        runOnUiThread { toast(msg) }
                    }.start()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TweakDeck"
        val (sv, c) = page()
        col = c
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        col.removeAllViews()

        col.section("SETUP STATUS")
        val sec = Sx.canSecure(this)
        val sys = Sx.canSystem(this)
        col.body(
            (if (sec) "✔" else "✘") + "  WRITE_SECURE_SETTINGS  (unlocks Secure + Global settings)\n" +
                    (if (sys) "✔" else "✘") + "  WRITE_SETTINGS  (unlocks System settings)"
        )
        if (!sec) {
            col.note("Connect the phone to your PC and run this once:")
            col.mono(Sx.GRANT_SECURE)
            col.button("Copy command") { copyToClipboard("adb", Sx.GRANT_SECURE) }
            col.note("Realme UI: if it says permission denied, turn on Developer options → \"Disable permission monitoring\" and \"USB debugging (Security settings)\", then run it again.")
        }
        if (!sys) {
            col.button("Allow modifying system settings") {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        }

        col.section("CUSTOMISE")
        col.button("Live wallpaper engine") { startActivity(Intent(this, WallpaperActivity::class.java)) }
        col.button("Fonts, display and system tweaks") { startActivity(Intent(this, TweaksActivity::class.java)) }
        col.button("Settings explorer (every key, editable)") { startActivity(Intent(this, ExplorerActivity::class.java)) }
        col.button("ADB command lab (DPI, resolution, debloat)") { startActivity(Intent(this, CommandsActivity::class.java)) }

        col.section("LAUNCHER")
        col.body("TweakDeck Home is a full launcher with icon-pack support, adjustable grid, hidden apps and a dock.")
        col.button("Launcher settings") { startActivity(Intent(this, LauncherSettingsActivity::class.java)) }
        col.button("Make TweakDeck my default launcher") {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                toast("Open Settings → Apps → Default apps → Home app")
            }
        }

        col.section("SAFETY NET")
        col.body("Take a snapshot of every setting before you experiment. Restore it any time.")
        col.button("Back up all settings to a file") { createBackup.launch("tweakdeck-backup.json") }
        col.button("Restore settings from a backup") { pickRestore.launch(arrayOf("application/json", "*/*")) }
        col.button("Undo my last change") { toast(Sx.undo(this)) }
    }
}
