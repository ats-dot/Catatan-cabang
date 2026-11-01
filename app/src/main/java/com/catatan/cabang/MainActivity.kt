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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import android.view.inputmethod.EditorInfo
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

class Folder(var name: String, val items: ArrayList<Rec> = ArrayList(), var id: String = "")

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

class SwipeFrame(ctx: Context, private val onSwipe: (Int) -> Unit) : FrameLayout(ctx) {
    private var x0 = 0f
    private var y0 = 0f
    private var dx = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val dist = 70 * ctx.resources.displayMetrics.density

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x0 = e.x
                y0 = e.y
                dx = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val mx = e.x - x0
                val my = e.y - y0
                if (abs(mx) > slop * 2 && abs(mx) > abs(my) * 1.5f) {
                    dx = mx
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> dx = e.x - x0
            MotionEvent.ACTION_UP -> {
                if (abs(dx) > dist) onSwipe(if (dx < 0) 1 else -1)
                dx = 0f
            }
            MotionEvent.ACTION_CANCEL -> dx = 0f
        }
        return true
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
    private var statFilter = 0           // 0 semua, 1 siap kirim, 2 terpakai
    private var topTab = 0               // 0 Akun, 1 Bukti kirim
    private var fOpen = false
    private var fField = 0               // 0 email, 1 ID
    private var fQuery = ""
    private val shownF = ArrayList<Folder>()
    private var fSearchHolder: LinearLayout? = null
    private val cGreen = 0xFF7FC8A0.toInt()
    private val cAmber = 0xFFD9B25F.toInt()
    private val cDone = 0xFF8A8A8A.toInt()
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
        load()
        showFolders()
    }

    override fun onResume() {
        super.onResume()
        Store.listener = { runOnUiThread { reloadFromStore() } }
        if (Store.changed) reloadFromStore()
    }

    override fun onPause() {
        Store.listener = null
        super.onPause()
    }

    private fun reloadFromStore() {
        Store.changed = false
        val wasIn = current
        val keyId = wasIn?.id ?: ""
        val keyName = wasIn?.name ?: ""
        load()
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
        if (selected.isNotEmpty()) {
            selected.clear()
            onSelectionChanged()
        } else if (searchOpen && current != null) {
            toggleSearch()
        } else if (current != null) {
            showFolders(-1)
        } else if (fOpen && topTab == 0) {
            toggleFolderSearch()
        } else if (topTab == 1) {
            switchTab(0)
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
        applied = false
        appliedQ = ""
        applyWindow()
        val col = column()

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(label("Catatan Cabang", 22f, fg, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (topTab == 0) top.addView(iconBtn(1, fOpen) { toggleFolderSearch() }, iconLp())
        top.addView(iconBtn(2, false) { menuFolders() }, iconLp())
        col.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        col.addView(tabBar(), lp(10))

        val fs = LinearLayout(this)
        fs.orientation = LinearLayout.VERTICAL
        col.addView(fs, lp(8))
        fSearchHolder = fs

        val frame = SwipeFrame(this) { d ->
            if (d > 0 && topTab == 0) switchTab(1)
            else if (d < 0 && topTab == 1) switchTab(0)
        }
        if (topTab == 0) {
            val lv = ListView(this)
            styleList(lv)
            val ad = FolderAdapter()
            lv.adapter = ad
            frame.addView(lv, FrameLayout.LayoutParams(-1, -1))
            val em = label("", 14f, sub, false)
            em.gravity = Gravity.CENTER
            frame.addView(em, FrameLayout.LayoutParams(-1, -1))
            lv.emptyView = em

            val fb = fab("+  Akun") { askAccount() }
            val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            fp.gravity = Gravity.BOTTOM or Gravity.END
            fp.bottomMargin = dp(18)
            frame.addView(fb, fp)
            lvFolders = lv
            adapterF = ad
            emptyF = em
        } else {
            val em = label("Belum ada bukti kirim.\nFolder muncul otomatis setelah satu pesanan selesai.", 14f, sub, false)
            em.gravity = Gravity.CENTER
            frame.addView(em, FrameLayout.LayoutParams(-1, -1))
            lvFolders = null
            adapterF = null
            emptyF = null
        }
        col.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)
        if (dir != 0) enter(col, dir)
        buildFolderSearch(false)
        refreshFolders()
    }

    private fun tabBar(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.background = shape(surface, 22, line)
        r.setPadding(dp(3), dp(3), dp(3), dp(3))
        val names = arrayOf("Akun", "Bukti kirim")
        for (i in 0..1) {
            val t = TextView(this)
            t.text = names[i]
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.typeface = Typeface.DEFAULT_BOLD
            t.setPadding(0, dp(9), 0, dp(9))
            if (i == topTab) {
                t.setTextColor(bg)
                t.background = shape(fg, 20)
            } else {
                t.setTextColor(sub)
            }
            t.setOnClickListener { if (i != topTab) switchTab(i) }
            r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return r
    }

    private fun switchTab(n: Int) {
        val d = if (n > topTab) 1 else -1
        topTab = n
        showFolders(d)
    }

    private fun toggleFolderSearch() {
        fOpen = !fOpen
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (!fOpen) {
            fQuery = ""
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
        showFolders(0)
        if (fOpen) buildFolderSearch(true)
    }

    private fun buildFolderSearch(focus: Boolean) {
        val sh = fSearchHolder ?: return
        sh.removeAllViews()
        if (!fOpen || topTab != 0) {
            sh.visibility = View.GONE
            return
        }
        sh.visibility = View.VISIBLE
        val et = input(if (fField == 0) "Cari email" else "Cari ID", fQuery,
            if (fField == 1) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT)
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
        val names = arrayOf("Email", "ID")
        for (i in 0..1) {
            val c = pill(names[i], i == fField) {}
            c.setPadding(dp(14), dp(8), dp(14), dp(8))
            c.setOnClickListener {
                fField = i
                for (j in list.indices) restyle(list[j], j == i)
                et.inputType = if (i == 1) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                et.hint = if (i == 0) "Cari email" else "Cari ID"
                et.setText("")
                fQuery = ""
                refreshFolders()
            }
            list.add(c)
            val cp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cp.rightMargin = dp(8)
            chips.addView(c, cp)
        }
        sh.addView(chips, lp(10))
        if (focus) {
            et.requestFocus()
            et.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(et, 0)
            }, 150)
        }
    }

    private fun refreshFolders() {
        shownF.clear()
        val q = fQuery.trim()
        for (f in folders) {
            if (fOpen && q.isNotEmpty()) {
                val v = if (fField == 0) f.name else f.id
                if (!v.contains(q, true)) continue
            }
            shownF.add(f)
        }
        adapterF?.notifyDataSetChanged()
        emptyF?.text = if (folders.isEmpty()) "Belum ada akun.\nTekan + Akun, atau tunggu kiriman dari aplikasi pembuat akun." else "Tidak ada hasil."
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
        private val tvName = TextView(ctx)
        private val tvId = TextView(ctx)
        private val tvSub = TextView(ctx)
        private var pos = 0

        init {
            val card = LinearLayout(ctx)
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
            mid.addView(tvName)
            mid.addView(tvId, lp(2))
            mid.addView(tvSub, lp(2))
            card.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            card.addView(IconView(ctx, 5, sub, bg), LinearLayout.LayoutParams(dp(20), dp(20)))

            root.setPadding(0, 0, 0, dp(10))
            root.addView(card, FrameLayout.LayoutParams(-1, -2))

            Gesture(root,
                { shownF.getOrNull(pos)?.let { openFolder(it) } },
                { shownF.getOrNull(pos)?.let { folderOptions(it) } },
                null).attach()
        }

        fun bind(p: Int, f: Folder) {
            pos = p
            if (f.name.isBlank()) {
                tvName.text = "Email belum diisi"
                tvName.setTextColor(cAmber)
            } else {
                tvName.text = f.name
                tvName.setTextColor(fg)
            }
            tvId.text = if (f.id.isBlank()) "ID belum ada" else "ID ${f.id}"
            val siap = f.items.count { Store.rank(it.note) == 2 }
            val pakai = f.items.count { Store.rank(it.note) == 3 }
            val sb = SpannableStringBuilder()
            sb.append("${f.items.size} server")
            if (siap > 0) {
                sb.append(" · ")
                val s = sb.length
                sb.append("$siap siap")
                sb.setSpan(ForegroundColorSpan(cGreen), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                sb.append(" · 0 siap")
            }
            sb.append(" · ")
            val s2 = sb.length
            sb.append("$pakai terpakai")
            if (pakai > 0) sb.setSpan(ForegroundColorSpan(cDone), s2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvSub.text = sb
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
        fe.isSingleLine = true
        fi.isSingleLine = true
        b.addView(fe, lp(12))
        b.addView(fi, lp(8))
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
            folders.add(Folder(e, ArrayList(), i))
            save()
            refreshFolders()
            d?.dismiss()
        }, p)
        b.addView(row, lp(16))
        d = showBox(b)
    }

    private fun askText(title: String, initial: String, ok: String, hint: String = "Judul folder", cb: (String) -> Unit) {
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
        strip.background = shape(surface, 14, line)
        strip.setPadding(dp(8), dp(8), dp(8), dp(8))
        val sa = label("0", 20f, fg, true)
        val ss = label("0", 20f, cGreen, true)
        val sp = label("0", 20f, cDone, true)
        val names3 = arrayOf("SERVER", "SIAP DM", "TERPAKAI")
        val nums3 = arrayOf(sa, ss, sp)
        for (i in 0..2) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            val lb = label(names3[i], 10f, sub, true)
            lb.letterSpacing = 0.1f
            nums3[i].gravity = Gravity.CENTER
            cell.addView(nums3[i])
            cell.addView(lb)
            strip.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(strip, lp(8))
        statAkun = sa
        statServer = ss
        statPakai = sp

        val fr = LinearLayout(this)
        fr.orientation = LinearLayout.HORIZONTAL
        val fchips = ArrayList<TextView>()
        val fnames = arrayOf("Semua", "Siap kirim", "Terpakai")
        for (i in 0..2) {
            val c = pill(fnames[i], i == statFilter) {}
            c.setPadding(dp(14), dp(8), dp(14), dp(8))
            c.setOnClickListener {
                statFilter = i
                for (j in fchips.indices) restyle(fchips[j], j == i)
                refreshData()
            }
            fchips.add(c)
            val cp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cp.rightMargin = dp(8)
            fr.addView(c, cp)
        }
        col.addView(fr, lp(8))

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

        val fb = fab("+  Server") { openForm(null) }
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
            if (statFilter == 1 && Store.rank(r.note) != 2) continue
            if (statFilter == 2 && Store.rank(r.note) != 3) continue
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
        adapterD?.notifyDataSetChanged()
        val searching = active
        statAkun?.text = shown.size.toString()
        statServer?.text = shown.count { Store.rank(it.note) == 2 }.toString()
        statPakai?.text = shown.count { Store.rank(it.note) == 3 }.toString()
        emptyD?.text = if (f.items.isEmpty()) "Belum ada server.\nTekan + Server untuk mulai." else "Tidak ada hasil."
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
            tvEmail.text = "Server ${r.server}"
            tvServer.visibility = View.GONE
            tvNote.text = r.note
            tvNote.setTextColor(when (Store.rank(r.note)) {
                2 -> cGreen
                1 -> cAmber
                3 -> cDone
                else -> sub
            })
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
    }
}
