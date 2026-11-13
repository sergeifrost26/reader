package com.example.tiktokreader

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class CommentService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        // Последние захваченные тексты, чтобы видеть их в окне приложения (для отладки)
        val captured = ArrayDeque<String>()
        @Volatile var pending = 0

        // Область экрана, где TikTok показывает комментарии LIVE (доли экрана).
        // Если читает лишнее или пропускает - меняй эти числа.
        const val TOP_MIN = 0.40f   // ниже 40% высоты
        const val RIGHT_MAX = 0.80f // левее 80% ширины

        // Если в тексте есть любое из этих слов - он не читается (системные сообщения)
        val SKIP_WORDS = listOf("присоедин", "joined", "вошел", "вошёл", "зашел", "зашёл", "вступил")

        val BLACKLIST = setOf(
            "подписаться", "follow", "share", "поделиться", "live", "комментарий",
            "добавить комментарий", "add comment", "say something...", "gift", "подарок"
        )
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private val seen = LinkedHashSet<String>()
    private var primed = false

    override fun onServiceConnected() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        // Голос идёт по каналу будильника: громкость отдельная от TikTok (медиа)
        tts?.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts?.language = Locale.getDefault()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { pending = maxOf(0, pending - 1) }
            @Deprecated("") override fun onError(id: String?) { pending = maxOf(0, pending - 1) }
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val dm = resources.displayMetrics
        val texts = ArrayList<String>()
        collect(root, texts, dm.widthPixels, dm.heightPixels)

        val fresh = ArrayList<String>()
        for (t in texts) {
            if (!isComment(t)) continue
            if (seen.add(t)) fresh.add(t)
        }
        while (seen.size > 400) seen.remove(seen.first())

        // Первый проход: запоминаем уже висящие комментарии, но не читаем их
        if (!primed) { primed = true; return }

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val speak = prefs.getBoolean("speak", true)
        val names = prefs.getBoolean("names", true)
        val rate = prefs.getFloat("rate", 1.1f)

        for (t in fresh) {
            captured.addLast(t)
            while (captured.size > 60) captured.removeFirst()
            if (!speak || !ready) continue
            var line = t
            if (!names) {
                val i = line.indexOf(": ")
                if (i in 1..30) line = line.substring(i + 2)
            }
            if (pending > 6) { tts?.stop(); pending = 0 } // не копим очередь
            pending++
            tts?.setSpeechRate(rate)
            tts?.speak(line, TextToSpeech.QUEUE_ADD, null, "c${System.nanoTime()}")
        }
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>, w: Int, h: Int) {
        if (n == null) return
        val txt = n.text?.toString()?.trim()
        if (!txt.isNullOrEmpty() && (n.className?.contains("TextView") == true)) {
            val r = Rect(); n.getBoundsInScreen(r)
            if (r.top > h * TOP_MIN && r.right < w * RIGHT_MAX) out.add(txt)
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, w, h)
    }

    private fun isComment(t: String): Boolean {
        if (t.length < 2) return false
        if (t.all { it.isDigit() || !it.isLetterOrDigit() }) return false
        if (t.lowercase() in BLACKLIST) return false
        val low = t.lowercase()
        if (SKIP_WORDS.any { low.contains(it) }) return false // "вошёл в чат" и т.п.
        return true
    }

    override fun onInterrupt() { tts?.stop() }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
