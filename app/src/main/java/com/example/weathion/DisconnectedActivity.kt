package com.example.weathion

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import java.util.Calendar

class DisconnectedActivity : AppCompatActivity() {

    private var errorType = "HP_OFFLINE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disconnected)

        val rootLayoutOff = findViewById<LinearLayout>(R.id.rootLayoutOff)
        val headerLayoutOff = findViewById<LinearLayout>(R.id.headerLayoutOff)
        val cardAnimOff = findViewById<CardView>(R.id.cardAnimOff)
        val cardBottomOff = findViewById<CardView>(R.id.cardBottomOff)
        val tvDescOff = findViewById<TextView>(R.id.tvDescOff)
        val tvBottomOff = findViewById<TextView>(R.id.tvBottomOff)
        val lottieOffline = findViewById<LottieAnimationView>(R.id.lottieOffline)

        // Ambil data error dari MainActivity
        errorType = intent.getStringExtra("ERROR_TYPE") ?: "HP_OFFLINE"

        // Fungsi buat nampilin UI Error Awal
        fun setupErrorUI() {
            cardBottomOff.visibility = View.VISIBLE // Munculin lagi kapsul pencetannya
            if (errorType == "HP_OFFLINE") {
                tvDescOff.text = "Kamu belum terhubung ke internet loh!"
                tvBottomOff.text = "Sambungin smartphone Ke jaringan dulu ya!\n(Klik untuk Refresh)"
                lottieOffline.setAnimation(R.raw.nowifi)
            } else {
                tvDescOff.text = "Weathion Station belum terkoneksi nih!"
                tvBottomOff.text = "Pastiin koneksi power dan jaringan tersambung dengan baik ya!\n(Klik untuk Refresh)"
                lottieOffline.setAnimation(R.raw.nokucing)
            }
            lottieOffline.playAnimation()
        }

        // Panggil pas pertama buka
        setupErrorUI()

        // TEMA SIANG/MALAM OTOMATIS
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour >= 18 || hour < 6

        if (isNight) {
            rootLayoutOff.setBackgroundColor(ContextCompat.getColor(this, R.color.lavender_grey))
            headerLayoutOff.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sweet_salmon))
            window.statusBarColor = ContextCompat.getColor(this, R.color.sweet_salmon)
            cardAnimOff.setCardBackgroundColor(ContextCompat.getColor(this, R.color.black_text))

            val shadowGreyColor = ContextCompat.getColor(this, R.color.shadow_grey)
            cardBottomOff.setCardBackgroundColor(shadowGreyColor)
            tvBottomOff.setTextColor(ContextCompat.getColor(this, R.color.bright_snow))
        } else {
            rootLayoutOff.setBackgroundColor(ContextCompat.getColor(this, R.color.bright_snow))
            headerLayoutOff.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light_gold))
            window.statusBarColor = ContextCompat.getColor(this, R.color.light_gold)
            cardAnimOff.setCardBackgroundColor(ContextCompat.getColor(this, R.color.lavender_grey))

            val salmonColor = ContextCompat.getColor(this, R.color.sweet_salmon)
            cardBottomOff.setCardBackgroundColor(salmonColor)
            tvBottomOff.setTextColor(ContextCompat.getColor(this, R.color.black_text))
        }

        // LOGIKA REFRESH PINTAR
        cardBottomOff.setOnClickListener {
            // 1. Umpetin kapsul dan ganti teks jadi loading
            cardBottomOff.visibility = View.GONE
            tvDescOff.text = "Menghubungkan ulang..."

            // Wajib lu tambahin file anim_loading.json ke folder raw ntar!
            lottieOffline.setAnimation(R.raw.loading)
            lottieOffline.playAnimation()

            // 2. Kasih delay 2 detik biar animasi loadingnya estetik keliatan muter dulu
            Handler(Looper.getMainLooper()).postDelayed({
                if (isInternetAvailable(this)) {
                    // Kalo HP udah dapet sinyal, langsung gas ke MainActivity.
                    // Ntar MainActivity yang ngurusin cek koneksi ESP-nya
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                } else {
                    // Kalo ternyata masih mampus jaringannya, balikin lagi UI ke semula
                    errorType = "HP_OFFLINE"
                    setupErrorUI()
                }
            }, 4000) // 2000 milidetik = 2 detik
        }
    }

    // Fungsi cek paketan HP (Sama kek di MainActivity)
    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}