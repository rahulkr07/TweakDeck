package com.tweakdeck.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppItem(val label: String, val pkg: String, val cn: ComponentName) {
    var icon: Drawable? = null
    fun iconCopy(): Drawable? = icon?.constantState?.newDrawable()?.mutate() ?: icon
}

/** The home screen: wallpaper shows through, search bar, app grid, dock. */
class HomeActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var search: EditText
    private val adapter = GridAdapter()

    private var all: List<AppItem> = emptyList()
    private var shown: List<AppItem> = emptyList()
    private var iconDp = 56
    private var showLabels = true

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            loadApps()
        }
    }

    private val prefs get() = Prefs.launcher(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // search + settings
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(6))
        }
        search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(0xAAFFFFFF.toInt())
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0x55000000)
            }
        }
        top.addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val gear = TextView(this).apply {
            text = "⚙"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(6), dp(6), dp(6))
            setOnClickListener { startActivity(Intent(this@HomeActivity, LauncherSettingsActivity::class.java)) }
        }
        top.addView(gear)
        root.addView(top)

        grid = GridView(this).apply {
            this.adapter = this@HomeActivity.adapter
            selector = null
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(4)
            setPadding(dp(8), 0, dp(8), 0)
            clipToPadding = false
            setOnItemClickListener { _, _, pos, _ -> launch(shown[pos]) }
            setOnItemLongClickListener { _, _, pos, _ -> showMenu(shown[pos]); true }
        }
        root.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0x55000000)
            }
        }
        val dockLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        dockLp.setMargins(dp(16), dp(4), dp(16), dp(12))
        root.addView(dock, dockLp)

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        search.doAfterTextChanged { applyFilter() }
        search.setOnEditorActionListener { _, _, _ ->
            shown.firstOrNull()?.let { launch(it) }
            true
        }
        onBackPressedDispatcher.addCallback(this) {
            search.setText("")
            grid.smoothScrollToPosition(0)
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, f, ContextCompat.RECEIVER_EXPORTED)
        applyPrefs()
        loadApps()
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(packageReceiver) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        search.setText("")
        grid.smoothScrollToPosition(0)
    }

    // ---- data -------------------------------------------------------------

    private fun applyPrefs() {
        val p = prefs
        grid.numColumns = p.getInt("cols", 4)
        iconDp = p.getInt("iconSize", 56)
        showLabels = p.getBoolean("labels", true)
        val scrim = p.getInt("scrim", 20)
        root.setBackgroundColor(Color.argb(scrim * 255 / 100, 0, 0, 0))
        adapter.notifyDataSetChanged()
    }

    private fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()

    private fun dockList(): List<String> =
        (prefs.getString("dock", "") ?: "").split(",").filter { it.isNotBlank() }

    private fun saveDock(l: List<String>) {
        prefs.edit().putString("dock", l.joinToString(",")).apply()
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val packName = prefs.getString("iconpack", null)
            val map = if (packName != null) IconPacks.loadMap(this, packName) else emptyMap()
            val packRes = packName?.let { runCatching { pm.getResourcesForApplication(it) }.getOrNull() }

            val items = pm.queryIntentActivities(intent, 0).map { ri ->
                val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                AppItem(ri.loadLabel(pm).toString(), cn.packageName, cn).also {
                    it.icon = (if (packName != null) IconPacks.icon(packRes, packName, map, cn) else null)
                        ?: ri.loadIcon(pm)
                }
            }.sortedBy { it.label.lowercase() }

            runOnUiThread {
                all = items
                applyFilter()
                renderDock()
            }
        }.start()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase()
        val hide = hidden()
        shown = all.filter { q.isEmpty() && it.pkg !in hide || q.isNotEmpty() && it.label.lowercase().contains(q) }
        adapter.notifyDataSetChanged()
    }

    private fun renderDock() {
        dock.removeAllViews()
        val byPkg = all.associateBy { it.pkg }
        val items = dockList().mapNotNull { byPkg[it] }.take(6)
        dock.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        for (item in items) {
            val iv = ImageView(this).apply {
                setImageDrawable(item.iconCopy())
                setOnClickListener { launch(item) }
                setOnLongClickListener { showMenu(item); true }
            }
            val lp = LinearLayout.LayoutParams(dp(iconDp), dp(iconDp))
            lp.setMargins(dp(8), 0, dp(8), 0)
            dock.addView(iv, lp)
        }
    }

    // ---- actions ----------------------------------------------------------

    private fun launch(item: AppItem) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(item.cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            toast("Can't open ${item.label}")
        }
    }

    private fun showMenu(item: AppItem) {
        val inDock = item.pkg in dockList()
        val options = arrayOf(
            if (inDock) "Remove from dock" else "Add to dock",
            "Hide from launcher",
            "App info",
            "Uninstall"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(item.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val l = dockList().toMutableList()
                        if (inDock) l.remove(item.pkg) else if (l.size < 6) l.add(item.pkg) else toast("Dock is full (6)")
                        saveDock(l)
                        renderDock()
                    }
                    1 -> {
                        prefs.edit().putStringSet("hidden", hidden() + item.pkg).apply()
                        applyFilter()
                        toast("Hidden. Search for it to find it, or unhide in launcher settings.")
                    }
                    2 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${item.pkg}"))
                    )
                    3 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${item.pkg}")))
                }
            }
            .show()
    }

    // ---- grid adapter -----------------------------------------------------

    private inner class GridAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = shown[position]
            val ctx = this@HomeActivity
            val cell = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(10), dp(2), dp(6))
                addView(ImageView(ctx))
                addView(TextView(ctx).apply {
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    maxLines = 1
                    gravity = Gravity.CENTER
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setShadowLayer(6f, 0f, 1f, Color.BLACK)
                })
            }
            val iv = cell.getChildAt(0) as ImageView
            val tv = cell.getChildAt(1) as TextView
            iv.layoutParams = LinearLayout.LayoutParams(dp(iconDp), dp(iconDp))
            iv.setImageDrawable(item.iconCopy())
            tv.text = item.label
            tv.visibility = if (showLabels) View.VISIBLE else View.GONE
            return cell
        }
    }
}
