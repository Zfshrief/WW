package com.zakaria.freecontactswidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.*

class ConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var cfg: Cfg
    private lateinit var listBox: LinearLayout
    private lateinit var titleInput: EditText
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        cfg = Store.load(this, widgetId)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        scroll.addView(root)

        fun label(t: String) = TextView(this).apply { text = t; textSize = 15f; setTextColor(0xFF333333.toInt()); setPadding(0, dp(12), 0, dp(4)) }
        fun spinner(items: Array<String>, sel: Int, onPick: (Int) -> Unit) = Spinner(this).apply {
            adapter = ArrayAdapter(this@ConfigActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(sel.coerceIn(0, items.size - 1))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onPick(pos) }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        fun colorRow(cols: IntArray, onPick: (Int) -> Unit) = LinearLayout(this).apply {
            for (c in cols) addView(View(this@ConfigActivity).apply {
                setBackgroundColor(if (c == 0) 0xFFCCCCCC.toInt() else c)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                setOnClickListener { onPick(c); Toast.makeText(this@ConfigActivity, "تم اختيار اللون", Toast.LENGTH_SHORT).show() }
            })
        }

        root.addView(TextView(this).apply { text = "محرر الويدجت المتكامل"; textSize = 22f; setTextColor(0xFF111111.toInt()) })
        root.addView(label("اسم الويدجت / الفولدر"))
        titleInput = EditText(this).apply { setText(cfg.title); hint = "مثال: العائلة، العمل" }
        root.addView(titleInput)

        root.addView(label("نوع عرض الويدجت"))
        root.addView(spinner(arrayOf("شبكة جهات اتصال مباشرة (حتى 12)", "ويدجت فولدر (يفتح نافذة منبثقة)"), if (cfg.widgetMode == 1) 1 else 0) { cfg.widgetMode = it })

        root.addView(Button(this).apply { text = "+ استيراد جهة اتصال (حتى 12)"; setOnClickListener { pick() } })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        root.addView(label("قناع شكل الصورة"))
        root.addView(spinner(arrayOf("دائرة", "مربع مدور", "مربع حاد", "سداسي", "قلب"), cfg.mask) { cfg.mask = it })

        root.addView(label("لون إطار الصور"))
        root.addView(colorRow(intArrayOf(0xFF6C63FF.toInt(), 0xFF2EC4B6.toInt(), 0xFFFF6B6B.toInt(), 0xFFFFB703.toInt(), 0xFF4CAF50.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())) { cfg.ring = it })

        root.addView(label("لون خلفية الويدجت"))
        root.addView(colorRow(intArrayOf(0xFFFFFFFF.toInt(), 0xFF202124.toInt(), 0xFF1A237E.toInt(), 0xFF004D40.toInt(), 0xFF3E2723.toInt(), 0xFFF3E5F5.toInt(), 0x00000000)) { cfg.bgColor = it })

        root.addView(label("شفافية الخلفية"))
        root.addView(SeekBar(this).apply {
            max = 255; progress = cfg.bgAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { cfg.bgAlpha = p }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        root.addView(CheckBox(this).apply { text = "إظهار الأسماء"; isChecked = cfg.showName; setOnCheckedChangeListener { _, c -> cfg.showName = c } })
        root.addView(label("لون خط الأسماء"))
        root.addView(colorRow(intArrayOf(0xFF222222.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD600.toInt(), 0xFF00E5FF.toInt(), 0xFFFF5252.toInt())) { cfg.textColor = it })

        root.addView(label("الإجراء الافتراضي عند الضغط"))
        root.addView(spinner(Act.ACTION_NAMES, cfg.defaultAction) { cfg.defaultAction = it })

        root.addView(Button(this).apply {
            text = "تثبيت كاختصار على الشاشة الرئيسية"
            setOnClickListener {
                cfg.title = titleInput.text.toString().ifEmpty { "مجموعتي" }
                Store.save(this@ConfigActivity, widgetId, cfg)
                val pop = Intent(this@ConfigActivity, FolderPopupActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                val icon = Render.tile(this@ConfigActivity, cfg.people.firstOrNull() ?: Person(cfg.title, "", null), cfg)
                Act.createPinnedShortcut(this@ConfigActivity, cfg.title, icon, pop)
                Toast.makeText(this@ConfigActivity, "تم إرسال طلب إضافة الاختصار", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "حفظ وتطبيق الويدجت"
            setBackgroundColor(0xFF6C63FF.toInt()); setTextColor(Color.WHITE)
            setOnClickListener { saveAndFinish() }
        })
        setContentView(scroll)
        refreshList()
    }

    private fun pick() {
        if (cfg.people.size >= 12) { Toast.makeText(this, "الحد الأقصى 12", Toast.LENGTH_SHORT).show(); return }
        startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 11)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 11 && resultCode == RESULT_OK && uri != null) {
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI), null, null, null)?.use { c ->
                if (c.moveToFirst()) cfg.people.add(Person(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2)))
            }
            refreshList()
        }
    }

    private fun showPersonDialog(index: Int) {
        val p = cfg.people[index]
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val actSp = Spinner(this).apply {
            val items = arrayOf("الإجراء العام للويدجت") + Act.ACTION_NAMES
            adapter = ArrayAdapter(this@ConfigActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection((p.action + 1).coerceIn(0, items.size - 1))
        }
        val simSp = Spinner(this).apply {
            adapter = ArrayAdapter(this@ConfigActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("شريحة: تلقائي", "SIM 1", "SIM 2"))
            setSelection(p.simSlot.coerceIn(0, 2))
        }
        box.addView(TextView(this).apply { text = "إجراء مخصص لـ ${p.name}:" }); box.addView(actSp)
        box.addView(TextView(this).apply { text = "\nشريحة الاتصال:" }); box.addView(simSp)
        AlertDialog.Builder(this).setTitle("تخصيص ${p.name}").setView(box).setPositiveButton("حفظ") { _, _ ->
            p.action = actSp.selectedItemPosition - 1; p.simSlot = simSp.selectedItemPosition; refreshList()
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun refreshList() {
        listBox.removeAllViews()
        for (i in cfg.people.indices) {
            val p = cfg.people[i]
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
            val lbl = (if (p.action >= 0) " • [${Act.ACTION_NAMES[p.action]}]" else "") + (if (p.simSlot > 0) " (SIM ${p.simSlot})" else "")
            row.addView(TextView(this).apply { text = "${p.name}$lbl\n${p.number}"; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(Button(this).apply { text = "تخصيص"; setOnClickListener { showPersonDialog(i) } })
            row.addView(Button(this).apply { text = "حذف"; setOnClickListener { cfg.people.removeAt(i); refreshList() } })
            listBox.addView(row)
        }
    }

    private fun saveAndFinish() {
        cfg.title = titleInput.text.toString().ifEmpty { "مجموعتي" }
        Store.save(this, widgetId, cfg)
        val mgr = AppWidgetManager.getInstance(this)
        ContactsWidgetProvider.update(this, mgr, widgetId)
        SingleContactWidgetProvider.update(this, mgr, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
