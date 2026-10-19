package com.catatan.cabang

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
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

class Folder(var name: String, val items: ArrayList<Rec> = ArrayList())

// Ikon gambar sendiri (tanpa file gambar)
class IconView(
    ctx: Context,
    private val kind: Int,
    private val fgc: Int,
    private val bgc: Int = Color.BLACK
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val u = minOf(width, height) / 24f
        c.save()
        c.translate((width - 24f * u) / 2f, (height - 24f * u) / 2f)
        c.scale(u, u)
        p.color = fgc
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.9f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        path.reset()
        when (kind) {
            0 -> { // kembali
                path.moveTo(15f, 5f); path.lineTo(8f, 12f); path.lineTo(15f, 19f)
                c.drawPath(path, p)
            }
            1 -> { // cari
                c.drawCircle(11f, 11f, 6.5f, p)
                c.drawLine(16f, 16f, 21f, 21f, p)
            }
            2 -> { // titik tiga
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 5f, 1.7f, p)
                c.drawCircle(12f, 12f, 1.7f, p)
                c.drawCircle(12f, 19f, 1.7f, p)
            }
            3 -> { // silang
                c.drawLine(6f, 6f, 18f, 18f, p)
                c.drawLine(18f, 6f, 6f, 18f, p)
            }
            4 -> { // folder
                c.drawRoundRect(RectF(3f, 6f, 21f, 19f), 3f, 3f, p)
                c.drawLine(3f, 10.5f, 21f, 10.5f, p)
            }
            5 -> { // panah kanan
                path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f)
                c.drawPath(path, p)
            }
            6 -> { // centang dalam lingkaran
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 12f, 11f, p)
                p.style = Paint.Style.STROKE
                p.color = bgc
                path.moveTo(7.2f, 12.5f); path.lineTo(10.5f, 15.8f); path.lineTo(16.8f, 8.8f)
                c.drawPath(path, p)
            }
        }
        c.restore()
    }
}

class MainActivity : Activity() {

    companion object {
        private const val LONG_PRESS_MS = 200L   // tahan 200ms => edit
        private const val REQ_BACKUP = 11
        private const val REQ_IMPORT = 12
    }

    private val ui = Handler(Looper.getMainLooper())
    private val folders = ArrayList<Folder>()
    private var current: Folder? = null
    private val shown = ArrayList<Rec>()
    private val selected = HashSet<Rec>()
    private lateinit var prefs: SharedPreferences

    private var dark = true
    private var searchOpen = false
    private var searchField = 0          // 0 email, 1 server, 2 level, 3 keterangan
    private var query = ""
    private var pendingAll = false
    private val fieldNames = arrayOf("Email", "Server", "Level", "Keterangan")

    // halaman folder
    private var lvFolders: ListView? = null
    private var adapterF: FolderAdapter? = null
    private var emptyF: TextView? = null

    // halaman data
    private var lvData: ListView? = null
    private var adapterD: RowAdapter? = null
    private var emptyD: TextView? = null
    private var topHolder: LinearLayout? = null
    private var searchHolder: LinearLayout? = null
    private var searchEt: EditText? = null
    private var statAkun: TextView? = null
    private var statServer: TextView? = null
    private var lblAkun: TextView? = null
    private var lblServer: TextView? = null
    private var fabData: View? = null
    private var actBar: View? = null
    private var barOn = false

