package app.pulk

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager

/**
 * «Проверка на этом телефоне»: a short list to try by hand, each marked «Работает · Не работает · Пропустить»,
 * and «Сообщить о проблеме» — Android's own «Поделиться» with the report; Пульк itself sends nothing.
 */
class CheckActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        val ui = Ui(this)
        val body = ui.column()
        body.addView(ui.title("Проверка на этом телефоне"))
        body.addView(ui.note("Попробуйте по очереди и отметьте. Пропускать можно что угодно. Отметки остаются только на телефоне."))
        for (i in Check.ITEMS) {
            body.addView(ui.card(ui.note(i.how), ui.choice(i.what, listOf("ok" to "Работает", "no" to "Не работает", "skip" to "Пропустить"),
                { Check.decode(prefs.check)[i.id] ?: "" }) { prefs.check = Check.encode(Check.decode(prefs.check) + (i.id to it)) }))
        }
        body.addView(ui.note("«Сообщить о проблеме» откроет «Поделиться»: выберите почту или мессенджер и допишите, что случилось. В письмо попадут версия, модель телефона, настройки и отметки — без набранного текста, фраз и имён."))
        val bottom = ui.bottomBar()
        bottom.addView(ui.button("Сообщить о проблеме", main = true) { share() })
        bottom.addView(ui.button("Готово") { finish() })
        setContentView(ui.screen(body, bottom))
    }

    fun reportText(): String {
        val m = resources.displayMetrics
        val phone = Check.Phone(
            version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?",
            maker = Build.MANUFACTURER ?: "", model = Build.MODEL ?: "",
            android = "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
            screen = "${(m.widthPixels / m.density).toInt()}×${(m.heightPixels / m.density).toInt()} dp",
            enabled = Ui.enabled(this), selected = Ui.selected(this),
            talkBack = (getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled)
        return Check.report(phone, prefs, Check.decode(prefs.check))
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Пульк: проблема")
            .putExtra(Intent.EXTRA_TEXT, reportText())
        try { startActivity(Intent.createChooser(send, "Сообщить о проблеме")) } catch (_: Exception) {}
    }
}
