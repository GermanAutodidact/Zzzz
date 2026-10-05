# VoiceLoop for ChatGPT
### Vollautomatischer Sprachdialog für Samsung Galaxy S10 (Android 10 / One UI 2.5)

VoiceLoop verwandelt die offizielle Android-App von ChatGPT (`com.openai.chatgpt`) in einen komplett freihändigen, kontinuierlichen Sprachassistenten. Ohne Root, ohne fragiles Tippen und ohne manuelle Interaktion.

---

## 🎯 Funktionsweise im Überblick

1. **Spracheingabe**: Sobald der Zyklus startet, lauscht VoiceLoop über das Mikrofon (`SpeechRecognizer`). Deutsche Sprache wird extrem präzise und mit Echtzeit-Vorschau erkannt.
2. **Automatisches Senden**: Der erkannte Text wird über den Android Bedienungshilfe-Dienst (`VoiceLoopAccessibilityService`) direkt in das Eingabefeld der ChatGPT-App eingefügt und abgesendet.
3. **Erkennung des Antwort-Endes**: VoiceLoop überwacht das Erscheinen und Verschwinden des Stop-Buttons ("Generierung beenden") sowie die Stabilisierung des Antworttexts in ChatGPT.
4. **Sprachausgabe (Vorlesen)**:
   - **Modus Auto-Fallback (Standard)**: Klickt automatisch auf den Lautsprecher-Button bzw. wählt „Laut vorlesen“ in ChatGPT. Erkennt über `AudioManager` und `AudioPlaybackCallback`, wann ChatGPT das Sprechen beendet hat. Falls ChatGPT zögert oder keine Audioausgabe startet, liest das integrierte Android High-Definition-TTS die Antwort verzögerungsfrei vor.
   - **Modus Direktes TTS**: Extrahiert den Antworttext sofort und liest ihn über die Systemstimme vor.
5. **Nächste Runde (Loop)**: Nach einer kurzen, einstellbaren Pause (z. B. 1–2 Sekunden) ertönt ein haptisches Signal und das Mikrofon lauscht automatisch auf Ihre nächste Frage.
6. **Schwebendes Steuerungs-HUD**: Über der ChatGPT-Oberfläche schwebt ein dezentes, verschiebbares Steuerungs-Overlay, das den aktuellen Zustand (🎙️ Zuhören, 🚀 Senden, ⏳ Generieren, 🔊 Vorlesen, ⏸️ Pause) live anzeigt und Buttons für Pause, Neu-Sprechen oder Beenden bereitstellt.

---

## 🏗️ Gewählte Architektur & Technische Begründung

### Vergleich verschiedener Ansätze:

| Ansatz | Root nötig? | Stabilität | UI-Robustheit | Vor-/Nachteile |
| :--- | :---: | :---: | :---: | :--- |
| **Android Accessibility Service (Verwendet)** | ❌ Nein | ⭐⭐⭐⭐⭐ Hoch | ⭐⭐⭐⭐⭐ Hoch | **Beste Lösung.** Arbeitet direkt mit dem UI-Baum (`AccessibilityNodeInfo`), unabhängig von Bildschirmauflösung oder UI-Skalierung. Kann Text setzen, Gesten ausführen und Audio/Status überwachen. |
| **Feste Bildschirmkoordinaten (`Input tap`)** | ⚠️ Root/ADB | ⭐ Niedrig | ❌ Extrem fragil | Bricht sofort bei Tastatur-Einblendung, Popups oder Modell-Änderungen. |
| **Inoffizielle Web-API / Reverse-Engineering** | ❌ Nein | ⭐⭐ Gering | ❌ Riskant | Führt häufig zu temporären OpenAI-Accountsperren (Cloudflare Captcha) und bricht bei Token-Ablauf. |
| **Reines Audio-Looping über Mikrofon** | ❌ Nein | ⭐⭐ Gering | ❌ Schlecht | Hohe Latenz, akustische Rückkopplung und keine verlässliche Ende-Erkennung. |

### System-Komponenten der App:

- **`VoiceLoopAccessibilityService`**:
  - Untersucht den `AccessibilityNodeInfo`-Baum von `com.openai.chatgpt`.
  - Findet dynamisch Eingabefelder (`EditText`, Hints wie *Message*, *Nachricht*, IDs).
  - Löst den Sende-Button per `ACTION_CLICK` oder koordinatenbasierter `dispatchGesture`-Berührung aus.
  - Erkennt Streaming-Zustand durch das Vorhandensein des Stop-Buttons.
  - Sucht nach „Laut vorlesen“ bzw. führt einen Long-Click auf die Antwortblase aus.
  - Extrahiert Text für Logging und TTS-Fallback.
