package com.zakaria.freecontactswidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "ويدجت جهات الاتصال"
        title.textSize = 24f
        root.addView(title)

        val help = TextView(this)
        help.textSize = 16f
        help.setPadding(0, pad, 0, pad)
        help.text = "1) اضغط مطولاً على مكان فاضي في الشاشة الرئيسية.\n" +
            "2) اختر ويدجت ثم ابحث عن «ويدجت جهات الاتصال».\n" +
            "3) اختر «مجموعة جهات» لاختيار حتى 8 جهات، أو «آخر المكالمات».\n" +
            "4) اضغط على أي مساحة فاضية في الويدجت لفتح إعداداته.\n\n" +
            "التطبيق مجاني بالكامل، من غير اشتراك أو إعلانات."
        root.addView(help)

        val btn = Button(this)
        btn.text = "تفعيل صلاحيات المكالمات"
        btn.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE), 1)
        }
        root.addView(btn)
        setContentView(root)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CallsWidgetProvider.refreshAll(this)
    }
}

class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var cfg: Cfg
    private lateinit var listBox: LinearLayout

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        cfg = Store.load(this, widgetId)

        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        scroll.addView(root)

        fun label(t: String): TextView {
            val v = TextView(this)
            v.text = t
            v.textSize = 16f
            v.setPadding(0, dp(14), 0, dp(4))
            return v
        }

        fun spinner(items: Array<String>, selected: Int, onPick: (Int) -> Unit): Spinner {
            val s = Spinner(this)
            s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
            s.setSelection(selected)
            s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onPick(position)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            return s
        }

        val head = TextView(this)
        head.text = "إعدادات الويدجت"
        head.textSize = 22f
        root.addView(head)

        val add = Button(this)
        add.text = "إضافة جهة اتصال (حتى 8)"
        add.setOnClickListener { pick() }
        root.addView(add)

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        root.addView(listBox)

        root.addView(label("شكل الصورة"))
        root.addView(spinner(arrayOf("دائرة", "مربع بزوايا مدورة", "مربع"), cfg.mask) { cfg.mask = it })

        root.addView(label("لون الإطار"))
        val colors = intArrayOf(
            0xFF6C63FF.toInt(), 0xFF2EC4B6.toInt(), 0xFFFF6B6B.toInt(), 0xFFFFB703.toInt(),
            0xFF4CAF50.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()
        )
        val colorRow = LinearLayout(this)
        for (col in colors) {
            val sw = View(this)
            sw.setBackgroundColor(col)
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            sw.layoutParams = lp
            sw.setOnClickListener {
                cfg.ring = col
                Toast.makeText(this, "تم اختيار اللون", Toast.LENGTH_SHORT).show()
            }
            colorRow.addView(sw)
        }
        root.addView(colorRow)

        val showName = CheckBox(this)
        showName.text = "إظهار الاسم"
        showName.isChecked = cfg.showName
        showName.setOnCheckedChangeListener { _, checked -> cfg.showName = checked }
        root.addView(showName)

        root.addView(label("عند الضغط على جهة الاتصال"))
        root.addView(spinner(arrayOf("اتصال", "رسالة SMS", "واتساب"), cfg.action) { cfg.action = it })

        root.addView(label("شفافية الخلفية"))
        val seek = SeekBar(this)
        seek.max = 255
        seek.progress = cfg.bgAlpha
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                cfg.bgAlpha = progress
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(seek)

        val save = Button(this)
        save.text = "حفظ"
        save.setOnClickListener { saveAndFinish() }
        root.addView(save)

        setContentView(scroll)
        refreshList()
    }

    private fun pick() {
        if (cfg.people.size >= 8) {
            Toast.makeText(this, "الحد الأقصى 8 جهات", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 11
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 11 && resultCode == RESULT_OK && uri != null) {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    cfg.people.add(Person(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2)))
                }
            }
            refreshList()
        }
    }

    private fun refreshList() {
        listBox.removeAllViews()
        for (i in cfg.people.indices) {
            val p = cfg.people[i]
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val t = TextView(this)
            t.text = p.name + "\n" + p.number
            t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(t)
            val rm = Button(this)
            rm.text = "حذف"
            rm.setOnClickListener {
                cfg.people.removeAt(i)
                refreshList()
            }
            row.addView(rm)
            listBox.addView(row)
        }
    }

    private fun saveAndFinish() {
        Store.save(this, widgetId, cfg)
        ContactsWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
