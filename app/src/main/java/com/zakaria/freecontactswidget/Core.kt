package com.zakaria.freecontactswidget

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.*
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.TextPaint
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

data class Person(val name: String, val number: String, val photo: String?, var action: Int = -1, var simSlot: Int = 0)

class Cfg {
    var title: String = "مجموعتي"
    var widgetMode: Int = 0
    var people: MutableList<Person> = mutableListOf()
    var mask: Int = 0
    var ring: Int = 0xFF6C63FF.toInt()
    var bgColor: Int = 0xFFFFFFFF.toInt()
    var textColor: Int = 0xFF222222.toInt()
    var textSize: Float = 28f
    var showName: Boolean = true
    var defaultAction: Int = 0
    var bgAlpha: Int = 220
}

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("widgets_v2", Context.MODE_PRIVATE)
    fun save(c: Context, id: Int, cfg: Cfg) {
        val arr = JSONArray()
        for (p in cfg.people) arr.put(JSONObject().put("n", p.name).put("p", p.number).put("ph", p.photo ?: "").put("act", p.action).put("sim", p.simSlot))
        val o = JSONObject().put("title", cfg.title).put("mode", cfg.widgetMode).put("people", arr).put("mask", cfg.mask).put("ring", cfg.ring).put("bgColor", cfg.bgColor).put("textColor", cfg.textColor).put("textSize", cfg.textSize.toDouble()).put("showName", cfg.showName).put("action", cfg.defaultAction).put("bgAlpha", cfg.bgAlpha)
        prefs(c).edit().putString("w$id", o.toString()).apply()
    }
    fun load(c: Context, id: Int): Cfg {
        val cfg = Cfg()
        val s = prefs(c).getString("w$id", null) ?: return cfg
        try {
            val o = JSONObject(s)
            cfg.title = o.optString("title", "مجموعتي")
            cfg.widgetMode = o.optInt("mode", 0)
            val arr = o.getJSONArray("people")
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val ph = x.optString("ph", "")
                cfg.people.add(Person(x.getString("n"), x.getString("p"), if (ph.isEmpty()) null else ph, x.optInt("act", -1), x.optInt("sim", 0)))
            }
            cfg.mask = o.optInt("mask", 0)
            cfg.ring = o.optInt("ring", 0xFF6C63FF.toInt())
            cfg.bgColor = o.optInt("bgColor", 0xFFFFFFFF.toInt())
            cfg.textColor = o.optInt("textColor", 0xFF222222.toInt())
            cfg.textSize = o.optDouble("textSize", 28.0).toFloat()
            cfg.showName = o.optBoolean("showName", true)
            cfg.defaultAction = o.optInt("action", 0)
            cfg.bgAlpha = o.optInt("bgAlpha", 220)
        } catch (_: Exception) {}
        return cfg
    }
    fun delete(c: Context, id: Int) { prefs(c).edit().remove("w$id").apply() }
}

