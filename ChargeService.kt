package com.example.maindicator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.math.abs

class ChargeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private lateinit var bm: BatteryManager

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val ch = NotificationChannel("ma", "Arus bateri", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
        startForeground(1, build(0, false))
        handler.post(tick)
    }

    private fun readMa(): Int {
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Kebanyakan telefon bagi microamp; sesetengah bagi milliamp
        return if (abs(raw) > 20000) raw / 1000 else raw
    }

    private fun update() {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val ma = readMa()
        nm.notify(1, build(ma, plugged))
    }

    private fun build(ma: Int, plugged: Boolean): Notification {
        val value = abs(ma)
        val label = if (plugged) "Mengecas" else "Guna bateri"
        return Notification.Builder(this, "ma")
            .setSmallIcon(numberIcon(value.toString()))
            .setContentTitle("$value mA")
            .setContentText(label)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    // Lukis nombor putih atas latar lutsinar; Android akan warnakan ikon status bar
    private fun numberIcon(text: String): Icon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = 90f
        }
        val w = p.measureText(text)
        if (w > size) p.textSize = 90f * size / w
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(text, size / 2f, y, p)
        return Icon.createWithBitmap(bmp)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
