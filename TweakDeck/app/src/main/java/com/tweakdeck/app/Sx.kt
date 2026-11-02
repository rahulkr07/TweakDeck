package com.tweakdeck.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

/** Access to the three Android settings tables. Secure/Global need WRITE_SECURE_SETTINGS (granted via ADB). */
object Sx {
    enum class Table(val label: String, val uri: Uri) {
        SYSTEM("System", Settings.System.CONTENT_URI),
        SECURE("Secure", Settings.Secure.CONTENT_URI),
        GLOBAL("Global", Settings.Global.CONTENT_URI)
    }

    const val PKG = "com.tweakdeck.app"
    const val GRANT_SECURE = "adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
    const val GRANT_SYSTEM = "adb shell appops set $PKG WRITE_SETTINGS allow"

    fun canSecure(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun canSystem(c: Context): Boolean = Settings.System.canWrite(c)

    fun get(c: Context, t: Table, key: String): String? = try {
        when (t) {
            Table.SYSTEM -> Settings.System.getString(c.contentResolver, key)
            Table.SECURE -> Settings.Secure.getString(c.contentResolver, key)
            Table.GLOBAL -> Settings.Global.getString(c.contentResolver, key)
        }
    } catch (e: Exception) {
        null
    }

    /** Returns null on success, or an error message. */
    fun put(c: Context, t: Table, key: String, value: String): String? = try {
        val ok = when (t) {
            Table.SYSTEM -> Settings.System.putString(c.contentResolver, key, value)
            Table.SECURE -> Settings.Secure.putString(c.contentResolver, key, value)
            Table.GLOBAL -> Settings.Global.putString(c.contentResolver, key, value)
        }
        if (ok) null else "The system refused to write $key"
    } catch (e: SecurityException) {
        when (t) {
            Table.SYSTEM -> "Missing permission. Run on PC: $GRANT_SYSTEM"
            else -> "Missing permission. Run on PC: $GRANT_SECURE"
        }
    } catch (e: Exception) {
        e.message ?: "Unknown error"
    }

    fun all(c: Context, t: Table): List<Pair<String, String>> = try {
        val out = ArrayList<Pair<String, String>>()
        c.contentResolver.query(t.uri, arrayOf("name", "value"), null, null, null)?.use { cur ->
            while (cur.moveToNext()) out.add((cur.getString(0) ?: "") to (cur.getString(1) ?: ""))
        }
        out.sortBy { it.first }
        out
    } catch (e: Exception) {
        emptyList()
    }

    /** put() that remembers the previous value so it can be undone. */
    fun putTracked(c: Context, t: Table, key: String, value: String): String? {
        val old = get(c, t, key)
        val err = put(c, t, key, value)
        if (err == null) {
            Prefs.misc(c).edit()
                .putString("undo_t", t.name)
                .putString("undo_k", key)
                .putString("undo_v", old)
                .putBoolean("undo_has", old != null)
                .apply()
        }
        return err
    }

    fun undo(c: Context): String {
        val p = Prefs.misc(c)
        val t = p.getString("undo_t", null) ?: return "Nothing to undo"
        val k = p.getString("undo_k", null) ?: return "Nothing to undo"
        val v = p.getString("undo_v", null)
        if (!p.getBoolean("undo_has", false) || v == null) {
            return "$k did not exist before, so it can't be removed through the API"
        }
        val err = put(c, Table.valueOf(t), k, v)
        return err ?: "Restored $k to $v"
    }

    // ---- backup / restore -------------------------------------------------

    private val neverRestore = setOf(
        "android_id", "device_provisioned", "user_setup_complete", "secure_frp_mode",
        "adb_enabled", "development_settings_enabled", "bluetooth_address", "bluetooth_name",
        "install_non_market_apps", "adb_wifi_enabled"
    )

    fun exportJson(c: Context): String {
        val root = JSONObject()
        root.put("app", "TweakDeck")
        root.put("device", Build.MODEL)
        root.put("sdk", Build.VERSION.SDK_INT)
        root.put("time", System.currentTimeMillis())
        for (t in Table.values()) {
            val o = JSONObject()
            all(c, t).forEach { o.put(it.first, it.second) }
            root.put(t.name, o)
        }
        return root.toString(1)
    }

    /** Returns (written, failed). Only changed values are written. */
    fun importJson(c: Context, json: String): Pair<Int, Int> {
        val root = JSONObject(json)
        var ok = 0
        var bad = 0
        for (t in Table.values()) {
            val o = root.optJSONObject(t.name) ?: continue
            for (k in o.keys()) {
                if (k in neverRestore) continue
                val v = o.optString(k, "")
                if (get(c, t, k) == v) continue
                if (put(c, t, k, v) == null) ok++ else bad++
            }
        }
        return ok to bad
    }
}