- **`SpeechRecognitionManager`**:
  - Nutzt Androids standardmäßige Spracherkennung (Google Speech Services / Samsung Spracheingabe).
  - Unterstützt Teilergebnisse (`EXTRA_PARTIAL_RESULTS`) für Echtzeit-Visualisierung.
  - Misst Schallpegel (`RMS dB`) für die animierte Pegelanzeige im Floating-HUD.
  - Erkennt Sprach-Stoppbefehle wie „Pause“ oder „Stopp“.
- **`VoiceLoopController`**:
  - Zustandsmaschine (`IDLE` ➔ `LISTENING` ➔ `SENDING` ➔ `WAITING` ➔ `READING` ➔ `COOLDOWN`).
  - Haptisches Feedback via `Vibrator` bei Zustandssprüngen.
  - Audio-Überwachung über `AudioManager.isMusicActive` und `AudioPlaybackCallback`.
- **`VoiceLoopForegroundService`**:
  - Hält den Dienst auf Android 10 / One UI 2.5 dauerhaft aktiv.
  - Zeigt persistente Benachrichtigung mit Schnellaktionen (Pause, Weiter, Beenden).
- **`FloatingHudManager`**:
  - Erzeugt das schwebende Overlay (`TYPE_APPLICATION_OVERLAY`).
  - Per Touch frei auf dem Bildschirm verschiebbar.
  - Enthält Minimiere-Funktion, Lautstärke-Balken, Live-Transkription und Tasten.
- **`VoiceLoopDatabase` (Room)**:
  - Protokolliert alle Dialog-Durchläufe und Diagnose-Events lokal.
- **`MainActivity` & Jetpack Compose UI**:
  - Dashboard mit Status-Animationen, Einrichtungs-Assistent, Einstellungen, Diagnose-Log-Stream und interaktivem UI-Inspektor.

---

## 📱 Spezifische Optimierungen für Samsung Galaxy S10 & One UI 2.5 (Android 10)

