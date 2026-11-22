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

data class Person(
    val name: String,
    val number: String,
    val photo: String?,
    var action: Int = -1, // -1 = استخدام إعداد الويدجت العام، أو رقم إجراء مخصص
    var simSlot: Int = 0  // 0 = تلقائي، 1 = شريحة 1، 2 = شريحة 2
)

class Cfg {
    var title: String = "مجموعتي"
    var widgetMode: Int = 0      // 0 = شبكة جهات، 1 = فولدر منبثق، 2 = جهة واحدة مع أزرار
    var people: MutableList<Person> = mutableListOf()
    var mask: Int = 0            // 0 دائرة، 1 مربع مدور، 2 مربع، 3 سداسي، 4 قلب
    var ring: Int = 0xFF6C63FF.toInt()
    var bgColor: Int = 0xFFFFFFFF.toInt()
    var textColor: Int = 0xFF222222.toInt()
    var textSize: Float = 28f
    var showName: Boolean = true
    var defaultAction: Int = 0   // 0 اتصال، 1 SMS، 2 واتساب، 3 تيليجرام، 4 ماسنجر، 5 فايبر، 6 إيميل، 7 ملف الجهة، 8 سيجنال، 9 سناب شات
    var bgAlpha: Int = 220
}

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("widgets_v2", Context.MODE_PRIVATE)

    fun save(c: Context, id: Int, cfg: Cfg) {
        val arr = JSONArray()
        for (p in cfg.people) {
            arr.put(
                JSONObject()
                    .put("n", p.name)
                    .put("p", p.number)
                    .put("ph", p.photo ?: "")
                    .put("act", p.action)
                    .put("sim", p.simSlot)
            )
        }
        val o = JSONObject()
            .put("title", cfg.title)
            .put("mode", cfg.widgetMode)
            .put("people", arr)
            .put("mask", cfg.mask)
            .put("ring", cfg.ring)
            .put("bgColor", cfg.bgColor)
            .put("textColor", cfg.textColor)
            .put("textSize", cfg.textSize.toDouble())
            .put("showName", cfg.showName)
            .put("action", cfg.defaultAction)
            .put("bgAlpha", cfg.bgAlpha)
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
                cfg.people.add(
                    Person(
                        x.getString("n"),
                        x.getString("p"),
                        if (ph.isEmpty()) null else ph,
                        x.optInt("act", -1),
                        x.optInt("sim", 0)
                    )
                )
            }
            cfg.mask = o.optInt("mask", 0)
            cfg.ring = o.optInt("ring", 0xFF6C63FF.toInt())
            cfg.bgColor = o.optInt("bgColor", 0xFFFFFFFF.toInt())
            cfg.textColor = o.optInt("textColor", 0xFF222222.toInt())
            cfg.textSize = o.optDouble("textSize", 28.0).toFloat()
            cfg.showName = o.optBoolean("showName", true)
            cfg.defaultAction = o.optInt("action", 0)
            cfg.bgAlpha = o.optInt("bgAlpha", 220)
        } catch (_: Exception) {
        }
        return cfg
    }

    fun delete(c: Context, id: Int) {
        prefs(c).edit().remove("w$id").apply()
    }
}

object Act {
    val ACTION_NAMES = arrayOf(
        "اتصال مباشر",
        "رسالة SMS",
        "واتساب (WhatsApp)",
        "تيليجرام (Telegram)",
        "ماسنجر (Messenger)",
        "فايبر (Viber)",
        "بريد إلكتروني (Email)",
        "عرض ملف جهة الاتصال",
        "سيجنال (Signal)",
        "تطبيق الهاتف (لوحة الاتصال)"
    )

    private fun intlNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (raw.startsWith("+")) return digits
        if (digits.startsWith("00")) return digits.substring(2)
        if (digits.startsWith("01") && digits.length == 11) return "20" + digits.substring(1)
        return digits
    }

    fun intent(c: Context, p: Person, fallbackAction: Int): Intent {
        val action = if (p.action >= 0) p.action else fallbackAction
        val num = p.number.filter { it.isDigit() || it == '+' }
        val intl = intlNumber(p.number)

        val i = when (action) {
            1 -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
            2 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl"))
            3 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$intl"))
            4 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/"))
            5 -> Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=%2B$intl"))
            6 -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            7 -> Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num)))
            8 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$intl"))
            9 -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
            else -> {
                val granted = c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val callIntent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                if (p.simSlot in 1..2 && granted) {
                    try {
                        val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                        val accounts = tm.callCapablePhoneAccounts
                        if (accounts.size >= p.simSlot) {
                            callIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts[p.simSlot - 1])
                        }
                    } catch (_: Exception) {
                    }
                }
                callIntent
            }
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return i
    }

    fun pendingActivity(c: Context, code: Int, i: Intent): PendingIntent =
        PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun createPinnedShortcut(c: Context, title: String, iconBmp: Bitmap, targetIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sm = c.getSystemService(ShortcutManager::class.java)
            if (sm != null && sm.isRequestPinShortcutSupported) {
                val info = ShortcutInfo.Builder(c, "sc_${System.currentTimeMillis()}")
                    .setShortLabel(title.ifEmpty { "جهة اتصال" })
                    .setIcon(Icon.createWithBitmap(iconBmp))
                    .setIntent(targetIntent.setAction(Intent.ACTION_VIEW))
                    .build()
                sm.requestPinShortcut(info, null)
            }
        }
    }
}

object Render {
    private const val W = 192
    private const val H = 240

    private fun loadPhoto(c: Context, uri: String?): Bitmap? {
        if (uri.isNullOrEmpty()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 400 && bounds.outHeight / (sample * 2) >= 400) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            c.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildMaskPath(mask: Int, rect: RectF): Path {
        val path = Path()
        when (mask) {
            0 -> path.addOval(rect, Path.Direction.CW)
            1 -> path.addRoundRect(rect, 38f, 38f, Path.Direction.CW)
            2 -> path.addRoundRect(rect, 6f, 6f, Path.Direction.CW)
            3 -> { // سداسي
                val cx = rect.centerX()
                val cy = rect.centerY()
                val r = rect.width() / 2f
                for (i in 0 until 6) {
                    val angle = Math.toRadians((60 * i - 30).toDouble())
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            else -> { // قلب
                val w = rect.width()
                val h = rect.height()
                val l = rect.left
                val t = rect.top
                path.moveTo(l + w / 2f, t + h * 0.92f)
                path.cubicTo(l - w * 0.1f, t + h * 0.55f, l + w * 0.05f, t + h * 0.05f, l + w / 2f, t + h * 0.30f)
                path.cubicTo(l + w * 0.95f, t + h * 0.05f, l + w * 1.1f, t + h * 0.55f, l + w / 2f, t + h * 0.92f)
                path.
