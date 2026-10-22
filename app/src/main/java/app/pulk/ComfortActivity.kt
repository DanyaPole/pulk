package app.pulk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * «Удобство»: the settings by the same five doors as the first start — Руки, Глаза и уши, Внимание, Речь,
 * Помощник рядом — with what is on now at the top, in words. Everything else stays in «Все настройки списком».
 */
class ComfortActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var head: TextView
    private var voice: Voice? = null
    private var voicesBox: LinearLayout? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); prefs = Prefs(this); ui = Ui(this)
        voice = Voice(this, prefs) { runOnUiThread { paintVoices() } }   // the list comes when the engine is ready
    }
    override fun onDestroy() { voice?.shutdown(); super.onDestroy() }

    /** The phone's own Russian voices; a touch chooses one and lets it speak. */
    private fun paintVoices() {
        val box = voicesBox ?: return
        box.removeAllViews()
        val names = voice?.ruVoices() ?: emptyList()
        if (names.isEmpty()) { box.addView(ui.note("Русский голос появится здесь, когда он есть на телефоне: Настройки → Специальные возможности → Синтез речи → русский язык.")); return }
        if (names.size < 2) return
        box.addView(choice("Голос", names.take(4).mapIndexed { i, n -> n to "Голос " + (i + 1) }, { prefs.voice.ifEmpty { names[0] } }) { prefs.voice = it; voice?.say("Привет! Это мой голос.") })
    }
    override fun onResume() { super.onResume(); build() }   // back from the hand check or the questions: what is on has changed

    private fun paintHead() { val on = Comfort.summary(prefs); head.text = if (on.isEmpty()) "Сейчас обычные настройки." else "Включено: ${on.joinToString(", ")}." }
    /** A choice that also refreshes the line «Включено». */
    private fun choice(title: String, opts: List<Pair<String, String>>, get: () -> String, set: (String) -> Unit) = ui.choice(title, opts, get) { set(it); paintHead() }

    private fun build() {
        val body = ui.column()
        body.addView(ui.title("Удобство"))
        head = ui.text("", 16f, ui.ink); body.addView(head); paintHead()
        body.addView(ui.button("Пройти пять вопросов заново") { startActivity(Intent(this, WizardActivity::class.java).putExtra(WizardActivity.DOORS_ONLY, true)) })

        body.addView(ui.h("Руки"))
        body.addView(choice("Рука", listOf("left" to "Левая", "right" to "Правая"), { prefs.hand }) { prefs.hand = it })
        body.addView(ui.button(if (prefs.calibrated) "Под мою руку — заново, 30 секунд" else "Под мою руку — 30 секунд") { startActivity(Intent(this, CalibrationActivity::class.java)) })
        body.addView(ui.note("Пульк узнает дрожь, длину смахов и куда палец на самом деле ведёт."))
        body.addView(choice("Защита от случайных касаний", listOf("0" to "Нет", "1" to "Слабая", "2" to "Сильная"), { prefs.guard.toString() }) { prefs.guard = it.toInt() })
        body.addView(choice("Способ набора", Prefs.modes(prefs, listOf("pulse" to "Пульс", "taps" to "Касания", "scan" to "Кнопка")), { prefs.mode }) { prefs.mode = it })
        body.addView(ui.note("Пульс — смах на букву. " + (if (prefs.mode == "jabs") "Толчки — слово одним касанием. " else "") + "Касания — без смахов: лепесток, потом буква. Кнопка — сканирование для того, кто может только нажать одну кнопку."))
        body.addView(choice("Размер звезды", listOf("100" to "Обычная", "112" to "Крупнее", "120" to "Крупная"), { (if (prefs.size >= 120) 120 else if (prefs.size >= 112) 112 else 100).toString() }) { prefs.size = it.toInt() })

        body.addView(ui.h("Глаза и уши"))
        body.addView(choice("Тема", listOf("auto" to "Как в телефоне", "contrast" to "Контраст"), { prefs.theme }) { prefs.theme = it })
        body.addView(choice("Крупный текст на экранах", listOf("off" to "Выкл", "on" to "Вкл"), { if (prefs.bigText) "on" else "off" }) {
            if (prefs.bigText != (it == "on")) { prefs.bigText = it == "on"; recreate() }   // seen at once, here and on every screen
        })
        body.addView(ui.note("Вдобавок к размеру шрифта в настройках телефона — его Пульк тоже учитывает."))
        body.addView(choice("Набор вслепую", listOf("off" to "Выкл", "on" to "Вкл"), { if (prefs.blind) "on" else "off" }) {
            prefs.blind = it == "on"
            if (prefs.blind && prefs.sound == "off") prefs.sound = "voice"   // eyes-free needs sound
            if (prefs.blind && prefs.compass == "off") prefs.compass = "voice"
        })
        body.addView(choice("Звуковой компас", listOf("off" to "Выкл", "notes" to "Ноты", "voice" to "Голос"), { prefs.compass }) { prefs.compass = it })
        body.addView(choice("Звук букв", listOf("off" to "Без звука", "clicks" to "Щелчки", "voice" to "Голос"), { prefs.sound }) { prefs.sound = it })
        body.addView(choice("Вибрация", listOf("on" to "Вкл", "off" to "Выкл"), { if (prefs.haptics) "on" else "off" }) { prefs.haptics = it == "on" })

        body.addView(ui.h("Внимание"))
        body.addView(choice("Простой режим", listOf("off" to "Выкл", "on" to "Вкл"), { if (prefs.simple) "on" else "off" }) { prefs.simple = it == "on" })
        body.addView(ui.note("Только буквы, пробел, ⌫, ⏎ и одна подсказка — без скрытых жестов и автоисправления."))
        body.addView(choice("Спокойный «Говорить»", listOf("off" to "Выкл", "on" to "Вкл"), { if (prefs.calm) "on" else "off" }) { prefs.calm = it == "on" })
        body.addView(ui.note("Мягче и медленнее голос, без звука «пульк» и ряби, меньше кнопок."))

        body.addView(ui.h("Речь"))
        body.addView(ui.button("Говорить — печатаю, телефон произносит") { startActivity(Intent(this, SpeakActivity::class.java)) })
        body.addView(choice("Говорить по словам", listOf("off" to "Выкл", "on" to "Вкл"), { if (prefs.speakWords) "on" else "off" }) { prefs.speakWords = it == "on" })
        voicesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(voicesBox); paintVoices()
        body.addView(choice("Высота голоса", listOf("1.0" to "Обычная", "1.3" to "Моложе"), { "%.1f".format(Locale.US, prefs.pitch) }) { prefs.pitch = it.toFloat(); voice?.say("Так я говорю.") })
        body.addView(choice("Темп голоса", listOf("0.8" to "Медленно", "1.0" to "Обычно", "1.3" to "Быстро"), { "%.1f".format(Locale.US, prefs.speakRate) }) { prefs.speakRate = it.toFloat() })

        body.addView(ui.h("Помощник рядом"))
        body.addView(ui.button("Как помочь ребёнку") { startActivity(Intent(this, ParentsActivity::class.java)) })
        body.addView(ui.button("Для взрослого: выбор, слова семьи, «Про меня»") { startActivity(Intent(this, MyPhrasesActivity::class.java)) })

        body.addView(ui.note("Настройки клавиатуры применяются, когда она открывается заново."))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Все настройки списком") { startActivity(Intent(this, OptionsActivity::class.java)) })
        bottom.addView(ui.button("Готово", main = true) { finish() })
        setContentView(ui.screen(body, bottom))
    }
}
