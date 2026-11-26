# JARVIS Android

JARVIS, Android için Kotlin ve Jetpack Compose ile geliştirilen Türkçe kişisel asistandır. Arayüz Stark/Iron Man HUD estetiğinden esinlenir.

## Paket ve derleme

- Paket: `com.jarvis.assistant`
- Dil / UI: Kotlin, Jetpack Compose, Material 3
- Minimum Android: API 26
- Compile / Target SDK: 35
- JDK: 17
- CI: GitHub Actions

Her başarılı CI çalışması bir debug APK üretir, birim testlerini çalıştırır, APK'yı doğrular ve sürümlü GitHub Release olarak arşivler. Debug APK Play Store dağıtım imzası değildir.

## Uygulanan özellikler

### AI sohbeti ve sağlayıcı bağlantısı
- Room üzerinde kalıcı sohbet geçmişi.
- OpenAI uyumlu `/chat/completions` API'sine birincil + yedek sağlayıcı geçişi.
- OpenRouter, Groq, Gemini OpenAI uyumlu uç noktası, Hugging Face Router ve yerel OpenAI uyumlu sunucu için profil alanları.
- API bağlantı testi ve desteklenen `/models` uç noktasından model keşfi.
- Şifreli SharedPreferences içinde API anahtarı saklama.
- İstek geçmişi ve belge bağlamı için karakter bütçesi; iptal edilen OkHttp isteği için coroutine iptal bağlantısı.
- Yanıt yönergelerinde kaynak disiplini, belirsizlik ve harici işlem iddialarını sınırlama.

**Sınırlar:** Çalışan sohbet yolu genel OpenAI uyumlu istemci ve birincil/yedek geçişidir. Ayrı `AiRouter`, `OpenAiProvider` ve `GeminiProvider` sınıfları henüz bu çalışma yoluna bağlanmış tam DI mimarisi değildir. Sağlayıcıların hepsi aynı model listesi veya API sözleşmesini desteklemez. Uygulama yerel model indirme/inference motoru içermez; Ollama gibi yerel sunucu ayrıca kurulup aynı cihazda çalışmalıdır. API anahtarlarını kullanıcı kendi hesabından bağlar; ücretsiz ve sınırsız kota garantisi yoktur.

### Ses ve sohbet
- Mikrofon düğmesiyle başlatılan Türkçe konuşma tanıma.
- Ara tanıma parçaları komut olarak çalıştırılmaz; nihai sonuç kullanılır.
- Android Text-to-Speech ile sesli yanıt.
- JARVIS STOP ile konuşma, TTS ve etkin AI isteğini durdurma.

**Sınırlar:** Sürekli arka plan dinleme ve uyandırma kelimesi bu sürümde yoktur. Android üretici/pil politikaları arka plan mikrofonunu kısıtlar.

### Telefon ve bildirim kontrolü
- Yüklü uygulamaları listeleme ve başlatma.
- Kullanıcı etkinleştirirse AccessibilityService üzerinden görünür ekran metni okuma, metne tıklama, yazma ve kaydırma.
- El feneri, medya ses seviyesi, parlaklık, ekran zaman aşımı ve Rahatsız Etmeyin kontrolleri; gereken özel izinleri Android ayarlarından isteme.
- Wi-Fi, Bluetooth, konum, pil ve diğer kısıtlı ayarlar için Android kontrol panellerini açma.
- Alarm ve zamanlayıcıyı Android Saat uygulamasının onay ekranında hazırlama.
- Son telefon işlemlerinin yerel geçmişi.
- Bildirim erişimi verilirse desteklenen bildirimleri uygulama içinde gösterme.
- Desteklenen RemoteInput bildirimleri için AI yanıt taslağı; gönderim yalnızca kullanıcının açık onayıyla.
- Bildirim önizlemelerini şifreli saklama ve uygulama yedeklemesini kapatma.

**Sınırlar:** Android izinleri, üretici yazılımı ve hedef uygulama desteği belirleyicidir. JARVIS her uygulamada sınırsız kontrol, her bildirimde yanıt veya kullanıcı onayı olmadan mesaj gönderme yapamaz.

### Hafıza, Knowledge Engine ve belge zekâsı
- Room ile kişisel notları ekleme, düzenleme ve silme.
- PDF, görsel ve metin belgelerini şifreli yerel bilgi kütüphanesine ekleme.
- Kütüphane sınırı: en fazla 20 belge, belge başına en fazla 20.000 karakter.
- Belgeyi parçalara ayıran ve BM25 benzeri anahtar-kelime/terim sıklığı puanlamasıyla ilgili bölümleri sıralayan yerel retrieval.
- İlgili kişisel hafıza notlarını sohbet isteğine bağlam olarak seçme.
- Sık geçen kavramlardan yerel kavram dizini oluşturma.
- PDF'in ilk sekiz sayfasını render edip OCR; görsellerden ML Kit OCR.

**Sınırlar ve gizlilik:** Bu sürüm embedding tabanlı vektör arama veya gerçek semantik RAG değildir; yerel terim tabanlı sıralı aramadır. Kavram dizini ontoloji/ilişki grafiği değildir. Belgeler cihazda şifreli saklanır; eşleşen alıntılar çevrimiçi AI yanıtı istendiğinde yapılandırılmış sağlayıcıya gönderilebilir. Hassas dosyaları eklemeden önce bunu dikkate al.

