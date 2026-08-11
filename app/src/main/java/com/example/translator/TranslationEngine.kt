package com.example.translator

object TranslationEngine {
    private val enZh = linkedMapOf(
        "hello" to "你好", "hi" to "你好", "good morning" to "早上好",
        "good afternoon" to "下午好", "good evening" to "晚上好", "good night" to "晚安",
        "thank you" to "谢谢", "thanks" to "谢谢", "please" to "请", "sorry" to "对不起",
        "excuse me" to "打扰一下", "yes" to "是", "no" to "不", "okay" to "好的",
        "how are you" to "你好吗", "i am fine" to "我很好", "what is your name" to "你叫什么名字",
        "my name is" to "我的名字是", "where is the bathroom" to "洗手间在哪里",
        "where is the hotel" to "酒店在哪里", "where is the airport" to "机场在哪里",
        "where is the station" to "车站在哪里", "how much" to "多少钱", "too expensive" to "太贵了",
        "i want this" to "我要这个", "i don't understand" to "我不明白", "can you help me" to "你能帮我吗",
        "speak slowly" to "请说慢一点", "repeat please" to "请再说一遍",
        "water" to "水", "food" to "食物", "restaurant" to "餐厅", "hotel" to "酒店",
        "airport" to "机场", "station" to "车站", "hospital" to "医院", "pharmacy" to "药店",
        "police" to "警察", "doctor" to "医生", "car" to "汽车", "bus" to "公交车",
        "train" to "火车", "taxi" to "出租车", "left" to "左边", "right" to "右边",
        "straight" to "直走", "today" to "今天", "tomorrow" to "明天", "yesterday" to "昨天",
        "morning" to "早上", "afternoon" to "下午", "evening" to "晚上",
        "one" to "一", "two" to "二", "three" to "三", "four" to "四", "five" to "五",
        "six" to "六", "seven" to "七", "eight" to "八", "nine" to "九", "ten" to "十"
    )

    private val zhEn = enZh.entries.associate { (en, zh) -> zh to en }.toList().sortedByDescending { it.first.length }

    fun translate(text: String, englishToChinese: Boolean): String {
        val input = text.trim()
        if (input.isEmpty()) return ""
        return if (englishToChinese) translateEnglish(input) else translateChinese(input)
    }

    private fun translateEnglish(input: String): String {
        val normalized = input.lowercase().trim()
        enZh[normalized]?.let { return it }
        var result = " $normalized "
        enZh.entries.sortedByDescending { it.key.length }.forEach { (en, zh) ->
            result = result.replace(" $en ", " $zh ", ignoreCase = true)
        }
        return result.trim().split(Regex("\\s+")).joinToString(" ") { enZh[it.lowercase()] ?: it }.trim()
    }

    private fun translateChinese(input: String): String {
        zhEn.firstOrNull { it.first == input }?.let { return it.second }
        var result = input
        zhEn.forEach { (zh, en) -> result = result.replace(zh, " $en ") }
        return result.replace(Regex("\\s+"), " ").trim()
    }
}
