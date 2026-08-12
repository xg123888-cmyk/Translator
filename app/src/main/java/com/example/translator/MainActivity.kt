package com.example.translator

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var direction: Button
    private lateinit var history: LinearLayout
    private lateinit var status: TextView
    private var enToZh = true
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var autoSpeak = false
    private val prefs by lazy { getSharedPreferences("translator", MODE_PRIVATE) }

    private val enZh by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build()
        )
    }
    private val zhEn by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { ttsReady = it == TextToSpeech.SUCCESS }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        scroll.addView(root)

        fun addText(text: String, size: Float, bold: Boolean = false) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
                if (bold) setTypeface(typeface, Typeface.BOLD)
            })
        }

        addText("中英离线翻译 2.0", 26f, true)
        addText("完整句子 · 离线模型 · 语音 · 对话 · 隐私优先", 14f)

        status = TextView(this).apply {
            text = "离线模型：首次使用需要联网下载"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFFF1F3F4.toInt())
        }
        root.addView(status, full())

        root.addView(Button(this).apply {
            text = "下载 / 检查离线模型"
            setOnClickListener { downloadModels(true) }
        }, full())

        direction = Button(this).apply {
            text = "English → 中文"
            setOnClickListener {
                enToZh = !enToZh
                updateDirection()
                val a = input.text.toString()
                val b = output.text.toString()
                if (b.isNotBlank() && b != "正在翻译…") {
                    input.setText(b)
                    output.text = a
                }
            }
        }
        root.addView(direction, full())

        input = EditText(this).apply {
            hint = "输入要翻译的完整句子…"
            minLines = 4
            gravity = Gravity.TOP
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(150)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "翻译"
            setOnClickListener { translate(false) }
        }, weight())
        actions.addView(Button(this).apply {
            text = "清空"
            setOnClickListener { input.setText(""); output.text = "" }
        }, weight())
        root.addView(actions, full())

        val voices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        voices.addView(Button(this).apply {
            text = "🎙 语音输入"
            setOnClickListener {
                autoSpeak = false
                startVoice(if (enToZh) "en-US" else "zh-CN")
            }
        }, weight())
        voices.addView(Button(this).apply {
            text = "🔊 朗读结果"
            setOnClickListener { speak() }
        }, weight())
        root.addView(voices, full())

        addText("翻译结果", 17f, true)
        output = TextView(this).apply {
            textSize = 21f
            minHeight = dp(120)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFFF1F3F4.toInt())
            setTextIsSelectable(true)
        }
        root.addView(output, full())

        root.addView(Button(this).apply {
            text = "复制结果"
            setOnClickListener {
                val r = output.text.toString()
                if (r.isBlank()) toast("还没有翻译结果")
                else {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("translation", r))
                    toast("已复制")
                }
            }
        }, full())

        addText("面对面对话", 19f, true)
        addText("点一方说话，识别后自动翻译并朗读给另一方。", 13f)

        val convo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        convo.addView(Button(this).apply {
            text = "🎙 中文说话"
            setOnClickListener {
                enToZh = false
                updateDirection()
                autoSpeak = true
                startVoice("zh-CN")
            }
        }, weight())
        convo.addView(Button(this).apply {
            text = "🎙 English"
            setOnClickListener {
                enToZh = true
                updateDirection()
                autoSpeak = true
                startVoice("en-US")
            }
        }, weight())
        root.addView(convo, full())

        addText("最近记录", 17f, true)
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(history, full())
        root.addView(Button(this).apply {
            text = "清除记录"
            setOnClickListener {
                prefs.edit().remove("history").apply()
                renderHistory()
            }
        }, full())

        addText("首次下载中英文模型时需要网络。下载完成后文本翻译可离线运行。语音是否完全离线取决于手机系统的离线语音服务。", 12f)

        setContentView(scroll)
        renderHistory()
        if (prefs.getBoolean("modelsReady", false)) downloadModels(false)
    }

    private fun downloadModels(showToast: Boolean) {
        status.text = "离线模型：正在准备…"
        val c = DownloadConditions.Builder().build()
        enZh.downloadModelIfNeeded(c).addOnSuccessListener {
            zhEn.downloadModelIfNeeded(c).addOnSuccessListener {
                status.text = "离线模型：已就绪 ✓"
                prefs.edit().putBoolean("modelsReady", true).apply()
                if (showToast) toast("离线模型已就绪")
            }.addOnFailureListener {
                status.text = "离线模型：下载失败"
                if (showToast) toast("请检查网络后重试")
            }
        }.addOnFailureListener {
            status.text = "离线模型：下载失败"
            if (showToast) toast("请检查网络后重试")
        }
    }

    private fun translate(speakAfter: Boolean) {
        val source = input.text.toString().trim()
        if (source.isEmpty()) { toast("请输入内容"); return }
        output.text = "正在翻译…"
        val tr = if (enToZh) enZh else zhEn
        tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                status.text = "离线模型：已就绪 ✓"
                prefs.edit().putBoolean("modelsReady", true).apply()
                tr.translate(source).addOnSuccessListener { result ->
                    output.text = result
                    saveHistory(source, result)
                    renderHistory()
                    if (speakAfter) speak()
                }.addOnFailureListener {
                    output.text = ""
                    toast("翻译失败")
                }
            }.addOnFailureListener {
                output.text = ""
                status.text = "离线模型：尚未下载"
                toast("首次使用请先联网下载模型")
            }
    }

    private fun startVoice(lang: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            toast("请允许麦克风权限后再点一次")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("这台手机没有可用的语音识别服务")
            return
        }

        recognizer?.destroy()
        recognizer = if (
            Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            try { SpeechRecognizer.createOnDeviceSpeechRecognizer(this) }
            catch (_: Exception) { SpeechRecognizer.createSpeechRecognizer(this) }
        } else SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { toast("请开始说话") }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    input.setText(text)
                    translate(autoSpeak)
                } else toast("没有识别到内容")
            }
            override fun onError(error: Int) { toast("语音识别未完成（$error）") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    private fun speak() {
        val r = output.text.toString().trim()
        if (r.isBlank() || r == "正在翻译…") { toast("还没有翻译结果"); return }
        if (!ttsReady) { toast("朗读服务尚未准备好"); return }
        val loc = if (enToZh) Locale.SIMPLIFIED_CHINESE else Locale.US
        val ok = tts?.setLanguage(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("手机缺少对应语言的朗读语音包")
            return
        }
        tts?.speak(r, TextToSpeech.QUEUE_FLUSH, null, "translation")
    }

    private fun saveHistory(source: String, translated: String) {
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val d = if (enToZh) "EN→中" else "中→EN"
        val line = "$stamp\t$d\t${source.replace("\n"," ")}\t${translated.replace("\n"," ")}"
        val old = prefs.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }
        prefs.edit().putString("history", (listOf(line) + old).take(20).joinToString("\n")).apply()
    }

    private fun renderHistory() {
        history.removeAllViews()
        val lines = prefs.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) history.addView(TextView(this).apply { text = "暂无记录" })
        lines.forEach { line ->
            val p = line.split("\t")
            history.addView(TextView(this).apply {
                text = if (p.size >= 4) "${p[0]}  ${p[1]}\n${p[2]}\n→ ${p[3]}" else line
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    if (p.size >= 4) {
                        enToZh = p[1] == "EN→中"
                        updateDirection()
                        input.setText(p[2])
                        output.text = p[3]
                    }
                }
            }, full())
        }
    }

    private fun updateDirection() {
        direction.text = if (enToZh) "English → 中文" else "中文 → English"
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        enZh.close()
        zhEn.close()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun full() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f).apply {
        marginStart = dp(3); marginEnd = dp(3)
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
