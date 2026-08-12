package com.example.translator

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
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
    private lateinit var modelStatus: TextView
    private lateinit var voiceStatus: TextView

    private var enToZh = true
    private var autoSpeak = false
    private var pendingLang: String? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

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

        fun title(text: String, size: Float) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTypeface(typeface, Typeface.BOLD)
            })
        }

        title("中英离线翻译 2.2", 26f)
        root.addView(TextView(this).apply {
            text = "完整句子 · 离线模型 · 设备端语音优先 · 面对面对话"
            textSize = 14f
        })

        modelStatus = TextView(this).apply {
            text = "离线模型：首次使用需要联网下载"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFFF1F3F4.toInt())
        }
        root.addView(modelStatus, full())

        root.addView(Button(this).apply {
            text = "下载 / 检查离线模型"
            setOnClickListener { downloadModels(true) }
        }, full())

        voiceStatus = TextView(this).apply {
            text = "语音：2.2 设备端识别优先"
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        root.addView(voiceStatus, full())

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

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(Button(this).apply {
            text = "翻译"
            setOnClickListener { translate(false) }
        }, weight())
        row1.addView(Button(this).apply {
            text = "清空"
            setOnClickListener { input.setText(""); output.text = "" }
        }, weight())
        root.addView(row1, full())

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(Button(this).apply {
            text = "🎙 语音输入"
            setOnClickListener {
                autoSpeak = false
                startVoice(if (enToZh) "en-US" else "zh-CN")
            }
        }, weight())
        row2.addView(Button(this).apply {
            text = "🔊 朗读结果"
            setOnClickListener { speak() }
        }, weight())
        root.addView(row2, full())

        title("翻译结果", 17f)
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

        title("面对面对话", 19f)
        root.addView(TextView(this).apply {
            text = "点一方说话，识别后自动翻译并朗读给另一方。"
        })

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(Button(this).apply {
            text = "🎙 中文说话"
            setOnClickListener {
                enToZh = false
                updateDirection()
                autoSpeak = true
                startVoice("zh-CN")
            }
        }, weight())
        row3.addView(Button(this).apply {
            text = "🎙 ENGLISH"
            setOnClickListener {
                enToZh = true
                updateDirection()
                autoSpeak = true
                startVoice("en-US")
            }
        }, weight())
        root.addView(row3, full())

        title("最近记录", 17f)
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(history, full())

        root.addView(Button(this).apply {
            text = "清除记录"
            setOnClickListener {
                prefs.edit().remove("history").apply()
                renderHistory()
            }
        }, full())

        root.addView(TextView(this).apply {
            text = "2.2：Android 12+ 优先尝试设备端语音识别；若设备端不可用，再尝试系统识别界面与默认识别服务。"
            textSize = 12f
        }, full())

        setContentView(scroll)
        renderHistory()
        if (prefs.getBoolean("modelsReady", false)) downloadModels(false)
    }

    private fun downloadModels(showToast: Boolean) {
        modelStatus.text = "离线模型：正在准备…"
        val c = DownloadConditions.Builder().build()
        enZh.downloadModelIfNeeded(c).addOnSuccessListener {
            zhEn.downloadModelIfNeeded(c).addOnSuccessListener {
                modelStatus.text = "离线模型：已就绪 ✓"
                prefs.edit().putBoolean("modelsReady", true).apply()
                if (showToast) toast("离线模型已就绪")
            }.addOnFailureListener {
                modelStatus.text = "离线模型：下载失败"
                if (showToast) toast("请检查网络后重试")
            }
        }.addOnFailureListener {
            modelStatus.text = "离线模型：下载失败"
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
                modelStatus.text = "离线模型：已就绪 ✓"
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
                modelStatus.text = "离线模型：尚未下载"
                toast("首次使用请先联网下载模型")
            }
    }

    private fun startVoice(lang: String) {
        pendingLang = lang
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            voiceStatus.text = "语音：正在启动设备端识别…"
            startDirectVoice(lang, onDevice = true)
            return
        }

        launchSystemVoice(lang)
    }

    private fun launchSystemVoice(lang: String) {
        voiceStatus.text = "语音：正在打开系统识别界面…"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (lang.startsWith("zh")) "请说中文" else "Please speak English")
        }
        try {
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (_: ActivityNotFoundException) {
            voiceStatus.text = "语音：系统界面不可用，尝试默认识别服务…"
            startDirectVoice(lang, onDevice = false)
        }
    }

    private fun startDirectVoice(lang: String, onDevice: Boolean) {
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus.text = "语音：手机没有可用的系统识别服务"
            toast("手机没有可用的系统语音识别服务")
            return
        }

        recognizer?.destroy()
        recognizer = try {
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        } catch (_: Exception) {
            SpeechRecognizer.createSpeechRecognizer(this)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { voiceStatus.text = "语音：请开始说话" }
            override fun onBeginningOfSpeech() { voiceStatus.text = "语音：正在听…" }
            override fun onEndOfSpeech() { voiceStatus.text = "语音：正在识别…" }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                handleSpeech(text)
            }
            override fun onError(error: Int) {
                val msg = speechError(error)
                voiceStatus.text = "语音：$msg（错误 $error）"
                toast("$msg（错误 $error）")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDevice)
            }
        })
    }

    private fun handleSpeech(text: String) {
        if (text.isBlank()) {
            voiceStatus.text = "语音：没有识别到内容"
            toast("没有识别到内容，请再试一次")
            return
        }
        voiceStatus.text = "语音：识别成功 ✓"
        input.setText(text)
        translate(autoSpeak)
    }

    private fun speechError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "录音发生错误"
        SpeechRecognizer.ERROR_CLIENT -> "识别服务被中断"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "语音服务拒绝录音权限"
        SpeechRecognizer.ERROR_NETWORK -> "语音服务网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音服务网络超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有听清"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音服务正忙"
        SpeechRecognizer.ERROR_SERVER -> "语音识别服务错误"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到说话声音"
        10 -> "请求过于频繁"
        11 -> "语音服务连接已断开"
        12 -> "当前语言不受支持"
        13 -> "当前语言模型不可用"
        else -> "语音识别未完成"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == RESULT_OK) {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                handleSpeech(results?.firstOrNull().orEmpty())
            } else {
                voiceStatus.text = "语音：系统识别已取消"
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                pendingLang?.let { startVoice(it) }
            } else {
                voiceStatus.text = "语音：麦克风权限被拒绝"
                toast("需要麦克风权限才能语音输入")
            }
        }
    }

    private fun speak() {
        val r = output.text.toString().trim()
        if (r.isBlank() || r == "正在翻译…") { toast("还没有翻译结果"); return }
        if (!ttsReady) { toast("朗读服务尚未准备好"); return }

        val locale = if (enToZh) Locale.SIMPLIFIED_CHINESE else Locale.US
        val ok = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
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

    companion object {
        private const val REQUEST_AUDIO = 1001
        private const val REQUEST_SPEECH = 1002
    }
}
