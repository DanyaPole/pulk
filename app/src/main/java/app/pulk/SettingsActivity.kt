package app.pulk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * The home screen (it opens from the launcher and from the keyboard): whether Пульк is on, a field to try it,
 * and three big buttons under the thumb — lessons, fitting to the hand, settings. The first time, the wizard.
 */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var ui: Ui

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); ui = Ui(this)
        if (!prefs.wizardDone) startActivity(Intent(this, WizardActivity::class.java))
    }

    override fun onResume() { super.onResume(); build() }   // back from Android's settings: show where things are now

    private fun build() {
        val body = ui.column()
        body.addView(ui.title("Пульк"))
        body.addView(ui.note("Клавиатура для одной руки: все буквы — в звезде под большим пальцем."))
        val on = Ui.enabled(this); val sel = Ui.selected(this)
        body.addView(ui.card(ui.text(when {
            on && sel -> "✓ Пульк включён и выбран"
            on -> "Пульк включён, но выбрана другая клавиатура"
            else -> "Пульк ещё не включён"
        }, 16f, ui.ink, true), ui.note(if (on && sel) "Удержи ⌄ на клавиатуре — откроются настройки. Удержи RU/EN — список клавиатур." else "Кнопка «Включить» внизу проведёт по шагам.")))
        body.addView(ui.button("Говорить — печатаю, телефон произносит") { startActivity(Intent(this, SpeakActivity::class.java)) })
        body.addView(ui.button("Кому и как помогает") { startActivity(Intent(this, AudienceActivity::class.java)) })
        body.addView(ui.h("Попробуй"))
        body.addView(EditText(this).apply {
            hint = "Нажми и печатай"; setHintTextColor(ui.muted); setTextColor(ui.ink); textSize = 20f; minLines = 2; gravity = Gravity.TOP
        })
        val bottom = ui.bottomBar()
        if (!on || !sel) bottom.addView(ui.button(if (!on) "Включить клавиатуру" else "Выбрать Пульк", main = true) {
            if (!on) startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            else (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        bottom.addView(ui.button("Обучение", main = on && sel && prefs.calibrated) { startActivity(Intent(this, TutorialActivity::class.java)) })
        bottom.addView(ui.button(if (prefs.calibrated) "Под мою руку — заново" else "Под мою руку — 30 секунд", main = on && sel && !prefs.calibrated) {
            startActivity(Intent(this, CalibrationActivity::class.java))
        })
        bottom.addView(ui.button("Настройки") { startActivity(Intent(this, OptionsActivity::class.java)) })
        setContentView(ui.screen(body, bottom))
    }
}
