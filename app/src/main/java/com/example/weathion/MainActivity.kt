package com.example.weathion

// INI CONTOH, PACKAGE LU JANGAN DIHAPUS
// package com.namalu.weathion

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.google.firebase.database.*
import java.util.Calendar
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var database: DatabaseReference
    private lateinit var lottieWeather: LottieAnimationView
    private lateinit var tvGreeting: TextView
    private lateinit var tvQuote: TextView
    private lateinit var tvStatusCuaca: TextView
    private lateinit var tvSuhu: TextView
    private lateinit var tvKelembapan: TextView
    private lateinit var tvLdr: TextView

    // Buat ngubah warna
    private lateinit var rootLayout: LinearLayout
    private lateinit var headerLayout: LinearLayout
    private lateinit var cardCuaca: CardView
    private lateinit var cardSuhu: CardView
    private lateinit var cardKelembapan: CardView
    private lateinit var cardLdr: CardView

    private var isNight = false

    private var wasRaining = false
    private var lastHujan: String = "Cerah"
    private var lastLdrRaw: Int = 0
    private var tvRealtimeLdrDialog: TextView? = null

    // Watchdog Timer buat ngecek ESP32 mati atau idup
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val espTimeoutRunnable = Runnable {
        // Kalo script ini jalan, berarti ESP32 udah 15 detik ga ngirim data = MATI
        goOffline("ESP_OFFLINE")
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Binding UI
        rootLayout = findViewById(R.id.rootLayout)
        headerLayout = findViewById(R.id.headerLayout)
        cardCuaca = findViewById(R.id.cardCuaca)
        cardSuhu = findViewById(R.id.cardSuhu)
        cardKelembapan = findViewById(R.id.cardKelembapan)
        cardLdr = findViewById(R.id.cardLdr)
        lottieWeather = findViewById(R.id.lottieWeather)
        tvGreeting = findViewById(R.id.tvGreeting)
        tvQuote = findViewById(R.id.tvQuote)
        tvStatusCuaca = findViewById(R.id.tvStatusCuaca)
        tvSuhu = findViewById(R.id.tvSuhu)
        tvKelembapan = findViewById(R.id.tvKelembapan)
        tvLdr = findViewById(R.id.tvLdr)

        database = FirebaseDatabase.getInstance().getReference("weather_station")

        checkConnectionAndStart()

        // --- SELIPAN STEP 2 TARUH SINI BGST ---
        // Minta izin notif otomatis pas app dibuka (Khusus Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Nyalain sistem timer 3 jam berkala
        scheduleNextAlarm(this)
    }

    private fun checkConnectionAndStart() {
        if (!isInternetAvailable(this)) {
            goOffline("HP_OFFLINE")
            return
        }

        // Tentukan Siang/Malam
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        isNight = hour >= 18 || hour < 6
        applyTheme()

        // Mulai dengerin Firebase
        sedotDataFirebase()
    }

    private fun applyTheme() {
        if (isNight) {
            // TEMA MALAM (Sesuai Palette Figma Baru)
            // Root-nya Lavender Grey, Card utamanya Shadow Grey biar kontras
            rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.lavender_grey))
            headerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sweet_salmon))

            // Bikin Status Bar (Jam/Baterai) warnanya nembus ngikutin Header
            window.statusBarColor = ContextCompat.getColor(this, R.color.sweet_salmon)

            cardCuaca.setCardBackgroundColor(ContextCompat.getColor(this, R.color.shadow_grey))

            // Kapsul data warnanya Shadow Grey
            val shadowGreyColor = ContextCompat.getColor(this, R.color.shadow_grey)
            cardSuhu.setCardBackgroundColor(shadowGreyColor)
            cardKelembapan.setCardBackgroundColor(shadowGreyColor)
            cardLdr.setCardBackgroundColor(shadowGreyColor)

            // Teks dalem kapsul diubah jadi putih (Bright Snow)
            tvStatusCuaca.setTextColor(ContextCompat.getColor(this, R.color.bright_snow))
            setTextColorForChildren(cardSuhu, R.color.bright_snow)
            setTextColorForChildren(cardKelembapan, R.color.bright_snow)
            setTextColorForChildren(cardLdr, R.color.bright_snow)

        } else {
            // TEMA SIANG
            rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bright_snow))
            headerLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light_gold))

            // Status bar ngikutin Header Siang
            window.statusBarColor = ContextCompat.getColor(this, R.color.light_gold)

            cardCuaca.setCardBackgroundColor(ContextCompat.getColor(this, R.color.lavender_grey))

            // Kapsul data warnanya Sweet Salmon
            val salmonColor = ContextCompat.getColor(this, R.color.sweet_salmon)
            cardSuhu.setCardBackgroundColor(salmonColor)
            cardKelembapan.setCardBackgroundColor(salmonColor)
            cardLdr.setCardBackgroundColor(salmonColor)

            // Teks dalem kapsul tetep item
            tvStatusCuaca.setTextColor(ContextCompat.getColor(this, R.color.bright_snow))
            setTextColorForChildren(cardSuhu, R.color.black_text)
            setTextColorForChildren(cardKelembapan, R.color.black_text)
            setTextColorForChildren(cardLdr, R.color.black_text)
        }
    }

    private fun setTextColorForChildren(card: CardView, colorRes: Int) {
        val root = card.getChildAt(0) as LinearLayout

        // Looping pinter biar kaga salah kasta lagi
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)

            // Kalo dia langsung ketemu Teks (Kaya di card Suhu & Kelembapan)
            if (child is TextView) { 
                child.setTextColor(ContextCompat.getColor(this, colorRes))
            }
            // Kalo dia nemu kotak lagi di dalem kotak (Kaya di card LDR Raw)
            else if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val innerChild = child.getChildAt(j)
                    if (innerChild is TextView) {
                        innerChild.setTextColor(ContextCompat.getColor(this, colorRes))
                    }
                }
            }
        }
    }

    private fun sedotDataFirebase() {
        // Mulai timer kematian ESP32. Kalo dlm 15 detik kaga dapet data, aplikasi pindah ke Disconnected
        resetWatchdog()

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Kalo dapet data, reset lagi timernya!
                resetWatchdog()

                if (snapshot.exists()) {
                    val suhu = snapshot.child("suhu").getValue(Double::class.java) ?: 0.0
                    val kelembaban = snapshot.child("kelembaban").getValue(Double::class.java) ?: 0.0
                    val ldrRaw = snapshot.child("cahaya").getValue(Int::class.java) ?: 0
                    val hujan = snapshot.child("hujan").getValue(String::class.java) ?: "Cerah"
                    val heartbeat = snapshot.child("heartbeat").getValue(Int::class.java) ?: 0

                    lastHujan = hujan
                    lastLdrRaw = ldrRaw

                    tvSuhu.text = "$suhu°C"
                    tvKelembapan.text = "$kelembaban%"
                    tvLdr.text = ldrRaw.toString()

                    // --- INI OBATNYA: Biar angka di dialog berubah REALTIME ---
                    tvRealtimeLdrDialog?.text = "Nilai LDR Realtime: $ldrRaw"

                    processWeatherLogic(hujan, ldrRaw)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun resetWatchdog() {
        watchdogHandler.removeCallbacks(espTimeoutRunnable)
        watchdogHandler.postDelayed(espTimeoutRunnable, 15000) // 15 Detik timeout
    }

    private fun processWeatherLogic(hujan: String, ldrRaw: Int) {
        // Ambil data settingan lu (defaultnya 1000, 2000, 3000)
        val prefs = getSharedPreferences("WeathionPrefs", Context.MODE_PRIVATE)
        val batasCerah = prefs.getInt("BATAS_CERAH", 1000)
        val batasSedikitBerawan = prefs.getInt("BATAS_SEDIKIT_BERAWAN", 2000)
        val batasBerawan = prefs.getInt("BATAS_BERAWAN", 3000)

        // 1. Prioritas Utama: UJAN
        // 1. Prioritas Utama: UJAN
        if (hujan == "Hujan") {
            // --- LOGIKA REALTIME NOTIF (ANTI SPAM) ---
            if (!wasRaining) {
                showRealtimeNotification("Hujan nih di Station!", "Mending buat indomie, atau siapin mantel!")
                wasRaining = true
            }

            if (isNight) {
                setWeatherState("Malam Hujan", "Hai, lagi ujan deras nih.", "Tarik selimut, waktunya rebahan nyeduh indomie!", R.raw.malamhujan)
            } else {
                setWeatherState("Siang Hujan", "Hai, lagi ujan deras nih.", "Mending di rumah aja, atau siap-siap sedia payung!", R.raw.sianghujan)
            }
            return
        } else {
            // Kalo udah kaga ujan, reset status biar ntar bisa nembak notif lagi pas ujan baru
            wasRaining = false
        }

        // 2. Kalo Ga Ujan, cek LDR pake BATAS YANG UDAH DI-SET
        if (isNight) {
            setWeatherState("Malam Cerah", "Hai, malam ini cerah", "Siap jalan-jalan cari angin malem!", R.raw.malamcerah)
        } else {
            when {
                ldrRaw <= batasCerah -> {
                    setWeatherState("Siang Cerah", "Hai, hari ini cerah", "Jangan lupa pakai sunscreen ya!", R.raw.siangcerah)
                }
                ldrRaw <= batasSedikitBerawan -> {
                    setWeatherState("Sedikit Berawan", "Hai, agak berawan nih", "Cuaca pas banget buat aktivitas!", R.raw.dikitberawan)
                }
                ldrRaw <= batasBerawan -> {
                    setWeatherState("Berawan", "Hai, langit berawan loh", "Matahari malu-malu nih di luar!.", R.raw.berawan)
                }
                else -> {
                    setWeatherState("Mendung", "Hai, langitnya mendung", "Bentar lagi ujan kayaknya, siap-siap!", R.raw.mendung)
                }
            }
        }
    }

    private fun setWeatherState(status: String, greeting: String, quote: String, lottieRes: Int) {
        tvStatusCuaca.text = status
        tvGreeting.text = greeting
        tvQuote.text = quote
        lottieWeather.setAnimation(lottieRes)
        lottieWeather.playAnimation()
    }

    private fun goOffline(type: String) {
        watchdogHandler.removeCallbacks(espTimeoutRunnable)
        val intent = Intent(this, DisconnectedActivity::class.java)
        intent.putExtra("ERROR_TYPE", type)
        startActivity(intent)
        finish() // Matiin layar ini
    }

    // Fungsi cek paketan HP user
    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogHandler.removeCallbacks(espTimeoutRunnable)
    }

    private fun showDevSettingsDialog() {
        val prefs = getSharedPreferences("WeathionPrefs", Context.MODE_PRIVATE)
        val batasCerah = prefs.getInt("BATAS_CERAH", 1000)
        val batasSedikitBerawan = prefs.getInt("BATAS_SEDIKIT_BERAWAN", 2000)
        val batasBerawan = prefs.getInt("BATAS_BERAWAN", 3000)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Weathion Dev Settings")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 30, 50, 30)

        // ========================================================
        // KOTAK TEXTVIEW REALTIME (Bakalan gerak terus angkanya)
        // ========================================================
        val tvRealtime = TextView(this)
        tvRealtime.text = "Nilai LDR Realtime: $lastLdrRaw"
        tvRealtime.textSize = 18f
        tvRealtime.setTypeface(null, android.graphics.Typeface.BOLD)
        tvRealtime.setTextColor(ContextCompat.getColor(this, R.color.black_text))
        tvRealtime.setPadding(0, 0, 0, 40)
        tvRealtimeLdrDialog = tvRealtime // Ikat ke variabel global biar bisa diakses Firebase
        layout.addView(tvRealtime)
        // ========================================================

        // 1. Input Cerah
        val tvCerah = TextView(this)
        tvCerah.text = "1. Batas Cerah ke Sedikit Berawan:"
        val inputCerah = EditText(this)
        inputCerah.inputType = InputType.TYPE_CLASS_NUMBER
        inputCerah.setText(batasCerah.toString())

        // 2. Input Sedikit Berawan
        val tvSedikit = TextView(this)
        tvSedikit.text = "2. Batas Sedikit Berawan ke Berawan:"
        tvSedikit.setPadding(0, 20, 0, 0)
        val inputSedikit = EditText(this)
        inputSedikit.inputType = InputType.TYPE_CLASS_NUMBER
        inputSedikit.setText(batasSedikitBerawan.toString())

        // 3. Input Berawan
        val tvBerawan = TextView(this)
        tvBerawan.text = "3. Batas Berawan ke Mendung:"
        tvBerawan.setPadding(0, 20, 0, 0)
        val inputBerawan = EditText(this)
        inputBerawan.inputType = InputType.TYPE_CLASS_NUMBER
        inputBerawan.setText(batasBerawan.toString())

        layout.addView(tvCerah)
        layout.addView(inputCerah)
        layout.addView(tvSedikit)
        layout.addView(inputSedikit)
        layout.addView(tvBerawan)
        layout.addView(inputBerawan)

        builder.setView(layout)

        builder.setPositiveButton("Simpan") { _, _ ->
            val valCerah = inputCerah.text.toString().toIntOrNull() ?: 1000
            val valSedikit = inputSedikit.text.toString().toIntOrNull() ?: 2000
            val valBerawan = inputBerawan.text.toString().toIntOrNull() ?: 3000

            if (valCerah < valSedikit && valSedikit < valBerawan && valBerawan <= 4095) {
                prefs.edit()
                    .putInt("BATAS_CERAH", valCerah)
                    .putInt("BATAS_SEDIKIT_BERAWAN", valSedikit)
                    .putInt("BATAS_BERAWAN", valBerawan)
                    .apply()
                Toast.makeText(this, "Threshold LDR sukses di-update!", Toast.LENGTH_SHORT).show()
                processWeatherLogic(lastHujan, lastLdrRaw)
            } else {
                Toast.makeText(this, "Input 0-4095 aja!.", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Batal") { dialog, _ -> dialog.cancel() }

        // Pas dialog ketutup (baik disave/batal/pencet luar), putus ikatan biar ga bocor RAM
        builder.setOnDismissListener {
            tvRealtimeLdrDialog = null
        }

        builder.show()
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // Rumus fisika g-force anjir
            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            // Kalo kekuatan kocokannya lebih dari 2.7 (Lumayan kenceng)
            if (gForce > 2.7f) {
                val now = System.currentTimeMillis()
                // Bikin jeda 1 detik biar dialognya kaga nongol berkali-kali ngebruk
                if (lastShakeTime + 1000 > now) {
                    return
                }
                lastShakeTime = now
                showDevSettingsDialog()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Diemin aja kaga kepake
    }

    // Wajib nyalain/matiin sensor pas aplikasi dibuka/ditutup biar kaga boros baterai
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Fungsi nembak notif realtime ujan
    private fun showRealtimeNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "weathion_realtime"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Notif Kilat", android.app.NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // --- INI KODINGAN BIAR BISA DIKLIK ---
        val tapIntent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, tapIntent, android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Biar notifnya ilang pas diklik
            .setContentIntent(pendingIntent) // <--- TANCEPIN DI SINI BGST
            .build()

        notificationManager.notify(1, notification)
    }

    // Pendeteksi jam matematika buat alarm berkala (6, 9, 12, 15, 18, 21, 24, 3)
    companion object {
        fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, WeatherAlarmReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val targetHours = arrayOf(0, 3, 6, 9, 12, 15, 18, 21)
            var nextHour = 0
            var daysToAdd = 0

            for (hour in targetHours) {
                if (hour > currentHour) {
                    nextHour = hour
                    break
                }
            }
            if (currentHour >= 21) {
                nextHour = 0
                daysToAdd = 1
            }

            calendar.set(Calendar.HOUR_OF_DAY, nextHour)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            if (daysToAdd > 0) calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        }

    }

}