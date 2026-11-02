package com.tweakdeck.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.content.res.Resources
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Support for ADW / Nova / Go style icon packs (anything that ships an appfilter.xml). */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme"
    )

    /** (package, label) of every installed icon pack. */
    @Suppress("DEPRECATION")
    fun list(pm: PackageManager): List<Pair<String, String>> =
        ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

    /** component string ("ComponentInfo{pkg/cls}") -> drawable name. */
    fun loadMap(ctx: Context, pack: String): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val res = ctx.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser = if (id != 0) {
                res.getXml(id)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().also {
                    it.setInput(res.assets.open("appfilter.xml"), null)
                }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null) out[comp] = drawable
                }
                ev = parser.next()
            }
        } catch (e: Exception) {
            // pack without a readable appfilter: fall back to system icons
        }
        return out
    }

    fun icon(res: Resources?, pack: String, map: Map<String, String>, cn: ComponentName): Drawable? {
        if (res == null) return null
        val name = map[cn.toString()] ?: return null
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) return null
        return try {
            @Suppress("DEPRECATION")
            res.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }
}