1. **Samsung One UI Akku-Optimierung**:
   - One UI 2.5 beendet Hintergrund-Apps aggressiv.
   - VoiceLoop integriert den direkten Aufruf für `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   - Empfehlung in der App: Unter *Gerätewartung > Akku > App-Energieverwaltung* die App *VoiceLoop* zu den *Apps, die nie in Standby versetzt werden* hinzufügen.
2. **Audio-Erkennung unter Android 10**:
   - Android 10 unterstützt `AudioManager.registerAudioPlaybackCallback`. VoiceLoop nutzt dies zur verzögerungsfreien Erkennung, wann ChatGPTs native Sprachausgabe beginnt und endet.
3. **Screen Overlay & One UI Gesten**:
   - Das Floating HUD nutzt `FLAG_NOT_FOCUSABLE`, sodass die Samsung Gesten-Navigation und die Android-Tastatur weiterhin uneingeschränkt bedienbar bleiben.

---

## 🖥️ Spezielle Optimierung für Samsung DeX (Desktop Modus)

VoiceLoop wurde explizit für den **Samsung DeX Desktop-Modus** optimiert:

1. **Multi-Window Accessibility Traversal (`getChatGptRootNode`)**:
   - In Samsung DeX laufen Apps in frei verschiebbaren Desktop-Fenstern parallel nebeneinander.
   - Wenn der Benutzer im VoiceLoop-Fenster klickt, ist normalerweise VoiceLoop das aktive Fenster (`rootInActiveWindow`). Ein herkömmlicher Dienst würde ChatGPT nicht mehr finden.
   - `VoiceLoopAccessibilityService` durchsucht dank `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` alle aktiven DeX-Fenster (`windows`), findet das ChatGPT-Fenster auf dem Desktop selbstständig und sendet Text sowie Sprachbefehle auch dann, wenn ChatGPT im Hintergrund oder nebenan geöffnet ist!
2. **Frei anpassbare Fenstergröße (`resizeableActivity="true"`)**:
   - VoiceLoop lässt sich in DeX wie ein gewöhnliches Desktop-Programm frei skalieren, maximieren oder an den Bildschirmrand andocken.
   - Durch vollständiges `configChanges`-Handling in `AndroidManifest.xml` führt das Skalieren des Fensters oder das An- und Abdocken an einen Monitor zu keiner Unterbrechung des aktiven Sprachdialogs.
3. **Adaptive Desktop-Oberfläche**:
   - Erkennt die Fensterbreite in DeX (ab 600dp) und schaltet automatisch auf eine seitliche `NavigationRail` um.
   - Die Inhalte werden auf 900dp zentriert, um ergonomische Lesbarkeit auf großen PC-Monitoren und TVs zu gewährleisten.
4. **Maus- und Trackpad-Unterstützung**:
   - Das Floating-HUD reagiert flüssig auf Mausklicks und Drag-and-Drop über der DeX-Arbeitsfläche.

---

## 🔑 Benötigte Berechtigungen

| Berechtigung | Zweck |
| :--- | :--- |
| `android.permission.RECORD_AUDIO` | Präzise Spracherkennung Ihrer Spracheingabe. |
| `android.permission.SYSTEM_ALERT_WINDOW` | Anzeige des schwebenden HUDs über der ChatGPT-App. |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Automatisches Einfügen, Senden und Erkennen in ChatGPT. |
| `android.permission.FOREGROUND_SERVICE` | Unterbrechungsfreier Betrieb im Hintergrund. |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Mikrofon-Zugriff im Vordergrunddienst. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Schutz vor One UI Standby-Beendigung. |
| `android.permission.VIBRATE` | Haptisches Feedback bei Sprechbereitschaft. |
| `android.permission.INTERNET` | Sprachmodell-Aktualisierungen & Online-Spracherkennung. |

---

## 📦 Bereitgestellte APK-Dateien (DeX-optimierte Release-Version)

Die finale, für Samsung DeX optimierte Release-APK wurde mit einem Produktionszertifikat signiert und liegt an folgenden Orten im Projekt bereit:

1. **Direkt im Hauptverzeichnis (Root)**:
   - `VoiceLoop-DeX-Release.apk` (ca. 11 MB, schlanker Release-Build)
   - `VoiceLoop-S10.apk` (ca. 11 MB, identisch mit DeX-Release)
2. **Im standardmäßigen Gradle-Build-Verzeichnis**:
   - `app/build/outputs/apk/release/app-release.apk`

### Wie Sie die APK herunterladen:
- **Über den AI Studio Datei-Explorer**: Klicken Sie mit der rechten Maustaste auf `VoiceLoop-DeX-Release.apk` im linken Dateibaum und wählen Sie **Download**.
- **Über das AI Studio Einstellungsmenü**: Oben rechts im Menü (Zahnrad/Export) finden Sie den direkten Menüpunkt zum Herunterladen der APK oder zum Exportieren des gesamten Projekts als ZIP.

---

## 🚀 Installations- und Einrichtungsanleitung (Samsung DeX)

1. **Wichtig vor der Installation**: Falls bereits ein früherer Installationsversuch auf dem S10 unvollständig vorhanden ist:
   - In DeX unter *Einstellungen ➔ Apps ➔ VoiceLoop* deinstallieren (verhindert Signatur-Konflikte).
   - Falls der Paket-Installer zuvor feststeckte: *Einstellungen ➔ Apps ➔ (Filtersymbol: Systemanwendungen anzeigen) ➔ Paket-Installer ➔ Speicher ➔ Daten & Cache löschen*.
2. **APK installieren**: Öffnen Sie `VoiceLoop-DeX-Release.apk` in Samsung DeX über die App *Eigene Dateien* (My Files).
   - Durch das Produktions-Zertifikat und den nicht-debugbaren Release-Status läuft die Installation zügig durch Play Protect bis 100 %.
2. **VoiceLoop öffnen**: Die App startet auf dem Dashboard. Ein Einrichtungs-Banner weist auf noch fehlende Berechtigungen hin.
3. **Setup-Assistent ausführen** (Reiter *Setup*):
   - **Mikrofon**: Auf *Mikrofon freigeben* tippen und bestätigen.
   - **Bedienungshilfe**: Auf *Bedienungshilfe öffnen* tippen ➔ *Installierte Apps* auswählen ➔ *VoiceLoop* aktivieren.
   - **Über anderen Apps anzeigen**: Schalter für VoiceLoop aktivieren.
   - **Samsung Akku-Optimierung**: Ausnahme genehmigen (*Nicht optimiert*).
   - **ChatGPT App**: Sicherstellen, dass die normale OpenAI ChatGPT-App installiert ist.
4. **Sprachdialog starten**:
   - Auf *Sprach-Loop starten* tippen.
   - ChatGPT öffnet sich automatisch und das schwebende HUD erscheint.
   - Sprechen Sie einfach los! VoiceLoop tippt Ihren Text ein, wartet die Antwort ab, lässt sie vorlesen und lauscht wieder auf Ihre nächste Eingabe.

---

## 🛠️ Fehlerdiagnose & Inspektor

- **Reiter *Test (Inspektor)***:
  - Ermöglicht das Senden eines Testprompts an ChatGPT per Knopfdruck, um die Funktionalität des Bedienungshilfe-Dienstes isoliert zu testen.
  - Testet das Auslösen der Vorlesefunktion.
- **Reiter *Logs***:
  - Zeigt Live-Ereignisse mit Zeitstempel an (Spracherkennung, Node-Treffer, Audio-Wiedergabe, Fehler).
  - Enthält einen Kopier-Button für die Zwischenablage.
- **Fallback-Mechanismus**:
  - Wenn OpenAI in einem App-Update den nativen Vorlese-Button ändert, greift nach 4 Sekunden automatisch das interne Text-To-Speech (TTS) und liest den extrahierten Text der Antwort vor. So bleibt der Sprachdialog stets funktionsfähig!
