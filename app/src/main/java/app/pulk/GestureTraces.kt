package app.pulk

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Opt-in, single-letter lesson samples. Never receives the editor's text or absolute touch position. */
object GestureTraces {
    const val LESSON = 0x5520
    const val BUBBLES = 0x5521
    const val SPEAK_LESSON = 0x5522
    private const val MAX_BYTES = 5 * 1024 * 1024
    val header: String = JSONObject().put("schema", "pulk-traces").put("version", 1)
        .put("coordinates", "filtered-dp-relative").put("unit", "stroke-or-jab").toString()

    data class Label(val field: Int, val target: String, val mode: String, val lang: String,
                     val hand: String, val tol: Double, val bias: List<Double>, val generation: Int, val session: Int)

    fun file(ctx: Context) = File(ctx.filesDir, "gesture-traces.jsonl")
    private fun shares(ctx: Context) = File(ctx.cacheDir, "gesture-trace-shares")
    fun sharedFile(ctx: Context) = File(shares(ctx),
        ctx.getSharedPreferences("trace-share", Context.MODE_PRIVATE).getString("token", "none") + ".jsonl")

    private fun eraseShares(ctx: Context) {
        shares(ctx).listFiles()?.forEach { f ->
            f.writeText("") // also empties an already open descriptor; a later export uses a different file
            f.delete() // an open Windows test descriptor may keep an empty file until the next cleanup
        }
    }

    @Synchronized fun consent(ctx: Context, enabled: Boolean) {
        val p = Prefs(ctx)
        p.traceGeneration++ // also invalidates a gesture held while consent changed
        p.recordTraces = enabled
    }

    @Synchronized fun clear(ctx: Context) {
        Prefs(ctx).traceGeneration++
        ctx.getSharedPreferences("trace-share", Context.MODE_PRIVATE).edit().clear().commit()
        // Truncate both copies: an old share URI must not retain the erased samples.
        if (file(ctx).exists()) file(ctx).writeText("")
        eraseShares(ctx)
    }

    @Synchronized fun count(ctx: Context): Int = try {
        if (!file(ctx).exists()) 0 else file(ctx).useLines { lines -> lines.count { it.isNotBlank() }.minus(1).coerceAtLeast(0) }
    } catch (_: IOException) { 0 }

    /** The path is the recogniser's input after smoothing; undo tol to keep distances in dp.
     * Each jab is separate, so neither a typed word nor a sequence of letters is stored.
     * Taps/service keys are omitted: replay needs no absolute location on the keyboard.
     */
    @Synchronized fun append(ctx: Context, label: Label, pts: DoubleArray, times: LongArray, scaleDp: Double, got: String): Boolean {
        val p = Prefs(ctx)
        if (!p.recordTraces || label.generation != p.traceGeneration || pts.size < 4 || pts.size > 4096 ||
            times.size != pts.size / 2 || scaleDp <= 0 || !scaleDp.isFinite() || label.tol <= 0 || !label.tol.isFinite()) return false
        if (label.mode == "pulse" && (2 until pts.size step 2).maxOf { kotlin.math.hypot(pts[it] - pts[0], pts[it + 1] - pts[1]) } < Pulse.P_MIN) return false
        if (pts.any { !it.isFinite() } || label.bias.any { !it.isFinite() } || times.asList().zipWithNext().any { (a, b) -> b < a }) return false
        val points = JSONArray()
        val signX = if (label.hand == "left") -1 else 1
        for (i in times.indices) points.put(JSONArray().put((pts[2 * i] - pts[0]) * scaleDp * label.tol * signX)
            .put(-(pts[2 * i + 1] - pts[1]) * scaleDp * label.tol).put(times[i] - times[0]))
        val line = JSONObject().put("mode", label.mode).put("target", label.target).put("recognized", got)
            .put("lang", label.lang).put("hand", label.hand).put("tol", label.tol).put("scaleDp", scaleDp)
            .put("bias", JSONArray(label.bias)).put("points", points).toString() + "\n"
        return try {
            val f = file(ctx)
            if (f.length() + line.toByteArray(Charsets.UTF_8).size + header.length + 1 > MAX_BYTES) return false
            if (f.length() == 0L) f.writeText(header + "\n")
            f.appendText(line)
            true
        } catch (_: IOException) { false } // storage full: typing must still work
    }

    /** A frozen copy: later typing is never added to an already shared file. */
    @Synchronized fun snapshot(ctx: Context): Uri? {
        if (count(ctx) == 0) return null
        eraseShares(ctx)
        // A recipient of an earlier export cannot read a later export via the old grant.
        val token = java.util.UUID.randomUUID().toString()
        shares(ctx).mkdirs()
        file(ctx).copyTo(File(shares(ctx), "$token.jsonl"))
        ctx.getSharedPreferences("trace-share", Context.MODE_PRIVATE).edit().putString("token", token).commit()
        return Uri.parse("content://${ctx.packageName}.traces/$token/traces.jsonl")
    }

    internal fun isShareUri(ctx: Context, uri: Uri): Boolean {
        val token = ctx.getSharedPreferences("trace-share", Context.MODE_PRIVATE).getString("token", null) ?: return false
        return uri.authority == "${ctx.packageName}.traces" && uri.path == "/$token/traces.jsonl"
    }
}
