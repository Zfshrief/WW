package com.zakaria.freecontactswidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        scroll.addView(root)
        root.addView(TextView(this).apply { text = "ويدجت جهات الاتصال الشامل"; textSize = 24f; setTextColor(0xFF1A1A1A.toInt()) })
        root.addView(TextView(this).apply { text = "اتصال ورسالة ومحادثة بضغطة واحدة • مجاني 100%"; textSize = 14f; setTextColor(0xFF6C63FF.toInt()); setPadding(0, 4, 0, pad) })
        root.addView(Button(this).apply {
            text = "1. تفعيل جميع الصلاحيات (المكالمات، الرسائل، الجهات)"
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE), 1)
            }
        })
        root.addView(TextView(this).apply {
            textSize = 15f; setPadding(0, 12, 0, pad)
            text = "• اضغط مطولاً على الشاشة الرئيسية > الويدجت > اختر أحد أشكال «ويدجت جهات الاتصال».\n• لتخصيص كل جهة: داخل إعدادات الويدجت اضغط «تخصيص» بجانب الاسم."
        })
        setContentView(scroll)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CallsWidgetProvider.refreshAll(this)
        SmsWidgetProvider.refreshAll(this)
        Toast.makeText(this, "تم تحديث الصلاحيات", Toast.LENGTH_SHORT).show()
    }
}

class FolderPopupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val cfg = Store.load(this, widgetId)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); setBackgroundColor(Color.WHITE) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = cfg.title; textSize = 20f; setTextColor(Color.BLACK); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        header.addView(Button(this).apply { text = "تعديل"; setOnClickListener { startActivity(Intent(this@FolderPopupActivity, ConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish() } })
        root.addView(header)
        val scroll = ScrollView(this)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(grid)
        for (p in cfg.people) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad / 2, 0, pad / 2)
                setOnClickListener { startActivity(Act.intent(this@FolderPopupActivity, p, cfg.defaultAction)); finish() }
            }
            row.addView(ImageView(this).apply { setImageBitmap(Render.tile(this@FolderPopupActivity, p, cfg)); layoutParams = LinearLayout.LayoutParams(pad * 3, pad * 3) })
            row.addView(TextView(this).apply { text = "  ${p.name}\n${p.number}"; textSize = 16f; setTextColor(Color.DKGRAY) })
            grid.addView(row)
        }
        root.addView(scroll)
        setContentView(root)
    }
}
