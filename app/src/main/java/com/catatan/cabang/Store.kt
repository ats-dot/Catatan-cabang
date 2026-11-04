package com.catatan.cabang

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Penyimpanan bersama: dipakai layar, penerima data (APK 1 dan APK 3), dan sinkron antar HP.
object Store {
    const val ACTION_AKUN = "com.catatan.cabang.AKUN_BARU"
    const val ACTION_UPDATE = "com.catatan.cabang.UPDATE_SERVER"

    private const val PREF = "catatan"
    private const val KEY = "akun_v2"
    private const val TOMB = "tomb_v1"
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
            o.put("t", r.t)
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
            o.put("note", f.note)
            o.put("t", f.t)
            o.put("items", recsJson(f.items))
            arr.put(o)
        }
        return arr
    }

    fun parseRecs(arr: JSONArray): ArrayList<Rec> {
        val out = ArrayList<Rec>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val r = Rec(
                pick(o, "email", "Email", "akun"),
                pick(o, "server", "Server"),
                pick(o, "level", "Level"),
                pick(o, "keterangan", "Keterangan", "note", "ket")
            )
            r.t = o.optLong("t")
            out.add(r)
        }
        return out
    }

    fun folderFrom(o: JSONObject): Folder {
        val name = o.optString("name", "Folder")
        val f = Folder(name, parseRecs(o.optJSONArray("items") ?: JSONArray()))
        f.id = pick(o, "id", "ID").trim()
        f.note = o.optString("note", "")
        f.t = o.optLong("t")
        return f
    }

    // ---------- kunci pencocokan (untuk sinkron) ----------
    fun fkey(f: Folder): String =
        if (f.id.isNotBlank()) "i:" + f.id.trim() else "n:" + f.name.trim().lowercase()

    fun rkey(r: Rec): String {
        val s = r.server.trim()
        val d = s.filter { it.isDigit() }
        return if (d.isNotEmpty() && d.length == s.length) d.trimStart('0').ifEmpty { "0" } else s.lowercase()
    }

    // ---------- batu nisan (penanda yang sudah dihapus) ----------
    private fun loadTomb(c: Context): HashMap<String, Long> {
        val out = HashMap<String, Long>()
        try {
            val s = prefs(c).getString(TOMB, null)
            if (s != null) {
                val o = JSONObject(s)
                val ks = o.keys()
                while (ks.hasNext()) {
                    val k = ks.next()
                    out[k] = o.optLong(k)
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveTomb(c: Context, t: Map<String, Long>) {
        val o = JSONObject()
        for ((k, v) in t) o.put(k, v)
        prefs(c).edit().putString(TOMB, o.toString()).apply()
    }

    fun addTomb(c: Context, key: String) {
        synchronized(lock) {
            val t = loadTomb(c)
            t[key] = System.currentTimeMillis()
            saveTomb(c, t)
        }
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
        synchronized(lock) { saveLocked(c, list, true) }
        SyncEngine.init(c)
        SyncEngine.kick()
    }

    private fun saveLocked(c: Context, list: List<Folder>, stamp: Boolean = true) {
        if (stamp) stampDiff(c, list)
        prefs(c).edit().putString(KEY, foldersJson(list).toString()).apply()
    }

    // Bandingkan dengan isi tersimpan: yang berubah diberi waktu sekarang, yang hilang dicatat sebagai terhapus
    private fun stampDiff(c: Context, list: List<Folder>) {
        val prev = loadLocked(c)
        val now = System.currentTimeMillis()
        val tomb = loadTomb(c)
        val prevF = HashMap<String, Folder>()
        for (f in prev) prevF[fkey(f)] = f
        val curKeys = HashSet<String>()
        for (f in list) {
            val k = fkey(f)
            curKeys.add(k)
            val pf = prevF[k]
            if (pf == null || pf.name != f.name || pf.id != f.id || pf.note != f.note) f.t = now
            val prevR = HashMap<String, Rec>()
            pf?.items?.forEach { prevR[rkey(it)] = it }
            val seen = HashSet<String>()
            for (r in f.items) {
                val rk = rkey(r)
                seen.add(rk)
                val pr = prevR[rk]
                if (pr == null || pr.level != r.level || pr.note != r.note) r.t = now
                tomb.remove("r|" + k + "|" + rk)
            }
            if (pf != null) {
                for (pr in pf.items) {
                    val rk = rkey(pr)
                    if (!seen.contains(rk)) tomb["r|" + k + "|" + rk] = now
                }
            }
            tomb.remove("f|" + k)
        }
        for (pf in prev) {
            val k = fkey(pf)
            if (!curKeys.contains(k)) tomb["f|" + k] = now
        }
        saveTomb(c, tomb)
    }

    private fun notifyChanged(kick: Boolean = true) {
        changed = true
        listener?.invoke()
        if (kick) SyncEngine.kick()
    }

    // Dari penerima data (perubahan lokal): segarkan layar dan jadwalkan sinkron
    fun ping() {
        notifyChanged(true)
    }

    // Dari sinkron (data baru dari HP lain): segarkan layar saja
    fun refreshUi() {
        notifyChanged(false)
    }

    // ---------- gabung dengan data dari HP lain ----------
    class MergeResult(val localChanged: Boolean, val pushText: String?)

    private fun canon(f: List<Folder>, z: Map<String, Long>, o: List<Order>): String {
        val root = JSONObject()
        val fa = JSONArray()
        for (x in f.sortedBy { fkey(it) }) {
            val jo = JSONObject()
            jo.put("name", x.name)
            jo.put("id", x.id)
            jo.put("note", x.note)
            jo.put("t", x.t)
            jo.put("items", recsJson(x.items.sortedBy { rkey(it) }))
            fa.put(jo)
        }
        root.put("f", fa)
        val zo = JSONObject()
        for (k in z.keys.sorted()) zo.put(k, z[k] ?: 0L)
        root.put("z", zo)
        root.put("o", BuktiStore.ordersJson(o.sortedBy { it.key }))
        return root.toString()
    }

    private fun mergeFolders(local: List<Folder>, remote: List<Folder>, tomb: Map<String, Long>): ArrayList<Folder> {
        val lm = LinkedHashMap<String, Folder>()
        for (f in local) lm[fkey(f)] = f
        val rm = LinkedHashMap<String, Folder>()
        for (f in remote) rm[fkey(f)] = f
        val keys = LinkedHashSet<String>()
        keys.addAll(lm.keys)
        keys.addAll(rm.keys)
        val out = ArrayList<Folder>()
        for (k in keys) {
            val a = lm[k]
            val b = rm[k]
            val base = if (a != null && b != null) (if (b.t > a.t) b else a) else (a ?: b!!)
            val f = Folder(base.name, ArrayList(), base.id)
            f.t = base.t
            f.note = base.note
            val ra = HashMap<String, Rec>()
            a?.items?.forEach { ra[rkey(it)] = it }
            val rb = HashMap<String, Rec>()
            b?.items?.forEach { rb[rkey(it)] = it }
            val rks = LinkedHashSet<String>()
            a?.items?.forEach { rks.add(rkey(it)) }
            b?.items?.forEach { rks.add(rkey(it)) }
            val ft = tomb["f|" + k] ?: 0L
            for (rk in rks) {
                val x = ra[rk]
                val y = rb[rk]
                val pickRec = if (x != null && y != null) (if (y.t > x.t) y else x) else (x ?: y!!)
                val tt = maxOf(tomb["r|" + k + "|" + rk] ?: 0L, ft)
                if (tt > 0L && tt >= pickRec.t) continue
                val nr = Rec(pickRec.email, pickRec.server, pickRec.level, pickRec.note)
                nr.t = pickRec.t
                f.items.add(nr)
            }
            if (f.items.isEmpty() && ft > 0L && ft >= f.t) continue
            out.add(f)
        }
        return out
    }

    // remoteText = isi dari database online (atau null kalau masih kosong).
    // Hasil: apakah data lokal berubah, dan teks gabungan yang perlu ditulis balik ke online (null = tidak perlu)
    fun mergeAll(c: Context, remoteText: String?): MergeResult = synchronized(lock) {
        val local = loadLocked(c)
        val ltomb = loadTomb(c)
        val rf = ArrayList<Folder>()
        val rt = HashMap<String, Long>()
        var ro = ArrayList<Order>()
        if (!remoteText.isNullOrBlank()) {
            try {
                val o = JSONObject(remoteText)
                val fa = o.optJSONArray("f")
                if (fa != null) {
                    for (i in 0 until fa.length()) {
                        val x = fa.optJSONObject(i) ?: continue
                        rf.add(folderFrom(x))
                    }
                }
                val z = o.optJSONObject("z")
                if (z != null) {
                    val ks = z.keys()
                    while (ks.hasNext()) {
                        val k = ks.next()
                        rt[k] = z.optLong(k)
                    }
                }
                ro = BuktiStore.parseOrders(o.optJSONArray("o") ?: JSONArray())
            } catch (e: Exception) {
            }
        }
        val tomb = HashMap<String, Long>(ltomb)
        for ((k, v) in rt) if (v > (tomb[k] ?: 0L)) tomb[k] = v
        val out = mergeFolders(local, rf, tomb)
        val lorders = BuktiStore.load(c)
        val mo = BuktiStore.mergeOrders(lorders, ro, tomb)
        val changedF = foldersJson(out).toString() != foldersJson(local).toString()
        val changedT = tomb != ltomb
        val changedO = BuktiStore.ordersJson(mo).toString() != BuktiStore.ordersJson(lorders).toString()
        if (changedF) saveLocked(c, out, false)
        if (changedT) saveTomb(c, tomb)
        if (changedO) BuktiStore.saveRaw(c, mo)
        val remoteCanon = canon(rf, rt, ro)
        val mergedCanon = canon(out, tomb, mo)
        val push = if (!remoteText.isNullOrBlank() && remoteCanon == mergedCanon) null else mergedCanon
        MergeResult(changedF || changedT || changedO, push)
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

    // Akun baru dari aplikasi Pembuat Akun: server dicatat level 3, folder diberi keterangan "Akun baru".
    // Keterangan itu dihapus otomatis begitu level yang dilaporkan hasil klaim lebih dari level awal
    const val NEW_LEVEL = 3
    const val NEW_NOTE = "Akun baru"

    // Server siap kirim: keterangan "1000 dm" DAN level minimal 15 (diamond 1000 tapi level di bawah 15 belum siap)
    const val READY_LEVEL = 15

    fun isReady(r: Rec): Boolean = rank(r.note) == 2 && num(r.level) >= READY_LEVEL

    // ---- status server (dipakai untuk warna, penanda, hitungan, dan pencarian) ----
    const val ST_SIAP = 1
    const val ST_TOPUP = 2
    const val ST_KLAIM = 3
    const val ST_BIASA = 4
    const val ST_PAKAI = 5
    const val ST_BARU = 6
    const val TOPUP_VG = 500      // VG segini atau lebih = bisa Top Up
    const val KLAIM_VG_MIN = 370  // VG 370 sampai 499 = tinggal satu kali klaim
    const val NEW_SERVER_NOTE = "Baru"

    fun status(r: Rec): Int {
        val k = rank(r.note)
        if (k == 3) return ST_PAKAI
        if (isReady(r)) return ST_SIAP
        if (r.note.trim().equals(NEW_SERVER_NOTE, true)) return ST_BARU
        // Data lama: server tanpa keterangan dan level 1 sampai 3 (belum pernah diklaim) juga dianggap Baru
        if (r.note.isBlank() && num(r.level) in 1L..NEW_LEVEL.toLong()) return ST_BARU
        if (k == 1) {
            val v = num(r.note)
            return when {
                v >= TOPUP_VG -> ST_TOPUP
                v >= KLAIM_VG_MIN -> ST_KLAIM
                else -> ST_BIASA
            }
        }
        return ST_BIASA
    }

    // indeks 1..6 = jumlah tiap status
    fun counts(items: List<Rec>): IntArray {
        val c = IntArray(7)
        for (r in items) c[status(r)]++
        return c
    }

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
        SyncEngine.init(c)
        synchronized(lock) {
            val list = loadLocked(c)
            var f = findFolder(list, id, email)
            if (f == null) {
                f = Folder(email, ArrayList(), id)
                f.note = NEW_NOTE
                list.add(f)
            } else {
                if (f.name.isBlank() && email.isNotEmpty()) f.name = email
                if (f.id.isEmpty() && id.isNotEmpty()) f.id = id
            }
            for (sv in servers) {
                val s = sv.trim()
                if (s.isEmpty()) continue
                if (f.items.none { sameServer(it.server, s) }) {
                    f.items.add(Rec("", s, NEW_LEVEL.toString(), NEW_SERVER_NOTE))
                }
            }
            saveLocked(c, list, true)
        }
        notifyChanged(true)
    }

    // Benar kalau dua ID beda tepat satu angka (satu angka hilang, bertambah, atau berubah)
    private fun oneEdit(a: String, b: String): Boolean {
        if (a == b) return false
        val la = a.length
        val lb = b.length
        if (Math.abs(la - lb) > 1) return false
        var i = 0
        while (i < la && i < lb && a[i] == b[i]) i++
        return when {
            la == lb -> a.substring(i + 1) == b.substring(i + 1)
            la > lb -> a.substring(i + 1) == b.substring(i)
            else -> a.substring(i) == b.substring(i + 1)
        }
    }

    // ---------- dari APK 3: update level / keterangan ----------
    fun upsertFromClaim(c: Context, idIn: String, serverIn: String, level: String, note: String) {
        val id = idIn.trim()
        val server = serverIn.trim()
        if (id.isEmpty() || server.isEmpty()) return
        SyncEngine.init(c)
        synchronized(lock) {
            val list = loadLocked(c)
            var f = findFolder(list, id, "")
            if (f == null) {
                // ID dari game (benar) tidak ada. Mungkin folder dari APK 1 yang ID-nya salah baca satu angka: hanya folder yang
                // masih "Akun baru" dan hanya kalau cocoknya tepat satu. ID folder dikoreksi, emailnya tetap
                val cand = list.filter { it.note.trim().equals(NEW_NOTE, true) && it.id.isNotEmpty() && oneEdit(it.id, id) }
                if (cand.size == 1) {
                    f = cand[0]
                    f.id = id
                }
            }
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
            // Level sudah berubah dari level akun baru: keterangan "Akun baru" dihapus
            if (lv.isNotEmpty() && num(lv) > NEW_LEVEL && f.note.trim().equals(NEW_NOTE, true)) f.note = ""
            val nt = note.trim()
            if (nt.isNotEmpty() && rank(nt) >= rank(r.note)) r.note = nt
            saveLocked(c, list, true)
        }
        notifyChanged(true)
    }
}
