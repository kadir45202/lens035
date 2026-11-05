# lens035

Android için film tadında fotoğraf ve video düzenleyici. Giriş ya da kayıt yok: uygulama açılır açılmaz düzenlemeye başlarsın.

Kullanımı iki katmanlı. Tek dokunuşla bir görünüm, film ya da efekt seçersin. İstersen seçili kutucuğun köşesindeki **ok** ile o öğenin ince ayarlarını açarsın. Lightroom'daki gibi tam kontrol isteyenler için Ayarla sekmesinde ışık, renk, karışım, renk tonu tekerlekleri, eğri, detay ve bölgesel maskeler var.

## Özellikler

### Fotoğraf
- **Görünümler:** Film, efekt ve ışık ayarlarını birlikte uygulayan 14 hazır görünüm (Yaz '98, Neon Gece, Sinematik, Anı…). Yoğunluk sürgüsü var. Kendi ön ayarlarını da kaydedebilirsin.
- **32 film profili:** Renkli negatif, diya, sinema, anlık, siyah-beyaz ve deneysel. Her filmin kendi ton eğrisi, renk kaymaları, doygunluk karakteri ve gren, halation, parlama, vinyet varsayılanları var. Ok ile yoğunluk, gren, halation, parlama, solgunluk, vinyet ve sıcaklık ayarlanır.
- **25 yaratıcı efekt:** Grain, toz, ışık sızıntısı, halation, parıltı, Kira yıldızları, güneş ışını, prizma, holo, VHS, glitch, RGB kayma, CRT, piksel, poster, duotone, balıkgözü, portre bulanıklığı, tilt-shift, zoom bulanıklığı, rüya, ayna, tarih damgası, anlık fotoğraf çerçevesi ve tuval. Efektler üst üste uygulanabilir ve her birinin kendi parametreleri var. Sık kullandıklarına uzun basınca yıldızlanır ve "Favoriler"de toplanır.
- **Tuval (hikâye / gönderi):** Fotoğrafı ya da videoyu kırpmadan 9:16, 4:5, 1:1 veya 16:9 tuvale sığdırır. Boşluk, fotoğrafın bulanık bir kopyasıyla ya da siyah, beyaz veya krem renkle dolar. Kenar boşluğu ve yuvarlak köşe ayarlanabilir.
- **Lightroom tarzı ayarlar:**
  - Işık: pozlama, kontrast, açık tonlar, gölgeler, beyazlar, siyahlar
  - Renk: sıcaklık, renk tonu, canlılık, doygunluk
  - 8 bantlı HSL karışımı
  - 3 yönlü renk tonu tekerlekleri ve RGB eğrileri
  - Detay: keskinleştirme ve parazit azaltma
  - Efektler: doku, netlik, pus giderme, vinyet, gren
  - .cube LUT içe aktarma
- **Renk eşleştirme:** Beğendiğin herhangi bir fotoğrafı seç, onun renk havası düzenlediğin fotoğrafa aktarılsın (menü › "Renkleri başka fotoğraftan al" ya da Ayarla › LUT). Sonuç bir LUT olarak saklanır; yoğunluğu ayarlanabilir ve başka fotoğraflarda da kullanılabilir.
- **Luminar'dan esinlenen akıllı araçlar:** Otomatik geliştir (fotoğrafı analiz eder), yapı, gökyüzü, altın saat, ışıltı ve cilt yumuşatma. Fotoğrafta kişi varsa cilt yumuşatma yalnızca kişiye uygulanır.
- **Portre bulanıklığı:** Kişi cihaz üzerinde bulunur (ML Kit, internet gerekmez). Kişi net kalır, arka plan ayarlanabilir şiddette bulanıklaşır. Arka plan bulanıklığı yalnızca arka plan piksellerinden üretildiği için kişinin etrafında hale oluşmaz. Işık topları ve kenar yumuşaklığı ayarlanabilir.
- **Kırpma ve perspektif:** En-boy oranları (sinema 2.39:1 dahil), 90° döndürme, aynalama, düzleştirme ve dikey/yatay perspektif düzeltme (eğik binaları doğrultur). **Otomatik** düğmesi fotoğraftaki düz çizgileri bulur, ufku düzeltir ve devrilen dikeyleri doğrultur. Boş köşeler hiç görünmez: kırpma kendiliğinden içeride kalır, ayar sırasında hizalama ızgarası çıkar.
- **Önce / sonra:** Karşılaştır düğmesine dokununca ekran ikiye bölünür, ayırıcı sürüklenebilir. Basılı tutunca orijinalin tamamı görünür.
- **Rötuş (leke giderme):** Lekeye, sivilceye ya da toz zerresine dokununca yakındaki temiz doku otomatik seçilir ve çevresine göre renk eşleştirilerek kapatılır. Kaynak daire sürüklenebilir, "Başka kaynak" alternatif önerir; fırça boyutu ve yumuşaklık ayarlanır. Yakınlaştırarak küçük lekelerde çalışılabilir.
- **Maskeler:** Kişi, doğrusal ve dairesel bölgesel düzenleme (en fazla 4). Kişi maskesi tek dokunuşla kişiyi seçer, ters çevrilince arka planı düzenler. Her maske Lightroom'daki gibi bir **aralıkla** sınırlanabilir: yalnızca belli bir parlaklık bandı (örneğin gökyüzünün parlak kısmı) ya da belli bir renk (örneğin yalnızca turuncular). Her maskede 12 ayar var.
- **Toplu uygulama:** "Başka fotoğraflara uygula" ile düzenleme 50 fotoğrafa kadar tek seferde uygulanır ve hepsi galeriye kaydedilir. Kırpma ve maskeler hariçtir; asıl fotoğraflar değişmez. Her fotoğraf kendi analizini ve gerekiyorsa kendi kişi tespitini alır.
- **Geçmiş:** Lightroom'daki gibi her adım adıyla listelenir ("Pozlama +0.35", "Film: Portre 400", "Grain eklendi"). Bir adıma dokununca düzenleme o hâline döner.
- **Diğer:** Geri al / yinele, histogram, ayarları kopyala/yapıştır, ilk kullanımda kısa ipuçları.
- **Dışa aktarma:** Tam çözünürlükte (5120 px'e kadar) JPEG ya da PNG olarak Resimler › Lens035 klasörüne kaydeder ve EXIF tarih/kamera bilgisini korur.
- **Projeler:** Her düzenleme otomatik kaydedilir, ana ekrandan kaldığın yerden devam edersin.

### Video
- Fotoğraftaki görünüm, film, efekt ve ayarların aynısı; kendi ön ayarların da uygulanabilir. Önizleme ile dışa aktarma aynı GPU hattını kullanır, yani gördüğün sonucu alırsın. (Kişi gerektiren portre bulanıklığı ve kişi maskesi yalnızca fotoğrafta var.)
- Küçük resimli zaman çizelgesinde kırpma, 0.5x–3x hız, sesi kapatma, en-boy kırpma, perspektif düzeltme ve döndürme.
- 720p / 1080p / orijinal (4K'ya kadar) H.264 dışa aktarma. HDR videolar SDR'a dönüştürülür.

### Poz kamerası
- Vizörde soluk bir referans durur. Galeriden seçtiğin bir fotoğrafı **görsel**, **kontur** ya da **siluet** olarak gösterebilir veya 16 hazır pozdan birini (ayakta, oturarak, eğlenceli, portre) seçebilirsin.
- ML Kit ile canlı poz eşleştirme yapar: ekranda "Uyum %82 · Soldaki kol biraz yukarı", "Biraz yaklaşın" gibi talimatlar çıkar, sen de bunları çektiğin kişiye iletirsin.
- Referansı sürükleyip büyütme, aynalama, ızgara, flaş, zamanlayıcı (3/10 sn) ve poz tutunca otomatik çekim.
- Çekilen fotoğraf galeriye kaydedilir ve doğrudan düzenleyicide açılır.

## Kurulum

### Hazır APK (en kolayı)
Her güncellemede GitHub uygulamayı kendisi derler (birkaç dakika) ve APK'yı **Releases** sayfasına koyar:
1. Telefondan https://github.com/kadir45202/lens035/releases/latest adresini aç.
2. **Assets** altındaki **lens035.apk** dosyasına dokunup indir.
3. İndirilen dosyayı aç; ilk seferde tarayıcıya "bilinmeyen uygulamaları yükleme" izni ver.

Depoda yalnızca kaynak kod tutulur; APK yalnızca Releases sayfasında durur. İstersen aşağıdaki gibi kendin de derleyebilirsin.

### Android Studio ile
1. Android Studio (Ladybug veya üstü) ve JDK 17 kur.
2. Projeyi aç, Gradle senkronizasyonunu bekle.
3. Telefonu USB ile bağla (Geliştirici seçenekleri › USB hata ayıklama açık) ve **Run ▶**'a bas. Uygulama telefona kurulup açılır.

### Komut satırından
```bash
./gradlew :app:assembleRelease
```
APK `app/build/outputs/apk/release/app-release.apk` konumunda oluşur. Telefona kurmak için:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

APK'lar depodaki sabit, gizli olmayan bir anahtarla (`keystore/`) imzalanır. Böylece hangi bilgisayarda derlenirse derlensin yeni sürüm eskisinin üzerine kurulur ve projeler kaybolmaz. Mağazada yayınlamadan önce gizli bir yükleme anahtarına geçilmelidir.

Gereksinimler: Android 8.0 (API 26) veya üstü ve OpenGL ES 3.0 destekli bir cihaz. Günümüzdeki telefonların hemen hepsi uygun.

## Mimari

```
core/   Saf Kotlin, Android bağımlılığı yok, JVM'de birim testli
  model/     EditState (tüm düzenleme), geometri, maskeler, efekt kataloğu
  film/      Parametrik film modelleri → 33³ 3D LUT üretimi
  color/     Renk bilimi (sRGB, HSV, beyaz dengesi, eğriler)
  render/    EditState → shader uniform eşlemesi
  analysis/  Histogram, pus tahmini, otomatik geliştirme, çizgi bulma ve otomatik düzeltme
  pose/      Poz şablonları ve eşleştirme / talimat üretimi
  (geometri) Çeyrek dönüş → aynalama → perspektif (H = K·R·K⁻¹) → düzleştirme → kırpma, tek 3x3 projektif matris
app/
  assets/shaders/  GLSL ES 3.00: bulanıklık piramidi, renk hattı, efektler
  gl/              LensPipeline (çok geçişli GPU hattı), fotoğraf/video önizleme,
                   ekran dışı dışa aktarma, Media3 GlEffect
  media/           Görüntü yükleme (EXIF, P3→sRGB), galeriye kaydetme, video dışa aktarma,
                   cihaz üzerinde kişi bölütleme (ML Kit selfie segmentation)
  ui/              Jetpack Compose arayüzü
```

Tek bir `LensPipeline` dört yerde aynı şekilde çalışır: canlı fotoğraf önizlemesi, tam çözünürlükte dışa aktarma, video önizlemesi (ExoPlayer → SurfaceTexture) ve video dışa aktarma (Media3 Transformer).

## Testler
- `./gradlew :core:test`: renk bilimi, eğriler, 32 filmin LUT doğruluğu, geometri ve perspektif (merkez korunumu, ters dönüşüm, boş köşesiz kırpma, döndürme/aynalamada birebir tutarlılık), otomatik düzeltme (sentetik eğik sahnelerde açıyı geri bulma), .cube ayrıştırma, shader eşlemesi, kişi maskesi kuralları, poz eşleştirme
- `./gradlew :app:connectedDebugAndroidTest`: cihaz/emülatör GPU'sunda tüm film, efekt ve görünümlerin render'ı, görüntü yönü, portre bulanıklığının kişiyi net bırakması, kişi maskesinin yalnızca kişiyi etkilemesi, Media3 ile video dışa aktarma, uygulamanın açılışı
- Shader'lar geliştirme sırasında WebGL2 (OpenGL ES 3.0 ile aynı dil) üzerinde gerçek fotoğraflarla görsel olarak doğrulandı

## Lisanslar
Instrument Sans ve JetBrains Mono fontları SIL Open Font License 1.1 ile dağıtılır (`app/src/main/assets/licenses`). Film adları özgündür; gerçek film markalarını taklit etmez, klasik fotokimyasal süreçlerin karakterini modeller.
