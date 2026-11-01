package com.catatan.cabang

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Penyimpanan bersama: dipakai layar dan penerima data (APK 1 dan APK 3).
object Store {
    const val ACTION_AKUN = "com.catatan.cabang.AKUN_BARU"
    const val ACTION_UPDATE = "com.catatan.cabang.UPDATE_SERVER"

    private const val PREF = "catatan"
    private const val KEY = "akun_v2"
    private val lock = Any()

    @Volatile var listener: (() -> Unit)? = null
    @Volatile var changed = false

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- JSON ----------
    fun pick(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "")
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    fun recsJson(list: List<Rec>): JSONArray {
        val arr = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("email", r.email)
            o.put("server", r.server)
            o.put("level", r.level)
            o.put("keterangan", r.note)
            arr.put(o)
        }
        return arr
    }

    fun foldersJson(list: List<Folder>): JSONArray {
        val arr = JSONArray()
        for (f in list) {
            val o = JSONObject()
            o.put("name", f.name)
            o.put("id", f.id)
            o.put("items", recsJson(f.items))
            arr.put(o)
        }
        return arr
    }

    fun parseRecs(arr: JSONArray): ArrayList<Rec> {
        val out = ArrayList<Rec>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Rec(
                    pick(o, "email", "Email", "akun"),
                    pick(o, "server", "Server"),
                    pick(o, "level", "Level"),
                    pick(o, "keterangan", "Keterangan", "note", "ket")
                )
            )
        }
        return out
    }

    fun folderFrom(o: JSONObject): Folder {
        val name = o.optString("name", "Folder")
        val f = Folder(name, parseRecs(o.optJSONArray("items") ?: JSONArray()))
        f.id = pick(o, "id", "ID").trim()
        return f
    }

    // ---------- simpan / muat ----------
    fun load(c: Context): ArrayList<Folder> = synchronized(lock) { loadLocked(c) }

    private fun loadLocked(c: Context): ArrayList<Folder> {
        val out = ArrayList<Folder>()
        try {
            val s = prefs(c).getString(KEY, null)
            if (s != null) {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    out.add(folderFrom(o))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(c: Context, list: List<Folder>) {
        synchronized(lock) { saveLocked(c, list) }
    }

    private fun saveLocked(c: Context, list: List<Folder>) {
        prefs(c).edit().putString(KEY, foldersJson(list).toString()).apply()
    }

    private fun notifyChanged() {
        changed = true
        listener?.invoke()
    }

    fun ping() {
        notifyChanged()
    }

    // ---------- status keterangan ----------
    // 3 = sudah terpakai, 2 = 1000 dm, 1 = VG, 0 = lainnya/kosong
    fun rank(note: String): Int {
        val t = note.lowercase()
        return when {
            t.contains("terpakai") -> 3
            t.contains("dm") && t.any { it.isDigit() } -> 2
            t.contains("vg") -> 1
            else -> 0
        }
    }

    // Server siap kirim: keterangan "1000 dm" DAN level minimal 15 (diamond 1000 tapi level di bawah 15 belum siap)
    const val READY_LEVEL = 15

    fun isReady(r: Rec): Boolean = rank(r.note) == 2 && num(r.level) >= READY_LEVEL

    private fun num(s: String): Long = Regex("\\d+").find(s)?.value?.toLongOrNull() ?: 0L

    private fun sameServer(a: String, b: String): Boolean {
        val x = a.trim()
        val y = b.trim()
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val nx = num(x)
        return nx > 0 && nx == num(y)
    }

    private fun findFolder(list: List<Folder>, id: String, email: String): Folder? {
        if (id.isNotEmpty()) {
            list.firstOrNull { it.id == id }?.let { return it }
        }
        if (email.isNotEmpty()) {
            return list.firstOrNull { it.name.trim().equals(email, true) && (it.id.isEmpty() || it.id == id) }
        }
        return null
    }

    // ---------- dari APK 1: akun baru + daftar server ----------
    fun upsertFromAts(c: Context, emailIn: String, idIn: String, servers: List<String>) {
        val email = emailIn.trim()
        val id = idIn.trim()
        if (email.isEmpty() && id.isEmpty()) return
        synchronized(lock) {
            val list = loadLocked(c)
            var f = findFolder(list, id, email)
            if (f == null) {
                f = Folder(email, ArrayList(), id)
                list.add(f)
            } else {
                if (f.name.isBlank() && email.isNotEmpty()) f.name = email
                if (f.id.isEmpty() && id.isNotEmpty()) f.id = id
            }
            for (sv in servers) {
                val s = sv.trim()
                if (s.isEmpty()) continue
                if (f.items.none { sameServer(it.server, s) }) {
                    f.items.add(Rec("", s, "1", ""))
                }
            }
            saveLocked(c, list)
        }
        notifyChanged()
    }

    // ---------- dari APK 3: update level / keterangan ----------
    fun upsertFromClaim(c: Context, idIn: String, serverIn: String, level: String, note: String) {
        val id = idIn.trim()
        val server = serverIn.trim()
        if (id.isEmpty() || server.isEmpty()) return
        synchronized(lock) {
            val list = loadLocked(c)
            var f = findFolder(list, id, "")
            if (f == null) {
                f = Folder("", ArrayList(), id)
                list.add(f)
            }
            var r = f.items.firstOrNull { sameServer(it.server, server) }
            if (r == null) {
                r = Rec("", server, "1", "")
                f.items.add(r)
            }
            val lv = level.trim()
            if (lv.isNotEmpty() && (r.level.isBlank() || num(lv) > num(r.level))) r.level = lv
            val nt = note.trim()
            if (nt.isNotEmpty() && rank(nt) >= rank(r.note)) r.note = nt
            saveLocked(c, list)
        }
        notifyChanged()
    }
}
