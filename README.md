# lens035

Android için film tadında fotoğraf ve video düzenleyici. Giriş ya da kayıt yok: uygulama açılır açılmaz düzenlemeye başlarsın.

Kullanımı iki katmanlı. Tek dokunuşla bir görünüm, film ya da efekt seçersin. İstersen seçili kutucuğun köşesindeki **ok** ile o öğenin ince ayarlarını açarsın. Lightroom'daki gibi tam kontrol isteyenler için Ayarla sekmesinde ışık, renk, karışım, renk tonu tekerlekleri, eğri, detay ve bölgesel maskeler var.

## Özellikler

### Fotoğraf
- **Görünümler:** Film, efekt ve ışık ayarlarını birlikte uygulayan 14 hazır görünüm (Yaz '98, Neon Gece, Sinematik, Anı…). Yoğunluk sürgüsü var. Kendi ön ayarlarını da kaydedebilirsin.
- **32 film profili:** Renkli negatif, diya, sinema, anlık, siyah-beyaz ve deneysel. Her filmin kendi ton eğrisi, renk kaymaları, doygunluk karakteri ve gren, halation, parlama, vinyet varsayılanları var. Ok ile yoğunluk, gren, halation, parlama, solgunluk, vinyet ve sıcaklık ayarlanır.
- **23 yaratıcı efekt:** Grain, toz, ışık sızıntısı, halation, parıltı, Kira yıldızları, güneş ışını, prizma, holo, VHS, glitch, RGB kayma, CRT, piksel, poster, duotone, balıkgözü, tilt-shift, zoom bulanıklığı, rüya, ayna, tarih damgası ve anlık fotoğraf çerçevesi. Efektler üst üste uygulanabilir ve her birinin kendi parametreleri var.
- **Lightroom tarzı ayarlar:**
  - Işık: pozlama, kontrast, açık tonlar, gölgeler, beyazlar, siyahlar
  - Renk: sıcaklık, renk tonu, canlılık, doygunluk
  - 8 bantlı HSL karışımı
  - 3 yönlü renk tonu tekerlekleri ve RGB eğrileri
  - Detay: keskinleştirme ve parazit azaltma
  - Efektler: doku, netlik, pus giderme, vinyet, gren
  - .cube LUT içe aktarma
- **Luminar'dan esinlenen akıllı araçlar:** Otomatik geliştir (fotoğrafı analiz eder), yapı, gökyüzü, altın saat, ışıltı ve cilt yumuşatma.
- **Kırpma:** En-boy oranları (sinema 2.39:1 dahil), 90° döndürme, aynalama ve boş köşe bırakmayan düzleştirme.
- **Maskeler:** Doğrusal ve dairesel bölgesel düzenleme (en fazla 4). Her maskede 12 ayar var.
- **Diğer:** Geri al / yinele, basılı tutunca orijinali gösterme, histogram, ayarları kopyala/yapıştır.
- **Dışa aktarma:** Tam çözünürlükte (5120 px'e kadar) JPEG ya da PNG olarak Resimler › Lens035 klasörüne kaydeder ve EXIF tarih/kamera bilgisini korur.
- **Projeler:** Her düzenleme otomatik kaydedilir, ana ekrandan kaldığın yerden devam edersin.

### Video
- Fotoğraftaki film, efekt ve ayarların aynısı. Önizleme ile dışa aktarma aynı GPU hattını kullanır, yani gördüğün sonucu alırsın.
- Küçük resimli zaman çizelgesinde kırpma, 0.5x–3x hız, sesi kapatma, en-boy kırpma ve döndürme.
- 720p / 1080p / orijinal (4K'ya kadar) H.264 dışa aktarma. HDR videolar SDR'a dönüştürülür.

### Poz kamerası
- Vizörde soluk bir referans durur. Galeriden seçtiğin bir fotoğrafı **görsel**, **kontur** ya da **siluet** olarak gösterebilir veya 16 hazır pozdan birini (ayakta, oturarak, eğlenceli, portre) seçebilirsin.
- ML Kit ile canlı poz eşleştirme yapar: ekranda "Uyum %82 · Soldaki kol biraz yukarı", "Biraz yaklaşın" gibi talimatlar çıkar, sen de bunları çektiğin kişiye iletirsin.
- Referansı sürükleyip büyütme, aynalama, ızgara, flaş, zamanlayıcı (3/10 sn) ve poz tutunca otomatik çekim.
- Çekilen fotoğraf galeriye kaydedilir ve doğrudan düzenleyicide açılır.

## Kurulum

### Hazır APK
GitHub Actions her push'ta APK üretir: **Actions › Android CI › son çalıştırma › Artifacts › `lens035-apk`**. Telefona `app-release.apk` dosyasını kur. Bu dosya optimize edilmiş sürümdür ve test için debug anahtarıyla imzalanmıştır.

### Kaynaktan derleme
1. Android Studio (Ladybug veya üstü), JDK 17
2. Projeyi aç ve Gradle senkronizasyonunu bekle
3. `./gradlew :app:assembleRelease` (ya da Run ▶)

Gereksinimler: Android 8.0 (API 26) veya üstü ve OpenGL ES 3.0 destekli bir cihaz. Günümüzdeki telefonların hemen hepsi uygun.

## Mimari

```
core/   Saf Kotlin, Android bağımlılığı yok, JVM'de birim testli
  model/     EditState (tüm düzenleme), geometri, maskeler, efekt kataloğu
  film/      Parametrik film modelleri → 33³ 3D LUT üretimi
  color/     Renk bilimi (sRGB, HSV, beyaz dengesi, eğriler)
  render/    EditState → shader uniform eşlemesi
  analysis/  Histogram, pus tahmini, otomatik geliştirme
  pose/      Poz şablonları ve eşleştirme / talimat üretimi
app/
  assets/shaders/  GLSL ES 3.00: bulanıklık piramidi, renk hattı, efektler
  gl/              LensPipeline (çok geçişli GPU hattı), fotoğraf/video önizleme,
                   ekran dışı dışa aktarma, Media3 GlEffect
  media/           Görüntü yükleme (EXIF, P3→sRGB), galeriye kaydetme, video dışa aktarma
  ui/              Jetpack Compose arayüzü
```

Tek bir `LensPipeline` dört yerde aynı şekilde çalışır: canlı fotoğraf önizlemesi, tam çözünürlükte dışa aktarma, video önizlemesi (ExoPlayer → SurfaceTexture) ve video dışa aktarma (Media3 Transformer).

## Testler
- `./gradlew :core:test`: renk bilimi, eğriler, 32 filmin LUT doğruluğu, geometri, .cube ayrıştırma, shader eşlemesi, poz eşleştirme
- `./gradlew :app:connectedDebugAndroidTest`: cihaz/emülatör GPU'sunda tüm film, efekt ve görünümlerin render'ı, görüntü yönü, Media3 ile video dışa aktarma, uygulamanın açılışı
- Shader'lar geliştirme sırasında WebGL2 (OpenGL ES 3.0 ile aynı dil) üzerinde gerçek fotoğraflarla görsel olarak doğrulandı

## Lisanslar
Instrument Sans ve JetBrains Mono fontları SIL Open Font License 1.1 ile dağıtılır (`app/src/main/assets/licenses`). Film adları özgündür; gerçek film markalarını taklit etmez, klasik fotokimyasal süreçlerin karakterini modeller.
