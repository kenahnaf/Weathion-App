#include <Arduino.h>

// ================= STEP 1: WAJIB DEFINE INI DI PALING ATAS =================
#define ENABLE_NO_AUTH   // Biar bisa konek tanpa email/password (Test Mode)
#define ENABLE_DATABASE

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <FirebaseClient.h>
#include <DHT.h>
// ================= KONFIGURASI JARINGAN & FIREBASE =================
#define WIFI_SSID "Weathion"       
#define WIFI_PASSWORD "12345678"
#define DATABASE_URL "https://weathion-fd049-default-rtdb.asia-southeast1.firebasedatabase.app/" 
// ====================================================================

// Definisi Pin Sensor
#define LDR_PIN 34    // Wajib ADC1 (Pin D34)
#define DHTPIN 4      // Pin D4
#define DHTTYPE DHT11
#define RAIN_PIN 5    // Pin D5

DHT dht(DHTPIN, DHTTYPE);

int heartbeat = 0; // Buat ngecek loop jalan apa nggak (optional) 

// --- SETUP FIREBASECLIENT V2 (ESP32 - SEAMLESS MODE) ---
NoAuth noAuth;
FirebaseApp app;
WiFiClientSecure ssl_client;

// INI YANG BENER! Gak pake DefaultNetwork anjing wkwk
using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);

RealtimeDatabase Database;

unsigned long timerSebelumnya = 0; 
const long intervalKirim = 5000; 

// Fungsi Callback buat nangkep error/sukses dari Firebase
void processData(AsyncResult &aResult) {
  if (!aResult.isResult()) return;

  if (aResult.isError()) {
    Firebase.printf("Error task: %s, msg: %s, code: %d\n", aResult.uid().c_str(), aResult.error().message().c_str(), aResult.error().code());
  }
  if (aResult.available()) {
    Firebase.printf("Task Sukses: %s, payload: %s\n", aResult.uid().c_str(), aResult.c_str());
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000); 
  
  dht.begin();
  pinMode(RAIN_PIN, INPUT);

  // Konek WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(300);
  }
  Serial.println("\nConnected with IP: ");
  Serial.println(WiFi.localIP());

  // Wajib biar ESP32 mau tembus HTTPS/SSL Firebase
  ssl_client.setInsecure();

  // Inisialisasi Firebase
  initializeApp(aClient, app, getAuth(noAuth), processData, "authTask");
  app.getApp<RealtimeDatabase>(Database);
  Database.url(DATABASE_URL);
}

void loop() {
  // Biar library Firebase jalan di background
  app.loop(); 

  unsigned long waktuSekarang = millis();
  
  // Pastiin udah authenticated & waktunya ngirim
  if (app.ready() && (waktuSekarang - timerSebelumnya >= intervalKirim)) {
    timerSebelumnya = waktuSekarang; 
    
    // BACA SENSOR (Raw LDR mentok 4095 di ESP32)
    int rawLDR = analogRead(LDR_PIN);
    float kelembaban = dht.readHumidity();
    float suhu = dht.readTemperature();
    int statusHujan = digitalRead(RAIN_PIN);
    String ujanString = (statusHujan == 0) ? "Hujan" : "Cerah";

    // Kalo sensor DHT ngaco, lewatin
    if (isnan(kelembaban) || isnan(suhu)) {
      Serial.println("DHT11 error (NaN). Skip kirim...");
      return; 
    }

    Serial.println("\n[NEMBAK DATA KE FIREBASE...]");

    heartbeat++;


    // Kirim data
    Database.set<float>(aClient, "/weather_station/suhu", suhu, processData, "SetSuhu");
    Database.set<float>(aClient, "/weather_station/kelembaban", kelembaban, processData, "SetKelembaban");
    Database.set<int>(aClient, "/weather_station/cahaya", rawLDR, processData, "SetCahaya");
    Database.set<String>(aClient, "/weather_station/hujan", ujanString, processData, "SetHujan");
    Database.set<int>(aClient, "/weather_station/heartbeat", heartbeat, processData, "SetHeartbeat");
  }
}
