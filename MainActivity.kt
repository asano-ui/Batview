package com.example.maindicator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            start()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) start()
        else {
            Toast.makeText(this, "Perlu kebenaran notifikasi", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun start() {
        startForegroundService(Intent(this, ChargeService::class.java))
        Toast.makeText(this, "Indikator mA aktif", Toast.LENGTH_SHORT).show()
        finish()
    }
}
