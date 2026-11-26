package com.example.maindicator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
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
import java.util.Locale
import kotlin.math.abs

class ChargeService : Service() {

    companion object {
        const val LOW_MA = 500        // bawah nilai ni dikira charger lemah
        const val LOW_SECONDS = 10    // mesti kekal rendah sekian saat baru beramaran
        const val WARN_BELOW_PERCENT = 90 // lepas 90% arus turun memang normal
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private lateinit var bm: BatteryManager
    private var lowCount = 0
    private var warned = false

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
        nm.createNotificationChannel(
            NotificationChannel("ma", "Arus bateri", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel("warn", "Amaran charger", NotificationManager.IMPORTANCE_HIGH)
        )
        startForeground(1, build(0, "Memulakan..."))
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun readMa(): Int {
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Kebanyakan telefon bagi microamp; sesetengah bagi milliamp
        return if (abs(raw) > 20000) raw / 1000 else raw
    }

    private fun update() {
        val st = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val plugged = st.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val status = st.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val level = st.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = st.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = level * 100 / scale
        val mv = st.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val temp = st.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0

        val ma = abs(readMa())
        val watt = ma * mv / 1_000_000.0

        val state = when {
            !plugged -> "Guna bateri"
            status == BatteryManager.BATTERY_STATUS_FULL -> "Penuh"
            else -> "Mengecas"
        }
        val detail = String.format(
            Locale.US, "%s · %.1f W · %.2f V · %.1f°C · %d%%",
            state, watt, mv / 1000.0, temp, percent
        )
        nm.notify(1, build(ma, detail))

        checkWeakCharger(
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
            percent = percent,
            ma = ma
        )
    }

    private fun checkWeakCharger(charging: Boolean, percent: Int, ma: Int) {
        val weak = charging && percent < WARN_BELOW_PERCENT && ma < LOW_MA
        if (weak) {
            lowCount++
            if (lowCount >= LOW_SECONDS && !warned) {
                warned = true
                val n = Notification.Builder(this, "warn")
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("Charger atau kabel lemah?")
                    .setContentText("Arus hanya $ma mA. Cuba tukar kabel atau charger.")
                    .setAutoCancel(true)
                    .build()
                nm.notify(2, n)
            }
        } else {
            lowCount = 0
            if (!charging || ma >= LOW_MA) warned = false
        }
    }

    private fun build(ma: Int, detail: String): Notification =
        Notification.Builder(this, "ma")
            .setSmallIcon(numberIcon(ma.toString()))
            .setContentTitle("$ma mA")
            .setContentText(detail)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

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

// Hidupkan indikator sendiri selepas telefon restart
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, ChargeService::class.java))
        }
    }
}