    // ---------- tema hitam putih ----------
    private val bg get() = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    private val fg get() = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    private val surface get() = if (dark) 0xFF0F0F0F.toInt() else 0xFFF5F5F5.toInt()
    private val surface2 get() = if (dark) 0xFF1A1A1A.toInt() else 0xFFE8E8E8.toInt()
    private val selSurface get() = if (dark) 0xFF1C1C1C.toInt() else 0xFFEAEAEA.toInt()
    private val line get() = if (dark) 0xFF2A2A2A.toInt() else 0xFFE0E0E0.toInt()
    private val sub get() = if (dark) 0xFF8C8C8C.toInt() else 0xFF6E6E6E.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("catatan", Context.MODE_PRIVATE)
        dark = prefs.getBoolean("dark", true)
        load()
        showFolders()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (selected.isNotEmpty()) {
            selected.clear()
            onSelectionChanged()
        } else if (searchOpen && current != null) {
            toggleSearch()
        } else if (current != null) {
            showFolders(-1)
        } else {
            super.onBackPressed()
        }
    }

    // ---------- helpers UI ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(fill: Int, radius: Int, stroke: Int = 0, sw: Int = 1): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != 0) g.setStroke(dp(sw), stroke)
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

    private fun restyle(t: TextView, filled: Boolean) {
        t.setTextColor(if (filled) bg else fg)
        t.background = shape(if (filled) fg else Color.TRANSPARENT, 22, if (filled) fg else line)
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        restyle(t, filled)
        t.setPadding(dp(16), dp(10), dp(16), dp(10))
        t.isClickable = true
        t.setOnClickListener { onClick() }
        pressFx(t)
        return t
    }

    // efek tekan: mengecil sedikit lalu kembali
    private fun pressFx(v: View) {
        v.setOnTouchListener { x, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
            } else if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
    }

    // animasi masuk halaman (dir: 1 dari kanan, -1 dari kiri)
    private fun enter(v: View, dir: Int) {
        v.alpha = 0f
        v.translationX = dp(36) * dir.toFloat()
        v.animate().alpha(1f).translationX(0f).setDuration(240)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // tampil / sembunyi bar dengan geser + pudar
    private fun setShown(v: View?, on: Boolean) {
        if (v == null) return
        if (on) {
            v.animate().cancel()
            v.visibility = View.VISIBLE
            v.alpha = 0f
            v.translationY = dp(40).toFloat()
            v.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        } else {
            if (v.visibility != View.VISIBLE) return
            v.animate().cancel()
            v.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(160)
                .withEndAction { v.visibility = View.GONE }.start()
        }
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

    private fun iconBtn(kind: Int, active: Boolean, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.background = shape(if (active) fg else Color.TRANSPARENT, 20, line)
        f.isClickable = true
        f.setOnClickListener { onClick() }
        val iv = IconView(this, kind, if (active) bg else fg, bg)
        f.addView(iv, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        pressFx(f)
        return f
    }

    private fun iconLp(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(dp(40), dp(40))
        p.leftMargin = dp(6)
        return p
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun applyWindow() {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        var flags = 0
        if (!dark) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun column(): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(bg)
        col.setPadding(dp(16), dp(12), dp(16), 0)
        return col
    }

    private fun styleList(lv: ListView) {
        lv.divider = null
        lv.dividerHeight = 0
        lv.selector = ColorDrawable(Color.TRANSPARENT)
        lv.setPadding(0, dp(8), 0, dp(100))
        lv.clipToPadding = false
        lv.isVerticalScrollBarEnabled = false
        lv.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun fab(text: String, onClick: () -> Unit): TextView {
        val f = pill(text, true, onClick)
        f.textSize = 15f
        f.setPadding(dp(22), dp(14), dp(22), dp(14))
        return f
    }

    private fun watcher(cb: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { cb(s?.toString() ?: "") }
    }

    private fun num(s: String): Long = Regex("\\d+").find(s)?.value?.toLongOrNull() ?: 0L
    private fun sumServer(list: List<Rec>): Long {
        var t = 0L
        for (r in list) t += num(r.server)
        return t
    }

    private fun rebuild() {
        val c = current
        if (c == null) showFolders() else showData(c)
    }

    // ---------- gesture: tap / tahan 200ms / geser kiri ----------
    private inner class Gesture(
        val v: View,
        val onTap: (() -> Unit)?,
        val onLong: (() -> Unit)?,
        val onSwipe: (() -> Unit)?
    ) {
        private var sx = 0f
        private var sy = 0f
        private var longDone = false
        private var swiped = false
        private var cancelled = false
        private var dragging = false
        private val slop = ViewConfiguration.get(this@MainActivity).scaledTouchSlop
        private val swipeDist = dp(80)
        private val maxDrag = dp(140).toFloat()
        private val longRun = Runnable {
            if (!swiped && !cancelled && !dragging) {
                longDone = true
                release()
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLong?.invoke()
            }
        }

        // kembalikan baris ke posisi & ukuran semula dengan halus
        private fun release() {
            v.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        }

        fun attach() {
            v.isClickable = true
            v.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().cancel()
                        v.translationX = 0f
                        longDone = false
                        swiped = false
                        cancelled = false
                        dragging = false
                        sx = e.rawX
                        sy = e.rawY
                        v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(90).start()
                        if (onLong != null && selected.isEmpty()) ui.postDelayed(longRun, LONG_PRESS_MS)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!longDone && !swiped && !cancelled) {
                            val dx = e.rawX - sx
                            val dy = e.rawY - sy
                            if (!dragging && abs(dy) > slop && abs(dy) >= abs(dx)) {
                                cancelled = true
                                ui.removeCallbacks(longRun)
                                release()
                            } else if (onSwipe != null && dx < -slop && abs(dx) > abs(dy)) {
                                // baris ikut jari ke kiri
                                if (!dragging) {
                                    dragging = true
                                    ui.removeCallbacks(longRun)
                                    v.animate().cancel()
                                    v.scaleX = 1f
                                    v.scaleY = 1f
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                var tx = dx * 0.8f
                                if (tx < -maxDrag) tx = -maxDrag
                                v.translationX = tx
                                if (dx < -swipeDist) {
                                    swiped = true
                                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onSwipe.invoke()
                                    release()
                                }
                            } else if (abs(dx) > slop) {
                                ui.removeCallbacks(longRun)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        ui.removeCallbacks(longRun)
                        release()
                        val dx = e.rawX - sx
                        val dy = e.rawY - sy
                        if (!longDone && !swiped && !cancelled && !dragging && abs(dx) <= slop * 2 && abs(dy) <= slop * 2) {
                            onTap?.invoke()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        ui.removeCallbacks(longRun)
                        release()
                    }
                }
                false
            }
        }
    }

    // =====================================================
    // HALAMAN 1: DAFTAR FOLDER
    // =====================================================
    private fun showFolders(dir: Int = 0) {
        current = null
        selected.clear()
        searchOpen = false
        query = ""
        applyWindow()
        val col = column()

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(label("Catatan Cabang", 22f, fg, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconBtn(2, false) { menuFolders() }, iconLp())
        col.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        val frame = FrameLayout(this)
        val lv = ListView(this)
        styleList(lv)
        val ad = FolderAdapter()
        lv.adapter = ad
        frame.addView(lv, FrameLayout.LayoutParams(-1, -1))
        val em = label("", 14f, sub, false)
        em.gravity = Gravity.CENTER
        frame.addView(em, FrameLayout.LayoutParams(-1, -1))
        lv.emptyView = em

        val fb = fab("+  Folder") {
            askText("Folder baru", "", "Buat") { name ->
                folders.add(Folder(name))
                save()
                refreshFolders()
            }
        }
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fp.gravity = Gravity.BOTTOM or Gravity.END
        fp.bottomMargin = dp(18)
        frame.addView(fb, fp)

        col.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        lvFolders = lv
        adapterF = ad
        emptyF = em
        setContentView(col)
        if (dir != 0) enter(col, dir)
        refreshFolders()
    }

    private fun refreshFolders() {
        adapterF?.notifyDataSetChanged()
        emptyF?.text = "Belum ada folder.\nTekan + Folder untuk mulai."
    }

    private inner class FolderAdapter : BaseAdapter() {
        override fun getCount() = folders.size
        override fun getItem(position: Int): Any = folders[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h: FolderHolder
            val v: View
            if (convertView == null) {
                h = FolderHolder()
                v = h.root
                v.tag = h
            } else {
                v = convertView
                h = convertView.tag as FolderHolder
            }
            h.bind(position, folders[position])
            return v
        }
    }

    private inner class FolderHolder {
        val ctx: Context = this@MainActivity
        val root = FrameLayout(ctx)
        private val tvName = TextView(ctx)
        private val tvSub = TextView(ctx)
        private var pos = 0

        init {
            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.background = shape(surface, 20, line)
            card.setPadding(dp(14), dp(14), dp(14), dp(14))

            val tile = FrameLayout(ctx)
            tile.background = shape(surface2, 16, line)
            tile.addView(IconView(ctx, 4, fg, bg), FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            card.addView(tile, LinearLayout.LayoutParams(dp(52), dp(52)))

            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.VERTICAL
            tvName.textSize = 17f
            tvName.setTextColor(fg)
            tvName.typeface = Typeface.DEFAULT_BOLD
            tvName.isSingleLine = true
            tvName.ellipsize = TextUtils.TruncateAt.END
            tvSub.textSize = 12f
            tvSub.setTextColor(sub)
            mid.addView(tvName)
            mid.addView(tvSub, lp(2))
            val mp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            mp.leftMargin = dp(14)
            card.addView(mid, mp)

            card.addView(IconView(ctx, 5, sub, bg), LinearLayout.LayoutParams(dp(20), dp(20)))

            root.setPadding(0, 0, 0, dp(10))
            root.addView(card, FrameLayout.LayoutParams(-1, -2))

            Gesture(root,
                { folders.getOrNull(pos)?.let { openFolder(it) } },
                { folders.getOrNull(pos)?.let { folderOptions(it) } },
                null).attach()
        }

        fun bind(p: Int, f: Folder) {
            pos = p
            tvName.text = f.name
            tvSub.text = "${f.items.size} akun · ${sumServer(f.items)} server"
        }
    }

    private fun openFolder(f: Folder) {
        searchOpen = false
        query = ""
        searchField = 0
        showData(f, 1)
    }

    private fun folderOptions(f: Folder) {
        val b = box()
        b.addView(label(f.name, 18f, fg, true))
        var d: Dialog? = null
        b.addView(pill("Ganti judul", false) {
            d?.dismiss()
            askText("Ganti judul", f.name, "Simpan") { name ->
                f.name = name
                save()
                refreshFolders()
            }
        }, lp(14))
        b.addView(pill("Hapus folder", false) {
            d?.dismiss()
            sheet("Hapus folder?", "\"${f.name}\" dan ${f.items.size} akun di dalamnya akan dihapus.",
                listOf(Triple("Hapus", true) {
                    folders.remove(f)
                    save()
                    refreshFolders()
                }))
        }, lp(8))
        d = showBox(b)
    }

    private fun askText(title: String, initial: String, ok: String, cb: (String) -> Unit) {
        val b = box()
        b.addView(label(title, 18f, fg, true))
        val et = input("Judul folder", initial, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        et.isSingleLine = true
        b.addView(et, lp(12))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        var d: Dialog? = null
        row.addView(pill("Batal", false) { d?.dismiss() })
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.leftMargin = dp(8)
        row.addView(pill(ok, true) {
            val s = et.text.toString().trim()
            if (s.isEmpty()) {
                et.error = "Wajib diisi"
                return@pill
            }
            d?.dismiss()
            cb(s)
        }, p)
        b.addView(row, lp(16))
        d = showBox(b)
    }

    private fun menuFolders() {
        popupMenu(listOf<Pair<String, () -> Unit>>(
            Pair("Backup semua") { doBackup(true) },
            Pair("Import") { doImport(true) },
            Pair(if (dark) "Mode Terang" else "Mode Gelap") { toggleTheme() }
        ))
    }

    private fun toggleTheme() {
        dark = !dark
        prefs.edit().putBoolean("dark", dark).apply()
        rebuild()
    }

    // =====================================================
    // HALAMAN 2: DATA AKUN
    // =====================================================
    private fun showData(f: Folder, dir: Int = 0) {
        current = f
        selected.clear()
        barOn = false
        applyWindow()
        val col = column()

        val th = LinearLayout(this)
        th.orientation = LinearLayout.HORIZONTAL
        th.gravity = Gravity.CENTER_VERTICAL
        col.addView(th, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        topHolder = th

        val sh = LinearLayout(this)
        sh.orientation = LinearLayout.VERTICAL
        col.addView(sh, lp(6))
        searchHolder = sh

        // strip total (ringkas)
        val strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.gravity = Gravity.CENTER_VERTICAL
        strip.background = shape(surface, 14, line)
        strip.setPadding(dp(14), dp(8), dp(14), dp(8))
        val sa = label("0", 20f, fg, true)
        val ss = label("0", 20f, fg, true)
        val la = label("AKUN", 11f, sub, true)
        val ls = label("SERVER", 11f, sub, true)
        la.letterSpacing = 0.1f
        ls.letterSpacing = 0.1f
        val c1 = LinearLayout(this)
        c1.orientation = LinearLayout.HORIZONTAL
        c1.gravity = Gravity.CENTER_VERTICAL
        c1.addView(sa)
        val l1 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l1.leftMargin = dp(6)
        c1.addView(la, l1)
        val c2 = LinearLayout(this)
        c2.orientation = LinearLayout.HORIZONTAL
        c2.gravity = Gravity.CENTER_VERTICAL
        c2.addView(ss)
        val l2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l2.leftMargin = dp(6)
        c2.addView(ls, l2)
        strip.addView(c1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        strip.addView(c2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(strip, lp(8))
        statAkun = sa
        statServer = ss
        lblAkun = la
        lblServer = ls

        // daftar + tombol
        val frame = FrameLayout(this)
        val lv = ListView(this)
        styleList(lv)
        val ad = RowAdapter()
        lv.adapter = ad
        frame.addView(lv, FrameLayout.LayoutParams(-1, -1))
        val em = label("", 14f, sub, false)
        em.gravity = Gravity.CENTER
        frame.addView(em, FrameLayout.LayoutParams(-1, -1))
        lv.emptyView = em

        val fb = fab("+  Tambah") { openForm(null) }
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fp.gravity = Gravity.BOTTOM or Gravity.END
        fp.bottomMargin = dp(18)
        frame.addView(fb, fp)

        // bar Edit / Hapus saat mode pilih
        val ab = LinearLayout(this)
        ab.orientation = LinearLayout.HORIZONTAL
        ab.background = shape(surface, 28, line)
        ab.setPadding(dp(10), dp(10), dp(10), dp(10))
        val pe = pill("Edit", true) { bulkEdit() }
        pe.textSize = 15f
        val pc = pill("Salin", false) { copySelected() }
        pc.textSize = 15f
        val pd = pill("Hapus", false) { bulkDelete() }
        pd.textSize = 15f
        val pel = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        pel.rightMargin = dp(4)
        val pcl = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        pcl.leftMargin = dp(4)
        pcl.rightMargin = dp(4)
        val pdl = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        pdl.leftMargin = dp(4)
        ab.addView(pe, pel)
        ab.addView(pc, pcl)
        ab.addView(pd, pdl)
        ab.visibility = View.GONE
        val abp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        abp.gravity = Gravity.BOTTOM
        abp.bottomMargin = dp(16)
        frame.addView(ab, abp)

        col.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        lvData = lv
        adapterD = ad
        emptyD = em
        fabData = fb
        actBar = ab
        setContentView(col)
        if (dir != 0) enter(col, dir)
        buildTop()
        buildSearch()
        refreshData()
    }

    private fun buildTop() {
        val th = topHolder ?: return
        val f = current ?: return
        th.removeAllViews()
        if (selected.isNotEmpty()) {
            th.addView(iconBtn(3, false) {
                selected.clear()
                onSelectionChanged()
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            val t = label("${selected.size} dipilih", 18f, fg, true)
            val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tp.leftMargin = dp(12)
            th.addView(t, tp)
            th.addView(pill("Pilih semua", false) { toggleAll() })
        } else {
            th.addView(iconBtn(0, false) { showFolders(-1) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            val t = label(f.name, 20f, fg, true)
            t.isSingleLine = true
            t.ellipsize = TextUtils.TruncateAt.END
            val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tp.leftMargin = dp(12)
            th.addView(t, tp)
            th.addView(iconBtn(1, searchOpen) { toggleSearch() }, iconLp())
            th.addView(iconBtn(2, false) { menuData() }, iconLp())
        }
    }

    private fun updateBars() {
        val sel = selected.isNotEmpty()
        if (sel == barOn) return
        barOn = sel
        setShown(fabData, !sel)
        setShown(actBar, sel)
    }

    private fun onSelectionChanged() {
        buildTop()
        updateBars()
        adapterD?.notifyDataSetChanged()
    }

    private fun toggleAll() {
        var all = shown.isNotEmpty()
        for (r in shown) if (!selected.contains(r)) all = false
        if (all) selected.clear() else selected.addAll(shown)
        onSelectionChanged()
    }

    // ---------- pencarian ----------
    private fun searchType() = if (searchField == 1) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT

    private fun buildSearch() {
        val sh = searchHolder ?: return
        sh.removeAllViews()
        if (!searchOpen) {
            sh.visibility = View.GONE
            searchEt = null
            return
        }
        sh.visibility = View.VISIBLE
        sh.alpha = 0f
        sh.translationY = -dp(10).toFloat()
        sh.animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
        val et = input("Cari ${fieldNames[searchField].lowercase()}", query, searchType())
        et.isSingleLine = true
        et.background = shape(surface, 14, fg)
        et.addTextChangedListener(watcher { query = it; refreshData() })
        searchEt = et
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(iconBtn(3, false) { et.setText("") }, iconLp())
        sh.addView(row)

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        val list = ArrayList<TextView>()
        for (i in fieldNames.indices) {
            val c = pill(fieldNames[i], i == searchField) {}
            c.setPadding(dp(14), dp(8), dp(14), dp(8))
            c.setOnClickListener {
                searchField = i
                for (j in list.indices) restyle(list[j], j == i)
                et.inputType = searchType()
                et.hint = "Cari ${fieldNames[i].lowercase()}"
                et.setText("")
            }
            list.add(c)
            val cp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cp.rightMargin = dp(8)
            chips.addView(c, cp)
        }
        hs.addView(chips)
        sh.addView(hs, lp(10))
    }

    private fun toggleSearch() {
        searchOpen = !searchOpen
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (!searchOpen) {
            query = ""
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
        buildTop()
        buildSearch()
        refreshData()
        if (searchOpen) {
            val et = searchEt
            if (et != null) {
                et.post {
                    et.requestFocus()
                    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    // ---------- filter + total ----------
    private fun refreshData() {
        val f = current ?: return
        val q = query.trim()
        shown.clear()
        for (r in f.items) {
            if (q.isNotEmpty()) {
                val v = when (searchField) {
                    0 -> r.email
                    1 -> r.server
                    2 -> r.level
                    else -> r.note
                }
                if (!v.contains(q, true)) continue
            }
            shown.add(r)
        }
        adapterD?.notifyDataSetChanged()
        val searching = q.isNotEmpty()
        statAkun?.text = shown.size.toString()
        statServer?.text = sumServer(shown).toString()
        lblAkun?.text = if (searching) "AKUN DICARI" else "AKUN"
        lblServer?.text = if (searching) "SERVER DICARI" else "SERVER"
        emptyD?.text = if (f.items.isEmpty()) "Belum ada data.\nTekan + Tambah untuk mulai." else "Tidak ada hasil."
    }

    // ---------- baris akun ----------
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
        private val card = LinearLayout(ctx)
        private val tvNo = TextView(ctx)
        private val chk = IconView(ctx, 6, fg, bg)
        private val tvEmail = TextView(ctx)
        private val tvServer = TextView(ctx)
        private val tvNote = TextView(ctx)
        private val tvLevel = TextView(ctx)
        private var pos = 0

        init {
            card.orientation = LinearLayout.HORIZONTAL
            card.setPadding(dp(14), dp(12), dp(14), dp(12))

            val left = FrameLayout(ctx)
            tvNo.textSize = 12f
            tvNo.setTextColor(sub)
            tvNo.typeface = Typeface.DEFAULT_BOLD
            left.addView(tvNo, FrameLayout.LayoutParams(-2, -2))
            chk.visibility = View.GONE
            left.addView(chk, FrameLayout.LayoutParams(dp(22), dp(22)))
            card.addView(left, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))

            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.VERTICAL
            tvEmail.textSize = 15f
            tvEmail.setTextColor(fg)
            tvEmail.typeface = Typeface.DEFAULT_BOLD
            tvServer.textSize = 13f
            tvServer.setTextColor(fg)
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

            Gesture(root,
                {
                    // tap: hanya aktif saat mode pilih
                    if (selected.isNotEmpty()) {
                        val r = shown.getOrNull(pos)
                        if (r != null) {
                            if (selected.contains(r)) selected.remove(r) else selected.add(r)
                            onSelectionChanged()
                        }
                    }
                },
                {
                    // tahan 200ms: edit satu akun (di luar mode pilih)
                    if (selected.isEmpty()) {
                        val r = shown.getOrNull(pos)
                        if (r != null) openForm(r)
                    }
                },
                {
                    // geser kiri: pilih akun
                    val r = shown.getOrNull(pos)
                    if (r != null) {
                        selected.add(r)
                        ui.post { onSelectionChanged() }
                    }
                }).attach()
        }

        fun bind(p: Int, r: Rec) {
            pos = p
            val sel = selected.contains(r)
            card.background = if (sel) shape(selSurface, 16, fg, 2) else shape(surface, 16, line)
            tvNo.text = (p + 1).toString()
            tvNo.visibility = if (sel) View.GONE else View.VISIBLE
            val wasSel = chk.visibility == View.VISIBLE
            chk.visibility = if (sel) View.VISIBLE else View.GONE
            if (sel && !wasSel) {
                chk.scaleX = 0f
                chk.scaleY = 0f
                chk.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(OvershootInterpolator()).start()
            } else {
                chk.animate().cancel()
                chk.scaleX = 1f
                chk.scaleY = 1f
            }
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
        content.alpha = 0f
        content.scaleX = 0.92f
        content.scaleY = 0.92f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
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
        row.addView(pill("Batal", false) { d?.dismiss() })
        for (a in actions) {
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.leftMargin = dp(8)
            row.addView(pill(a.first, a.second) { d?.dismiss(); a.third() }, p)
        }
        b.addView(row, lp(18))
        d = showBox(b)
    }

    private fun popupMenu(entries: List<Pair<String, () -> Unit>>) {
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.background = shape(bg, 16, line)
        b.setPadding(0, dp(6), 0, dp(6))
        val d = Dialog(this)
        for (e in entries) {
            val t = label(e.first, 15f, fg, false)
            t.setPadding(dp(18), dp(13), dp(18), dp(13))
            t.isClickable = true
            t.setOnClickListener { d.dismiss(); e.second() }
            b.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(b)
        val w = d.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(dp(230), WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.TOP or Gravity.END)
            val a = w.attributes
            a.x = dp(12)
            a.y = dp(56)
            a.dimAmount = 0.35f
            w.attributes = a
        }
        d.show()
        b.alpha = 0f
        b.translationY = -dp(10).toFloat()
        b.animate().alpha(1f).translationY(0f).setDuration(180)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun menuData() {
        popupMenu(listOf<Pair<String, () -> Unit>>(
            Pair("Salin Format") { copyFormat() },
            Pair("Backup folder ini") { doBackup(false) },
            Pair("Import ke folder ini") { doImport(false) },
            Pair("Hapus Semua") { clearAll() },
            Pair(if (dark) "Mode Terang" else "Mode Gelap") { toggleTheme() }
        ))
    }

    // ---------- form tambah / edit satu akun ----------
    private fun openForm(rec: Rec?) {
        val f = current ?: return
        val b = box()
        b.addView(label(if (rec == null) "Tambah Data" else "Edit Data", 20f, fg, true))
        val t = InputType.TYPE_CLASS_TEXT
        val fEmail = input("Email", rec?.email ?: "", t or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val fServer = input("Server", rec?.server ?: "", InputType.TYPE_CLASS_NUMBER)
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
                confirmWipe(f, listOf(rec)) {}
            })
        }
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
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
                f.items.add(Rec(email, server, level, note))
            } else {
                rec.email = email
                rec.server = server
                rec.level = level
                rec.note = note
            }
            save()
            refreshData()
            if (rec == null && query.isBlank() && shown.isNotEmpty()) lvData?.setSelection(shown.size - 1)
            d?.dismiss()
        }, pSave)
        b.addView(row, lp(18))

        val sv = ScrollView(this)
        sv.addView(b)
        d = showBox(sv)
    }

    // ---------- edit & hapus banyak akun ----------
    private fun bulkEdit() {
        val targets = ArrayList<Rec>(selected)
        if (targets.isEmpty()) return
        val b = box()
        b.addView(label("Edit ${targets.size} akun", 20f, fg, true))
        b.addView(label("Pilih yang mau diubah:", 13f, sub, false), lp(4))

        val t = InputType.TYPE_CLASS_TEXT
        val on = BooleanArray(4)
        val fields = arrayOf(
            input("Email baru", "", t or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            input("Server baru", "", InputType.TYPE_CLASS_NUMBER),
            input("Level baru", "", t),
            input("Keterangan baru", "", t or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        )
        val chips = ArrayList<TextView>()
        for (i in 0 until 4) {
            val c = pill(fieldNames[i], false) {}
            c.setOnClickListener {
                on[i] = !on[i]
                restyle(c, on[i])
                fields[i].visibility = if (on[i]) View.VISIBLE else View.GONE
            }
            chips.add(c)
            fields[i].visibility = View.GONE
        }
        for (r in 0 until 2) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in 0 until 2) {
                val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (k == 0) p.rightMargin = dp(4) else p.leftMargin = dp(4)
                row.addView(chips[r * 2 + k], p)
            }
            b.addView(row, lp(if (r == 0) 10 else 8))
        }
        for (i in 0 until 4) b.addView(fields[i], lp(10))

        val btn = LinearLayout(this)
        btn.orientation = LinearLayout.HORIZONTAL
        btn.gravity = Gravity.END
        var d: Dialog? = null
        btn.addView(pill("Batal", false) { d?.dismiss() })
        val ps = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ps.leftMargin = dp(8)
        btn.addView(pill("Terapkan", true) {
            var any = false
            for (x in on) if (x) any = true
            if (!any) {
                toast("Pilih field yang mau diubah")
                return@pill
            }
            if (on[0] && fields[0].text.toString().trim().isEmpty()) {
                fields[0].error = "Wajib diisi"
                return@pill
            }
            for (r in targets) {
                if (on[0]) r.email = fields[0].text.toString().trim()
                if (on[1]) r.server = fields[1].text.toString().trim()
                if (on[2]) r.level = fields[2].text.toString().trim()
                if (on[3]) r.note = fields[3].text.toString().trim()
            }
            selected.clear()
            save()
            refreshData()
            onSelectionChanged()
            d?.dismiss()
            toast("${targets.size} akun diubah")
        }, ps)
        b.addView(btn, lp(18))

        val sv = ScrollView(this)
        sv.addView(b)
        d = showBox(sv)
    }

    private fun bulkDelete() {
        val f = current ?: return
        val targets = ArrayList<Rec>(selected)
        if (targets.isEmpty()) return
        confirmWipe(f, targets) {
            selected.clear()
            onSelectionChanged()
        }
    }

    private fun isEmptyData(r: Rec) = r.server.isBlank() && r.level.isBlank() && r.note.isBlank()

    // Hapus = kosongkan server, level, keterangan (email tetap).
    // Akun yang datanya sudah kosong, kalau dihapus lagi, baru hilang dari daftar.
    private fun confirmWipe(f: Folder, targets: List<Rec>, after: () -> Unit) {
        var clear = 0
        for (r in targets) if (!isEmptyData(r)) clear++
        val remove = targets.size - clear
        val msg = if (remove == 0) {
            "Server, level, dan keterangan $clear akun akan dikosongkan. Email tetap ada."
        } else if (clear == 0) {
            "$remove akun yang datanya sudah kosong akan dihapus dari daftar."
        } else {
            "Server, level, dan keterangan $clear akun akan dikosongkan (email tetap ada). $remove akun yang sudah kosong akan dihapus dari daftar."
        }
        sheet("Hapus ${targets.size} akun?", msg, listOf(Triple("Hapus", true) {
            for (r in targets) {
                if (isEmptyData(r)) {
                    f.items.remove(r)
                    selected.remove(r)
                } else {
                    r.server = ""
                    r.level = ""
                    r.note = ""
                }
            }
            save()
            refreshData()
            after()
        }))
    }

    // Salin akun terpilih: baris yang kosong dilewati
    private fun copySelected() {
        val f = current ?: return
        val sb = StringBuilder()
        var n = 0
        for (r in f.items) {
            if (!selected.contains(r)) continue
            val lines = ArrayList<String>()
            if (r.email.isNotBlank()) lines.add("email:${r.email}")
            if (r.server.isNotBlank()) lines.add("server:${r.server}")
            if (r.level.isNotBlank()) lines.add("level:${r.level}")
            if (r.note.isNotBlank()) lines.add("keterangan:${r.note}")
            if (n > 0) sb.append("\n\n")
            sb.append(lines.joinToString("\n"))
            n++
        }
        if (n == 0) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Catatan Cabang", sb.toString()))
        toast("$n akun disalin")
    }

    // ---------- aksi lain ----------
    private fun copyFormat() {
        val f = current ?: return
        val list: List<Rec> = if (query.isNotBlank()) shown else f.items
        if (list.isEmpty()) {
            toast("Tidak ada data")
            return
        }
        // format: email<TAB>server<TAB>level<TAB>keterangan  (satu baris per data)
        val text = list.joinToString("\n") { "${it.email}\t${it.server}\t${it.level}\t${it.note}" }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Catatan Cabang", text))
        toast("${list.size} data disalin")
    }

    private fun clearAll() {
        val f = current ?: return
        if (f.items.isEmpty()) {
            toast("Data sudah kosong")
            return
        }
        sheet("Hapus semua data?", "${f.items.size} akun di folder ini akan dihapus permanen. Buat backup dulu kalau perlu.",
            listOf(Triple("Hapus Semua", true) {
                f.items.clear()
                selected.clear()
                save()
                refreshData()
                onSelectionChanged()
            }))
    }

    private fun doBackup(all: Boolean) {
        pendingAll = all
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, "catatan-cabang-backup.json")
        startActivityForResult(i, REQ_BACKUP)
    }

    private fun doImport(all: Boolean) {
        pendingAll = all
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
                val txt = if (pendingAll) {
                    JSONObject().put("folders", foldersJson()).toString(2)
                } else {
                    recsJson(current?.items ?: ArrayList()).toString(2)
                }
                contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(txt.toByteArray(Charsets.UTF_8))
                }
                toast("Backup tersimpan")
            } else if (requestCode == REQ_IMPORT) {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                val parsed = parseFolders(text)
                val recs = ArrayList<Rec>()
                for (pf in parsed) recs.addAll(pf.items)
                if (pendingAll) {
                    if (parsed.isEmpty()) {
                        toast("File tidak berisi data")
                        return
                    }
                    sheet("Import ${parsed.size} folder", "Gabung dengan folder sekarang, atau ganti semuanya?", listOf(
                        Triple("Gabung", false) {
                            folders.addAll(parsed)
                            save()
                            refreshFolders()
                        },
                        Triple("Ganti", true) {
                            folders.clear()
                            folders.addAll(parsed)
                            save()
                            refreshFolders()
                        }
                    ))
                } else {
                    val f = current ?: return
                    if (recs.isEmpty()) {
                        toast("File tidak berisi data")
                        return
                    }
                    sheet("Import ${recs.size} akun", "Gabung dengan data folder ini, atau ganti semuanya?", listOf(
                        Triple("Gabung", false) {
                            f.items.addAll(recs)
                            save()
                            refreshData()
                        },
                        Triple("Ganti", true) {
                            f.items.clear()
                            f.items.addAll(recs)
                            selected.clear()
                            save()
                            refreshData()
                            onSelectionChanged()
                        }
                    ))
                }
            }
        } catch (e: Exception) {
            toast("Gagal: ${e.message}")
        }
    }

    // ---------- penyimpanan ----------
    private fun recsJson(list: List<Rec>): JSONArray {
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

    private fun foldersJson(): JSONArray {
        val arr = JSONArray()
        for (f in folders) {
            val o = JSONObject()
            o.put("name", f.name)
            o.put("items", recsJson(f.items))
            arr.put(o)
        }
        return arr
    }

    private fun parseRecs(arr: JSONArray): ArrayList<Rec> {
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

    private fun folderFrom(o: JSONObject): Folder {
        val name = o.optString("name", "Folder")
        return Folder(name, parseRecs(o.optJSONArray("items") ?: JSONArray()))
    }

    private fun parseFolders(text: String): List<Folder> {
        val t = text.trim()
        val out = ArrayList<Folder>()
        if (t.startsWith("[")) {
            val arr = JSONArray(t)
            val first = arr.optJSONObject(0)
            if (first != null && first.has("items")) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    out.add(folderFrom(o))
                }
            } else {
                out.add(Folder("Import", parseRecs(arr)))
            }
        } else {
            val o = JSONObject(t)
            val fa = o.optJSONArray("folders")
            if (fa != null) {
                for (i in 0 until fa.length()) {
                    val fo = fa.optJSONObject(i) ?: continue
                    out.add(folderFrom(fo))
                }
            } else {
                val a = o.optJSONArray("data") ?: o.optJSONArray("items") ?: o.optJSONArray("records") ?: JSONArray()
                out.add(Folder("Import", parseRecs(a)))
            }
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
        prefs.edit().putString("folders", foldersJson().toString()).apply()
    }

    private fun load() {
        folders.clear()
        try {
            val s = prefs.getString("folders", null)
            if (s != null) {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    folders.add(folderFrom(o))
                }
                return
            }
            // migrasi dari versi lama (data satu daftar) -> folder "Catatan"
            val old = prefs.getString("data", null)
            if (old != null) {
                val recs = parseRecs(JSONArray(old))
                if (recs.isNotEmpty()) {
                    folders.add(Folder("Catatan", recs))
                    save()
                }
            }
        } catch (e: Exception) {
        }
    }
}
