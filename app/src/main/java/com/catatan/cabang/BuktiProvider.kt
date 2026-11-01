package com.catatan.cabang

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns

// Pintu masuk untuk aplikasi Klaim & Kirim:
//  - call("order", key, extras)  : buat / perbarui catatan pesanan
//  - openFile(.../foto/<key>/before.jpg atau after.jpg, "w") : tulis foto
// Juga dipakai untuk membagikan foto ke aplikasi lain (baca).
class BuktiProvider : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri): String = "image/jpeg"

    private fun parse(uri: Uri): Pair<String, String>? {
        val seg = uri.pathSegments
        if (seg.size != 3 || seg[0] != "foto") return null
        val kind = if (seg[2].startsWith("after")) "after" else "before"
        return Pair(seg[1], kind)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val pk = parse(uri) ?: return null
        val f = BuktiStore.photoFile(ctx, pk.first, pk.second)
        if (!f.exists()) return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols)
        val row = ArrayList<Any?>()
        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(BuktiStore.displayName(ctx, BuktiStore.safe(pk.first), pk.second))
                OpenableColumns.SIZE -> row.add(f.length())
                else -> row.add(null)
            }
        }
        c.addRow(row.toTypedArray())
        return c
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val pk = parse(uri) ?: return null
        val f = BuktiStore.photoFile(ctx, pk.first, pk.second)
        if (mode.contains("w")) {
            f.parentFile?.mkdirs()
            val h = Handler(Looper.getMainLooper())
            h.postDelayed({ Store.ping() }, 1500)
            h.postDelayed({ Store.ping() }, 4000)
            return ParcelFileDescriptor.open(
                f,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            )
        }
        if (!f.exists()) return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "order") return null
        val ctx = context ?: return null
        val key = arg ?: return null
        BuktiStore.upsert(ctx, key, extras)
        Store.ping()
        val r = Bundle()
        r.putBoolean("ok", true)
        return r
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
