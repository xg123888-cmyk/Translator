package com.example.translator

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var directionButton: Button
    private lateinit var historyBox: LinearLayout
    private var englishToChinese = true
    private val prefs by lazy { getSharedPreferences("translator", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(20), dp(18), dp(24)) }
        scroll.addView(root)

        root.addView(TextView(this).apply { text = "中英离线翻译"; textSize = 26f; setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "无需网络 · 本地短语词典 · 隐私优先"; textSize = 14f; setPadding(0, dp(4), 0, dp(16)) })

        directionButton = Button(this).apply {
            text = "English → 中文"
            setOnClickListener {
                englishToChinese = !englishToChinese
                text = if (englishToChinese) "English → 中文" else "中文 → English"
                val oldInput = input.text.toString(); val oldOutput = output.text.toString()
                input.setText(if (oldOutput.isNotBlank()) oldOutput else oldInput); output.text = ""
            }
        }
        root.addView(directionButton, matchWidth())

        input = EditText(this).apply { hint = "输入要翻译的内容…"; minLines = 4; gravity = Gravity.TOP; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)).apply { topMargin = dp(12) })

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actionRow.addView(Button(this).apply { text = "翻译"; setOnClickListener { doTranslate() } }, weight())
        actionRow.addView(Button(this).apply { text = "清空"; setOnClickListener { input.setText(""); output.text = "" } }, weight())
        root.addView(actionRow, matchWidth().apply { topMargin = dp(8) })

        root.addView(TextView(this).apply { text = "翻译结果"; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(18), 0, dp(6)) })
        output = TextView(this).apply { textSize = 21f; setPadding(dp(14), dp(14), dp(14), dp(14)); minHeight = dp(110); setBackgroundColor(0xFFF1F3F4.toInt()); setTextIsSelectable(true) }
        root.addView(output, matchWidth())

        root.addView(Button(this).apply {
            text = "复制结果"
            setOnClickListener {
                val result = output.text.toString()
                if (result.isBlank()) toast("还没有翻译结果") else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("translation", result)); toast("已复制")
                }
            }
        }, matchWidth().apply { topMargin = dp(8) })

        root.addView(TextView(this).apply { text = "最近记录"; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(20), 0, dp(6)) })
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyBox, matchWidth())
        root.addView(Button(this).apply { text = "清除记录"; setOnClickListener { prefs.edit().remove("history").apply(); renderHistory() } }, matchWidth().apply { topMargin = dp(8) })
        root.addView(TextView(this).apply { text = "说明：当前 v1.0 使用内置本地常用词与短语库，完全离线。后续可继续扩充词库与离线模型。"; textSize = 12f; setPadding(0, dp(18), 0, 0) })
        setContentView(scroll)
        renderHistory()
    }

    private fun doTranslate() {
        val source = input.text.toString().trim()
        if (source.isEmpty()) { toast("请输入内容"); return }
        val translated = TranslationEngine.translate(source, englishToChinese)
        output.text = translated; saveHistory(source, translated); renderHistory()
    }

    private fun saveHistory(source: String, translated: String) {
        val old = prefs.getString("history", "") ?: ""
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val direction = if (englishToChinese) "EN→中" else "中→EN"
        val line = "$stamp\t$direction\t${source.replace("\n", " ")}\t${translated.replace("\n", " ")}"
        val updated = (listOf(line) + old.lines().filter { it.isNotBlank() }).take(12)
        prefs.edit().putString("history", updated.joinToString("\n")).apply()
    }

    private fun renderHistory() {
        historyBox.removeAllViews()
        val lines = (prefs.getString("history", "") ?: "").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) { historyBox.addView(TextView(this).apply { text = "暂无记录"; setPadding(dp(8), dp(8), dp(8), dp(8)) }); return }
        lines.forEach { line ->
            val parts = line.split("\t")
            val view = TextView(this).apply {
                text = if (parts.size >= 4) "${parts[0]}  ${parts[1]}\n${parts[2]}\n→ ${parts[3]}" else line
                textSize = 14f; setPadding(dp(10), dp(10), dp(10), dp(10)); setBackgroundColor(0xFFF7F7F7.toInt())
                setOnClickListener { if (parts.size >= 4) { input.setText(parts[2]); output.text = parts[3] } }
            }
            historyBox.addView(view, matchWidth().apply { bottomMargin = dp(6) })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
