package com.example.weathion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class RainService : Service() {

    private var wasRaining = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Bikin notif kecil penanda kalo satpam Weathion lagi jalan di background
        val channelId = "weathion_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Weathion Background", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // Kalo dipencet buka aplikasi
        val tapIntent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Weathion Aktif")
            .setContentText("Memantau cuaca di latar belakang...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(88, notification) // Bikin aplikasi lu IMMORTAL

        // Pantau Firebase dari sini!
        val database = FirebaseDatabase.getInstance().getReference("weather_station")
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val hujan = snapshot.child("hujan").getValue(String::class.java) ?: "Cerah"

                    // Trigger Hujan Realtime
                    if (hujan == "Hujan" && !wasRaining) {
                        sendRealtimeRainAlert()
                        wasRaining = true
                    } else if (hujan != "Hujan") {
                        wasRaining = false
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun sendRealtimeRainAlert() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "weathion_realtime_bg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notif Kilat", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        // Bikin Notif bisa diklik
        val tapIntent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 1, tapIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ WARNING WEATHION!")
            .setContentText("Stasiun mendeteksi HUJAN! Angkat jemuran bos!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // INI OBAT BIAR BISA DIKLIK MASUK APP
            .build()

        manager.notify(99, notification)
    }
}