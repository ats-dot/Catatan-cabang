package com.catatan.cabang

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// Satu pesanan ke satu ID penerima = satu bukti kirim.
class Order(val key: String) {
    var id = ""
    var nick = ""
    var target = 0L
    var sent = 0L
    var time = 0L
    var done = false
    val servers = ArrayList<String>()
}

object BuktiStore {
    private const val PREF = "catatan"
    private const val KEY = "bukti_v1"
    private val lock = Any()

    fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun dir(c: Context): File {
        val d = File(c.applicationContext.filesDir, "bukti")
        d.mkdirs()
        return d
    }

    fun photoFile(c: Context, key: String, kind: String) = File(dir(c), safe(key) + "_" + kind + ".jpg")

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun load(c: Context): ArrayList<Order> = synchronized(lock) { loadLocked(c) }

    private fun loadLocked(c: Context): ArrayList<Order> {
        val out = ArrayList<Order>()
        try {
            val s = prefs(c).getString(KEY, null)
            if (s != null) {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val ord = Order(o.optString("key"))
                    ord.id = o.optString("id")
                    ord.nick = o.optString("nick")
                    ord.target = o.optLong("target")
                    ord.sent = o.optLong("sent")
                    ord.time = o.optLong("time")
                    ord.done = o.optBoolean("done")
                    val sv = o.optJSONArray("servers")
                    if (sv != null) for (j in 0 until sv.length()) ord.servers.add(sv.optString(j))
                    out.add(ord)
                }
            }
        } catch (e: Exception) {
        }
        out.sortByDescending { it.time }
        return out
    }

    private fun saveLocked(c: Context, list: List<Order>) {
        val arr = JSONArray()
        for (o in list) {
            val j = JSONObject()
            j.put("key", o.key)
            j.put("id", o.id)
            j.put("nick", o.nick)
            j.put("target", o.target)
            j.put("sent", o.sent)
            j.put("time", o.time)
            j.put("done", o.done)
            val sv = JSONArray()
            for (s in o.servers) sv.put(s)
            j.put("servers", sv)
            arr.put(j)
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    @Suppress("DEPRECATION")
    private fun str(ex: Bundle?, k: String): String? = ex?.get(k)?.toString()

    // Dipanggil dari aplikasi Klaim & Kirim. Semua field opsional kecuali key.
    fun upsert(c: Context, key: String, ex: Bundle?) {
        synchronized(lock) {
            val list = loadLocked(c)
            val found = list.firstOrNull { it.key == key }
            val o = found ?: Order(key).also {
                it.time = str(ex, "time")?.toLongOrNull() ?: System.currentTimeMillis()
                list.add(it)
            }
            str(ex, "id")?.trim()?.let { if (it.isNotEmpty()) o.id = it }
            str(ex, "nick")?.trim()?.let { if (it.isNotEmpty()) o.nick = it }
            str(ex, "target")?.toLongOrNull()?.let { if (it > 0) o.target = it }
            str(ex, "sent")?.toLongOrNull()?.let { o.sent = it }
            str(ex, "server")?.trim()?.let { if (it.isNotEmpty() && !o.servers.contains(it)) o.servers.add(it) }
            str(ex, "done")?.let { o.done = it == "true" }
            saveLocked(c, list)
        }
    }

    fun delete(c: Context, o: Order) {
        synchronized(lock) {
            val list = loadLocked(c)
            list.removeAll { it.key == o.key }
            saveLocked(c, list)
        }
        for (k in arrayOf("before", "after")) {
            try {
                photoFile(c, o.key, k).delete()
            } catch (e: Exception) {
            }
        }
    }

    // nama file yang dilihat penerima saat foto dibagikan
    fun displayName(c: Context, safeKey: String, kind: String): String {
        val o = load(c).firstOrNull { safe(it.key) == safeKey }
        val base = if (o != null && o.id.isNotBlank()) o.id else safeKey
        return base + "_" + kind + ".jpg"
    }

    fun decode(path: String, maxW: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (o.outWidth / (s * 2) >= maxW) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            BitmapFactory.decodeFile(path, o2)
        } catch (e: Throwable) {
            null
        }
    }

    // Bukti contoh untuk menguji tampilan (menu titik tiga)
    fun addSample(c: Context) {
        val key = "contoh_" + System.currentTimeMillis()
        val b = Bundle()
        b.putString("id", "555111222")
        b.putString("nick", "Rina_ML")
        b.putString("target", "3000")
        b.putString("sent", "3000")
        b.putString("done", "true")
        b.putString("server", "123456789 · server 2041")
        upsert(c, key, b)
        val b2 = Bundle()
        b2.putString("server", "123456789 · server 2087")
        upsert(c, key, b2)
        val b3 = Bundle()
        b3.putString("server", "987654321 · server 2133")
        upsert(c, key, b3)
        for (k in arrayOf("before", "after")) {
            try {
                val bmp = Bitmap.createBitmap(1200, 540, Bitmap.Config.ARGB_8888)
                val cv = Canvas(bmp)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = 0xFF2F2F2F.toInt()
                cv.drawRect(0f, 0f, 1200f, 540f, p)
                p.color = 0xFFEDEDED.toInt()
                p.textSize = 90f
                p.typeface = Typeface.DEFAULT_BOLD
                p.textAlign = Paint.Align.CENTER
                cv.drawText(if (k == "before") "BEFORE" else "AFTER", 600f, 290f, p)
                p.textSize = 36f
                p.color = 0xFFA3A3A3.toInt()
                cv.drawText("foto contoh", 600f, 350f, p)
                FileOutputStream(photoFile(c, key, k)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            } catch (e: Throwable) {
            }
        }
    }
}