### Mission Control ve uzman rol akışı
- Hedefi en fazla beş sıralı alt göreve bölme.
- Planı kullanıcıya gösterip kullanıcı başlattıktan sonra adımları sırayla çalıştırma.
- İsteğe bağlı Challenger karşıt görüş ve Critic hata/güvenlik kontrolü.
- Görev durumu, durdurma ve sınırlı yerel geçmiş.

**Sınırlar:** Planner, görev, Challenger ve Critic rolleri aynı yapılandırılmış AI sağlayıcısına gönderilen farklı istemlerdir; ayrı modeller veya bağımsız araç kullanan ajanlar değildir. Telefon, GitHub veya dosya işlemleri otomatik yapılmaz. Durdurma sonraki adımları engeller; devam eden sunucu isteğinin geri alınacağını garanti etmez.

### Research Studio ve otomatik takip
- Wikipedia Türkçe, Crossref, OpenAlex ve arXiv üzerinden kaynak keşfi.
- Kaynak başlığı, kısa özet veya akademik metaveri ve doğrudan URL gösterimi.
- Kaynak bağlamını AI sağlayıcısına göndererek sentez, eksik bilgi ve doğrulama notları üretme.
- En fazla 10 araştırma konusunu şifreli yerel depoda takip etme.
- Kullanıcı açarsa WorkManager ile ağ bulunduğunda yaklaşık 24 saatte bir arka plan kaynak yenilemesi.

**Sınırlar:** Bu tam web taraması değildir; arama servislerinin döndürdüğü özet/metaveriyle sınırlıdır. Tam makale metnine erişim garanti edilmez. Atıf sayısı kalite garantisi değildir. WorkManager çalıştırma saatini garanti etmez; Android pil/ağ koşullarına göre ertelenebilir.

### JARVIS Evolution / geliştirme laboratuvarı
- Ağ, depolama ve izinler için temel öz tanılama.
- Kullanıcının dosya seçicisinden seçtiği kaynak kodları yerel inceleme.
- TODO/FIXME, uzun satır ve olası gömülü anahtar kalıpları için sezgisel statik tarama.
- İki dosyanın satır bazlı fark özeti.
- AI kod incelemesi ve geliştirme önerileri.
- Seçilen son dosya için AI düzeltme taslağı üretme, taslağı uygulama içinde inceleme/düzenleme ve yeni dosya olarak dışa aktarma. Orijinal dosya otomatik değiştirilmez.
- GitHub deposunun salt okunur kaynak ağacı ve dosya önizlemesi.
- GitHub Actions derleme geçmişi ve yayımlanmış APK sürümleri.
- Rollback yönergesi.

**Sınırlar:** Kod taraması tam güvenlik denetimi değildir. AI düzeltme taslağı derlenmiş veya test edilmiş sayılmaz; dışa aktarılan dosyayı projeye uygulamadan önce incele ve test et. Android normal uygulama izinleriyle kurulu APK'yı sessizce eski sürüme düşürmeye izin vermez. Bu sürüm uygulama içinden GitHub commit/PR göndermez.

## Henüz tamamlanmayan / dış entegrasyon gerektiren işler

- Uyandırma kelimesi ve sürekli arka plan ses servisi.
- Tam çevrimdışı büyük dil modeli.
- Gerçek embedding modeli, vektör veritabanı, reranking ve semantik RAG.
- Ontoloji tabanlı kişisel bilgi grafiği ve ilişkisel çıkarım.
- Bağımsız modeller, ayrı araç izinleri ve araç yürütme motoruyla gerçek çoklu ajan sistemi.
- Geniş web taraması, kaynak güvenilirliği doğrulaması ve tam metin akademik erişim.
- GitHub kodunu uygulama içinden güvenli branch/PR ile düzenleme, CI sonuçlarına göre onaylı otomatik yayın ve gerçek rollback akışı.
- Takvim/akıllı ev/video ve diğer üçüncü taraf servis entegrasyonları.
- Sağlayıcıya özel bütün model keşifleri, API anahtarlarının sunucu tarafı proxy arkasına alınması.
- Kapsamlı UI/instrumentation testleri, fiziksel cihaz/üretici bazlı testler ve imzalı release dağıtımı.

## Test ve APK

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions: https://github.com/syntaxxsl/JARVIS-Android/actions

## API kurulumu

1. JARVIS içindeki AI Router ekranını aç.
2. Birincil sağlayıcının HTTPS adresini, modelini ve API anahtarını gir.
3. İsteğe bağlı yedek sağlayıcıyı ayarla.
4. Bağlantı testini çalıştır ve ayarları kaydet.

API anahtarını GitHub'a, kaynak koda veya ekran görüntüsüne koyma. Cihaz içi şifreli depolama, cihazın tamamen ele geçirilmesi riskini ortadan kaldırmaz. Üretim kullanımı için güvenli sunucu tarafı proxy tercih edilmelidir.
