# Translator 2.2

这是真正的 2.2 语音修复版。

- Android 12+ 优先尝试设备端 SpeechRecognizer
- 设备端不可用时再尝试系统语音识别界面
- 系统界面不可用时才回退到默认识别服务
- 语音错误会显示 Android 原始错误编号，方便继续定位
- Manifest 同时声明 RecognitionService 与 RECOGNIZE_SPEECH 查询
- 文本离线翻译、历史记录、朗读、面对面对话继续保留

注意：如果手机本身没有可用的系统/设备端语音识别服务，应用无法仅靠 Android SpeechRecognizer 完成识别；这时下一步需要把离线 ASR 模型直接集成到应用。
