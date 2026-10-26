package app.pulk

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException

/** Only the explicit share snapshot, only for reading; Android grants access to the chosen recipient. */
class TraceProvider : ContentProvider() {
    override fun onCreate() = true
    private fun checked(uri: Uri): java.io.File {
        if (!GestureTraces.isShareUri(context!!, uri)) throw FileNotFoundException()
        return GestureTraces.sharedFile(context!!)
    }
    override fun getType(uri: Uri): String { checked(uri); return "application/x-ndjson" }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = synchronized(GestureTraces) {
        if (mode != "r") throw FileNotFoundException("Read only")
        ParcelFileDescriptor.open(checked(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): MatrixCursor = synchronized(GestureTraces) {
        val f = checked(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        MatrixCursor(columns).apply { addRow(columns.map {
            when (it) { OpenableColumns.DISPLAY_NAME -> "pulk-traces.jsonl"; OpenableColumns.SIZE -> f.length(); else -> null }
        }) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
}
