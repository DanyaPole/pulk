package app.pulk

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Offline replay: $env:PULK_TRACES='C:\path\pulk-traces.jsonl'; .\gw.ps1 testDebugUnitTest --tests app.pulk.TraceReplayTest */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TraceReplayTest {
    companion object {
        /** Do not print samples: only aggregate accuracy per mode. Uses the actual Android recogniser. */
        fun replay(f: File): Map<String, Pair<Int, Int>> {
            require(f.length() <= 5 * 1024 * 1024) { "Trace file exceeds 5 MiB" }
            val lines = f.readLines()
            val header = JSONObject(lines.first())
            require(header.getString("schema") == "pulk-traces" && header.getInt("version") == 1 &&
                header.getString("coordinates") == "filtered-dp-relative") { "Unsupported trace schema" }
            val result = linkedMapOf<String, Pair<Int, Int>>()
            val biasBefore = Pulse.bias.copyOf()
            try {
                for (line in lines.drop(1).filter { it.isNotBlank() }) {
                    val r = JSONObject(line)
                    val scale = r.getDouble("scaleDp") * r.getDouble("tol")
                    require(scale > 0 && scale.isFinite())
                    val points = r.getJSONArray("points")
                    require(points.length() in 2..2048)
                    val pts = DoubleArray(points.length() * 2)
                    var time = 0L
                    val signX = if (r.getString("hand") == "left") -1 else 1
                    for (i in 0 until points.length()) {
                        val p = points.getJSONArray(i)
                        val t = p.getLong(2)
                        require(t >= time && (i != 0 || t == 0L)); time = t
                        pts[2 * i] = p.getDouble(0) * signX / scale
                        pts[2 * i + 1] = -p.getDouble(1) / scale
                    }
                    require(pts.all { it.isFinite() } && pts[0] == 0.0 && pts[1] == 0.0)
                    val bias = r.getJSONArray("bias")
                    require(bias.length() == 8)
                    for (i in Pulse.bias.indices) Pulse.bias[i] = bias.getDouble(i)
                    val mode = r.getString("mode")
                    val table = Pulse.table(r.getString("lang"), false)
                    val got = when (mode) {
                        "pulse" -> Pulse.resolve(table, pts).v
                        "jabs" -> Pulse.jab(table, pts).v
                        "petals" -> Pulse.jabPetal(pts).toString()
                        else -> error("Unknown trace mode")
                    }
                    val (right, n) = result[mode] ?: (0 to 0)
                    result[mode] = (right + if (got == r.getString("target")) 1 else 0) to (n + 1)
                }
            } finally { biasBefore.copyInto(Pulse.bias) }
            for ((mode, counts) in result) println("traces $mode: ${counts.first}/${counts.second} = ${counts.first.toDouble() / counts.second}")
            return result
        }
    }

    @Test fun fixtureAndOptionalLocalFile() {
        assertEquals(mapOf("pulse" to (1 to 2), "jabs" to (1 to 1), "petals" to (1 to 1)), replay(File("../tests/fixtures/gesture-traces.jsonl")))
        System.getenv("PULK_TRACES")?.takeIf { it.isNotBlank() }?.let { assertTrue(replay(File(it)).isNotEmpty()) }
    }
}
