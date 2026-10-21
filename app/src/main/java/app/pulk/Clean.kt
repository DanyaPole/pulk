package app.pulk

import android.content.Context
import java.io.File

/**
 * «Передать устройство чистым»: for a centre where one tablet goes from child to child. Everything Пульк keeps
 * goes — settings, phrases, «Про меня», the family's words and photos, the conversation, the journal, drops in
 * the pond, learned words — as if just installed. The open keyboard drops its words the next time it opens.
 */
object Clean {
    fun all(ctx: Context) {
        ctx.getSharedPreferences("pulk", Context.MODE_PRIVATE).edit().clear().commit()
        for (dir in listOf(ctx.filesDir, ctx.cacheDir)) dir.listFiles()?.forEach { it.deleteRecursively() }
        Prefs(ctx).forget = true   // the keyboard's words in memory: cleared as soon as it opens again
    }

    /** What is left after cleaning (for the test and the screen): files that are not Пульк's own. */
    fun leftovers(ctx: Context): List<File> = listOf(ctx.filesDir, ctx.cacheDir).flatMap { it.listFiles()?.toList() ?: emptyList() }
}
