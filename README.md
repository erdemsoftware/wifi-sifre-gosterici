# 📶 WiFi Şifre Gösterici (Root)

Android cihazınızda kayıtlı olan tüm Wi-Fi ağlarının adlarını ve şifrelerini listeleyen, tek dokunuşla panoya kopyalamanızı sağlayan ultra hafif ve pratik bir Android uygulaması.

---

## ⚠️ Önemli Gereksinim: ROOT ZORUNLUDUR!
> **Bu uygulamanın çalışabilmesi için cihazınızın ROOT'lu olması (Magisk, KernelSU veya APatch) şarttır.**
> 
> **Neden?**  
> Android güvenlik mimarisi gereği, kayıtlı Wi-Fi şifreleri sistemin korumalı dizinlerinde (`/data/misc/wifi/` ve `/data/misc/apexdata/`) saklanır. Normal uygulamaların bu dosyalara erişim yetkisi yoktur. Uygulama yalnızca bu dosyaları okuyabilmek için Root (Superuser) yetkisi talep eder.

---

## ✨ Özellikler

- **Ultra Hafif:** Yalnızca **21 KB** boyutundadır.
- **Tek Tıkla Kopyala:** Her Wi-Fi ağının yanında bulunan "Kopyala" butonuyla şifreyi anında panoya kopyalayabilirsiniz.
- **Tümünü Kopyala:** Üstteki butonla tüm Wi-Fi ağlarını ve şifrelerini tek seferde liste halinde kopyalayabilirsiniz.
- **Geniş Uyumluluk:** Android 9, 10, 11, 12, 13 ve 14 sürümleriyle uyumludur (`WifiConfigStore.xml` konumlarını otomatik algılar).
- **%100 Gizlilik ve Güvenlik:** 
  - İnternet izni (`INTERNET`) **bulunmaz**.
  - Reklam, telemetri veya veri toplama kodu içermez.
  - Verileriniz cihazınızdan asla dışarı çıkmaz.

---

## 📲 Kurulum ve Kullanım

1. `WiFi_Sifreleri.apk` dosyasını telefonunuza indirin.
2. Dosyaya dokunarak kurulumu tamamlayın.
3. Uygulamayı ilk kez açtığınızda Magisk / KernelSU tarafından gelen **Root İzni Ver (Grant)** seçeneğini onaylayın.
4. Kayıtlı tüm Wi-Fi ağlarınız ve şifreleriniz anında ekranda listelenecektir.

---

## 🛠️ Proje Yapısı ve Derleme

Bu proje sıfır bağımlılıkla saf Android SDK ve Java ile derlenmiştir.

- `src/com/wifi/sifreler/MainActivity.java`: Ana kaynak kod (Root kontrolü, XML ayrıştırıcı ve Arayüz).
- `res/layout/`: Sade Material tasarım arayüz dosyaları.
- `build.py`: Projeyi doğrudan SDK Build-Tools ile derleyip imzalayan Python betiği.

---

## 📄 Lisans
MIT License - Dilediğiniz gibi kullanabilir ve geliştirebilirsiniz.
