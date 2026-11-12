package com.example.tiktokreader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var log: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(Button(this).apply {
            text = "1. Включить службу доступности"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Switch(this).apply {
            text = "Озвучивать комментарии"; isChecked = prefs.getBoolean("speak", true)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("speak", v).apply() }
        })
        root.addView(Switch(this).apply {
            text = "Читать имена авторов"; isChecked = prefs.getBoolean("names", true)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("names", v).apply() }
        })
        root.addView(TextView(this).apply { text = "Скорость речи" })
        root.addView(SeekBar(this).apply {
            max = 20; progress = ((prefs.getFloat("rate", 1.1f) - 0.5f) * 10).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    prefs.edit().putFloat("rate", 0.5f + p / 10f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        root.addView(TextView(this).apply {
            text = "\nПоследние пойманные тексты (для отладки):"
        })
        log = TextView(this)
        root.addView(ScrollView(this).apply { addView(log) })
        setContentView(root)
    }

    private val tick = object : Runnable {
        override fun run() {
            log.text = CommentService.captured.reversed().joinToString("\n")
            handler.postDelayed(this, 1000)
        }
    }
    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }
}
