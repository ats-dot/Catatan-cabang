package com.catatan.cabang

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Sinkron antar HP lewat Firebase Realtime Database (REST, tanpa SDK).
// Data akun dan bukti kirim (teks) disimpan satu nilai teks di /s/<kunci>/akun dan TIDAK dihapus otomatis.
// Foto disimpan terpisah di /s/<kunci>/foto/... dan salinan online dihapus setelah 3 hari.
// "Kunci" = kata sandi: hanya yang tahu alamat database dan kunci yang bisa membaca atau menulis.
object SyncEngine {
    private const val PREF = "sync"
    private const val KEEP_MS = 3L * 24 * 60 * 60 * 1000
    private const val POLL_MS = 25_000L

    @Volatile private var ctx: Context? = null
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var again = false
    @Volatile private var periodic = false
    @Volatile var lastMsg = ""

    fun init(c: Context) {
        if (ctx == null) ctx = c.applicationContext
    }

    private fun sp() = ctx?.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    var url: String
        get() = sp()?.getString("url", "") ?: ""
        set(v) {
            sp()?.edit()?.putString("url", v.trim().trimEnd('/'))?.apply()
        }

    var key: String
        get() = sp()?.getString("key", "") ?: ""
        set(v) {
            sp()?.edit()?.putString("key", v.trim())?.apply()
        }

    var on: Boolean
        get() = sp()?.getBoolean("on", false) ?: false
        set(v) {
            sp()?.edit()?.putBoolean("on", v)?.apply()
        }

    private var lastOk: Long
        get() = sp()?.getLong("last_ok", 0L) ?: 0L
        set(v) {
            sp()?.edit()?.putLong("last_ok", v)?.apply()
        }

    private var lastVer: String
        get() = sp()?.getString("ver", "") ?: ""
        set(v) {
            sp()?.edit()?.putString("ver", v)?.apply()
        }

    fun configured(): Boolean = on && url.startsWith("https://") && key.length >= 16

    fun newKey(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02x", x))
        return sb.toString()
    }

    fun statusText(): String {
        if (!on) return "Sinkron mati"
        val t = lastOk
        val ok = if (t > 0L) "Terakhir berhasil " + SimpleDateFormat("d MMM, HH.mm", Locale("id", "ID")).format(Date(t)) else "Belum pernah berhasil"
        return if (lastMsg.isNotEmpty() && lastMsg != "Tersinkron" && lastMsg != "Tidak ada perubahan") ok + ". " + lastMsg else ok
    }

    // ---------- jadwal ----------
    private val kickRun = Runnable { syncNow(true, null) }

