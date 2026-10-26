package app.pulk

import android.content.Context
import android.net.Uri
import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureTracesTest {
    private val ctx get() = RuntimeEnvironment.getApplication() as Context
    private lateinit var svc: PulkService
    private val pts = doubleArrayOf(100.0, 200.0, 115.0, 200.0, 130.0, 200.0, 150.0, 200.0)
    private val times = longArrayOf(3000, 3016, 3032, 3048)
    @Before fun setup() {
        ctx.getSharedPreferences("pulk", Context.MODE_PRIVATE).edit().clear().commit()
        GestureTraces.clear(ctx); Coach.reset(); Pulse.bias.fill(0.0)
        svc = Robolectric.setupService(PulkService::class.java)
        Coach.active = true; Coach.next = "н"; Coach.traceField = GestureTraces.LESSON
        input()
    }
    private fun input(type: Int = InputType.TYPE_CLASS_TEXT, pkg: String = ctx.packageName, field: Int = GestureTraces.LESSON, flags: Int = 0) {
        svc.startInput(EditorInfo().apply { inputType = type; packageName = pkg; fieldId = field; imeOptions = flags })
    }
    private fun record() = svc.recordTrace(svc.traceLabel(), pts, times, 1.0, "н")

    @Test fun offUntilExplicitConsent() {
        assertFalse(Prefs(ctx).recordTraces); assertNull(svc.traceLabel()); record()
        assertEquals(0, GestureTraces.count(ctx))
        GestureTraces.consent(ctx, true); record(); assertEquals(1, GestureTraces.count(ctx))
        GestureTraces.consent(ctx, false); record(); assertEquals(1, GestureTraces.count(ctx))
    }

    @Test fun passwordsIncognitoOtherAppsAndFreeAnswersNeverRecord() {
        GestureTraces.consent(ctx, true)
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
            input(InputType.TYPE_CLASS_TEXT or variation); assertNull(svc.traceLabel()); record()
        }
        input(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD); record()
        input(InputType.TYPE_NULL); record()
        input(pkg = "other.app"); record()
        input(field = Ui.ID_SPEAK); record()
        input(flags = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING); record()
        input(); Coach.pause(); record(); Coach.resume()
        svc.onFinishInputView(true); record()
        assertEquals(0, GestureTraces.count(ctx))
    }

    @Test fun changingFieldConsentOrErasingDiscardsPendingSamples() {
        GestureTraces.consent(ctx, true)
        val label = svc.traceLabel()!!
        input(pkg = "other.app"); svc.recordTrace(label, pts, times, 1.0, "н")
        input(); GestureTraces.consent(ctx, false); GestureTraces.consent(ctx, true)
        svc.recordTrace(label, pts, times, 1.0, "н"); assertEquals(0, GestureTraces.count(ctx))
        val next = svc.traceLabel()!!; record(); assertEquals(1, GestureTraces.count(ctx))
        GestureTraces.snapshot(ctx)
        GestureTraces.clear(ctx); svc.recordTrace(next, pts, times, 1.0, "н")
        assertEquals(0, GestureTraces.count(ctx)); assertEquals(0L, GestureTraces.sharedFile(ctx).length())
        assertNull(GestureTraces.snapshot(ctx))
    }

    @Test fun formatHasRelativePointsAndCanBeReplayed() {
        GestureTraces.consent(ctx, true); Prefs(ctx).hand = "left"; Prefs(ctx).tol = 1.5f
        record()
        val lines = GestureTraces.file(ctx).readLines()
        assertEquals(1, JSONObject(lines[0]).getInt("version"))
        val r = JSONObject(lines[1])
        assertEquals(setOf("mode", "target", "recognized", "lang", "hand", "tol", "scaleDp", "bias", "points"), r.keys().asSequence().toSet())
        val first = r.getJSONArray("points").getJSONArray(0)
        assertEquals(0.0, first.getDouble(0), 0.0); assertEquals(0.0, first.getDouble(1), 0.0); assertEquals(0L, first.getLong(2))
        assertEquals(-75.0, r.getJSONArray("points").getJSONArray(3).getDouble(0), 0.001)
        assertEquals(48L, r.getJSONArray("points").getJSONArray(3).getLong(2))
        assertEquals(mapOf("pulse" to (1 to 1)), TraceReplayTest.replay(GestureTraces.file(ctx)))
    }

    @Test fun exportIsReadOnlySnapshotAndOldGrantsCannotReadNextExport() {
        GestureTraces.consent(ctx, true); record()
        val uri = GestureTraces.snapshot(ctx)!!
        record(); assertEquals(2, GestureTraces.count(ctx))
        val provider = Robolectric.buildContentProvider(TraceProvider::class.java).create().get()
        val held = android.os.ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri, "r"))
        val snapshot = android.os.ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri, "r")).bufferedReader().use { it.readText() }
        assertEquals(2, snapshot.trim().lines().size)
        assertEquals("application/x-ndjson", provider.getType(uri))
        assertThrows(java.io.FileNotFoundException::class.java) { provider.openFile(uri, "w") }
        assertThrows(java.io.FileNotFoundException::class.java) { provider.openFile(Uri.parse("content://${ctx.packageName}.traces/../secret"), "r") }
        GestureTraces.snapshot(ctx)
        held.bufferedReader().use { assertEquals("", it.readText()) }
        assertThrows(java.io.FileNotFoundException::class.java) { provider.openFile(uri, "r") }
        GestureTraces.clear(ctx)
        assertEquals(0, GestureTraces.count(ctx))
    }

    @Test fun manifestOnlyAllowsExplicitReadGrants() {
        val pm = ctx.packageManager
        val provider = pm.resolveContentProvider("${ctx.packageName}.traces", 0)!!
        assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
        val permissions = pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions
        assertEquals(listOf("android.permission.VIBRATE"), permissions.toList())
    }
}
