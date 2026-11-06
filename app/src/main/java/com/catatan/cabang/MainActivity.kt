package com.catatan.cabang

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class Rec(
    var email: String,
    var server: String,
    var level: String,
    var note: String
) {
    var t: Long = 0L   // waktu perubahan terakhir (untuk sinkron)
}

class Folder(var name: String, val items: ArrayList<Rec> = ArrayList(), var id: String = "") {
    var note: String = ""   // keterangan folder
    var t: Long = 0L   // waktu perubahan email, ID, atau keterangan (untuk sinkron)
}

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
            7 -> { // centang (konfirmasi)
                path.moveTo(5f, 12.5f); path.lineTo(10f, 17.5f); path.lineTo(19f, 7f)
                c.drawPath(path, p)
            }
            10 -> { // salin: dua kotak bertumpuk
                c.drawRoundRect(9f, 9f, 20f, 20f, 2f, 2f, p)
                path.moveTo(5f, 15f); path.lineTo(5f, 6f); path.quadTo(5f, 4f, 7f, 4f); path.lineTo(15f, 4f)
                c.drawPath(path, p)
            }
            11 -> { // edit: pensil
                path.moveTo(4f, 20f); path.lineTo(8f, 20f); path.lineTo(19f, 9f); path.lineTo(15f, 5f); path.lineTo(4f, 16f); path.close()
                c.drawPath(path, p)
                c.drawLine(13.5f, 6.5f, 17.5f, 10.5f, p)
            }
            12 -> { // hapus: tong sampah
                c.drawLine(4f, 7f, 20f, 7f, p)
                c.drawLine(10f, 11f, 10f, 17f, p)
                c.drawLine(14f, 11f, 14f, 17f, p)
                path.moveTo(6f, 7f); path.lineTo(7f, 19f); path.lineTo(17f, 19f); path.lineTo(18f, 7f)
                c.drawPath(path, p)
                path.reset()
                path.moveTo(9f, 7f); path.lineTo(9f, 4f); path.lineTo(15f, 4f); path.lineTo(15f, 7f)
                c.drawPath(path, p)
            }
            8 -> { // unduh: panah ke bawah masuk ke nampan
                c.drawLine(12f, 4f, 12f, 15f, p)
                path.moveTo(7.5f, 10.5f); path.lineTo(12f, 15f); path.lineTo(16.5f, 10.5f)
                c.drawPath(path, p)
                path.reset()
                path.moveTo(4.5f, 15.5f); path.lineTo(4.5f, 19.5f); path.lineTo(19.5f, 19.5f); path.lineTo(19.5f, 15.5f)
                c.drawPath(path, p)
            }
            9 -> { // kirim: tiga titik terhubung
                c.drawCircle(18f, 5.5f, 2.6f, p)
                c.drawCircle(6f, 12f, 2.6f, p)
                c.drawCircle(18f, 18.5f, 2.6f, p)
                c.drawLine(8.2f, 10.8f, 15.8f, 6.7f, p)
                c.drawLine(8.2f, 13.2f, 15.8f, 17.3f, p)
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

// Penanda pilihan: cincin, dan titik di tengah saat dipilih (gaya pilihan iOS)
class RadioView(ctx: Context, private val offColor: Int, private val onColor: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var on = false
        set(v) {
            field = v
            invalidate()
        }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = 2f * d
        val r = minOf(w, h) / 2f - sw / 2f - 1f
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.color = if (on) onColor else offColor
        c.drawCircle(w / 2f, h / 2f, r, p)
        if (on) {
            p.style = Paint.Style.FILL
            c.drawCircle(w / 2f, h / 2f, r * 0.55f, p)
        }
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
    private var searchField = 0          // 0 server, 1 level, 2 keterangan
    private var statFilter = 0           // 0 semua, atau kode status 1..6 (Siap, Top Up, 1 klaim, Biasa, Terpakai, Baru)
    private val srvSel = BooleanArray(7) // status yang dipilih di pencarian Server (indeks = kode status)
    private val matchCounts = HashMap<Folder, IntArray>()
    private var totalTv: TextView? = null
    private var srvRow: View? = null
    private var chipHolder: LinearLayout? = null
    private var countPill: TextView? = null
    private var sumBox: View? = null
    private var topTab = 0               // 0 Akun, 1 Bukti kirim
    private var fOpen = false
    private var fField = 0               // 0 email, 1 ID
    private var fQuery = ""
    private val shownF = ArrayList<Folder>()
    private var fSearchHolder: LinearLayout? = null
    private val cGreen = 0xFF7FC8A0.toInt()
    private val cAmber = 0xFFD9B25F.toInt()
    private val cDone = 0xFF8A8A8A.toInt()
    private val cTop = 0xFF6FA8DC.toInt()
    private val cKlaim = 0xFFE59A5B.toInt()
    private val cBaru = 0xFFCFCFCF.toInt()
    private val stOrder = intArrayOf(1, 2, 3, 4, 5, 6)

    private fun stColor(st: Int): Int = when (st) {
        Store.ST_SIAP -> cGreen
        Store.ST_TOPUP -> cTop
        Store.ST_KLAIM -> cKlaim
        Store.ST_BIASA -> cAmber
        Store.ST_PAKAI -> cDone
        Store.ST_BARU -> cBaru
        else -> sub
    }

    private fun stName(st: Int): String = when (st) {
        Store.ST_SIAP -> "Siap"
        Store.ST_TOPUP -> "Top Up"
        Store.ST_KLAIM -> "1 klaim"
        Store.ST_BIASA -> "Biasa"
        Store.ST_PAKAI -> "Terpakai"
        Store.ST_BARU -> "Baru"
        else -> ""
    }

    // Urutan alami: angka lebih dulu dari huruf, deretan angka dibandingkan sebagai bilangan (2 sebelum 10), huruf tanpa melihat besar kecil
    private fun natCmp(a: String, b: String): Int {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            val cx = x[i]
            val cy = y[j]
            if (cx.isDigit() && cy.isDigit()) {
                var i2 = i
                while (i2 < x.length && x[i2].isDigit()) i2++
                var j2 = j
                while (j2 < y.length && y[j2].isDigit()) j2++
                val nx = x.substring(i, i2).trimStart('0')
                val ny = y.substring(j, j2).trimStart('0')
                if (nx.length != ny.length) return nx.length - ny.length
                val c = nx.compareTo(ny)
                if (c != 0) return c
                i = i2
                j = j2
            } else {
                if (cx != cy) {
                    if (cx.isDigit() != cy.isDigit()) return if (cx.isDigit()) -1 else 1
                    return cx.compareTo(cy)
                }
                i++
                j++
            }
        }
        return (x.length - i) - (y.length - j)
    }

    // Folder urut berdasarkan email (a sampai z); yang emailnya kosong di paling bawah, diurutkan menurut ID
    private fun sortFolders() {
        shownF.sortWith { a, b ->
            val ab = a.name.isBlank()
            val bb = b.name.isBlank()
            when {
                ab && bb -> natCmp(a.id, b.id)
                ab -> 1
                bb -> -1
                else -> natCmp(a.name, b.name)
            }
        }
    }

    private fun addDot(sb: SpannableStringBuilder, color: Int, text: String) {
        val st = sb.length
        sb.append("●")
        sb.setSpan(ForegroundColorSpan(color), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(" ").append(text)
    }

    // navigasi ala iPhone
    private var rootFrame: FrameLayout? = null
    private var homeView: View? = null
    private var scrim: View? = null
    private var detailView: View? = null
    private var navP = 0f
    private var navAnim: ValueAnimator? = null
    private var heightAnim: ValueAnimator? = null
    private var pager: FrameLayout? = null
    private val pages = ArrayList<View>()
    private var pos = 0f
    private var posAnim: ValueAnimator? = null
    private var tabThumb: View? = null
    private var tabThumbW = 0f
    private val tabLabels = ArrayList<TextView>()
    private var searchBtn: FrameLayout? = null
    private var sumLine: TextView? = null
    private var sumLegend: TextView? = null
    private var sumBar: LinearLayout? = null

    // pilih banyak di halaman utama (daftar akun dan daftar bukti kirim)
    private var homeSel = false
    private val selF = LinkedHashSet<Folder>()
    private val selB = LinkedHashSet<Order>()
    private var homeTop: LinearLayout? = null
    private var homeBar: LinearLayout? = null
    private var homeEditBtn: View? = null
    private var homeFab: View? = null
    private var selTitle: TextView? = null
    private val argb = ArgbEvaluator()

    // bukti kirim
    private val orders = ArrayList<Order>()
    private var curOrder: Order? = null
    private var lvBukti: ListView? = null
    private var adapterB: BaseAdapter? = null
    private var query = ""               // teks yang sedang diketik
    private var applied = false          // sudah dikonfirmasi (tekan centang)?
    private var appliedQ = ""            // teks yang dipakai saat dikonfirmasi
    private var pendingAll = false
    private val fieldNames = arrayOf("Server", "Level", "Keterangan")

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
    private var statPakai: TextView? = null
    private var lblAkun: TextView? = null
    private var lblServer: TextView? = null
    private var fabData: View? = null
    private var actBar: View? = null
    private var barOn = false

    // ---------- tema hitam putih ----------
    private val bg get() = if (dark) 0xFF1F1F1F.toInt() else 0xFFEFEFEF.toInt()
    private val fg get() = if (dark) 0xFFEDEDED.toInt() else 0xFF1F1F1F.toInt()
    private val surface get() = if (dark) 0xFF2A2A2A.toInt() else 0xFFF8F8F8.toInt()
    private val surface2 get() = if (dark) 0xFF333333.toInt() else 0xFFE4E4E4.toInt()
    private val selSurface get() = if (dark) 0xFF363636.toInt() else 0xFFE0E0E0.toInt()
    private val line get() = if (dark) 0xFF3D3D3D.toInt() else 0xFFD6D6D6.toInt()
    private val sub get() = if (dark) 0xFFA3A3A3.toInt() else 0xFF6B6B6B.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("catatan", Context.MODE_PRIVATE)
        dark = prefs.getBoolean("dark", true)
        SyncEngine.init(applicationContext)
        load()
        showFolders()
    }

    override fun onResume() {
        super.onResume()
        Store.listener = { runOnUiThread { reloadFromStore() } }
        if (Store.changed) reloadFromStore()
        SyncEngine.startPeriodic()
        SyncEngine.syncNow(true, null)
    }

    override fun onPause() {
        Store.listener = null
        SyncEngine.stopPeriodic()
        super.onPause()
    }

    private fun reloadFromStore() {
        Store.changed = false
        if (homeSel) exitHomeSel()
        val oc = curOrder
        if (oc != null) {
            load()
            refreshFolders()
            refreshBukti()
            val n = orders.firstOrNull { it.key == oc.key }
            if (n == null) leaveFolder(true) else showOrder(n, false)
            return
        }
        val wasIn = current
        val keyId = wasIn?.id ?: ""
        val keyName = wasIn?.name ?: ""
        load()
        refreshBukti()
        selected.clear()
        if (wasIn != null) {
            current = folders.firstOrNull {
                if (keyId.isNotEmpty()) it.id == keyId else (it.id.isEmpty() && it.name == keyName)
            }
        }
        if (wasIn == null) {
            refreshFolders()
        } else if (current == null) {
            showFolders()
        } else {
            refreshData()
            onSelectionChanged()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (homeSel) {
            exitHomeSel()
        } else if (selected.isNotEmpty()) {
            selected.clear()
            onSelectionChanged()
        } else if (searchOpen && current != null) {
            toggleSearch()
        } else if (current != null || curOrder != null) {
            showFolders(-1)
        } else if (fOpen && topTab == 0) {
            closeFolderSearch()
        } else if (topTab == 1 || pos > 0.5f) {
            goTo(0)
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
                x.animate().cancel()
                x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90)
                    .setInterpolator(DecelerateInterpolator()).start()
            } else if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).setDuration(340)
                    .setInterpolator(OvershootInterpolator(2.4f)).start()
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
            v.animate().alpha(1f).translationY(0f).setDuration(320)
                .setInterpolator(iosCurve()).start()
        } else {
            if (v.visibility != View.VISIBLE) return
            v.animate().cancel()
            v.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(200)
                .setInterpolator(iosCurve()).withEndAction { v.visibility = View.GONE }.start()
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
        val oc = curOrder
        buildRoot()
        if (c != null) showData(c, 0)
        else if (oc != null) showOrder(oc, false)
    }

    // ---------- gesture: tap / tahan 200ms / geser kiri ----------
    private inner class Gesture(
        val v: View,
        val onTap: (() -> Unit)?,
        val onLong: (() -> Unit)?,
        val onSwipe: (() -> Unit)?,
        val swipeRight: Boolean = false
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
                            } else if (onSwipe != null && (if (swipeRight) dx else -dx) > slop && abs(dx) > abs(dy)) {
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
                                if (tx > maxDrag) tx = maxDrag
                                v.translationX = tx
                                if ((if (swipeRight) dx else -dx) > swipeDist) {
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
    // =====================================================
    // NAVIGASI ALA iPHONE
    // =====================================================
    private fun iosCurve() = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    private fun onAnimDone(a: Animator, block: () -> Unit) {
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(an: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(an: Animator) {
                if (!cancelled) block()
            }
        })
    }

    private fun rootW(): Float {
        val w = rootFrame?.width ?: 0
        return (if (w > 0) w else resources.displayMetrics.widthPixels).toFloat()
    }

    private fun layers(on: Boolean) {
        val t = if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        homeView?.setLayerType(t, null)
        detailView?.setLayerType(t, null)
    }

    // p = 0: daftar akun tampil. p = 1: isi folder tampil.
    private fun applyNav(p: Float) {
        navP = p
        val w = rootW()
        homeView?.translationX = -p * w * 0.28f
        detailView?.translationX = (1f - p) * w
        val sc = scrim
        if (sc != null) {
            sc.visibility = if (p > 0.001f) View.VISIBLE else View.GONE
            sc.alpha = 0.35f * p
        }
    }

    private fun animateNav(to: Float) {
        navAnim?.cancel()
        val from = navP
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = (140 + 340 * abs(to - from)).toLong()
        a.interpolator = iosCurve()
        homeView?.visibility = View.VISIBLE
        layers(true)
        a.addUpdateListener { applyNav(it.animatedValue as Float) }
        onAnimDone(a) { finishNav(to) }
        navAnim = a
        a.start()
    }

    private fun finishNav(to: Float) {
        layers(false)
        navP = to
        if (to <= 0f) {
            detailView?.let { rootFrame?.removeView(it) }
            detailView = null
            homeView?.visibility = View.VISIBLE
            homeView?.translationX = 0f
            scrim?.visibility = View.GONE
        } else {
            applyNav(1f)
            homeView?.visibility = View.INVISIBLE
        }
    }

    private fun pushDetail(col: View, animated: Boolean) {
        val root = rootFrame ?: return
        navAnim?.cancel()
        detailView?.let { root.removeView(it) }
        detailView = col
        col.elevation = dp(12).toFloat()
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        homeView?.visibility = View.VISIBLE
        if (animated) {
            applyNav(0f)
            col.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    col.viewTreeObserver.removeOnPreDrawListener(this)
                    animateNav(1f)
                    return true
                }
            })
        } else {
            applyNav(1f)
            finishNav(1f)
        }
    }

    private fun leaveFolder(animated: Boolean) {
        current = null
        curOrder = null
        selected.clear()
        searchOpen = false
        query = ""
        applied = false
        appliedQ = ""
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        refreshFolders()
        if (animated) animateNav(0f) else finishNav(0f)
    }

    // tarik dari tepi kiri untuk kembali (seperti iPhone)
    private inner class NavRoot(ctx: Context) : FrameLayout(ctx) {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private var x0 = 0f
        private var y0 = 0f
        private var track = false
        private var drag = false
        private var vt: VelocityTracker? = null

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (detailView == null) return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.x
                    y0 = e.y
                    drag = false
                    track = e.x < dp(48)
                    vt?.recycle()
                    vt = if (track) VelocityTracker.obtain() else null
                    vt?.addMovement(e)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (track && !drag) {
                        vt?.addMovement(e)
                        val dx = e.x - x0
                        val dy = e.y - y0
                        if (dx > slop * 2 && dx > abs(dy) * 1.3f) {
                            drag = true
                            navAnim?.cancel()
                            homeView?.visibility = View.VISIBLE
                            layers(true)
                            x0 = e.x
                            parent?.requestDisallowInterceptTouchEvent(true)
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!drag) {
                        vt?.recycle()
                        vt = null
                        track = false
                    }
                }
            }
            return drag
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (!drag) return false
            vt?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (width > 0) applyNav(1f - ((e.x - x0) / width).coerceIn(0f, 1f))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.computeCurrentVelocity(1000)
                    val v = vt?.xVelocity ?: 0f
                    val close = e.actionMasked == MotionEvent.ACTION_UP && (v > 900f || (v > -900f && navP < 0.5f))
                    drag = false
                    track = false
                    vt?.recycle()
                    vt = null
                    if (close) leaveFolder(true) else animateNav(1f)
                }
            }
            return true
        }
    }

    // =====================================================
    // HALAMAN 1: AKUN dan BUKTI KIRIM (pager)
    // =====================================================
    private fun showFolders(dir: Int = 0) {
        if (dir < 0 && detailView != null) {
            leaveFolder(true)
            return
        }
        current = null
        curOrder = null
        selected.clear()
        searchOpen = false
        query = ""
        applied = false
        appliedQ = ""
        buildRoot()
    }

    private fun buildRoot() {
        applyWindow()
        posAnim?.cancel()
        navAnim?.cancel()
        heightAnim?.cancel()
        pages.clear()
        val root = NavRoot(this)
        root.setBackgroundColor(bg)

        val home = LinearLayout(this)
        home.orientation = LinearLayout.VERTICAL
        home.setBackgroundColor(bg)
        home.setPadding(dp(16), dp(12), dp(16), 0)

        homeSel = false
        selF.clear()
        selB.clear()
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        homeTop = top
        buildHomeTop()
        home.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        home.addView(tabBar(), lp(10))

        val fs = LinearLayout(this)
        fs.orientation = LinearLayout.VERTICAL
        home.addView(fs, lp(8))
        fSearchHolder = fs

        val pg = HomePager(this)
        val p0 = FrameLayout(this)
        val col0 = LinearLayout(this)
        col0.orientation = LinearLayout.VERTICAL
        val sbx = buildSummary()
        sumBox = sbx
        col0.addView(sbx, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val body0 = FrameLayout(this)
        val lv = ListView(this)
        styleList(lv)
        val ad = FolderAdapter()
        lv.adapter = ad
        body0.addView(lv, FrameLayout.LayoutParams(-1, -1))
        val em = label("", 14f, sub, false)
        em.gravity = Gravity.CENTER
        body0.addView(em, FrameLayout.LayoutParams(-1, -1))
        lv.emptyView = em
        val fb = fab("+") { askAccount() }
        homeFab = fb
        fb.textSize = 26f
        fb.minWidth = dp(58)
        fb.minHeight = dp(58)
        fb.setPadding(0, 0, 0, dp(2))
        fb.background = shape(fg, 29)
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fp.gravity = Gravity.BOTTOM or Gravity.END
        fp.bottomMargin = dp(18)
        body0.addView(fb, fp)
        col0.addView(body0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        p0.addView(col0, FrameLayout.LayoutParams(-1, -1))
        lvFolders = lv
        adapterF = ad
        emptyF = em

        val p1 = FrameLayout(this)
        val lvb = ListView(this)
        styleList(lvb)
        val adb = BuktiAdapter()
        lvb.adapter = adb
        p1.addView(lvb, FrameLayout.LayoutParams(-1, -1))
        val em1 = label("", 14f, sub, false)
        em1.gravity = Gravity.CENTER
        p1.addView(em1, FrameLayout.LayoutParams(-1, -1))
        lvb.emptyView = em1
        lvBukti = lvb
        adapterB = adb

        pg.addView(p0, FrameLayout.LayoutParams(-1, -1))
        pg.addView(p1, FrameLayout.LayoutParams(-1, -1))
        pages.add(p0)
        pages.add(p1)
        pager = pg
        home.addView(pg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(home, FrameLayout.LayoutParams(-1, -1))

        // bar bawah saat mode pilih banyak
        val hb = LinearLayout(this)
        hb.orientation = LinearLayout.HORIZONTAL
        hb.background = shape(surface, 28, line)
        hb.setPadding(dp(10), dp(10), dp(10), dp(10))
        val psal = iconPill(10, "Salin", false, 14f) { copySelectedHome() }
        val pedt = iconPill(11, "Edit", false, 14f) { editSelectedHome() }
        val pdel = iconPill(12, "Hapus", false, 14f) { deleteSelectedHome() }
        val lsal = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lsal.rightMargin = dp(4)
        val ledt = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        ledt.leftMargin = dp(4)
        ledt.rightMargin = dp(4)
        val ldel = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        ldel.leftMargin = dp(4)
        hb.addView(psal, lsal)
        hb.addView(pedt, ledt)
        hb.addView(pdel, ldel)
        homeEditBtn = pedt
        hb.visibility = View.GONE
        val hbp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        hbp.gravity = Gravity.BOTTOM
        hbp.setMargins(dp(16), 0, dp(16), dp(16))
        root.addView(hb, hbp)
        homeBar = hb
        val sc = View(this)
        sc.setBackgroundColor(Color.BLACK)
        sc.visibility = View.GONE
        root.addView(sc, FrameLayout.LayoutParams(-1, -1))

        rootFrame = root
        homeView = home
        scrim = sc
        detailView = null
        navP = 0f
        setContentView(root)
        pos = topTab.toFloat()
        applyPos(pos)
        styleSearchBtn()
        buildFolderSearch(false)
        refreshFolders()
    }

    private fun tabBar(): FrameLayout {
        val tb = FrameLayout(this)
        tb.background = shape(surface, 22, line)
        tb.setPadding(dp(3), dp(3), dp(3), dp(3))
        val th = View(this)
        th.background = shape(fg, 20)
        tb.addView(th, FrameLayout.LayoutParams(0, 0))
        tabThumb = th
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        tabLabels.clear()
        val names = arrayOf("Akun", "Bukti kirim")
        for (i in 0..1) {
            val t = TextView(this)
            t.text = names[i]
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.typeface = Typeface.DEFAULT_BOLD
            t.setTextColor(sub)
            t.setPadding(0, dp(9), 0, dp(9))
            t.setOnClickListener { if (!homeSel) goTo(i) }
            tabLabels.add(t)
            row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        tb.addView(row, FrameLayout.LayoutParams(-1, -2))
        tb.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val iw = (r - l - dp(6)) / 2
            val ih = row.height
            val lpp = th.layoutParams
            if (lpp.width != iw || lpp.height != ih) {
                lpp.width = iw
                lpp.height = ih
                th.layoutParams = lpp
            }
            tabThumbW = iw.toFloat()
            th.translationX = pos * iw
        }
        return tb
    }

    // pos: 0 sampai 1 (pecahan saat digeser). Halaman, penanda tab, dan teks tab mengikuti angka ini.
    private fun applyPos(p: Float) {
        pos = p
        val w = (pager?.width ?: 0).toFloat()
        for (i in pages.indices) {
            val d = i - p
            val v = pages[i]
            v.visibility = if (abs(d) >= 1f) View.INVISIBLE else View.VISIBLE
            v.translationX = d * w
        }
        tabThumb?.translationX = p * tabThumbW
        for (i in tabLabels.indices) {
            val k = (1f - abs(i - p)).coerceIn(0f, 1f)
            tabLabels[i].setTextColor(argb.evaluate(k, sub, bg) as Int)
        }
        searchBtn?.alpha = (1f - p).coerceIn(0f, 1f)
        searchBtn?.isClickable = p < 0.5f
        topTab = Math.round(p).coerceIn(0, 1)
        if (p > 0.15f && fOpen) closeFolderSearch()
    }

    private fun pagerLayers(on: Boolean) {
        val t = if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        for (v in pages) v.setLayerType(t, null)
    }

    private fun goTo(i: Int) {
        posAnim?.cancel()
        val a = ValueAnimator.ofFloat(pos, i.toFloat())
        a.duration = (240 + 240 * abs(i - pos)).toLong()
        a.interpolator = iosCurve()
        pagerLayers(true)
        a.addUpdateListener { applyPos(it.animatedValue as Float) }
        onAnimDone(a) {
            applyPos(i.toFloat())
            pagerLayers(false)
        }
        posAnim = a
        a.start()
    }

    private inner class HomePager(ctx: Context) : FrameLayout(ctx) {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private var x0 = 0f
        private var y0 = 0f
        private var drag = false
        private var startPos = 0f
        private var vt: VelocityTracker? = null

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            applyPos(pos)
        }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (homeSel) return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.x
                    y0 = e.y
                    drag = false
                    vt?.recycle()
                    vt = VelocityTracker.obtain()
                    vt?.addMovement(e)
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dx = e.x - x0
                    val dy = e.y - y0
                    if (!drag && abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.3f) {
                        drag = true
                        startPos = pos
                        posAnim?.cancel()
                        pagerLayers(true)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        x0 = e.x
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!drag) {
                        vt?.recycle()
                        vt = null
                    }
                }
            }
            return drag
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            vt?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (drag && width > 0) {
                        var p = startPos - (e.x - x0) / width
                        if (p < 0f) p *= 0.35f
                        if (p > 1f) p = 1f + (p - 1f) * 0.35f
                        applyPos(p)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (drag) {
                        vt?.computeCurrentVelocity(1000)
                        val v = vt?.xVelocity ?: 0f
                        val start = Math.round(startPos)
                        val moved = pos - start
                        var target = start
                        if (v < -700f || moved > 0.22f) target = start + 1
                        else if (v > 700f || moved < -0.22f) target = start - 1
                        goTo(target.coerceIn(0, 1))
                    }
                    drag = false
                    vt?.recycle()
                    vt = null
                }
            }
            return true
        }
    }

    // ---------- pencarian di halaman akun ----------
    private fun styleSearchBtn() {
        val f = searchBtn ?: return
        f.background = shape(if (fOpen) fg else Color.TRANSPARENT, 20, line)
        f.removeAllViews()
        f.addView(IconView(this, 1, if (fOpen) bg else fg, bg), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
    }

    private fun toggleFolderSearch() {
        if (topTab != 0 || homeSel) return
        if (fOpen) closeFolderSearch() else openFolderSearch()
    }

    private fun openFolderSearch() {
        fOpen = true
        styleSearchBtn()
        buildFolderSearch(true)
    }

    private fun closeFolderSearch() {
        if (!fOpen) return
        fOpen = false
        fQuery = ""
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        styleSearchBtn()
        fSearchHolder?.let { collapseHolder(it) }
        refreshFolders()
    }

    private fun expandHolder(v: View) {
        heightAnim?.cancel()
        v.visibility = View.VISIBLE
        val ws = View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels - dp(32), View.MeasureSpec.EXACTLY)
        v.measure(ws, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val target = v.measuredHeight
        v.alpha = 0f
        val a = ValueAnimator.ofInt(0, target)
        a.duration = 300
        a.interpolator = iosCurve()
        a.addUpdateListener {
            val lpp = v.layoutParams
            lpp.height = it.animatedValue as Int
            v.layoutParams = lpp
            v.alpha = it.animatedFraction
        }
        onAnimDone(a) {
            val lpp = v.layoutParams
            lpp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            v.layoutParams = lpp
            v.alpha = 1f
        }
        heightAnim = a
        a.start()
    }

    private fun collapseHolder(v: ViewGroup) {
        heightAnim?.cancel()
        val from = v.height
        if (from <= 0) {
            v.visibility = View.GONE
            v.removeAllViews()
            return
        }
        val a = ValueAnimator.ofInt(from, 0)
        a.duration = 240
        a.interpolator = iosCurve()
        a.addUpdateListener {
            val lpp = v.layoutParams
            lpp.height = it.animatedValue as Int
            v.layoutParams = lpp
            v.alpha = 1f - it.animatedFraction
        }
        onAnimDone(a) {
            v.visibility = View.GONE
            v.removeAllViews()
            val lpp = v.layoutParams
            lpp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            v.layoutParams = lpp
            v.alpha = 1f
        }
        heightAnim = a
        a.start()
    }

    private fun buildFolderSearch(focus: Boolean) {
        val sh = fSearchHolder ?: return
        sh.removeAllViews()
        if (!fOpen) {
            sh.visibility = View.GONE
            return
        }
        val hints = arrayOf("Cari email", "Cari ID", "Cari keterangan", "Cari nomor server")
        val et = input(hints[fField], fQuery,
            if (fField == 1 || fField == 3) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT)
        et.isSingleLine = true
        et.background = shape(surface, 14, fg)
        et.addTextChangedListener(watcher {
            fQuery = it
            refreshFolders()
        })
        sh.addView(et)
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        val list = ArrayList<TextView>()
        val names = arrayOf("Email", "ID", "Keterangan", "Server")
        // baris pilihan status (hanya saat mode Server) dan total hasil
        val srv = HorizontalScrollView(this)
        srv.isHorizontalScrollBarEnabled = false
        srv.overScrollMode = View.OVER_SCROLL_NEVER
        val srvLin = LinearLayout(this)
        srvLin.orientation = LinearLayout.HORIZONTAL
        srv.addView(srvLin, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fun buildSrvChips() {
            srvLin.removeAllViews()
            for (st in stOrder) {
                srvLin.addView(statusChip("● " + stName(st), stColor(st), srvSel[st]) {
                    srvSel[st] = !srvSel[st]
                    buildSrvChips()
                    refreshFolders()
                })
            }
        }
        buildSrvChips()
        val tot = TextView(this)
        tot.textSize = 13f
        tot.setTextColor(sub)
        srvRow = srv
        totalTv = tot
        for (i in 0..3) {
            val c = pill(names[i], i == fField) {}
            c.setPadding(dp(14), dp(8), dp(14), dp(8))
            c.setOnClickListener {
                fField = i
                for (j in list.indices) restyle(list[j], j == i)
                et.inputType = if (i == 1 || i == 3) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                et.hint = hints[i]
                et.setText("")
                fQuery = ""
                srv.visibility = if (i == 3) View.VISIBLE else View.GONE
                tot.visibility = if (i == 3) View.VISIBLE else View.GONE
                refreshFolders()
            }
            list.add(c)
            val cp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cp.rightMargin = dp(8)
            chips.addView(c, cp)
        }
        sh.addView(chips, lp(10))
        sh.addView(srv, lp(8))
        sh.addView(tot, lp(10))
        srv.visibility = if (fField == 3) View.VISIBLE else View.GONE
        tot.visibility = if (fField == 3) View.VISIBLE else View.GONE
        sumBox?.visibility = if (fField == 3) View.GONE else View.VISIBLE
        expandHolder(sh)
        if (focus) {
            et.requestFocus()
            et.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(et, 0)
            }, 120)
        }
    }

    // Ringkasan total semua akun (tidak ikut pencarian): teks satu baris, legenda warna, dan bar tipis
    private fun buildSummary(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(2), dp(4), dp(2), dp(2))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val t1 = TextView(this)
        t1.textSize = 12f
        t1.setTextColor(sub)
        val t2 = TextView(this)
        t2.textSize = 11f
        t2.setTextColor(sub)
        row.addView(t1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(t2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(row)
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        val bp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
        bp.topMargin = dp(7)
        box.addView(bar, bp)
        sumLine = t1
        sumLegend = t2
        sumBar = bar
        return box
    }

    private fun updateSummary() {
        val t1 = sumLine ?: return
        val t2 = sumLegend ?: return
        val bar = sumBar ?: return
        // Saat mencari (Email, ID, Keterangan), total dan warna mengikuti akun yang tampil, bukan semua akun
        val src: List<Folder> = if (fOpen && fQuery.isNotBlank() && fField != 3) shownF else folders
        val total = IntArray(7)
        var servers = 0
        for (f in src) {
            val c = Store.counts(f.items)
            for (st in stOrder) total[st] += c[st]
            servers += f.items.size
        }
        val a = SpannableStringBuilder()
        fun num(n: Int) {
            val st = a.length
            a.append(n.toString())
            a.setSpan(StyleSpan(Typeface.BOLD), st, a.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            a.setSpan(ForegroundColorSpan(fg), st, a.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        num(servers)
        a.append(" server · ")
        num(src.size)
        a.append(" akun")
        t1.text = a
        val l = SpannableStringBuilder()
        for (st in stOrder) {
            if (total[st] > 0) {
                if (l.isNotEmpty()) l.append("   ")
                addDot(l, stColor(st), total[st].toString())
            }
        }
        t2.text = l
        bar.removeAllViews()
        fun seg(color: Int, n: Int) {
            if (n <= 0) return
            val v = View(this)
            v.background = shape(color, 2)
            val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, n.toFloat())
            if (bar.childCount > 0) p.leftMargin = dp(2)
            bar.addView(v, p)
        }
        if (servers == 0) {
            seg(line, 1)
        } else {
            for (st in stOrder) seg(stColor(st), total[st])
        }
    }

    private fun refreshFolders() {
        // ringkasan halaman utama disembunyikan selama pencarian Server terbuka (total hasil sudah ada di atas daftar)
        sumBox?.visibility = if (fOpen && fField == 3) View.GONE else View.VISIBLE
        shownF.clear()
        matchCounts.clear()
        val q = fQuery.trim()
        if (fOpen && fField == 3) {
            // pencarian Server: akun yang punya server cocok, lengkap dengan jumlah server yang cocok
            val any = (1..6).any { srvSel[it] }
            var servers = 0
            for (f in folders) {
                val cnt = IntArray(7)
                for (r in f.items) {
                    val st = Store.status(r)
                    if (any && !srvSel[st]) continue
                    if (q.isNotEmpty() && !r.server.contains(q)) continue
                    cnt[st]++
                }
                val n = cnt.sum()
                if (n > 0) {
                    matchCounts[f] = cnt
                    shownF.add(f)
                    servers += n
                }
            }
            sortFolders()
            val tb = SpannableStringBuilder()
            tb.append(servers.toString())
            tb.setSpan(StyleSpan(Typeface.BOLD), 0, tb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tb.setSpan(ForegroundColorSpan(fg), 0, tb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tb.append(" server · ")
            val st2 = tb.length
            tb.append(shownF.size.toString())
            tb.setSpan(StyleSpan(Typeface.BOLD), st2, tb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tb.setSpan(ForegroundColorSpan(fg), st2, tb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tb.append(" akun")
            totalTv?.text = tb
            adapterF?.notifyDataSetChanged()
            updateSummary()
            emptyF?.text = if (folders.isEmpty()) "" else "Tidak ada hasil."
            return
        }
        for (f in folders) {
            if (fOpen && q.isNotEmpty()) {
                val v = when (fField) {
                    0 -> f.name
                    1 -> f.id
                    else -> f.note
                }
                if (!v.contains(q, true)) continue
            }
            shownF.add(f)
        }
        sortFolders()
        adapterF?.notifyDataSetChanged()
        updateSummary()
        emptyF?.text = if (folders.isEmpty()) "" else "Tidak ada hasil."
    }

    // =====================================================
    // BUKTI KIRIM
    // =====================================================
    private fun refreshBukti() {
        adapterB?.notifyDataSetChanged()
    }

    private fun orderTitle(o: Order): String {
        val base = if (o.id.isBlank()) "ID belum ada" else o.id
        return if (o.nick.isBlank()) base else "$base · ${o.nick}"
    }

    private fun fmtNum(n: Long): String =
        java.text.NumberFormat.getIntegerInstance(Locale("id", "ID")).format(n)

    private fun fmtTime(t: Long): String =
        SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID")).format(Date(t))

    private fun orderInfo(o: Order): String =
        if (o.done) "${fmtNum(o.sent)} BBC · ${o.servers.size} server"
        else "Berjalan · ${fmtNum(o.sent)} dari ${fmtNum(o.target)} BBC"

    private fun photoUri(o: Order, kind: String): Uri =
        Uri.parse("content://com.catatan.cabang.bukti/foto/${BuktiStore.safe(o.key)}/$kind.jpg")

    private inner class BuktiAdapter : BaseAdapter() {
        override fun getCount() = orders.size
        override fun getItem(position: Int): Any = orders[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h: BuktiHolder
            val v: View
            if (convertView == null) {
                h = BuktiHolder()
                v = h.root
                v.tag = h
            } else {
                v = convertView
                h = convertView.tag as BuktiHolder
            }
            h.bind(position, orders[position])
            return v
        }
    }

    private inner class BuktiHolder {
        val ctx: Context = this@MainActivity
        val root = FrameLayout(ctx)
        private val card = LinearLayout(ctx)
        private val tvName = TextView(ctx)
        private val tvInfo = TextView(ctx)
        private val tvTime = TextView(ctx)
        private val chev = IconView(ctx, 5, sub, bg)
        private val chk = RadioView(ctx, sub, fg)
        private var pos = 0
        private var item: Order? = null
        private var lastSel = false
        private var lastChk = false

        fun currentItem(): Order? = item

        init {
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.background = shape(surface, 18, line)
            card.setPadding(dp(16), dp(14), dp(14), dp(14))

            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.VERTICAL
            tvName.textSize = 16f
            tvName.setTextColor(fg)
            tvName.typeface = Typeface.DEFAULT_BOLD
            tvName.isSingleLine = true
            tvName.ellipsize = TextUtils.TruncateAt.END
            tvInfo.textSize = 12f
            tvInfo.setTextColor(sub)
            tvTime.textSize = 12f
            tvTime.setTextColor(sub)
            mid.addView(tvName)
            mid.addView(tvInfo, lp(2))
            mid.addView(tvTime, lp(2))
            card.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(chev, LinearLayout.LayoutParams(dp(20), dp(20)))
            chk.visibility = View.GONE
            card.addView(chk, LinearLayout.LayoutParams(dp(24), dp(24)))

            root.setPadding(0, 0, 0, dp(10))
            root.addView(card, FrameLayout.LayoutParams(-1, -2))

            // ketuk: buka bukti (atau pilih saat mode pilih); geser ke KIRI: mulai mode pilih
            Gesture(root,
                { orders.getOrNull(pos)?.let { if (homeSel) toggleHomeB(it) else showOrder(it, true) } },
                { orders.getOrNull(pos)?.let { if (!homeSel) orderOptions(it) } },
                { orders.getOrNull(pos)?.let { enterHomeSelB(it) } },
                false).attach()
        }

        fun bind(p: Int, o: Order) {
            pos = p
            root.alpha = 1f
            root.scaleX = 1f
            root.scaleY = 1f
            root.translationY = 0f
            tvName.text = orderTitle(o)
            tvInfo.text = orderInfo(o)
            tvInfo.setTextColor(if (o.done) sub else cAmber)
            tvTime.text = fmtTime(o.time)

            val sameItem = item === o
            item = o
            val sel = homeSel
            val c = sel && selB.contains(o)
            chev.visibility = if (sel) View.GONE else View.VISIBLE
            chk.visibility = if (sel) View.VISIBLE else View.GONE
            if (sel) {
                chk.on = c
                card.background = shape(if (c) selSurface else surface, 18, if (c) sub else line)
                if (sameItem && (!lastSel || c != lastChk)) pop(chk)
            } else {
                card.background = shape(surface, 18, line)
            }
            lastSel = sel
            lastChk = c
        }
    }

    private fun orderOptions(o: Order) {
        val b = box()
        b.addView(label(orderTitle(o), 18f, fg, true))
        var d: Dialog? = null
        b.addView(pill("Hapus bukti", false) {
            d?.dismiss()
            sheet("Hapus bukti?", "Foto dan catatan pesanan ini akan dihapus dari HP ini.",
                listOf(Triple("Hapus", true) {
                    BuktiStore.delete(this, o)
                    orders.remove(o)
                    refreshBukti()
                }))
        }, lp(14))
        d = showBox(b)
    }

    private fun showOrder(o: Order, animated: Boolean) {
        curOrder = o
        current = null
        applyWindow()
        val col = column()

        val th = LinearLayout(this)
        th.orientation = LinearLayout.HORIZONTAL
        th.gravity = Gravity.CENTER_VERTICAL
        th.addView(iconBtn(0, false) { showFolders(-1) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        val tl = LinearLayout(this)
        tl.orientation = LinearLayout.VERTICAL
        val tn = label(orderTitle(o), 16f, fg, true)
        tn.isSingleLine = true
        tn.ellipsize = TextUtils.TruncateAt.END
        val ti = label(orderInfo(o) + " · " + fmtTime(o.time), 12f, sub, false)
        ti.isSingleLine = true
        ti.ellipsize = TextUtils.TruncateAt.END
        tl.addView(tn)
        tl.addView(ti)
        val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tp.leftMargin = dp(12)
        th.addView(tl, tp)
        col.addView(th, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        val ar = LinearLayout(this)
        ar.orientation = LinearLayout.HORIZONTAL
        ar.addView(iconPill(8, "Unduh", true) { downloadOrder(o) })
        val pk = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pk.leftMargin = dp(8)
        ar.addView(iconPill(9, "Kirim", false) { shareOrder(o) }, pk)
        col.addView(ar, lp(12))

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 0, 0, dp(24))
        body.addView(photoCard("Before", o, "before"), lp(0))
        body.addView(photoCard("After", o, "after"), lp(10))
        body.addView(serverCard(o), lp(10))
        sv.addView(body)
        col.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).also { it.topMargin = dp(12) })

        pushDetail(col, animated)
    }

    // Tombol berikon + tulisan pendek (Unduh, Kirim)
    private fun iconPill(kind: Int, text: String, filled: Boolean, size: Float = 13f, onClick: () -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER
        box.background = if (filled) shape(fg, 20) else shape(Color.TRANSPARENT, 20, line)
        box.setPadding(dp(14), dp(10), dp(16), dp(10))
        val c = if (filled) bg else fg
        box.addView(IconView(this, kind, c, bg), LinearLayout.LayoutParams(dp(18), dp(18)))
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(c)
        val tp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tp.leftMargin = dp(8)
        box.addView(t, tp)
        box.isClickable = true
        box.setOnClickListener { onClick() }
        box.contentDescription = text
        pressFx(box)
        return box
    }

    private fun photoCard(title: String, o: Order, kind: String): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = shape(surface, 16, line)
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        card.addView(label(title, 13f, sub, true))
        val f = BuktiStore.photoFile(this, o.key, kind)
        if (f.exists()) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.adjustViewBounds = true
            iv.minimumHeight = dp(140)
            iv.background = shape(surface2, 12)
            iv.setOnClickListener { viewPhoto(o, kind) }
            card.addView(iv, lp(8))
            Thread {
                val bmp = BuktiStore.decode(f.path, 1200)
                runOnUiThread { if (bmp != null) iv.setImageBitmap(bmp) }
            }.start()
        } else {
            card.addView(label("Foto belum ada", 13f, sub, false), lp(8))
        }
        return card
    }

    private fun serverCard(o: Order): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = shape(surface, 16, line)
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        card.addView(label("Server dipakai (${o.servers.size})", 13f, sub, true))
        if (o.servers.isEmpty()) {
            card.addView(label("Belum tercatat", 13f, sub, false), lp(8))
        } else {
            for (s in o.servers) card.addView(label(s, 14f, fg, false), lp(6))
        }
        return card
    }

    private fun viewPhoto(o: Order, kind: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(photoUri(o, kind), "image/jpeg")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(i)
        } catch (e: Exception) {
            toast("Tidak ada aplikasi untuk membuka foto")
        }
    }

    private fun shareOrder(o: Order) {
        val uris = ArrayList<Uri>()
        for (k in arrayOf("before", "after")) {
            if (BuktiStore.photoFile(this, o.key, k).exists()) uris.add(photoUri(o, k))
        }
        if (uris.isEmpty()) {
            toast("Belum ada foto")
            return
        }
        try {
            val i = Intent(Intent.ACTION_SEND_MULTIPLE)
            i.type = "image/jpeg"
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val cd = ClipData.newUri(contentResolver, "bukti", uris[0])
            for (k in 1 until uris.size) cd.addItem(ClipData.Item(uris[k]))
            i.clipData = cd
            startActivity(Intent.createChooser(i, "Kirim bukti"))
        } catch (e: Exception) {
            toast("Gagal membuka menu bagikan")
        }
    }

    private fun downloadOrder(o: Order) {
        if (Build.VERSION.SDK_INT < 29) {
            toast("Butuh Android 10 ke atas")
            return
        }
        var n = 0
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(o.time))
        for (k in arrayOf("before", "after")) {
            val f = BuktiStore.photoFile(this, o.key, k)
            if (!f.exists()) continue
            try {
                val cv = ContentValues()
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, "${o.id}_${stamp}_$k.jpg")
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CatatanCabang")
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: continue
                contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                n++
            } catch (e: Exception) {
            }
        }
        toast(if (n > 0) "$n foto disimpan di Pictures/CatatanCabang" else "Gagal menyimpan foto")
    }

    // =====================================================
    // PILIH BANYAK (daftar akun: geser kanan; daftar bukti kirim: geser kiri)
    // =====================================================
    private fun pop(v: View) {
        v.animate().cancel()
        v.scaleX = 0.5f
        v.scaleY = 0.5f
        v.animate().scaleX(1f).scaleY(1f).setDuration(300)
            .setInterpolator(OvershootInterpolator(3f)).start()
    }

    private fun homeCount(): Int = if (topTab == 0) selF.size else selB.size

    private fun notifyHome() {
        adapterF?.notifyDataSetChanged()
        adapterB?.notifyDataSetChanged()
    }

    private fun enterHomeSelF(f: Folder) {
        if (homeSel) return
        selF.clear()
        selB.clear()
        selF.add(f)
        startHomeSel()
    }

    private fun enterHomeSelB(o: Order) {
        if (homeSel) return
        selF.clear()
        selB.clear()
        selB.add(o)
        startHomeSel()
    }

    private fun startHomeSel() {
        homeSel = true
        homeEditBtn?.visibility = if (topTab == 0) View.VISIBLE else View.GONE
        buildHomeTop()
        setShown(homeFab, false)
        setShown(homeBar, true)
        notifyHome()
    }

    private fun exitHomeSel() {
        if (!homeSel) return
        homeSel = false
        selF.clear()
        selB.clear()
        buildHomeTop()
        setShown(homeFab, true)
        setShown(homeBar, false)
        notifyHome()
    }

    private fun toggleHomeF(f: Folder) {
        if (!selF.remove(f)) selF.add(f)
        afterToggle()
    }

    private fun toggleHomeB(o: Order) {
        if (!selB.remove(o)) selB.add(o)
        afterToggle()
    }

    private fun afterToggle() {
        if (homeCount() == 0) {
            exitHomeSel()
            return
        }
        selTitle?.text = homeCount().toString() + " dipilih"
        notifyHome()
    }

    private fun toggleAllHome() {
        if (topTab == 0) {
            var all = shownF.isNotEmpty()
            for (f in shownF) if (!selF.contains(f)) all = false
            if (all) selF.clear() else selF.addAll(shownF)
        } else {
            var all = orders.isNotEmpty()
            for (o in orders) if (!selB.contains(o)) all = false
            if (all) selB.clear() else selB.addAll(orders)
        }
        afterToggle()
    }

    // Baris atas halaman utama: judul dan tombol, atau "N dipilih" saat mode pilih
    private fun buildHomeTop() {
        val top = homeTop ?: return
        top.removeAllViews()
        if (homeSel) {
            top.addView(iconBtn(3, false) { exitHomeSel() }, LinearLayout.LayoutParams(dp(40), dp(40)))
            val t = label(homeCount().toString() + " dipilih", 18f, fg, true)
            selTitle = t
            val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tp.leftMargin = dp(12)
            top.addView(t, tp)
            if (topTab == 0) {
                val pu = pill("Terpakai", false) { selectUsedHome() }
                pu.textSize = 13f
                top.addView(pu)
            }
            val pa = pill("Pilih semua", false) { toggleAllHome() }
            pa.textSize = 13f
            val pal = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            pal.leftMargin = dp(6)
            top.addView(pa, pal)
        } else {
            selTitle = null
            top.addView(label("Catatan Cabang", 22f, fg, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val sb = iconBtn(1, fOpen) { toggleFolderSearch() }
            searchBtn = sb
            sb.alpha = (1f - pos).coerceIn(0f, 1f)
            sb.isClickable = pos < 0.5f
            top.addView(sb, iconLp())
            top.addView(iconBtn(2, false) { menuFolders() }, iconLp())
            styleSearchBtn()
        }
        top.animate().cancel()
        top.alpha = 0f
        top.translationY = -dp(6).toFloat()
        top.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(iosCurve()).start()
    }

    // Akun terpakai: punya server, dan semua servernya bertanda "Sudah terpakai"
    private fun isUsedFolder(f: Folder): Boolean = f.items.isNotEmpty() && f.items.all { Store.rank(it.note) == 3 }

    private fun selectUsedHome() {
        if (topTab != 0) return
        val used = shownF.filter { isUsedFolder(it) }
        if (used.isEmpty()) {
            toast("Belum ada akun yang semua servernya terpakai")
            return
        }
        selF.clear()
        selF.addAll(used)
        afterToggle()
    }

    // Salin yang dipilih: akun = "email<tab>ID", bukti = "ID<tab>nick<tab>jumlah BBC<tab>tanggal" (satu baris tiap item)
    private fun copySelectedHome() {
        val sb = StringBuilder()
        var n = 0
        if (topTab == 0) {
            for (f in selF) {
                if (n > 0) sb.append('\n')
                sb.append(if (f.name.isBlank()) "(tanpa email)" else f.name).append('\t').append(f.id)
                n++
            }
            copyText("$n akun", sb.toString())
        } else {
            for (o in selB) {
                if (n > 0) sb.append('\n')
                sb.append(o.id).append('\t').append(o.nick).append('\t').append(o.sent).append('\t').append(fmtTime(o.time))
                n++
            }
            copyText("$n bukti", sb.toString())
        }
    }

    private fun editSelectedHome() {
        if (topTab == 0 && selF.isNotEmpty()) editAccounts(ArrayList(selF))
    }

    // Edit dari mode pilih. Segmen Email, ID, Keterangan lalu satu kolom isian. Beberapa akun terpilih:
    // Email dan ID redup (tidak bisa dipilih), hanya Keterangan yang diisi sama untuk semua
    private fun editAccounts(list: List<Folder>) {
        if (list.isEmpty()) return
        val multi = list.size > 1
        val b = box()
        b.addView(label(if (multi) "Edit ${list.size} akun" else "Edit akun", 18f, fg, true))
        val cur = if (multi) arrayOf("", "", "") else arrayOf(list[0].name, list[0].id, list[0].note)
        val names = arrayOf("Email", "ID", "Keterangan")
        var sel = 2          // segmen terpilih
        var owner = 2        // segmen pemilik teks yang sedang tampil di kolom isian
        val et = input(names[2], cur[2], InputType.TYPE_CLASS_TEXT)
        et.isSingleLine = true

        // Segmen: satu pil putih meluncur di belakang tiga tulisan
        val segFrame = FrameLayout(this)
        segFrame.background = shape(surface, 24, line)
        segFrame.setPadding(dp(3), dp(3), dp(3), dp(3))
        val thumb = View(this)
        thumb.background = shape(fg, 20)
        segFrame.addView(thumb, FrameLayout.LayoutParams(0, 0))
        val segRow = LinearLayout(this)
        segRow.orientation = LinearLayout.HORIZONTAL
        segFrame.addView(segRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val tvs = ArrayList<TextView>()
        var anim = 0
        segFrame.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            // Ukuran pil putih diatur eksplisit mengikuti deretan tulisan (View polos dengan match_parent
            // di dalam wadah wrap_content akan melebar sepenuh dialog)
            val w = (r - l - dp(6)) / 3
            val h = segRow.height
            val lpT = thumb.layoutParams
            if (w > 0 && h > 0 && (lpT.width != w || lpT.height != h)) {
                lpT.width = w
                lpT.height = h
                thumb.layoutParams = lpT
            }
            if (anim == 0) thumb.translationX = (sel * w).toFloat()
        }
        for (k in 0..2) {
            val t = TextView(this)
            t.text = names[k]
            t.textSize = 13f
            t.typeface = Typeface.DEFAULT_BOLD
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10), 0, dp(10))
            t.setTextColor(if (k == sel) bg else sub)
            t.alpha = if (multi && k < 2) 0.35f else 1f
            tvs.add(t)
            segRow.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val ease = iosCurve()
        val argb = ArgbEvaluator()
        fun pick(k: Int, after: (() -> Unit)? = null) {
            if (multi && k < 2) return
            if (k == sel) {
                after?.invoke()
                return
            }
            val old = sel
            val dir = if (k > old) 1 else -1
            // teks yang sedang tampil disimpan dulu ke pemiliknya
            cur[owner] = et.text.toString()
            sel = k
            val my = ++anim
            // pil putih meluncur, warna tulisan ikut berubah pelan
            val segW = thumb.layoutParams.width.toFloat()
            thumb.animate().cancel()
            thumb.animate().translationX(k * segW).setDuration(260).setInterpolator(ease).start()
            val ca = ValueAnimator.ofFloat(0f, 1f)
            ca.duration = 260
            ca.interpolator = ease
            ca.addUpdateListener { v ->
                val f = v.animatedValue as Float
                tvs[old].setTextColor(argb.evaluate(f, bg, sub) as Int)
                tvs[k].setTextColor(argb.evaluate(f, sub, bg) as Int)
            }
            ca.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (anim == my) anim = 0
                }
            })
            ca.start()
            // isi kolom: teks lama memudar sambil bergeser searah pil, teks baru masuk dari arah yang sama
            val shift = dp(12).toFloat() * dir
            et.animate().cancel()
            et.animate().alpha(0f).translationX(shift).setDuration(110).withEndAction {
                owner = k
                et.hint = names[k]
                et.inputType = when (k) {
                    0 -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    1 -> InputType.TYPE_CLASS_NUMBER
                    else -> InputType.TYPE_CLASS_TEXT
                }
                et.isSingleLine = true
                et.setText(cur[k])
                et.setSelection(et.text.length)
                et.error = null
                et.translationX = -shift
                et.animate().alpha(1f).translationX(0f).setDuration(120).setInterpolator(ease).withEndAction {
                    after?.invoke()
                }.start()
            }.start()
        }
        for (k in 0..2) tvs[k].setOnClickListener { pick(k) }
        b.addView(segFrame, lp(12))
        b.addView(et, lp(10))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        var d: Dialog? = null
        row.addView(pill("Batal", false) { d?.dismiss() })
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sp.leftMargin = dp(8)
        row.addView(pill("Simpan", true) {
            cur[owner] = et.text.toString()
            val e = cur[0].trim()
            val i = cur[1].trim()
            val n = cur[2].trim()
            if (!multi) {
                val f = list[0]
                if (e.isEmpty() && i.isEmpty()) {
                    pick(0) { et.error = "Isi email atau ID" }
                    return@pill
                }
                if (i.isNotEmpty() && i != f.id && folders.any { it !== f && it.id == i }) {
                    pick(1) { et.error = "ID sudah ada" }
                    return@pill
                }
                f.name = e
                f.id = i
                f.note = n
            } else {
                for (f in list) f.note = n
            }
            save()
            refreshFolders()
            d?.dismiss()
            exitHomeSel()
        }, sp)
        b.addView(row, lp(16))
        d = showBox(b)
    }

    private fun deleteSelectedHome() {
        if (topTab == 0) confirmDeleteFolders(ArrayList(selF), false) else confirmDeleteOrders(ArrayList(selB), false)
    }

    private fun confirmDeleteFolders(list: List<Folder>, all: Boolean) {
        if (list.isEmpty()) return
        val sv = list.sumOf { it.items.size }
        val msg = (if (all) "SEMUA " else "") + list.size + " akun dan " + sv + " server akan dihapus dari HP ini" +
            (if (SyncEngine.configured()) " dan dari HP lain (sinkron menyala)" else "") +
            ". Buat Backup dulu kalau perlu."
        sheet(if (all) "Hapus semua akun?" else "Hapus " + list.size + " akun?", msg, listOf(
            Triple("Backup dulu", false) { doBackup(true) },
            Triple("Hapus", true) { performDeleteFolders(list) }
        ))
    }

    private fun confirmDeleteOrders(list: List<Order>, all: Boolean) {
        if (list.isEmpty()) return
        val msg = (if (all) "SEMUA " else "") + list.size + " bukti kirim beserta fotonya akan dihapus dari HP ini" +
            (if (SyncEngine.configured()) " dan dari HP lain (sinkron menyala)" else "") + "."
        sheet(if (all) "Hapus semua bukti?" else "Hapus " + list.size + " bukti?", msg, listOf(
            Triple("Hapus", true) { performDeleteOrders(list) }
        ))
    }

    private fun performDeleteFolders(list: List<Folder>) {
        val doomed = HashSet<Any>(list)
        animateRemoval(lvFolders, doomed, { v -> (v.tag as? FolderHolder)?.currentItem() }) {
            folders.removeAll(list.toSet())
            save()
            exitHomeSel()
            refreshFolders()
        }
    }

    private fun performDeleteOrders(list: List<Order>) {
        val doomed = HashSet<Any>(list)
        animateRemoval(lvBukti, doomed, { v -> (v.tag as? BuktiHolder)?.currentItem() }) {
            for (o in list) {
                BuktiStore.delete(this, o)
                orders.remove(o)
            }
            exitHomeSel()
            refreshBukti()
        }
    }

    // Baris yang dihapus memudar dan mengecil, lalu baris di bawahnya meluncur menutup celah
    private fun animateRemoval(lv: ListView?, doomed: Set<Any>, itemOf: (View) -> Any?, apply: () -> Unit) {
        if (lv == null || lv.childCount == 0) {
            apply()
            return
        }
        var any = false
        val tops = HashMap<Any, Int>()
        for (i in 0 until lv.childCount) {
            val v = lv.getChildAt(i)
            val k = itemOf(v) ?: continue
            if (doomed.contains(k)) {
                any = true
                v.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(200).setInterpolator(iosCurve()).start()
            } else {
                tops[k] = v.top
            }
        }
        ui.postDelayed({
            apply()
            lv.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val o = lv.viewTreeObserver
                    if (o.isAlive) o.removeOnPreDrawListener(this)
                    for (i in 0 until lv.childCount) {
                        val v = lv.getChildAt(i)
                        val k = itemOf(v) ?: continue
                        val old = tops[k] ?: continue
                        val d = old - v.top
                        if (d != 0) {
                            v.translationY = d.toFloat()
                            v.animate().translationY(0f).setDuration(300).setInterpolator(iosCurve()).start()
                        }
                    }
                    return true
                }
            })
        }, if (any) 200L else 0L)
    }

    private fun copyText(what: String, text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Catatan Cabang", text))
        toast("$what disalin")
    }

    private inner class FolderAdapter : BaseAdapter() {
        override fun getCount() = shownF.size
        override fun getItem(position: Int): Any = shownF[position]
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
            h.bind(position, shownF[position])
            return v
        }
    }

    private inner class FolderHolder {
        val ctx: Context = this@MainActivity
        val root = FrameLayout(ctx)
        private val card = LinearLayout(ctx)
        private val tvName = TextView(ctx)
        private val tvId = TextView(ctx)
        private val tvSub = TextView(ctx)
        private val noteSv = HorizontalScrollView(ctx)
        private val tvNote = TextView(ctx)
        private val chev = IconView(ctx, 5, sub, bg)
        private val chk = RadioView(ctx, sub, fg)
        private var pos = 0
        private var item: Folder? = null
        private var lastSel = false
        private var lastChk = false

        fun currentItem(): Folder? = item

        init {
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            card.background = shape(surface, 18, line)
            card.setPadding(dp(16), dp(14), dp(14), dp(14))

            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.VERTICAL
            tvName.textSize = 16f
            tvName.setTextColor(fg)
            tvName.typeface = Typeface.DEFAULT_BOLD
            tvName.isSingleLine = true
            tvName.ellipsize = TextUtils.TruncateAt.END
            tvId.textSize = 12f
            tvId.setTextColor(sub)
            tvSub.textSize = 12f
            tvSub.setTextColor(sub)
            tvNote.textSize = 12f
            tvNote.setTextColor(fg)
            tvNote.alpha = 0.85f
            tvNote.isSingleLine = true
            tvNote.setHorizontallyScrolling(true)
            noteSv.isHorizontalScrollBarEnabled = false
            noteSv.isHorizontalFadingEdgeEnabled = true
            noteSv.setFadingEdgeLength(dp(24))
            noteSv.overScrollMode = View.OVER_SCROLL_NEVER
            noteSv.addView(tvNote, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // Keterangan yang panjang bisa digeser; ketukan di baris ini tetap membuka folder
            val noteTap = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    shownF.getOrNull(pos)?.let { if (homeSel) toggleHomeF(it) else openFolder(it) }
                    return true
                }
            })
            noteSv.setOnTouchListener { _, e ->
                noteTap.onTouchEvent(e)
                false
            }
            mid.addView(tvName)
            mid.addView(tvId, lp(2))
            mid.addView(noteSv, lp(2))
            mid.addView(tvSub, lp(2))
            card.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            card.addView(chev, LinearLayout.LayoutParams(dp(20), dp(20)))
            chk.visibility = View.GONE
            card.addView(chk, LinearLayout.LayoutParams(dp(24), dp(24)))

            root.setPadding(0, 0, 0, dp(10))
            root.addView(card, FrameLayout.LayoutParams(-1, -2))

            // ketuk: buka folder (atau pilih saat mode pilih); geser ke KANAN: mulai mode pilih
            Gesture(root,
                { shownF.getOrNull(pos)?.let { if (homeSel) toggleHomeF(it) else openFolder(it) } },
                { shownF.getOrNull(pos)?.let { if (!homeSel) folderOptions(it) } },
                { shownF.getOrNull(pos)?.let { enterHomeSelF(it) } },
                true).attach()
        }

        fun bind(p: Int, f: Folder) {
            pos = p
            root.alpha = 1f
            root.scaleX = 1f
            root.scaleY = 1f
            root.translationY = 0f
            if (f.name.isBlank()) {
                tvName.text = "Email belum diisi"
                tvName.setTextColor(cAmber)
            } else {
                tvName.text = f.name
                tvName.setTextColor(fg)
            }
            tvId.text = if (f.id.isBlank()) "ID belum ada" else "ID ${f.id}"
            if (f.note.isBlank()) {
                noteSv.visibility = View.GONE
            } else {
                noteSv.visibility = View.VISIBLE
                tvNote.text = f.note
                noteSv.scrollTo(0, 0)
            }
            val sb = SpannableStringBuilder()
            val mc = if (fOpen && fField == 3) matchCounts[f] else null
            if (mc != null) {
                var first = true
                for (st in stOrder) {
                    if (mc[st] > 0) {
                        if (!first) sb.append("   ")
                        first = false
                        addDot(sb, stColor(st), mc[st].toString() + " " + stName(st))
                    }
                }
            } else {
                val cnt = Store.counts(f.items)
                sb.append("${f.items.size} server")
                for (st in stOrder) {
                    if (cnt[st] > 0) {
                        sb.append("   ")
                        addDot(sb, stColor(st), cnt[st].toString())
                    }
                }
            }
            tvSub.text = sb

            val sameItem = item === f
            item = f
            val sel = homeSel
            val c = sel && selF.contains(f)
            chev.visibility = if (sel) View.GONE else View.VISIBLE
            chk.visibility = if (sel) View.VISIBLE else View.GONE
            if (sel) {
                chk.on = c
                card.background = shape(if (c) selSurface else surface, 18, if (c) sub else line)
                if (sameItem && (!lastSel || c != lastChk)) pop(chk)
            } else {
                card.background = shape(surface, 18, line)
            }
            lastSel = sel
            lastChk = c
        }
    }

    private fun openFolder(f: Folder) {
        searchOpen = false
        query = ""
        applied = false
        appliedQ = ""
        searchField = 0
        statFilter = 0
        showData(f, 1)
    }

    private fun folderOptions(f: Folder) {
        val b = box()
        b.addView(label(if (f.name.isBlank()) "ID ${f.id}" else f.name, 18f, fg, true))
        var d: Dialog? = null
        b.addView(pill("Ubah email", false) {
            d?.dismiss()
            askText("Ubah email", f.name, "Simpan", "Email") { name ->
                f.name = name
                save()
                refreshFolders()
            }
        }, lp(14))
        b.addView(pill("Ubah ID akun", false) {
            d?.dismiss()
            askText("Ubah ID akun", f.id, "Simpan", "ID akun") { v ->
                f.id = v.trim()
                save()
                refreshFolders()
            }
        }, lp(8))
        b.addView(pill("Ubah keterangan", false) {
            d?.dismiss()
            askText("Ubah keterangan", f.note, "Simpan", "Keterangan", true) { v ->
                f.note = v.trim()
                save()
                refreshFolders()
            }
        }, lp(8))
        b.addView(pill("Hapus folder", false) {
            d?.dismiss()
            sheet("Hapus folder?", "\"${if (f.name.isBlank()) f.id else f.name}\" dan ${f.items.size} server di dalamnya akan dihapus.",
                listOf(Triple("Hapus", true) {
                    folders.remove(f)
                    save()
                    refreshFolders()
                }))
        }, lp(8))
        d = showBox(b)
    }

    private fun askAccount() {
        val b = box()
        b.addView(label("Akun baru", 18f, fg, true))
        val fe = input("Email", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val fi = input("ID akun", "", InputType.TYPE_CLASS_NUMBER)
        val fn = input("Keterangan", "", InputType.TYPE_CLASS_TEXT)
        fe.isSingleLine = true
        fi.isSingleLine = true
        fn.isSingleLine = true
        b.addView(fe, lp(12))
        b.addView(fi, lp(8))
        b.addView(fn, lp(8))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        var d: Dialog? = null
        row.addView(pill("Batal", false) { d?.dismiss() })
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.leftMargin = dp(8)
        row.addView(pill("Simpan", true) {
            val e = fe.text.toString().trim()
            val i = fi.text.toString().trim()
            if (e.isEmpty() && i.isEmpty()) {
                fe.error = "Isi email atau ID"
                return@pill
            }
            if (i.isNotEmpty() && folders.any { it.id == i }) {
                fi.error = "ID sudah ada"
                return@pill
            }
            val nf = Folder(e, ArrayList(), i)
            nf.note = fn.text.toString().trim()
            folders.add(nf)
            save()
            refreshFolders()
            d?.dismiss()
        }, p)
        b.addView(row, lp(16))
        d = showBox(b)
    }

    private fun askText(title: String, initial: String, ok: String, hint: String = "Judul folder", allowEmpty: Boolean = false, cb: (String) -> Unit) {
        val b = box()
        b.addView(label(title, 18f, fg, true))
        val et = input(hint, initial, InputType.TYPE_CLASS_TEXT)
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
            if (s.isEmpty() && !allowEmpty) {
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
        val l = ArrayList<Pair<String, () -> Unit>>()
        if (topTab == 0) {
            l.add(Pair("Hapus semua akun") { confirmDeleteFolders(ArrayList(folders), true) })
        } else {
            l.add(Pair("Hapus semua bukti") { confirmDeleteOrders(ArrayList(orders), true) })
        }
        l.add(Pair("Backup semua") { doBackup(true) })
        l.add(Pair("Import") { doImport(true) })
        l.add(Pair("Sinkron antar HP") { showSync() })
        l.add(Pair(if (dark) "Mode Terang" else "Mode Gelap") { toggleTheme() })
        popupMenu(l)
    }

    // ---------- sinkron antar HP ----------
    private fun showSync() {
        val b = box()
        b.addView(label("Sinkron antar HP", 18f, fg, true))
        b.addView(label(SyncEngine.statusText(), 12f, sub, false), lp(4))
        val fUrl = input("Alamat database (https://...)", SyncEngine.url, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val fKey = input("Kunci sinkron", SyncEngine.key, InputType.TYPE_CLASS_TEXT)
        fUrl.isSingleLine = true
        fKey.isSingleLine = true
        b.addView(fUrl, lp(12))
        b.addView(fKey, lp(8))

        val r1 = LinearLayout(this)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(pill("Buat kunci", false) { fKey.setText(SyncEngine.newKey()) })
        val pc = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pc.leftMargin = dp(8)
        r1.addView(pill("Salin kunci", false) { copyText("Kunci", fKey.text.toString().trim()) }, pc)
        b.addView(r1, lp(10))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        var d: Dialog? = null
        if (SyncEngine.on) {
            row.addView(pill("Matikan", false) {
                SyncEngine.on = false
                d?.dismiss()
                toast("Sinkron dimatikan")
            })
        }
        val pb = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pb.leftMargin = dp(8)
        row.addView(pill("Batal", false) { d?.dismiss() }, pb)
        val ps = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ps.leftMargin = dp(8)
        row.addView(pill("Simpan", true) {
            val u = fUrl.text.toString().trim().trimEnd('/')
            val k = fKey.text.toString().trim()
            if (!u.startsWith("https://")) {
                fUrl.error = "Harus diawali https://"
                return@pill
            }
            if (k.length < 16 || !k.all { it.isLetterOrDigit() }) {
                fKey.error = "Minimal 16 huruf atau angka"
                return@pill
            }
            SyncEngine.url = u
            SyncEngine.key = k
            SyncEngine.on = true
            d?.dismiss()
            toast("Menyinkronkan...")
            SyncEngine.syncNow(true) { msg -> toast(msg) }
        }, ps)
        b.addView(row, lp(16))

        val sv = ScrollView(this)
        sv.addView(b)
        d = showBox(sv)
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
        th.minimumHeight = dp(44)
        col.addView(th, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        topHolder = th

        val sh = LinearLayout(this)
        sh.orientation = LinearLayout.VERTICAL
        col.addView(sh, lp(6))
        searchHolder = sh

        // penyaring status: hanya status yang ada isinya, dibuat ulang di refreshData
        val chipSv = HorizontalScrollView(this)
        chipSv.isHorizontalScrollBarEnabled = false
        chipSv.overScrollMode = View.OVER_SCROLL_NEVER
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipSv.addView(chipRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(chipSv, lp(8))
        chipHolder = chipRow

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

        val fb = fab("+") { openForm(null) }
        fb.textSize = 26f
        fb.minWidth = dp(58)
        fb.minHeight = dp(58)
        fb.setPadding(0, 0, 0, dp(2))
        fb.background = shape(fg, 29)
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fp.gravity = Gravity.BOTTOM or Gravity.END
        fp.bottomMargin = dp(18)
        frame.addView(fb, fp)

        // pil hitungan melayang di kiri bawah (total server dan jumlah tiap status yang ada isinya)
        val cp = TextView(this)
        cp.textSize = 13f
        cp.setTextColor(fg)
        cp.background = shape(surface, 22, line)
        cp.setPadding(dp(14), dp(10), dp(14), dp(10))
        val cpp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cpp.gravity = Gravity.BOTTOM or Gravity.START
        cpp.bottomMargin = dp(26)
        frame.addView(cp, cpp)
        countPill = cp

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
        pushDetail(col, dir != 0)
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
            val tl = LinearLayout(this)
            tl.orientation = LinearLayout.VERTICAL
            val tn = label(if (f.name.isBlank()) "Email belum diisi" else f.name, 16f, if (f.name.isBlank()) cAmber else fg, true)
            tn.isSingleLine = true
            tn.ellipsize = TextUtils.TruncateAt.END
            tn.setOnClickListener { copyText("Email", f.name) }
            val ti = label(if (f.id.isBlank()) "ID belum ada" else "ID ${f.id}", 12f, sub, false)
            ti.setOnClickListener { copyText("ID", f.id) }
            tl.addView(tn)
            tl.addView(ti)
            if (f.note.isNotBlank()) {
                val nsv = HorizontalScrollView(this)
                nsv.isHorizontalScrollBarEnabled = false
                nsv.isHorizontalFadingEdgeEnabled = true
                nsv.setFadingEdgeLength(dp(24))
                nsv.overScrollMode = View.OVER_SCROLL_NEVER
                val tnote = label(f.note, 12f, fg, false)
                tnote.alpha = 0.85f
                tnote.isSingleLine = true
                tnote.setHorizontallyScrolling(true)
                nsv.addView(tnote, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                tl.addView(nsv)
            }
            val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tp.leftMargin = dp(12)
            th.addView(tl, tp)
            th.addView(iconBtn(1, searchOpen) { toggleSearch() }, iconLp())
            th.addView(iconBtn(2, false) { menuData() }, iconLp())
        }
    }

    private fun updateBars() {
        val sel = selected.isNotEmpty()
        if (sel == barOn) return
        barOn = sel
        setShown(fabData, !sel)
        setShown(countPill, !sel)
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
    private fun searchType() = if (searchField == 0) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT

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
        et.addTextChangedListener(watcher {
            query = it
            // teks diubah setelah dikonfirmasi: kembali tampil semua sampai dikonfirmasi lagi
            if (applied) {
                applied = false
                appliedQ = ""
                refreshData()
            }
        })
        et.imeOptions = EditorInfo.IME_ACTION_SEARCH
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applySearch()
                true
            } else {
                false
            }
        }
        searchEt = et
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
                query = ""
                applied = false
                appliedQ = ""
                refreshData()
            }
            list.add(c)
            val cp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cp.rightMargin = dp(8)
            chips.addView(c, cp)
        }
        hs.addView(chips)
        sh.addView(hs, lp(10))
    }

    // konfirmasi pencarian (tombol centang / tombol cari di keyboard)
    private fun applySearch() {
        applied = true
        appliedQ = query
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        refreshData()
    }

    private fun toggleSearch() {
        searchOpen = !searchOpen
        applied = false
        appliedQ = ""
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
        val q = appliedQ.trim()
        val active = searchOpen && applied
        shown.clear()
        for (r in f.items) {
            if (statFilter != 0 && Store.status(r) != statFilter) continue
            if (active) {
                val v = when (searchField) {
                    0 -> r.server
                    1 -> r.level
                    else -> r.note
                }
                if (q.isEmpty()) {
                    // dikonfirmasi tanpa mengetik: tampilkan yang kolomnya kosong
                    if (v.isNotBlank()) continue
                } else {
                    val ql = q.lowercase()
                    val vl = v.trim().lowercase()
                    val zero = ql == "0" || ql == "nol" || ql == "kosong"
                    if (zero) {
                        // 0 / nol / kosong: yang kosong ikut keluar, sama dengan yang tertulis 0/nol/kosong
                        if (!(vl.isEmpty() || vl == "0" || vl == "nol" || vl == "kosong")) continue
                    } else if (!v.contains(q, true)) {
                        continue
                    }
                }
            }
            shown.add(r)
        }
        shown.sortWith { a, b -> natCmp(a.server, b.server) }
        adapterD?.notifyDataSetChanged()
        emptyD?.text = if (f.items.isEmpty()) "" else "Tidak ada hasil."
        val cnt = Store.counts(f.items)
        if (statFilter != 0 && cnt[statFilter] == 0) statFilter = 0
        rebuildStatusChips(cnt)
        val pb = SpannableStringBuilder()
        pb.append(f.items.size.toString())
        pb.setSpan(StyleSpan(Typeface.BOLD), 0, pb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for (st in stOrder) {
            if (cnt[st] > 0) {
                pb.append("  ")
                addDot(pb, stColor(st), cnt[st].toString())
            }
        }
        countPill?.text = pb
    }

    // Penyaring status di dalam folder: Semua, lalu satu tombol berwarna untuk tiap status yang ada isinya
    private fun rebuildStatusChips(cnt: IntArray) {
        val row = chipHolder ?: return
        row.removeAllViews()
        row.addView(statusChip("Semua", fg, statFilter == 0) {
            statFilter = 0
            refreshData()
        })
        for (st in stOrder) {
            if (cnt[st] == 0) continue
            row.addView(statusChip("● " + stName(st), stColor(st), statFilter == st) {
                statFilter = st
                refreshData()
            })
        }
    }

    private fun statusChip(text: String, color: Int, selected: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        if (selected) {
            t.setTextColor(color)
            t.background = shape((color and 0x00FFFFFF) or 0x33000000, 20, color)
        } else {
            t.setTextColor(color)
            t.background = shape(Color.TRANSPARENT, 20, line)
        }
        t.setOnClickListener { onClick() }
        pressFx(t)
        val lpp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpp.rightMargin = dp(8)
        t.layoutParams = lpp
        return t
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
        private val tvTag = TextView(ctx)
        private val tvLevel = TextView(ctx)
        private var pos = 0

        init {
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
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

            tvTag.textSize = 12f
            tvTag.setPadding(dp(9), dp(3), dp(9), dp(3))
            val lpt = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpt.leftMargin = dp(8)
            card.addView(tvTag, lpt)

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
            tvEmail.text = "Server ${r.server}"
            tvServer.visibility = View.GONE
            tvNote.text = r.note
            val stt = Store.status(r)
            tvNote.setTextColor(stColor(stt))
            tvTag.text = stName(stt)
            tvTag.setTextColor(stColor(stt))
            tvTag.background = shape(Color.TRANSPARENT, 12, stColor(stt))
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
        content.scaleX = 0.9f
        content.scaleY = 0.9f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)
            .setInterpolator(iosCurve()).start()
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
        b.addView(label(if (rec == null) "Tambah Server" else "Edit Server", 20f, fg, true))
        val t = InputType.TYPE_CLASS_TEXT
        val fServer = input("Server", rec?.server ?: "", InputType.TYPE_CLASS_NUMBER)
        val fLevel = input("Level", rec?.level ?: "", t)
        val fNote = input("Keterangan", rec?.note ?: "", t or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        fNote.minLines = 2
        fNote.gravity = Gravity.TOP
        b.addView(fServer, lp(14))
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
            val server = fServer.text.toString().trim()
            if (server.isEmpty()) {
                fServer.error = "Wajib diisi"
                return@pill
            }
            val level = fLevel.text.toString().trim()
            val note = fNote.text.toString().trim()
            if (rec == null) {
                f.items.add(Rec("", server, level, note))
            } else {
                rec.server = server
                rec.level = level
                rec.note = note
            }
            save()
            refreshData()
            if (rec == null && !(searchOpen && applied) && shown.isNotEmpty()) lvData?.setSelection(shown.size - 1)
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
        b.addView(label("Edit ${targets.size} server", 20f, fg, true))
        b.addView(label("Pilih yang mau diubah:", 13f, sub, false), lp(4))

        val t = InputType.TYPE_CLASS_TEXT
        val on = BooleanArray(3)
        val fields = arrayOf(
            input("Server baru", "", InputType.TYPE_CLASS_NUMBER),
            input("Level baru", "", t),
            input("Keterangan baru", "", t or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        )
        val chips = ArrayList<TextView>()
        val crow = LinearLayout(this)
        crow.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 3) {
            val c = pill(fieldNames[i], false) {}
            c.setPadding(dp(8), dp(10), dp(8), dp(10))
            c.setOnClickListener {
                on[i] = !on[i]
                restyle(c, on[i])
                fields[i].visibility = if (on[i]) View.VISIBLE else View.GONE
            }
            chips.add(c)
            fields[i].visibility = View.GONE
            val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) p.leftMargin = dp(6)
            crow.addView(c, p)
        }
        b.addView(crow, lp(10))
        for (i in 0 until 3) b.addView(fields[i], lp(10))

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
            for (r in targets) {
                if (on[0]) r.server = fields[0].text.toString().trim()
                if (on[1]) r.level = fields[1].text.toString().trim()
                if (on[2]) r.note = fields[2].text.toString().trim()
            }
            selected.clear()
            save()
            refreshData()
            onSelectionChanged()
            d?.dismiss()
            toast("${targets.size} server diubah")
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

    private fun confirmWipe(f: Folder, targets: List<Rec>, after: () -> Unit) {
        sheet("Hapus ${targets.size} server?", "${targets.size} server akan dihapus dari folder ini.",
            listOf(Triple("Hapus", true) {
                for (r in targets) {
                    f.items.remove(r)
                    selected.remove(r)
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
        toast("$n server disalin")
    }

    // ---------- aksi lain ----------
    private fun copyFormat() {
        val f = current ?: return
        val list: List<Rec> = if (searchOpen && applied) shown else f.items
        if (list.isEmpty()) {
            toast("Tidak ada data")
            return
        }
        // format: server<TAB>level<TAB>keterangan  (satu baris per server)
        val text = list.joinToString("\n") { "${it.server}\t${it.level}\t${it.note}" }
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
        sheet("Hapus semua data?", "${f.items.size} server di folder ini akan dihapus permanen. Buat backup dulu kalau perlu.",
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
    private fun recsJson(list: List<Rec>): JSONArray = Store.recsJson(list)

    private fun foldersJson(): JSONArray = Store.foldersJson(folders)

    private fun parseRecs(arr: JSONArray): ArrayList<Rec> = Store.parseRecs(arr)

    private fun folderFrom(o: JSONObject): Folder = Store.folderFrom(o)

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

    private fun save() {
        Store.save(this, folders)
    }

    private fun load() {
        folders.clear()
        folders.addAll(Store.load(this))
        orders.clear()
        orders.addAll(BuktiStore.load(this))
    }
}
