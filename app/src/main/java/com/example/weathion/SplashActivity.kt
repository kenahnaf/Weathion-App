package com.example.weathion

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Calendar

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val circleTop = findViewById<View>(R.id.circleTop)
        val circleBottom = findViewById<View>(R.id.circleBottom)
        val textLogo = findViewById<TextView>(R.id.textLogo)
        val splashRoot = findViewById<View>(R.id.splashRoot)

        // --- OBAT WARNING 1 & 2: Cara modern bikin Full Screen ---
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // LOGIKA SIANG MALAM
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 18 || hour < 6

        if (isNight) {
            // Background jadi Shadow Grey, teks jadi Putih
            splashRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.shadow_grey))
            textLogo.setTextColor(ContextCompat.getColor(this, R.color.bright_snow))

            circleTop.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sweet_salmon))
            circleBottom.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.lavender_grey))
        } else {
            // Background jadi Putih, teks jadi Hitam
            splashRoot.setBackgroundColor(ContextCompat.getColor(this, R.color.bright_snow))
            textLogo.setTextColor(ContextCompat.getColor(this, R.color.black_text))

            circleTop.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light_gold))
            circleBottom.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.lavender_grey))
        }

        // --- OBAT ANIMASI: Biar konsisten di semua layar HP ---
        // Kita gerakin 250dp ke tengah, tapi dikaliin sama density layar HP lu
        val moveDistance = 150 * resources.displayMetrics.density

        circleTop.animate().translationY(moveDistance).setDuration(1200).start()
        circleBottom.animate().translationY(-moveDistance).setDuration(1200).start()

        Handler(Looper.getMainLooper()).postDelayed({
            textLogo.animate().alpha(1f).setDuration(800).start()
        }, 500)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))

            // --- OBAT WARNING 3: Transisi modern khusus Android 14+ ---
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            finish()
        }, 3500)
    }
}