object Act {
    val ACTION_NAMES = arrayOf("اتصال مباشر", "رسالة SMS", "واتساب", "تيليجرام", "ماسنجر", "فايبر", "إيميل", "ملف الجهة", "سيجنال", "لوحة الاتصال")
    private fun intl(raw: String): String {
        val d = raw.filter { it.isDigit() }
        if (raw.startsWith("+")) return d
        if (d.startsWith("00")) return d.substring(2)
        if (d.startsWith("01") && d.length == 11) return "20" + d.substring(1)
        return d
    }
    fun intent(c: Context, p: Person, fallback: Int): Intent {
        val act = if (p.action >= 0) p.action else fallback
        val num = p.number.filter { it.isDigit() || it == '+' }
        val w = intl(p.number)
        val i = when (act) {
            1 -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
            2 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$w"))
            3 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$w"))
            4 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/"))
            5 -> Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=%2B$w"))
            6 -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            7 -> Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num)))
            8 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$w"))
            9 -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
            else -> {
                val ok = c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val ci = Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                if (p.simSlot in 1..2 && ok) {
                    try {
                        val acc = (c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).callCapablePhoneAccounts
                        if (acc.size >= p.simSlot) ci.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, acc[p.simSlot - 1])
                    } catch (_: Exception) {}
                }
                ci
            }
        }
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun pendingActivity(c: Context, code: Int, i: Intent): PendingIntent =
        PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun createPinnedShortcut(c: Context, title: String, iconBmp: Bitmap, targetIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sm = c.getSystemService(ShortcutManager::class.java)
            if (sm != null && sm.isRequestPinShortcutSupported) {
                val info = ShortcutInfo.Builder(c, "sc_${System.currentTimeMillis()}").setShortLabel(title.ifEmpty { "جهة" }).setIcon(Icon.createWithBitmap(iconBmp)).setIntent(targetIntent.setAction(Intent.ACTION_VIEW)).build()
                sm.requestPinShortcut(info, null)
            }
        }
    }
}

object Render {
    private fun loadPhoto(c: Context, uri: String?): Bitmap? {
        if (uri.isNullOrEmpty()) return null
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, b) }
            var s = 1
            while (b.outWidth / (s * 2) >= 400 && b.outHeight / (s * 2) >= 400) s *= 2
            c.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        } catch (_: Exception) { null }
    }
    private fun maskPath(mask: Int, r: RectF): Path = Path().apply {
        when (mask) {
            0 -> addOval(r, Path.Direction.CW)
            1 -> addRoundRect(r, 38f, 38f, Path.Direction.CW)
            2 -> addRoundRect(r, 6f, 6f, Path.Direction.CW)
            3 -> {
                for (i in 0 until 6) {
                    val a = Math.toRadians((60 * i - 30).toDouble())
                    val x = (r.centerX() + (r.width() / 2f) * cos(a)).toFloat()
                    val y = (r.centerY() + (r.width() / 2f) * sin(a)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            else -> {
                moveTo(r.left + r.width() / 2f, r.top + r.height() * 0.92f)
                cubicTo(r.left - r.width() * 0.1f, r.top + r.height() * 0.55f, r.left + r.width() * 0.05f, r.top + r.height() * 0.05f, r.left + r.width() / 2f, r.top + r.height() * 0.30f)
                cubicTo(r.left + r.width() * 0.95f, r.top + r.height() * 0.05f, r.left + r.width() * 1.1f, r.top + r.height() * 0.55f, r.left + r.width() / 2f, r.top + r.height() * 0.92f)
                close()
            }
        }
    }
    fun tile(c: Context, p: Person, cfg: Cfg): Bitmap {
        val bmp = Bitmap.createBitmap(192, 240, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val top = if (cfg.showName) 10f else 44f
        val rect = RectF(20f, top, 172f, top + 152f)
        val path = maskPath(cfg.mask, rect)
        val photo = loadPhoto(c, p.photo)
        cv.save()
        cv.clipPath(path)
        if (photo != null) {
            val s = minOf(photo.width, photo.height)
            cv.drawBitmap(photo, Rect((photo.width - s) / 2, (photo.height - s) / 2, (photo.width + s) / 2, (photo.height + s) / 2), rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            cv.drawColor(Color.HSVToColor(floatArrayOf((Math.abs(p.name.hashCode()) % 360).toFloat(), 0.5f, 0.85f)))
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 68f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            cv.drawText(p.name.trim().take(1).uppercase(), rect.centerX(), rect.centerY() - (tp.ascent() + tp.descent()) / 2f, tp)
        }
        cv.restore()
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; color = cfg.ring })
        if (cfg.showName) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = cfg.textSize; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = cfg.textColor }
            cv.drawText(TextUtils.ellipsize(p.name, tp, 184f, TextUtils.TruncateAt.END).toString(), 96f, 222f, tp)
        }
        return bmp
    }
}