    // Ada perubahan lokal: sinkron sebentar lagi (digabung kalau banyak perubahan beruntun)
    fun kick() {
        if (ctx == null || !configured()) return
        ui.removeCallbacks(kickRun)
        ui.postDelayed(kickRun, 2500L)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!periodic) return
            if (configured()) syncNow(false, null)
            ui.postDelayed(this, POLL_MS)
        }
    }

    fun startPeriodic() {
        if (periodic) return
        periodic = true
        ui.postDelayed(tick, POLL_MS)
    }

    fun stopPeriodic() {
        periodic = false
        ui.removeCallbacks(tick)
    }

    // full = false: hanya memeriksa penanda versi (kecil); data penuh diambil kalau ada perubahan dari HP lain
    fun syncNow(full: Boolean, done: ((String) -> Unit)?) {
        val c = ctx ?: return
        if (!configured()) {
            done?.invoke("Sinkron belum diatur")
            return
        }
        if (running) {
            again = true
            done?.invoke("Sedang berjalan, diulang sesudahnya")
            return
        }
        running = true
        Thread {
            var msg = ""
            try {
                msg = runOnce(c, full)
                while (again) {
                    again = false
                    msg = runOnce(c, true)
                }
            } catch (e: IOException) {
                msg = "Tidak bisa terhubung. Periksa internet"
            } catch (e: Throwable) {
                msg = "Gagal: " + e.javaClass.simpleName
            } finally {
                running = false
            }
            lastMsg = msg
            ui.post { done?.invoke(msg) }
        }.start()
    }

    // ---------- HTTP ----------
    private class Resp(val code: Int, val body: String, val etag: String?)

    private fun http(method: String, u: String, body: String?, headers: Map<String, String>): Resp {
        val conn = URL(u).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 25_000
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Resp(code, text, conn.getHeaderField("ETag"))
        } finally {
            conn.disconnect()
        }
    }

    // Nilai teks dari Firebase ("null" = belum ada)
    private fun readString(r: Resp): String? {
        val b = r.body.trim()
        if (b.isEmpty() || b == "null") return null
        return try {
            JSONTokener(b).nextValue() as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun explain(code: Int): String = when (code) {
        401, 403 -> "Ditolak (HTTP " + code + "). Periksa Rules di Firebase"
        404 -> "Alamat database salah (HTTP 404)"
        else -> "Gagal (HTTP " + code + ")"
    }

    private fun base() = url + "/s/" + key

    private fun readVer(): String? = readString(http("GET", base() + "/ver.json", null, emptyMap()))

    private fun bumpVer() {
        val v = System.currentTimeMillis().toString() + "-" + (Math.random() * 1_000_000).toInt()
        val r = http("PUT", base() + "/ver.json", JSONObject.quote(v), emptyMap())
        if (r.code in 200..299) lastVer = v
    }

    // ---------- satu putaran sinkron ----------
    private fun runOnce(c: Context, full: Boolean): String {
        val verBefore = readVerChecked() ?: return lastMsg.ifEmpty { "Gagal membaca versi" }
        if (!full && verBefore.first && verBefore.second == lastVer) return "Tidak ada perubahan"

        var changed = false
        var pushed = false
        for (attempt in 0 until 3) {
            val g = http("GET", base() + "/akun.json", null, mapOf("X-Firebase-ETag" to "true"))
            if (g.code !in 200..299) return explain(g.code)
            val remote = readString(g)
            val m = Store.mergeAll(c, remote)
            if (m.localChanged) changed = true
            val push = m.pushText ?: break
            val hdr = HashMap<String, String>()
            hdr["if-match"] = g.etag ?: "null_etag"
            val p = http("PUT", base() + "/akun.json", JSONObject.quote(push), hdr)
            if (p.code == 412) continue
            if (p.code !in 200..299) return explain(p.code)
            pushed = true
            break
        }

        val ph = syncPhotos(c)
        if (ph.second) changed = true
        if (pushed || ph.first) bumpVer() else if (verBefore.first) lastVer = verBefore.second
        lastOk = System.currentTimeMillis()
        if (changed) Store.refreshUi()
        return "Tersinkron"
    }

    // Pair(ada versi online?, nilainya). null = gagal membaca
    private fun readVerChecked(): Pair<Boolean, String>? {
        val r = http("GET", base() + "/ver.json", null, emptyMap())
        if (r.code !in 200..299) {
            lastMsg = explain(r.code)
            return null
        }
        val v = readString(r)
        return Pair(v != null, v ?: "")
    }

    // ---------- foto ----------
    // Hasil: Pair(ada foto yang diunggah, ada foto yang diunduh)
    private fun syncPhotos(c: Context): Pair<Boolean, Boolean> {
        var up = false
        var down = false
        val now = System.currentTimeMillis()
        val orders = BuktiStore.load(c).filter { now - it.time < KEEP_MS }
        val ir = http("GET", base() + "/fidx.json", null, emptyMap())
        if (ir.code !in 200..299) return Pair(false, false)
        var idx = JSONObject()
        val it0 = readString(ir)
        if (it0 != null) {
            try {
                idx = JSONObject(it0)
            } catch (e: Exception) {
            }
        }
        var idxChanged = false
        for (o in orders) {
            for (kind in arrayOf("before", "after")) {
                val name = BuktiStore.safe(o.key) + "_" + kind
                val f = BuktiStore.photoFile(c, o.key, kind)
                val has = idx.has(name)
                if (f.exists() && !has) {
                    val bytes = jpegBytes(f.path)
                    if (bytes != null) {
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val r = http("PUT", base() + "/foto/" + name + ".json", JSONObject.quote(b64), emptyMap())
                        if (r.code in 200..299) {
                            idx.put(name, now)
                            idxChanged = true
                            up = true
                        }
                    }
                } else if (!f.exists() && has) {
                    val r = http("GET", base() + "/foto/" + name + ".json", null, emptyMap())
                    val b64 = if (r.code in 200..299) readString(r) else null
                    if (b64 != null) {
                        try {
                            val data = Base64.decode(b64, Base64.NO_WRAP)
                            f.parentFile?.mkdirs()
                            f.writeBytes(data)
                            down = true
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        // Bersihkan salinan online yang lebih tua dari 3 hari (foto di HP tidak ikut terhapus)
        val names = ArrayList<String>()
        val ks = idx.keys()
        while (ks.hasNext()) names.add(ks.next())
        for (n in names) {
            if (now - idx.optLong(n) > KEEP_MS) {
                http("DELETE", base() + "/foto/" + n + ".json", null, emptyMap())
                idx.remove(n)
                idxChanged = true
            }
        }
        if (idxChanged) http("PUT", base() + "/fidx.json", JSONObject.quote(idx.toString()), emptyMap())
        return Pair(up, down)
    }

    // Foto dikecilkan dan dimampatkan sebelum diunggah (sekitar 100 KB)
    private fun jpegBytes(path: String): ByteArray? {
        val bmp = BuktiStore.decode(path, 1200) ?: return null
        var out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        if (out.size() > 350_000) {
            out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 50, out)
        }
        bmp.recycle()
        return out.toByteArray()
    }
}
