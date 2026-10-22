package app.pulk

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen on a small phone (320 × 640 dp) with «Крупный текст» and the phone's own font made larger, for each
 * hand: no text cut off at the bottom of its box, no word broken in the middle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FitTest {
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    private var checked = 0
    private fun problems(a: Activity, name: String): List<String> {
        val root = a.findViewById<ViewGroup>(android.R.id.content)
        val d = a.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(d.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(d.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, d.widthPixels, d.heightPixels)
        val out = ArrayList<String>()
        checked += all(root).count { it is TextView && (it as TextView).layout != null }
        // nothing sticks out past the right edge (rows that scroll sideways on purpose aside)
        fun inSideScroll(v: View): Boolean { var p = v.parent; while (p is View) { if (p is android.widget.HorizontalScrollView) return true; p = p.parent }; return false }
        val xy = IntArray(2)
        for (v in all(root)) if (v.visibility == View.VISIBLE && v.width > 0 && !inSideScroll(v)) {
            v.getLocationInWindow(xy)
            if (xy[0] < -1 || xy[0] + v.width > d.widthPixels + 1) out.add("$name: ${v.javaClass.simpleName} «${(v as? TextView)?.text?.take(30) ?: ""}» вылезает за край")
        }
        for (t in all(root).filterIsInstance<TextView>()) {
            val l = t.layout ?: continue
            if (t.visibility != View.VISIBLE || t.text.isNullOrBlank()) continue
            val s = t.text.toString()
            val need = l.height + t.compoundPaddingTop + t.compoundPaddingBottom
            if (need > t.height + 2) out.add("$name: «${s.take(40)}» обрезан снизу ($need > ${t.height})")
            for (i in 0 until l.lineCount - 1) {
                val e = l.getLineEnd(i)
                if (e in 1 until s.length && s[e - 1].isLetter() && s[e].isLetter()) out.add("$name: «${s.take(40)}» слово разорвано: «${s.substring(maxOf(0, e - 6), minOf(s.length, e + 6))}»")
            }
        }
        return out
    }

    private fun screens(): List<Pair<String, Activity>> {
        fun <T : Activity> b(c: Class<T>, i: Intent? = null) = Robolectric.buildActivity(c, i).setup().get()
        return listOf(
            "Главная" to b(SettingsActivity::class.java),
            "Настройки" to b(OptionsActivity::class.java),
            "Ещё" to b(OptionsActivity::class.java, Intent().putExtra(OptionsActivity.MORE, true)),
            "Удобство" to b(ComfortActivity::class.java),
            "Кто печатает" to b(AudienceActivity::class.java),
            "Говорить" to b(SpeakActivity::class.java),
            "Разговор" to b(TalkActivity::class.java),
            "Мои фразы" to b(MyPhrasesActivity::class.java),
            "Родителям" to b(ParentsActivity::class.java),
            "Учимся говорить" to b(SpeakLessonActivity::class.java),
            "Тренировки" to b(DrillsActivity::class.java),
            "Пузыри" to b(BubblesActivity::class.java),
            "Пруд" to b(PondActivity::class.java),
            "Мастер" to b(WizardActivity::class.java),
            "Обучение" to b(TutorialActivity::class.java),
            "Скорость" to b(SpeedActivity::class.java),
            "Проверка" to b(CheckActivity::class.java),
        )
    }

    private fun check(hand: String) {
        RuntimeEnvironment.setFontScale(1.3f)
        Prefs(RuntimeEnvironment.getApplication()).apply { bigText = true; this.hand = hand; lang = "ru"; wizardDone = true; lessonsDone = 1 }
        val p = screens().flatMap { (n, a) -> problems(a, n) }
        Coach.reset()
        assertTrue(p.joinToString("\n"), p.isEmpty())
    }

    @Test fun bigTextFitsRightHand() = check("right")
    @Test fun bigTextFitsLeftHand() = check("left")
}
