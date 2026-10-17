package com.catatan.cabang

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class Rec(
    var email: String,
    var server: String,
    var level: String,
    var note: String
)

class MainActivity : Activity() {

    companion object {
        private const val LONG_PRESS_MS = 200L   // tahan 200ms => edit
        private const val REQ_BACKUP = 11
        private const val REQ_IMPORT = 12
    }

    private val ui = Handler(Looper.getMainLooper())
    private val items = ArrayList<Rec>()
    private val shown = ArrayList<Rec>()
    private lateinit var prefs: SharedPreferences

    private var dark = true
    private var qEmail = ""
    private var qServer = ""
    private var qNote = ""

    private lateinit var listView: ListView
    private lateinit var adapter: RowAdapter
    private lateinit var statLabel: TextView
    private lateinit var statAkun: TextView
    private lateinit var statServer: TextView
    private lateinit var statNote: TextView
    private lateinit var emptyTv: TextView

    // ---------- tema hitam putih ----------
    private val bg get() = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    private val fg get() = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    private val surface get() = if (dark) 0xFF0F0F0F.toInt() else 0xFFF5F5F5.toInt()
    private val line get() = if (dark) 0xFF2A2A2A.toInt() else 0xFFE0E0E0.toInt()
    private val sub get() = if (dark) 0xFF8C8C8C.toInt() else 0xFF6E6E6E.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("catatan", Context.MODE_PRIVATE)
        dark = prefs.getBoolean("dark", true)
        load()
        buildUi()
    }

    // ---------- helpers UI ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(fill: Int, radius: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun lp(top: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setTextColor(if (filled) bg else fg)
        t.background = shape(if (filled) fg else Color.TRANSPARENT, 22, if (filled) fg else line)
        t.setPadding(dp(16), dp(10), dp(16), dp(10))
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun input(hint: String, value: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.inputType = type
        e.textSize = 14f
        e.setTextColor(fg)
        e.setHintTextColor(sub)
        e.background = shape(surface, 14, line)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ---------- UI utama ----------
    private fun buildUi() {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        var flags = 0
        if (!dark) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(bg)
        col.setPadding(dp(16), dp(14), dp(16), 0)

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label("Catatan Cabang", 24f, fg, true))
        titles.addView(label("Akun & server", 13f, sub, false))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pill(if (dark) "Terang" else "Gelap", false) {
            dark = !dark
            prefs.edit().putBoolean("dark", dark).apply()
            buildUi()
        })
        col.addView(header, lp())

        // kartu total
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = shape(surface, 20, line)
        card.setPadding(dp(18), dp(14), dp(18), dp(14))
        statLabel = label("TOTAL", 11f, sub, true)
        statLabel.letterSpacing = 0.12f
        card.addView(statLabel)
        val nums = LinearLayout(this)
        nums.orientation = LinearLayout.HORIZONTAL
        statAkun = label("0", 34f, fg, true)
        statServer = label("0", 34f, fg, true)
        val c1 = LinearLayout(this)
        c1.orientation = LinearLayout.VERTICAL
        c1.addView(statAkun)
        c1.addView(label("AKUN", 11f, sub, true))
        val c2 = LinearLayout(this)
        c2.orientation = LinearLayout.VERTICAL
        c2.addView(statServer)
        c2.addView(label("SERVER", 11f, sub, true))
        nums.addView(c1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nums.addView(c2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(nums)
        statNote = label("", 12f, fg, false)
        statNote.visibility = View.GONE
        card.addView(statNote, lp(8))
        col.addView(card, lp(14))

        // pencarian
        val textType = InputType.TYPE_CLASS_TEXT
        val fEmail = input("Cari email", qEmail, textType or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val fServer = input("Cari server", qServer, textType)
        val fNote = input("Cari keterangan", qNote, textType)
        fEmail.isSingleLine = true
        fServer.isSingleLine = true
        fNote.isSingleLine = true
        fEmail.addTextChangedListener(watcher { qEmail = it; refresh() })
        fServer.addTextChangedListener(watcher { qServer = it; refresh() })
        fNote.addTextChangedListener(watcher { qNote = it; refresh() })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val pe = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        pe.rightMargin = dp(8)
        row.addView(fEmail, pe)
        row.addView(fServer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row, lp(12))
        col.addView(fNote, lp(8))

        // aksi
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val acts = LinearLayout(this)
        acts.orientation = LinearLayout.HORIZONTAL
        fun addAct(t: String, f: () -> Unit) {
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.rightMargin = dp(8)
            acts.addView(pill(t, false, f), p)
        }
        addAct("Salin Format") { copyFormat() }
        addAct("Backup") { doBackup() }
        addAct("Import") { doImport() }
        addAct("Hapus Semua") { clearAll() }
        hs.addView(acts)
        col.addView(hs, lp(12))
        col.addView(label("Tahan baris 0,2 detik untuk edit / hapus", 11f, sub, false), lp(10))

        // daftar + tombol tambah
        val frame = FrameLayout(this)
        adapter = RowAdapter()
        listView = ListView(this)
        listView.divider = null
        listView.dividerHeight = 0
        listView.selector = ColorDrawable(Color.TRANSPARENT)
        listView.setPadding(0, dp(8), 0, dp(96))
        listView.clipToPadding = false
        listView.isVerticalScrollBarEnabled = false
        listView.overScrollMode = View.OVER_SCROLL_NEVER
        listView.adapter = adapter
        frame.addView(listView, FrameLayout.LayoutParams(-1, -1))

        emptyTv = label("", 14f, sub, false)
        emptyTv.gravity = Gravity.CENTER
        frame.addView(emptyTv, FrameLayout.LayoutParams(-1, -1))
        listView.emptyView = emptyTv

        val fab = pill("+  Tambah", true) { openForm(null) }
        fab.textSize = 15f
        fab.setPadding(dp(22), dp(14), dp(22), dp(14))
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fp.gravity = Gravity.BOTTOM or Gravity.END
        fp.bottomMargin = dp(18)
        frame.addView(fab, fp)

        col.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)
        refresh()
    }

    private fun watcher(cb: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { cb(s?.toString() ?: "") }
    }

    // ---------- filter + total ----------
    private fun countServers(list: List<Rec>): Int =
        list.map { it.server.trim().lowercase() }.filter { it.isNotEmpty() }.toSet().size

    private fun refresh() {
        val a = qEmail.trim()
        val b = qServer.trim()
        val c = qNote.trim()
        shown.clear()
        for (r in items) {
            if (a.isNotEmpty() && !r.email.contains(a, true)) continue
            if (b.isNotEmpty() && !r.server.contains(b, true)) continue
            if (c.isNotEmpty() && !r.note.contains(c, true)) continue
            shown.add(r)
        }
        adapter.notifyDataSetChanged()

        val searching = a.isNotEmpty() || b.isNotEmpty() || c.isNotEmpty()
        val akun = shown.size
        val srv = countServers(shown)
        statLabel.text = if (searching) "HASIL PENCARIAN" else "TOTAL"
        statAkun.text = akun.toString()
        statServer.text = srv.toString()
        if (searching) {
            statNote.visibility = View.VISIBLE
            statNote.text = "Sedang dicari: $akun akun · $srv server  (dari ${items.size} akun · ${countServers(items)} server)"
        } else {
            statNote.visibility = View.GONE
        }
        emptyTv.text = if (items.isEmpty()) "Belum ada data.\nTekan + Tambah untuk mulai." else "Tidak ada hasil."
    }

    // ---------- baris daftar (tahan 200ms => edit) ----------
    private inner class RowAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h: RowHolder
            val v: View
            if (convertView == null) {
                h = RowHolder()
                v = h.root
                v.tag = h
            } else {
                v = convertView
                h = convertView.tag as RowHolder
            }
            h.bind(position, shown[position])
            return v
        }
    }

    private inner class RowHolder {
        val ctx: Context = this@MainActivity
        val root = FrameLayout(ctx)
        private val tvNo = TextView(ctx)
        private val tvEmail = TextView(ctx)
        private val tvServer = TextView(ctx)
        private val tvNote = TextView(ctx)
        private val tvLevel = TextView(ctx)
        private var pos = 0
        private var sx = 0f
        private var sy = 0f
        private var fired = false
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

        private val trigger = Runnable {
            fired = true
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val r = shown.getOrNull(pos)
            if (r != null) openForm(r)
        }

        init {
            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.HORIZONTAL
            card.background = shape(surface, 16, line)
            card.setPadding(dp(14), dp(12), dp(14), dp(12))

            tvNo.textSize = 12f
            tvNo.setTextColor(sub)
            tvNo.typeface = Typeface.DEFAULT_BOLD
            card.addView(tvNo, LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT))

            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.VERTICAL
            tvEmail.textSize = 15f
            tvEmail.setTextColor(fg)
            tvEmail.typeface = Typeface.DEFAULT_BOLD
            tvServer.textSize = 13f
            tvServer.setTextColor(fg)
            tvServer.typeface = Typeface.MONOSPACE
            tvNote.textSize = 13f
            tvNote.setTextColor(sub)
            mid.addView(tvEmail)
            mid.addView(tvServer, lp(2))
            mid.addView(tvNote, lp(2))
            card.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            tvLevel.textSize = 12f
            tvLevel.typeface = Typeface.DEFAULT_BOLD
            tvLevel.setTextColor(bg)
            tvLevel.background = shape(fg, 12)
            tvLevel.setPadding(dp(10), dp(4), dp(10), dp(4))
            val lpl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpl.leftMargin = dp(8)
            card.addView(tvLevel, lpl)

            root.setPadding(0, 0, 0, dp(8))
            root.addView(card, FrameLayout.LayoutParams(-1, -2))
            root.isClickable = true
            root.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        fired = false
                        sx = e.x
                        sy = e.y
                        v.alpha = 0.7f
                        ui.postDelayed(trigger, LONG_PRESS_MS)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(e.x - sx) > slop || abs(e.y - sy) > slop) {
                            ui.removeCallbacks(trigger)
                            v.alpha = 1f
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        ui.removeCallbacks(trigger)
                        v.alpha = 1f
                    }
                }
                fired
            }
        }

        fun bind(p: Int, r: Rec) {
            pos = p
            tvNo.text = (p + 1).toString()
            tvEmail.text = r.email
            tvServer.text = r.server
            tvServer.visibility = if (r.server.isBlank()) View.GONE else View.VISIBLE
            tvNote.text = r.note
            tvNote.visibility = if (r.note.isBlank()) View.GONE else View.VISIBLE
            tvLevel.text = r.level
            tvLevel.visibility = if (r.level.isBlank()) View.GONE else View.VISIBLE
        }
    }

    // ---------- dialog ----------
    private fun showBox(content: View): Dialog {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(content)
        val w = d.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        d.show()
        return d
    }

    private fun box(): LinearLayout {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.background = shape(bg, 24, line)
        b.setPadding(dp(20), dp(20), dp(20), dp(16))
        return b
    }

    private fun sheet(title: String, msg: String, actions: List<Triple<String, Boolean, () -> Unit>>) {
        val b = box()
        b.addView(label(title, 18f, fg, true))
        b.addView(label(msg, 14f, sub, false), lp(8))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        var d: Dialog? = null
        val cancel = pill("Batal", false) { d?.dismiss() }
        row.addView(cancel)
        for (a in actions) {
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.leftMargin = dp(8)
            row.addView(pill(a.first, a.second) { d?.dismiss(); a.third() }, p)
        }
        b.addView(row, lp(18))
        d = showBox(b)
    }

    private fun openForm(rec: Rec?) {
        val b = box()
        b.addView(label(if (rec == null) "Tambah Data" else "Edit Data", 20f, fg, true))
        val t = InputType.TYPE_CLASS_TEXT
        val fEmail = input("Email", rec?.email ?: "", t or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val fServer = input("Server", rec?.server ?: "", t)
        val fLevel = input("Level", rec?.level ?: "", t)
        val fNote = input("Keterangan", rec?.note ?: "", t or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        fNote.minLines = 2
        fNote.gravity = Gravity.TOP
        b.addView(fEmail, lp(14))
        b.addView(fServer, lp(10))
        b.addView(fLevel, lp(10))
        b.addView(fNote, lp(10))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        var d: Dialog? = null
        if (rec != null) {
            row.addView(pill("Hapus", false) {
                d?.dismiss()
                sheet("Hapus data?", rec.email, listOf(Triple("Hapus", true) {
                    items.remove(rec)
                    save()
                    refresh()
                }))
            })
        }
        val spacer = View(this)
        row.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(pill("Batal", false) { d?.dismiss() })
        val pSave = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pSave.leftMargin = dp(8)
        row.addView(pill("Simpan", true) {
            val email = fEmail.text.toString().trim()
            if (email.isEmpty()) {
                fEmail.error = "Wajib diisi"
                return@pill
            }
            val server = fServer.text.toString().trim()
            val level = fLevel.text.toString().trim()
            val note = fNote.text.toString().trim()
            if (rec == null) {
                items.add(Rec(email, server, level, note))
            } else {
                rec.email = email
                rec.server = server
                rec.level = level
                rec.note = note
            }
            save()
            refresh()
            if (rec == null && shown.isNotEmpty()) listView.setSelection(shown.size - 1)
            d?.dismiss()
        }, pSave)
        b.addView(row, lp(18))

        val sv = ScrollView(this)
        sv.addView(b)
        d = showBox(sv)
    }

    // ---------- aksi ----------
    private fun copyFormat() {
        val list = if (shown.isEmpty()) items else shown
        if (list.isEmpty()) {
            toast("Tidak ada data")
            return
        }
        // format: email<TAB>server<TAB>keterangan<TAB>level  (satu baris per data)
        val text = list.joinToString("\n") { "${it.email}\t${it.server}\t${it.note}\t${it.level}" }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Catatan Cabang", text))
        toast("${list.size} data disalin")
    }

    private fun clearAll() {
        if (items.isEmpty()) {
            toast("Data sudah kosong")
            return
        }
        sheet("Hapus semua data?", "${items.size} akun akan dihapus permanen. Buat backup dulu kalau perlu.",
            listOf(Triple("Hapus Semua", true) {
                items.clear()
                save()
                refresh()
            }))
    }

    private fun doBackup() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, "catatan-cabang-backup.json")
        startActivityForResult(i, REQ_BACKUP)
    }

    private fun doImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, REQ_IMPORT)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == REQ_BACKUP) {
                contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(toJson().toString(2).toByteArray(Charsets.UTF_8))
                }
                toast("Backup tersimpan (${items.size} data)")
            } else if (requestCode == REQ_IMPORT) {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                val list = parse(text)
                if (list.isEmpty()) {
                    toast("File tidak berisi data")
                    return
                }
                sheet("Import ${list.size} data", "Gabung dengan data sekarang, atau ganti semuanya?", listOf(
                    Triple("Gabung", false) {
                        items.addAll(list)
                        save()
                        refresh()
                        toast("${list.size} data ditambahkan")
                    },
                    Triple("Ganti", true) {
                        items.clear()
                        items.addAll(list)
                        save()
                        refresh()
                        toast("${list.size} data diimport")
                    }
                ))
            }
        } catch (e: Exception) {
            toast("Gagal: ${e.message}")
        }
    }

    // ---------- penyimpanan ----------
    private fun toJson(): JSONArray {
        val arr = JSONArray()
        for (r in items) {
            val o = JSONObject()
            o.put("email", r.email)
            o.put("server", r.server)
            o.put("level", r.level)
            o.put("keterangan", r.note)
            arr.put(o)
        }
        return arr
    }

    private fun parse(text: String): List<Rec> {
        val t = text.trim()
        val arr: JSONArray = if (t.startsWith("[")) {
            JSONArray(t)
        } else {
            val o = JSONObject(t)
            o.optJSONArray("data") ?: o.optJSONArray("items") ?: o.optJSONArray("records") ?: JSONArray()
        }
        val out = ArrayList<Rec>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Rec(
                pick(o, "email", "Email", "akun"),
                pick(o, "server", "Server"),
                pick(o, "level", "Level"),
                pick(o, "keterangan", "Keterangan", "note", "ket")
            ))
        }
        return out
    }

    private fun pick(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "")
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    private fun save() {
        prefs.edit().putString("data", toJson().toString()).apply()
    }

    private fun load() {
        items.clear()
        val s = prefs.getString("data", null) ?: return
        try {
            items.addAll(parse(s))
        } catch (e: Exception) {
        }
    }
}
