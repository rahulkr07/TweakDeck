package com.tweakdeck.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Browse, search and edit every key in System / Secure / Global. */
class ExplorerActivity : BaseActivity() {

    private var table = Sx.Table.SYSTEM
    private var rows: List<Pair<String, String>> = emptyList()
    private var shown: List<Pair<String, String>> = emptyList()
    private lateinit var list: ListView
    private lateinit var count: TextView
    private lateinit var search: EditText
    private val tabButtons = ArrayList<MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Settings explorer"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), 0)
        }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in Sx.Table.values()) {
            val b = MaterialButton(this).apply {
                text = t.label
                isAllCaps = false
                setOnClickListener { table = t; reload() }
            }
            tabButtons.add(b)
            tabs.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabs)

        search = EditText(this).apply {
            hint = "Search key or value"
            setSingleLine()
        }
        search.doAfterTextChanged { filter() }
        root.addView(search)

        count = TextView(this).apply { textSize = 12f; alpha = 0.7f }
        root.addView(count)

        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        list.setOnItemClickListener { _, _, pos, _ -> edit(shown[pos].first, shown[pos].second) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            copyToClipboard("key", shown[pos].first)
            true
        }
        reload()
    }

    private fun reload() {
        tabButtons.forEachIndexed { i, b -> b.alpha = if (Sx.Table.values()[i] == table) 1f else 0.5f }
        Thread {
            val r = Sx.all(this, table)
            runOnUiThread {
                rows = r
                filter()
            }
        }.start()
    }

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        shown = if (q.isEmpty()) rows else rows.filter {
            it.first.lowercase().contains(q) || it.second.lowercase().contains(q)
        }
        count.text = "${shown.size} of ${rows.size} keys · tap to edit · long-press to copy key"
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            shown.map { "${it.first}\n= ${it.second.take(120)}" }
        )
    }

    private fun edit(key: String, current: String) {
        val input = EditText(this).apply {
            setText(current)
            setSelection(current.length)
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(key)
            .setMessage("${table.label} table. The previous value is remembered so you can undo.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val err = Sx.putTracked(this, table, key, input.text.toString())
                toast(err ?: "Saved")
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addNew() {
        val k = EditText(this).apply { hint = "key" }
        val v = EditText(this).apply { hint = "value" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(k)
            addView(v)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New ${table.label} setting")
            .setView(box)
            .setPositiveButton("Write") { _, _ ->
                val key = k.text.toString().trim()
                if (key.isEmpty()) return@setPositiveButton
                toast(Sx.putTracked(this, table, key, v.text.toString()) ?: "Written")
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Add key").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 2, 1, "Undo last change").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        1 -> { addNew(); true }
        2 -> { toast(Sx.undo(this)); reload(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
