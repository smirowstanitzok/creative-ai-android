# CreativeAI (Android)

Android-App (Material 3) für die **Creative Cloud Infrastructure Suite**. Sie
startet für ein gewähltes Kreativ-Profil einen GPU-Pod auf
[RunPod](https://runpod.io), öffnet die Oberfläche im In-App-Browser und
terminiert den Pod auf Knopfdruck wieder – die App-Variante des Python-Skripts
aus dem Nachbar-Repo [`creative-ai`](../creative-ai).

## Funktionen

- **Dashboard mit Cards** für alle 9 Produktionsprofile (Bild, Video, Musik,
  Sprache, Chat, 3D …).
- **Tippen auf eine Card** startet den Pod; alle anderen Cards werden gesperrt,
  solange ein Pod aktiv ist. Der Status wird als *Läuft* angezeigt.
- **Stop-Button** je aktiver Card → Rückfrage „Soll der KI Agent gestoppt
  werden?" (Ja/Nein). *Ja* terminiert den Pod und gibt alle Cards wieder frei.
- **Tippen auf die Card (außerhalb des Stop-Buttons)** öffnet die Anwendung im
  In-App-Browser. Über den Zurück-Pfeil bzw. das Banner „Zurück zur geöffneten
  Anwendung" wechselt man verlustfrei zwischen Übersicht und Browser hin und her
  (der WebView-Zustand bleibt erhalten).
- **Pull-to-Refresh** holt die tatsächlich laufenden Pods von RunPod und gleicht
  sie mit den Cards ab.
- **Menü**: *Alle Pods terminieren*, *API Key konfigurieren*, *Zu Runpod*
  (öffnet <https://console.runpod.io/>).

## API-Key

Beim ersten Start ist kein Key hinterlegt. Über **Menü → API Key konfigurieren**
den RunPod-API-Key (Schreibrechte nötig) eintragen – er wird lokal im
app-privaten DataStore gespeichert und aus dem Cloud-Backup ausgenommen.
Key erstellen: <https://www.console.runpod.io/user/settings>.

## Verhaltens-Parität zum Python-Skript

Die Datei [`app/src/main/assets/profiles.json`](app/src/main/assets/profiles.json)
wird aus der Python-Referenz erzeugt. Die Docker-Overrides (`dockerStartCmd` /
`dockerEntrypoint`) inklusive Bootstrap-Skripten werden **byte-genau**
übernommen, damit ein von der App gestarteter Pod exakt so provisioniert wird
wie vom Skript. Bei Änderungen an den Profilen in `../creative-ai/main.py`
`profiles.json` neu erzeugen:

```bash
python3 scripts/gen_profiles.py > app/src/main/assets/profiles.json
```

## Bauen

Voraussetzungen: JDK 17, Android SDK (Platform 35, Build-Tools 35).

```bash
# Debug-APK
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Signierte Release-APK (nutzt creative-ai-release.jks im Repo-Root, falls vorhanden)
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Der `sdk.dir` wird in `local.properties` erwartet.

## Installieren aufs Handy

1. Die `.apk` aufs Telefon übertragen (USB, Cloud, Mail) **oder** per USB-Debugging:
   ```bash
   adb install -r CreativeAI-1.0.apk
   ```
2. Auf dem Gerät „Installation aus unbekannten Quellen" für die verwendete App
   (Dateimanager/Browser) erlauben und die APK öffnen.

## Technik

- Kotlin, Jetpack Compose, Material 3
- RunPod REST v1 (`https://rest.runpod.io/v1`) via OkHttp + kotlinx.serialization
- DataStore (Preferences) für den API-Key
- MVVM: `DashboardViewModel` hält Zustand & Pod-Lebenszyklus (Start,
  Bereitschafts-Polling, Stop mit Retry, Reconcile, Terminate-All)
