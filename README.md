# Agenda+ (Android Magister Client) 📱🚀

**Agenda+** is een moderne, razendsnelle en privacy-vriendelijke Android-app voor Magister-scholieren. Gebouwd met **Kotlin** en **Jetpack Compose**, voorzien van zowel een strak **Nothing OS-thema** (OLED puur zwart, dot-matrix typografie en Nothing Red accenten) als een dynamisch **Material You-thema**.

---

## ✨ Belangrijkste Functies

- ⚡ **10x Sneller dan Magister**: Directe lokale cache, geen trage laadwieltjes.
- 🎨 **Twee Complete Thema's**: Wissel naadloos tussen **Nothing OS** en **Material You**.
- 🚌 **Live OV Bustijden & Schoolnavigatie**: Geïntegreerde OV-data (OVapi) en OpenStreetMap routekaart.
- 📊 **Cijfers & What-If Calculator**: Interactieve voortgangsringen en rekenmachine voor streefcijfers.
- 📱 **Home Screen Widgets**: Volgende les, cijfers & gemiddelde, en openstaand huiswerk direct op je startscherm.
- 📴 **100% Offline-modus**: Je rooster, cijfers en huiswerk blijven altijd beschikbaar in de schoolkelder.
- 🔒 **Privacy-First**: Direct inloggen via je eigen Magister-schoolomgeving zonder externe tussenliggende servers.

---

## 🛠️ Automatisch APK Bouwen & Releases op GitHub

In de map `.github/workflows/release.yml` is een complete **GitHub Actions workflow** geconfigureerd. Zodra je deze repository naar GitHub pusht, kun je met één klik of tag een kant-en-klaar `.apk`-bestand laten bouwen en publiceren op je GitHub Releases-pagina!

### Optie 1: Automatisch via een Git Tag (Aanbevolen)
Voer in je terminal de volgende commando's uit:
```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions start direct:
1. Het Android-project wordt gecompileerd met JDK 17 en Gradle.
2. Er wordt een gesigneerd `.apk`-bestand aangemaakt (`AgendaPlus-v1.0.0.apk`).
3. Er wordt automatisch een **GitHub Release** aangemaakt onder de tab **Releases** met het downloadbare APK-bestand!

---

### Optie 2: Handmatig via de GitHub Webinterface
1. Ga naar je repository op [GitHub](https://github.com).
2. Klik op het tabblad **Actions** bovenaan.
3. Selecteer in het linkermenu de workflow **Build & Release Android APK**.
4. Klik op de knop **Run workflow**.
5. Vul optioneel een versienummer in (bijv. `v1.0.0`) en eventuele release notes.
6. Klik op **Run workflow**. Na circa 2 minuten staat je release klaar onder **Releases**!

---

### Optie 3: Release aanmaken in de GitHub UI
1. Ga naar je repository op GitHub.
2. Klik rechts op **Releases** -> **Draft a new release**.
3. Kies een tag (bijv. typ `v1.0.0` en klik op *Create new tag: v1.0.0 on publish*).
4. Vul een titel en beschrijving in.
5. Klik op **Publish release**.
6. De GitHub Action wordt getriggerd door de tag en voegt automatisch `AgendaPlus-v1.0.0.apk` toe aan de release!

---

## 🔑 Ondertekening (Signing)

- **Standaard (Geen configuratie nodig)**: De workflow gebruikt automatisch de meegeleverde veilige `debug.keystore`. Hierdoor is de gegenereerde APK direct downloadbaar en installeerbaar op elk Android-toestel zonder dat je ingewikkelde instellingen hoeft te doen!
- **Eigen Release Keystore (Optioneel)**: Wil je de app publiceren met een eigen officiële upload-keystore? Voeg dan de volgende **Repository Secrets** toe in GitHub (*Settings -> Secrets and variables -> Actions*):
  - `KEYSTORE_BASE64`: De base64-gecodeerde string van je `.jks`-bestand (`base64 -w 0 my-upload-key.jks`).
  - `STORE_PASSWORD`: Het wachtwoord van je keystore.
  - `KEY_PASSWORD`: Het wachtwoord van je key alias.

---

## 💻 Lokaal Bouwen

Wil je de app lokaal op je eigen computer bouwen?
```bash
# Debug APK bouwen
./gradlew assembleDebug

# Tests uitvoeren
./gradlew testDebugUnitTest
```
Het gegenereerde APK-bestand bevindt zich na het bouwen in:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📁 Projectstructuur
- `app/`: De volledige Android-applicatie in Kotlin en Jetpack Compose.
- `website/`: Moderne landingspagina voor www.agendaplus.nl met live Nothing OS / Material You theme switcher.
- `promo-video/`: 115 BPM interactieve verticale motion-graphics promoclip met MP4-downloader.
- `Logo/`: Hoge resolutie vector en PNG logo's voor zowel Nothing OS als Material You.
- `.github/workflows/`: Geautomatiseerde CI/CD en release pipeline.
