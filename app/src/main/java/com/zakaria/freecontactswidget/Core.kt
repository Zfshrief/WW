package com.zakaria.freecontactswidget

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject

data class Person(val name: String, val number: String, val photo: String?)

class Cfg {
    var people: MutableList<Person> = mutableListOf()
    var mask: Int = 0            // 0 circle, 1 rounded, 2 square
    var ring: Int = 0xFF6C63FF.toInt()
    var showName: Boolean = true
    var action: Int = 0          // 0 call, 1 sms, 2 whatsapp
    var bgAlpha: Int = 200
}

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun save(c: Context, id: Int, cfg: Cfg) {
        val arr = JSONArray()
        for (p in cfg.people) {
            arr.put(JSONObject().put("n", p.name).put("p", p.number).put("ph", p.photo ?: ""))
        }
        val o = JSONObject()
            .put("people", arr)
            .put("mask", cfg.mask)
            .put("ring", cfg.ring)
            .put("showName", cfg.showName)
            .put("action", cfg.action)
            .put("bgAlpha", cfg.bgAlpha)
        prefs(c).edit().putString("w$id", o.toString()).apply()
    }

    fun load(c: Context, id: Int): Cfg {
        val cfg = Cfg()
        val s = prefs(c).getString("w$id", null) ?: return cfg
        try {
            val o = JSONObject(s)
            val arr = o.getJSONArray("people")
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val ph = x.optString("ph", "")
                cfg.people.add(Person(x.getString("n"), x.getString("p"), if (ph.isEmpty()) null else ph))
            }
            cfg.mask = o.optInt("mask", 0)
            cfg.ring = o.optInt("ring", 0xFF6C63FF.toInt())
            cfg.showName = o.optBoolean("showName", true)
            cfg.action = o.optInt("action", 0)
            cfg.bgAlpha = o.optInt("bgAlpha", 200)
        } catch (e: Exception) {
            // keep defaults
        }
        return cfg
    }

    fun delete(c: Context, id: Int) {
        prefs(c).edit().remove("w$id").apply()
    }
}

object Act {
    private fun waNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (raw.startsWith("+")) return digits
        if (digits.startsWith("00")) return digits.substring(2)
        if (digits.startsWith("01") && digits.length == 11) return "20" + digits.substring(1)
        return digits
    }

    fun intent(c: Context, p: Person, action: Int): Intent {
        val num = p.number.filter { it.isDigit() || it == '+' }
        val i = when (action) {
            1 -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
            2 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waNumber(p.number)))
            else -> {
                val granted = c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num"))
            }
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return i
    }

    fun pendingActivity(c: Context, code: Int, i: Intent): PendingIntent =
        PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
        } catch (e: Exception) {
            null
        }
    }

    fun tile(c: Context, p: Person, cfg: Cfg): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val d = 156f
        val left = (W - d) / 2f
        val top = if (cfg.showName) 10f else (H - d) / 2f
        val rect = RectF(left, top, left + d, top + d)

        val path = Path()
        when (cfg.mask) {
            0 -> path.addOval(rect, Path.Direction.CW)
            1 -> path.addRoundRect(rect, 40f, 40f, Path.Direction.CW)
            else -> path.addRoundRect(rect, 8f, 8f, Path.Direction.CW)
        }

        val photo = loadPhoto(c, p.photo)
        cv.save()
        cv.clipPath(path)
        if (photo != null) {
            val s = minOf(photo.width, photo.height)
            val src = Rect((photo.width - s) / 2, (photo.height - s) / 2, (photo.width + s) / 2, (photo.height + s) / 2)
            cv.drawBitmap(photo, src, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            val hue = (Math.abs(p.name.hashCode()) % 360).toFloat()
            cv.drawColor(Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.85f)))
            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
            tp.color = Color.WHITE
            tp.textSize = 72f
            tp.textAlign = Paint.Align.CENTER
            tp.typeface = Typeface.DEFAULT_BOLD
            val letter = p.name.trim().take(1).uppercase()
            cv.drawText(letter, rect.centerX(), rect.centerY() - (tp.ascent() + tp.descent()) / 2f, tp)
        }
        cv.restore()

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = 8f
        ringPaint.color = cfg.ring
        cv.drawPath(path, ringPaint)

        if (cfg.showName) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
            tp.textSize = 30f
            tp.textAlign = Paint.Align.CENTER
            tp.typeface = Typeface.DEFAULT_BOLD
            tp.color = if (cfg.bgAlpha < 100) Color.WHITE else 0xFF222222.toInt()
            val t = TextUtils.ellipsize(p.name, tp, (W - 8).toFloat(), TextUtils.TruncateAt.END).toString()
            cv.drawText(t, W / 2f, H - 22f, tp)
        }
        return bmp
    }
}
