package com.zakaria.freecontactswidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
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
            R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6, R.id.t7
        )

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val cfg = Store.load(context, id)
            val rv = RemoteViews(context.packageName, R.layout.widget_group)
            rv.setInt(R.id.bg, "setImageAlpha", cfg.bgAlpha)

            val settings = Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            rv.setOnClickPendingIntent(R.id.bg, Act.pendingActivity(context, id * 10 + 9, settings))

            val n = minOf(cfg.people.size, 8)
            for (i in 0 until 8) {
                if (i < n) {
                    val p = cfg.people[i]
                    rv.setViewVisibility(TILES[i], View.VISIBLE)
                    rv.setImageViewBitmap(TILES[i], Render.tile(context, p, cfg))
                    rv.setOnClickPendingIntent(
                        TILES[i],
                        Act.pendingActivity(context, id * 10 + i, Act.intent(context, p, cfg.action))
                    )
                } else {
                    rv.setViewVisibility(TILES[i], View.INVISIBLE)
                }
            }
            rv.setViewVisibility(R.id.row2, if (n > 4) View.VISIBLE else View.GONE)
            manager.updateAppWidget(id, rv)
        }
    }
}

class CallsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val rows = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3)
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
                    while (cur.moveToNext() && labels.size < 4) {
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
            } catch (e: Exception) {
                // ignore, show empty state
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

            for (i in 0 until 4) {
                if (!allowed) {
                    rv.setTextViewText(rows[i], if (i == 0) "افتح التطبيق وفعّل صلاحية سجل المكالمات" else "")
                    if (i == 0) {
                        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        rv.setOnClickPendingIntent(rows[i], Act.pendingActivity(context, id * 10, open))
                    }
                } else if (i < labels.size) {
                    rv.setTextViewText(rows[i], labels[i])
                    val dial = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + numbers[i]))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    rv.setOnClickPendingIntent(rows[i], Act.pendingActivity(context, id * 10 + i, dial))
                } else {
                    rv.setTextViewText(rows[i], "")
                }
            }
            manager.updateAppWidget(id, rv)
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CallsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val i = Intent(context, CallsWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(i)
        }
    }
}
