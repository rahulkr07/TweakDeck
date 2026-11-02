package com.tweakdeck.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** Base class: back arrow in the action bar. */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

fun Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    toast("Copied")
}

fun Context.accentColor(): Int =
    MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF3F51B5.toInt())

/** A scrolling vertical column. */
fun Context.page(): Pair<ScrollView, LinearLayout> {
    val sv = ScrollView(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(40))
    }
    sv.addView(col)
    return sv to col
}

fun LinearLayout.section(text: String) {
    addView(TextView(context).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(context.accentColor())
        setPadding(0, context.dp(22), 0, context.dp(4))
    })
}

fun LinearLayout.body(text: String, sizeSp: Float = 14f, alpha: Float = 1f): TextView {
    val tv = TextView(context).apply {
        this.text = text
        textSize = sizeSp
        this.alpha = alpha
        setPadding(0, context.dp(4), 0, context.dp(4))
    }
    addView(tv)
    return tv
}

fun LinearLayout.note(text: String): TextView = body(text, 12f, 0.7f)

fun LinearLayout.mono(text: String): TextView {
    val tv = TextView(context).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        setBackgroundColor(0x22888888)
    }
    addView(tv)
    return tv
}

fun LinearLayout.button(text: String, onClick: () -> Unit): MaterialButton {
    val b = MaterialButton(context).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.topMargin = context.dp(6)
    addView(b, lp)
    return b
}

fun LinearLayout.switchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    addView(MaterialSwitch(context).apply {
        text = title
        isChecked = checked
        setPadding(0, context.dp(8), 0, context.dp(8))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    })
}

/** Integer slider. onCommit fires when the finger lifts. */
fun LinearLayout.slider(
    title: String,
    lo: Int,
    hi: Int,
    value: Int,
    fmt: (Int) -> String = { it.toString() },
    onCommit: (Int) -> Unit
) {
    val label = TextView(context).apply {
        text = "$title: ${fmt(value)}"
        setPadding(0, context.dp(12), 0, 0)
    }
    val bar = SeekBar(context)
    bar.max = hi - lo
    bar.progress = (value - lo).coerceIn(0, hi - lo)
    bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            label.text = "$title: ${fmt(p + lo)}"
        }

        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {
            onCommit(bar.progress + lo)
        }
    })
    addView(label)
    addView(bar)
}

/** A button that opens a single-choice dialog. options = (label, value). */
fun LinearLayout.choice(
    title: String,
    options: List<Pair<String, String>>,
    current: String?,
    onPick: (String) -> Unit
) {
    fun labelFor(v: String?) = options.firstOrNull { it.second == v }?.first ?: (v ?: "not set")
    lateinit var b: MaterialButton
    b = button("$title: ${labelFor(current)}") {
        var sel = options.indexOfFirst { it.second == current }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(options.map { it.first }.toTypedArray(), sel) { d, which ->
                sel = which
                val v = options[which].second
                b.text = "$title: ${labelFor(v)}"
                onPick(v)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
