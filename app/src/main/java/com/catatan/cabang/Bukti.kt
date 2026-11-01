package com.catatan.cabang

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Satu pesanan ke satu ID penerima = satu bukti kirim.
class Order(val key: String) {
    var id = ""
    var nick = ""
    var target = 0L
    var sent = 0L
    var time = 0L
    var done = false
    var t = 0L          // waktu perubahan terakhir (untuk sinkron)
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

    fun ordersJson(list: List<Order>): JSONArray {
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
            j.put("t", o.t)
            val sv = JSONArray()
            for (s in o.servers) sv.put(s)
            j.put("servers", sv)
            arr.put(j)
        }
        return arr
    }

    fun parseOrders(arr: JSONArray): ArrayList<Order> {
        val out = ArrayList<Order>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ord = Order(o.optString("key"))
            ord.id = o.optString("id")
            ord.nick = o.optString("nick")
            ord.target = o.optLong("target")
            ord.sent = o.optLong("sent")
            ord.time = o.optLong("time")
            ord.done = o.optBoolean("done")
            ord.t = o.optLong("t")
            val sv = o.optJSONArray("servers")
            if (sv != null) for (j in 0 until sv.length()) ord.servers.add(sv.optString(j))
            out.add(ord)
        }
        return out
    }

    fun load(c: Context): ArrayList<Order> = synchronized(lock) { loadLocked(c) }

    private fun loadLocked(c: Context): ArrayList<Order> {
        var out = ArrayList<Order>()
        try {
            val s = prefs(c).getString(KEY, null)
            if (s != null) out = parseOrders(JSONArray(s))
        } catch (e: Exception) {
        }
        out.sortByDescending { it.time }
        return out
    }

    private fun saveLocked(c: Context, list: List<Order>) {
        prefs(c).edit().putString(KEY, ordersJson(list).toString()).apply()
    }

    // Dipakai sinkron: simpan apa adanya (tanpa mengubah waktu perubahan)
    fun saveRaw(c: Context, list: List<Order>) {
        synchronized(lock) { saveLocked(c, list) }
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
            o.t = System.currentTimeMillis()
            saveLocked(c, list)
        }
    }

    fun delete(c: Context, o: Order) {
        synchronized(lock) {
            val list = loadLocked(c)
            list.removeAll { it.key == o.key }
            saveLocked(c, list)
        }
        Store.addTomb(c, "o|" + o.key)
        for (k in arrayOf("before", "after")) {
            try {
                photoFile(c, o.key, k).delete()
            } catch (e: Exception) {
            }
        }
        SyncEngine.init(c)
        SyncEngine.kick()
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

    // Gabungkan pesanan lokal dan jarak jauh: perubahan terbaru menang, daftar server digabung
    fun mergeOrders(local: List<Order>, remote: List<Order>, tomb: Map<String, Long>): ArrayList<Order> {
        val m = LinkedHashMap<String, Order>()
        for (o in local) m[o.key] = o
        for (r in remote) {
            val l = m[r.key]
            if (l == null) {
                m[r.key] = r
            } else {
                val win = if (r.t > l.t) r else l
                val lose = if (win === r) l else r
                for (s in lose.servers) if (!win.servers.contains(s)) win.servers.add(s)
                if (lose.done) win.done = true
                if (lose.sent > win.sent) win.sent = lose.sent
                if (win.nick.isBlank()) win.nick = lose.nick
                if (win.id.isBlank()) win.id = lose.id
                if (lose.t > win.t) win.t = lose.t
                m[r.key] = win
            }
        }
        val out = ArrayList<Order>()
        for (o in m.values) {
            val tt = tomb["o|" + o.key] ?: 0L
            if (tt > 0L && tt >= o.t) continue
            out.add(o)
        }
        out.sortByDescending { it.time }
        return out
    }
}
