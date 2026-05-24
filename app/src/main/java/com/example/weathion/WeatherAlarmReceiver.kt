package com.example.weathion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class WeatherAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val database = FirebaseDatabase.getInstance().getReference("weather_station")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val suhu = snapshot.child("suhu").getValue(Double::class.java) ?: 0.0
                    val kelembaban = snapshot.child("kelembaban").getValue(Double::class.java) ?: 0.0
                    val ldrRaw = snapshot.child("cahaya").getValue(Int::class.java) ?: 0
                    val hujan = snapshot.child("hujan").getValue(String::class.java) ?: "Cerah"

                    // Baca settingan LDR dari memori
                    val prefs = context.getSharedPreferences("WeathionPrefs", Context.MODE_PRIVATE)
                    val batasCerah = prefs.getInt("BATAS_CERAH", 1000)
                    val batasSedikitBerawan = prefs.getInt("BATAS_SEDIKIT_BERAWAN", 2000)
                    val batasBerawan = prefs.getInt("BATAS_BERAWAN", 3000)

                    // Terjemahin angka LDR jadi kata-kata awan
                    val kondisiAwan = when {
                        ldrRaw <= batasCerah -> "Cerah Terang"
                        ldrRaw <= batasSedikitBerawan -> "Sedikit Berawan"
                        ldrRaw <= batasBerawan -> "Berawan"
                        else -> "Mendung"
                    }

                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val isNight = hour >= 18 || hour < 6

                    // Title tetep Suhu & Kelembapan (sesuai request)
                    val titleText = "🌡️ Suhu: $suhu°C | 💧 Hum: $kelembaban%"
                    val messageText: String

                    // Logika Message Siang vs Malam
                    if (!isNight) {
                        // Siang: Cerah/Hujan + Status Awan
                        val statusHujan = if (hujan == "Hujan") "Lagi hujan nih!" else "Ga hujan, siap aktivitas!"
                        messageText = "Status: $statusHujan | Awan: $kondisiAwan"
                    } else {
                        // Malam: Hujan atau Kaga aja
                        messageText = if (hujan == "Hujan") "Lagi hujan nih, enak buat rebahan!" else "Malam cerah, masa ga ngopi?"
                    }

                    showNotification(context, titleText, messageText)
                }
                MainActivity.scheduleNextAlarm(context)
            }
            override fun onCancelled(error: DatabaseError) {
                MainActivity.scheduleNextAlarm(context)
            }
        })
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "weathion_periodic"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Laporan Berkala", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        // INI OBAT BIAR NOTIF BISA DIKLIK MASUK APP
        val tapIntent = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 2, tapIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // Panggil Intent-nya di sini
            .build()

        manager.notify(2, notification)
    }
}