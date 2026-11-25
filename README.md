# JARVIS - Advanced Personal AI Assistant

## Proje Özeti
JARVIS, Iron Man filmindeki karakterine ilham alınan, Android telefonlarda çalışan gelişmiş, modüler ve kişiselleştirilebilir bir yapay zekâ ve telefon asistanı uygulamasıdır.

## Paket Bilgileri
- **Uygulama Adı**: JARVIS
- **Paket adı**: com.jarvis.assistant
- **Dil**: Kotlin
- **Arayüz**: Jetpack Compose
- **Tasarım**: Material 3 (Siyah/koyu tema, neon mavi vurgular)
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: En son stabil Android SDK

## Mimari
Uygulama modüler MVVM (Model-View-ViewModel) mimarisi üzerine kurulu aşağıdaki modüllerden oluşur:

### Core Modüller
1. **AI Core** - Çoklu AI sağlayıcı desteği (OpenAI, Gemini, OpenRouter, Groq, HuggingFace, Custom OpenAI-compatible, Offline modeller)
2. **AI Router** - Sağlayıcı yönlendirme ve fallback mekanizması
3. **Chat Module** - Doğal Türkçe sohbet, konuşma geçmişi, markdown desteği
4. **Voice Module** - Speech Recognition + Text-to-Speech (Türkçe destek)
5. **Memory Module** - Room veritabanı ile kalıcı yerel veri saklama
6. **Agent System** - 6 ajan türü: Research, Developer, Planner, Phone, Learning, Creative
7. **Phone Automation** - Uygulama kontrolü, ayarlar, Wi-Fi/Bluetooth, alarm, ses/parlaktlık
8. **Notification Manager** - Bildirim algılama ve yönetim
9. **File Manager** - Dosya görüntüleme, PDF okuma, OCR, analiz
10. **Task Manager** - Hatırlatıcılar, alarm, görev takibi, takvim entegrasyonu
11. **Security Manager** - İzinler, gizlilik kontrolü, API anahtarı yönetimi
12. **Settings** - DataStore ile ayarlar yönetimi

### Teknik Stack
- **Dil**: Kotlin 2.0.21
- **UI Toolkit**: Jetpack Compose + Material 3
- **Veritabanı**: Room (Room veritabanı)
- **Dependency Injection**: Hilt
- **Network**: Retrofit + OkHttp
- **Mimarisi**: MVVM + Repository pattern
- **Build System**: Gradle Wrapper (v8.9)

## Özellikler (Mevcut)

### ✅ Çalışan Özellikler
- **Dashboard Ekranı**: JARVIS ana kontrol merkezi (siyah/koyu tema, neon mavi vurgular)
- **Mikrofon Girişi**: Ses tanıma desteği (SpeechRecognizer)
- **Metin Chat**: Sohbet girişi ve yanıt gösterimi
- **TTS (Text-to-Speech)**: Sesli cevap verme
- **Temel Offline Komutlar**: saat, tarih, merhaba, alarm
- **Tema Sistemi**: Açık ve koyu tema geçişleri
- **Holografik/Animasyon efektleri**: Compose animasyonları

### 📁 Proje Yapısı
```
app/src/main/java/com/jarvis/assistant/
├── JarvisApplication.kt        # Hilt Application sınıfı
├── MainActivity.kt             # Ana aktivite (Dashboard)
├── feature/
│   └── dashboard/
│       └── DashboardScreen.kt  # Dashboard UI
├── core/
│   ├── ai/                     # AI sağlayıcı ve router
│   │   ├── model/              # AiMessage, AiRequest, AiResponse, AiProviderType
│   │   ├── provider/           # OpenAiProvider, GeminiProvider, OfflineProvider
│   │   ├── router/             # AiRouter
│   │   └── offline/            # OfflineCommandHandler
│   ├── common/                 # Constants, model (Result, UiState), util
│   │   ├── constants/          # AppConstants, AiConstants
│   │   ├── model/              # Result<>, UiState<>
│   │   └── util/               # DispatcherProvider, TimeUtils
│   ├── ui/                     # Tema (Color, Type, Theme)
│   ├── voice/                  # SpeechRecognizerManager, TtsManager
│   ├── data/                   # Room entities, DAO, AppDatabase
│   │   ├── entity/             # ChatMessageEntity, MemoryEntity, TaskEntity
│   │   ├── dao/                # ChatMessageDao, MemoryDao, TaskDao
│   │   └── AppDatabase.kt
│   └── network/                # API servisleri ve DTO'lar
│       ├── api/                # OpenAiService, GeminiService
│       └── dto/                # OpenAiDtos, GeminiDtos
│── repository/                 # ChatRepository
└── feature/                    # Ek ekranlar (Chat, Settings, Memory, Agents vb.)
```

## Kurulum Talimatları

### 1. Projeyi İndirme
```bash
# Proje klasörüne git
cd /workspace

# Gradle wrapper'ı çalıştır (ilk çalışma Gradle'i indirir)
export GRADLE_USER_HOME=/workspace/.gradle
./gradlew :app:assembleDebug
```

### 2. Uygulama Derlemesi
Debug APK şu konumda çıkar:
```
/workspace/app/build/outputs/debug/app-debug.apk
```

### 3. Uygulama Özellikleri
- Uygulamayı ilk açtığında JARVIS dashboard ekranı görünecek
- Mikrofon butonuna basılı tutarak sesli komut verilebilir
- Metin girişi ile de sohbet possible
- Saat/tarih, hava durumu gibi bilgiler gösterilir
- Temel offline komutlar çalışır (internet olmadan)
- AI sağlayıcısı eklendiğinde internet üzerinden gelişmiş cevaplar

### 4. Geliştirme Ortamı Kurulumu
- Android Studio Arctic Fox veya newer
- JDK 17
- Android SDK (API 26 - 35)
- 4GB+ RAM önerilir

## Kullanılan Teknolojiler
- Kotlin 2.0.21
- Jetpack Compose
- Material Design 3
- Room Database
- Hilt Dependency Injection
- Retrofit & OkHttp
- Coroutines & Flow
- Speech Recognition API
- Text-to-Speech API
- Gradle 8.9

## Güvenlik & Gizlilik
- API anahtarları güvenli şekilde saklanmalı (SharedPreferences/EncryptedStorage)
- Kullanıcı onayı olmadan hassas işlem yapılmamalı
- Network security config, cleartext trafiği engeller
- ProGuard kuralları uygulanmalı
- Hassas izinler (MICROPHONE, NOTIFICATIONS) kullanıcı onayıyla

## Gelecek Gelişim Vaiçeleri
- OpenAI/Gemini API entegrasyonu
- Gelişmiş sesli asistan (sürekli dinleme)
- Hafıza sistemi (kullanıcı tercihleri, önceki konuşmalar)
- Ajan sistem (Research, Developer, Planner vb.)
- Görsel zekâ (OCR, fotoğraf analiz)
- Video ve ses işleme
- Takvim ve hatırlatıcı entegrasyonu
- Dosya yönetici ve bulut senkronizasyonu