package com.zakaria.freecontactswidget

import android.Manifest
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

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "ويدجت جهات الاتصال الشامل"
            textSize = 24f
            setTextColor(0xFF1A1A1A.toInt())
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "اتصال ورسالة ومحادثة بضغطة واحدة • مجاني 100%"
            textSize = 14f
            setTextColor(0xFF6C63FF.toInt())
            setPadding(0, 4, 0, pad)
        }
        root.addView(sub)

        val btnPerms = Button(this).apply {
            text = "1. تفعيل جميع الصلاحيات (المكالمات، الرسائل، الجهات)"
            setOnClickListener {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.READ_SMS,
                        Manifest.permission.READ_PHONE_STATE
                    ), 1
                )
            }
        }
        root.addView(btnPerms)

        val guideTitle = TextView(this).apply {
            text = "\nدليل الاستخدام السريع:"
            textSize = 18f
            setTextColor(0xFF222222.toInt())
        }
        root.addView(guideTitle)

        val help = TextView(this).apply {
            textSize = 15f
            setPadding(0, 8, 0, pad)
            text = "• لإضافة ويدجت: اضغط مطولاً على الشاشة الرئيسية > الويدجت (Widgets) > اختر أحد أشكال «ويدجت جهات الاتصال»:\n" +
                    "  1) مجموعة جهات (شبكة حتى 12 شخصاً أو فولدر منبثق).\n" +
                    "  2) جهة اتصال واحدة (بطاقة مع أزرار اتصال وواتساب وSMS).\n" +
                    "  3) قائمة آخر المكالمات.\n" +
                    "  4) قائمة آخر الرسائل.\n\n" +
                    "• لتخصيص كل جهة اتصال: داخل إعدادات الويدجت، اضغط على زر «تخصيص» بجانب أي اسم لتحديد إجراء خاص به (اتصال، واتساب، تيليجرام، إيميل...) أو اختيار شريحة SIM 1 / SIM 2.\n\n" +
                    "• لإنشاء اختصار مباشر على الشاشة الرئيسية: اضغط زر «تثبيت كاختصار» داخل محرر الويدجت."
        }
        root.addView(help)

        setContentView(scroll)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CallsWidgetProvider.refreshAll(this)
        SmsWidgetProvider.refreshAll(this)
        Toast.makeText(this, "تم تحديث الصلاحيات بنجاح", Toast.LENGTH_SHORT).show()
    }
}

class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var cfg: Cfg
    private lateinit var listBox: LinearLayout
    private lateinit var titleInput: EditText

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)

        fun label(t: String): TextView = TextView(this).apply {
            text = t
            textSize = 15f
            setTextColor(0xFF333333.toInt())
            setPadding(0, dp(14), 0, dp(4))
        }

        fun spinner(items: Array<String>, selected: Int, onPick: (Int) -> Unit): Spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@ConfigActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(selected.coerceIn(0, items.size - 1))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onPick(position)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        val head = TextView(this).apply {
            text = "محرر الويدجت المتكامل"
            textSize = 22f
            setTextColor(0xFF111111.toInt())
        }
        root.addView(head)

        root.addView(label("اسم الويدجت / الفولدر"))
        titleInput = EditText(this).apply {
            setText(cfg.title)
            hint = "مثال: العائلة، العمل، المفضلة"
        }
        root.addView(titleInput)

        root.addView(label("نوع عرض الويدجت"))
        root.addView(
            spinner(
                arrayOf("شبكة جهات اتصال مباشرة (حتى 12)", "ويدجت فولدر (يفتح نافذة منبثقة بالجهات)"),
                if (cfg.widgetMode == 1) 1 else 0
            ) { cfg.widgetMode = it }
        )

        val addBtn = Button(this).apply {
            text = "+ استيراد جهة اتصال من الدفتر (حتى 12)"
            setOnClickListener { pick() }
        }
        root.addView(addBtn)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        root.addView(label("قناع شكل الصورة (Mask)"))
        root.addView(
            spinner(arrayOf("دائرة", "مربع بزوايا مدورة", "مربع حاد", "شكل سداسي", "شكل قلب"), cfg.mask) {
                cfg.mask = it
            }
        )

        root.addView(label("لون إطار الصور"))
        val colors = intArrayOf(
            0xFF6C63FF.toInt(), 0xFF2EC4B6.toInt(), 0xFFFF6B6B.toInt(), 0xFFFFB703.toInt(),
            0xFF4CAF50.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()
        )
        root.addView(buildColorRow(colors) { cfg.ring = it })

        root.addView(label("لون خلفية الويدجت"))
        val bgColors = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF202124.toInt(), 0xFF1A237E.toInt(), 0xFF004D40.toInt(),
            0xFF3E2723.toInt(), 0xFFF3E5F5.toInt(), 0x00000000
        )
        root.addView(buildColorRow(bgColors) { cfg.bgColor = it })

        root.addView(label("شفافية الخلفية"))
        val seekAlpha = SeekBar(this).apply {
            max = 255
            progress = cfg.bgAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { cfg.bgAlpha = progress }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(seekAlpha)

        val showName = CheckBox(this).apply {
            text = "إظهار أسماء جهات الاتصال"
            isChecked = cfg.showName
            setOnCheckedChangeListener { _, checked -> cfg.showName = checked }
        }
        root.addView(showName)

        root.addView(label("لون خط الأسماء"))
        val textColors = intArrayOf(
            0xFF222222.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD600.toInt(), 0xFF00E5FF.toInt(), 0xFFFF5252.toInt()
        )
        root.addView(buildColorRow(textColors) { cfg.textColor = it })

        root.addView(label("الإجراء الافتراضي عند الضغط على أي جهة"))
        root.addView(spinner(Act.ACTION_NAMES, cfg.defaultAction) { cfg.defaultAction = it })

        val pinBtn = Button(this).apply {
            text = "تثبيت كاختصار على الشاشة الرئيسية"
            setOnClickListener {
                cfg.title = titleInput.text.toString().ifEmpty { "مجموعتي" }
                Store.save(this@ConfigActivity, widgetId, cfg)
                val popupIntent = Intent(this@ConfigActivity, FolderPopupActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                val icon = if (cfg.people.isNotEmpty()) {
                    Render.tile(this@ConfigActivity, cfg.people[0], cfg)
                } else {
                    Render.tile(this@ConfigActivity, Person(cfg.title, "", null), cfg)
                }
                Act.createPinnedShortcut(this@ConfigActivity, cfg.title, icon, popupIntent)
                Toast.makeText(this@ConfigActivity, "تم إرسال طلب إضافة الاختصار للشاشة الرئيسية", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(pinBtn)

        val saveBtn = Button(
