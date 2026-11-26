# JARVIS Android

JARVIS, Android için Kotlin ve Jetpack Compose ile geliştirilen, Türkçe sesli/metin etkileşimi olan kişisel asistan uygulamasıdır. Arayüz Stark/Iron Man HUD estetiğinden esinlenir.

## Paket ve derleme

- Paket: `com.jarvis.assistant`
- Dil: Kotlin
- Arayüz: Jetpack Compose + Material 3
- Minimum Android: API 26
- Compile/Target SDK: 35
- JDK: 17
- CI: GitHub Actions

GitHub Actions her başarılı derlemede debug APK üretir, dosyanın varlığını doğrular, test raporunu ve APK artifact'ini yükler ve APK'yı ön sürüm GitHub Release olarak arşivler. CI derlemelerinde `versionCode` ve `versionName` otomatik artırılır. Debug imzalı APK, Play Store dağıtım imzası değildir.

## Gerçekten uygulanmış özellikler

### Sohbet ve AI bağlantısı
- Metin sohbeti ve Room üzerinde kalıcı sohbet geçmişi.
- OpenAI uyumlu `/chat/completions` API uç noktasına birincil ve yedek sağlayıcı bağlantısı.
- OpenRouter, Groq ve Gemini'nin OpenAI uyumlu uç noktaları için hazır adres/model profilleri.
- Birincil sağlayıcı başarısız olursa yapılandırılmış yedek sağlayıcıya geçiş.
- Ayar ekranında, henüz kaydedilmemiş form değerleriyle de gerçek API bağlantı testi.
- API anahtarları Android cihazındaki şifreli SharedPreferences alanında tutulur; istekler HTTPS ile doğrudan sağlayıcıya gider.

**Sınırlar:** Bu sürümde OpenAI uyumlu API akışı uygulamanın çalışan sohbet yoludur. Ayrı `AiRouter`, `OpenAiProvider` ve `GeminiProvider` sınıfları tam uygulama akışına bağlanmış çok sağlayıcılı bir DI sistemi değildir. Hugging Face ve yerel LLM bağlantısı henüz tamamlanmamıştır. Ücretsiz kota veya kesintisiz ücretsiz kullanım garantisi yoktur.

### Ses
- Mikrofon düğmesiyle başlatılan Türkçe SpeechRecognizer girişi.
- Nihai tanıma sonucu komut olarak işlenir; ara tahminler komut çalıştırmaz.
- Android Text-to-Speech ile Türkçe sesli yanıt.
- Sürekli arka plan dinleme veya "JARVIS" uyandırma kelimesi henüz yoktur.

### Cihaz işlemleri
- Yüklü uygulamaları listeleme ve başlatma.
- Kullanıcı tarafından etkinleştirilirse AccessibilityService üzerinden görünür ekran metnini okuma, metin alanına yazma, metne tıklama ve kaydırma.
- El feneri, medya ses seviyesi, parlaklık, ekran zaman aşımı ve Rahatsız Etmeyin gibi desteklenen kontroller.
- Wi-Fi, Bluetooth ve diğer kısıtlı ayarlar için Android sistem ekranlarına yönlendirme.
- Alarm ve zamanlayıcı için Android saat uygulamasının onay ekranını açma.
- Son telefon işlemlerinin yerel geçmişi.

**Sınırlar:** Android izinleri, üretici yazılımı ve hedef uygulama desteği belirleyicidir. Her uygulamada sınırsız kontrol veya kullanıcı onayı olmadan mesaj gönderme vaat edilmez.

### Bildirimler
- Kullanıcı özel erişimi açarsa desteklenen bildirimleri cihazda tutma.
- Yanıt eylemi sunan bildirimler için AI taslağı oluşturma.
- Kullanıcı taslağı görüp onayladıktan sonra Android RemoteInput üzerinden yanıt gönderme.

### Hafıza ve belgeler
- Room ile yerel kişisel notları ekleme, düzenleme ve silme.
- PDF'in ilk sekiz sayfasını render edip OCR ile metne dönüştürme.
- Görsellerden ML Kit OCR ile metin çıkarma.

### JARVIS Evolution
- Cihaz izinleri, ağ ve depolama için temel öz tanılama.
- Android dosya seçicisinden kullanıcının seçtiği metin/kod dosyalarını inceleme.
- Seçilen dosyalarda TODO/FIXME, uzun satır ve olası gömülü anahtar kalıpları için sezgisel tarama.
- İki seçilmiş dosyanın satır bazlı fark özeti.
- Kullanıcının açıkça başlatmasıyla seçilen kod parçalarını AI sağlayıcısına göndererek inceletme.
- GitHub deposunun kaynak ağacını ve salt okunur dosya önizlemesini uygulama içinde gösterme.
- GitHub Actions derleme geçmişi ve yayımlanmış APK sürümlerini gösterme.
- Rollback yönergesi. Android, normal uygulama izinleriyle kurulu APK'yı sessizce eski sürüme düşürmeye izin vermez.

## Henüz tamamlanmayanlar

- Her zaman dinleyen uyandırma kelimesi ve güvenilir arka plan ses servisi.
- Tam çevrimdışı büyük dil modeli.
- Araştırma, geliştirici, planlayıcı ve telefon ajanları için bağımsız araç kullanan otonom yürütme altyapısı. Şu an ajan kartları sohbet istemi hazırlar.
- Uygulama içinden GitHub kaynak kodunu düzenleme, commit/PR açma ve otomatik geri alma.
- Tam proje dosya yöneticisi, video analizi, takvim senkronizasyonu ve gelişmiş görev arayüzü.
- Otomatik UI/instrumentation test kapsamı ve kapsamlı birim test paketi.
- Üretim dağıtımı için imzalı release build ve güvenli sunucu tarafı API proxy'si.

Görev yönetimi arayüzü şimdilik özellikle ertelenmiştir.

## Derleme

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

APK çıktısı:

```
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions çalışmaları: https://github.com/syntaxxsl/JARVIS-Android/actions

## API anahtarları ve güvenlik

1. JARVIS içinde AI Router ekranını aç.
2. Birincil sağlayıcı için HTTPS API adresi, model ve anahtar gir.
3. İsteğe bağlı olarak yedek sağlayıcıyı da yapılandır.
4. Önce bağlantı testini çalıştır; ardından ayarları kaydet.

API anahtarını GitHub deposuna, kaynak koda, ekran görüntüsüne veya herkese açık issue'ya ekleme. Cihaz içi şifreli depolama, anahtarın uygulama çalışırken bellekte kullanılmasını veya cihazın tamamen ele geçirilmesi riskini ortadan kaldırmaz. Daha güçlü üretim güvenliği için sunucu tarafı proxy gerekir.
