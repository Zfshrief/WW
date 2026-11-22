package com.zakaria.freecontactswidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.Telephony
import android.view.View
import android.widget.RemoteViews

class ContactsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        for (id in ids) Store.delete(context, id)
    }

    companion object {
        private val TILES = intArrayOf(
            R.id.t0, R.id.t1, R.id.t2, R.id.t3,
            R.id.t4, R.id.t5, R.id.t6, R.id.t7,
            R.id.t8, R.id.t9, R.id.t10, R.id.t11
        )

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val cfg = Store.load(context, id)
            val rv = RemoteViews(context.packageName, R.layout.widget_group)
            rv.setInt(R.id.bg, "setColorFilter", cfg.bgColor)
            rv.setInt(R.id.bg, "setImageAlpha", cfg.bgAlpha)

            val settings = Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            rv.setOnClickPendingIntent(R.id.bg, Act.pendingActivity(context, id * 20 + 19, settings))

            // وضع الفولدر المنبثق
            if (cfg.widgetMode == 1) {
                rv.setViewVisibility(R.id.folder_box, View.VISIBLE)
                rv.setViewVisibility(R.id.row1, View.GONE)
                rv.setViewVisibility(R.id.row2, View.GONE)
                rv.setViewVisibility(R.id.row3, View.GONE)
                rv.setTextViewText(R.id.folder_box, "📁 ${cfg.title} (${cfg.people.size})")
                val popup = Intent(context, FolderPopupActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                rv.setOnClickPendingIntent(R.id.folder_box, Act.pendingActivity(context, id * 20 + 18, popup))
                manager.updateAppWidget(id, rv)
                return
            }

            rv.setViewVisibility(R.id.folder_box, View.GONE)
            val n = minOf(cfg.people.size, 12)
            for (i in 0 until 12) {
                if (i < n) {
                    val p = cfg.people[i]
                    rv.setViewVisibility(TILES[i], View.VISIBLE)
                    rv.setImageViewBitmap(TILES[i], Render.tile(context, p, cfg))
                    rv.setOnClickPendingIntent(
                        TILES[i],
                        Act.pendingActivity(context, id * 20 + i, Act.intent(context, p, cfg.defaultAction))
                    )
                } else {
                    rv.setViewVisibility(TILES[i], View.INVISIBLE)
                }
            }
            rv.setViewVisibility(R.id.row1, View.VISIBLE)
            rv.setViewVisibility(R.id.row2, if (n > 4) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.row3, if (n > 8) View.VISIBLE else View.GONE)
            manager.updateAppWidget(id, rv)
        }
    }
}

class SingleContactWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, manager, id)
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val cfg = Store.load(context, id)
            val rv = RemoteViews(context.packageName, R.layout.widget_single)
            rv.setInt(R.id.bg, "setColorFilter", cfg.bgColor)
            rv.setInt(R.id.bg, "setImageAlpha", cfg.bgAlpha)

            val settings = Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            rv.setOnClickPendingIntent(R.id.bg, Act.pendingActivity(context, id * 20 + 15, settings))

            val p = cfg.people.firstOrNull()
            if (p != null) {
                rv.setImageViewBitmap(R.id.avatar, Render.tile(context, p, cfg))
                rv.setTextViewText(R.id.contact_name, p.name)
                rv.setTextColor(R.id.contact_name, cfg.textColor)
                rv.setOnClickPendingIntent(R.id.avatar, Act.pendingActivity(context, id * 20 + 1, Act.intent(context, p, cfg.defaultAction)))
                rv.setOnClickPendingIntent(R.id.btn_call, Act.pendingActivity(context, id * 20 + 2, Act.intent(context, p.copy(action = 0), 0)))
                rv.setOnClickPendingIntent(R.id.btn_sms, Act.pendingActivity(context, id * 20 + 3, Act.intent(context, p.copy(action = 1), 1)))
                rv.setOnClickPendingIntent(R.id.btn_wa, Act.pendingActivity(context, id * 20 + 4, Act.intent(context, p.copy(action = 2), 2)))
            } else {
                rv.setTextViewText(R.id.contact_name, "اضغط لاختيار جهة")
                rv.setOnClickPendingIntent(R.id.contact_name, Act.pendingActivity(context, id * 20 + 1, settings))
            }
            manager.updateAppWidget(id, rv)
        }
    }
}

class CallsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val rows = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5)
        val labels = ArrayList<String>()
        val numbers = ArrayList<String>()

        val allowed = context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE),
                    null, null, CallLog.Calls.DATE + " DESC"
                )?.use { cur ->
                    while (cur.moveToNext() && labels.size < 6) {
                        val number = cur.getString(0) ?: ""
                        val name = cur.getString(1)
                        val type = cur.getInt(2)
                        val arrow = when (type) {
                            CallLog.Calls.OUTGOING_TYPE -> "↗ "
                            CallLog.Calls.MISSED_TYPE -> "✖ "
                            else -> "↙ "
                        }
                        labels.add(arrow + (if (name.isNullOrEmpty()) number else name))
                        numbers.add(number)
                    }
                }
            } catch (_: Exception) {
            }
        }

        for (id in ids) {
            val rv = RemoteViews(context.packageName, R.layout.widget_calls)
            val refresh = Intent(context, CallsWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            rv.setOnClickPendingIntent(
                R.id.title,
                PendingIntent.getBroadcast(context, id, refresh, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )

            for (i in 0 until 6) {
                if (!allowed) {
                    rv.setTextViewText(rows[i], if (i == 0) "افتح التطبيق وفعّل صلاحية المكالمات" else "")
                    if (i == 0) {
                        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        rv.setOn
