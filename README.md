# SiteCouncil

Android-Prototyp (MVP). Die App analysiert eine Website per URL mit einem Rat aus sechs KI-Experten, einem Moderator und simulierten Testnutzern. Ergebnis: Gesamtscore, Teilscores, Heatmap, priorisierte Maßnahmen, Technik-Befunde, Markdown-Bericht.

Kein Backend. Die App ruft die Anthropic Messages API direkt vom Gerät auf. Das ist für einen Prototyp in Ordnung, für eine Store-Version nicht (siehe Grenzen).

## Stand der Prüfung

Dieses Projekt wurde in einer Umgebung ohne Android SDK und ohne Zugriff auf Maven-Repositories erstellt. Daraus folgt:

* `./gradlew assembleDebug` wurde **nicht** ausgeführt. Der erste Build auf Ihrem Rechner ist der eigentliche Test.
* Übersetzt und ausgeführt wurden die reinen Kotlin-Teile (domain, Technik-Regeln, robots.txt-Parser, Heatmap-Raster, Persona-Aggregation) mit 34 Unit-Tests, alle grün. `JsonExtractorTest` lief dabei nicht mit, weil lokal das Serialization-Compiler-Plugin fehlte.
* Alle übrigen Dateien (Compose, Hilt, Room, WebView, OkHttp) sind nur auf Syntax geprüft. Rechnen Sie beim ersten Build mit einzelnen Compilerfehlern, etwa durch abweichende API-Signaturen.
* Das Zeichnen einer nicht angezeigten WebView in ein Bitmap ist der wackligste Teil. Bleibt der Screenshot leer, steht unter "Bekannte Grenzen", was zu tun ist.

## Setup

Voraussetzungen: Android Studio (Koala oder neuer), JDK 17, Android SDK 34.

1. Projektordner in Android Studio öffnen und Gradle-Sync abwarten. Android Studio legt `local.properties` mit dem SDK-Pfad selbst an.
2. Auf der Kommandozeile: `./gradlew assembleDebug`. Die APK liegt danach unter `app/build/outputs/apk/debug/`.
3. Tests: `./gradlew testDebugUnitTest`.
4. App starten, in den Einstellungen den Anthropic API-Key eintragen. Der Key liegt verschlüsselt in EncryptedSharedPreferences und wird nirgends geloggt.
5. URL eingeben, optional Zielgruppe und Seitenziel, "Analysieren".

Der Gradle-Wrapper zeigt auf Gradle 8.9. Versionen stehen in `gradle/libs.versions.toml` (AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.02, Hilt 2.52, Room 2.6.1).

## Architektur

Single-Activity, Jetpack Compose, MVVM, Hilt.

```
com.sitecouncil
  ui/          Compose-Bildschirme und ViewModels (start, progress, result, settings), Navigation, Theme
  domain/      Datenmodelle, Score-Berechnung, JSON-Extraktion, URL-Prüfung, Kostenschätzung, Bericht
  data/        Anthropic-Client, Einstellungen, Room-Verlauf, Zugriff auf assets
  analysis/    Seitenladen (WebView), Technik-Prüfungen, Ablaufsteuerung, Heatmap, Persona-Aggregation
  experts/     Expertendefinitionen, Modellaufrufe des Rats, JSON-Reparatur
  di/          Hilt-Modul
```

### Ablauf einer Analyse (`AnalysisController`)

1. **Seite laden** (`PageLoader`): Unsichtbare WebView mit JavaScript, Mobil- oder Desktop-User-Agent. Nach dem Laden liest ein Skript Text, gerendertes HTML und die Bounding Boxes aller Überschriften, Buttons, Links und Formularfelder aus. Der Screenshot wird in Segmente von höchstens 1568 Pixeln Höhe zerlegt (maximal 5), damit die API nichts herunterskaliert.
2. **Technik-Prüfungen** (`TechChecker`, `TechRules`, `RobotsParser`): ohne KI, per OkHttp und Jsoup. Statuscode, Redirect-Kette, HTTPS, robots.txt für GPTBot, ClaudeBot, PerplexityBot, Google-Extended und Bingbot, sitemap.xml, llms.txt, Title, Description, Canonical, hreflang, Open Graph, noindex/nofollow, Überschriftenstruktur, JSON-LD, Alt-Texte, Linktexte, Vergleich rohes gegen gerendertes HTML, Ladezeit, Seitengröße.
3. **Expertenrat** (`Council.expert`): sechs parallele Aufrufe. Jeder Experte hat einen eigenen Prompt und antwortet nur mit JSON.
4. **Personas** (`Council.generatePersonas`, `Council.runPersona`): laufen gleichzeitig mit den Experten. Ein Aufruf erzeugt N Personas, danach N parallele Testläufe.
5. **Moderator und Heatmap**: laufen gleichzeitig. Der Moderator benennt Widersprüche, löst sie auf und priorisiert Maßnahmen. Der Heatmap-Aufruf liefert Boxen für "Aufmerksamkeit" und "Änderungsbedarf".
6. **Speichern**: Ergebnis als JSON in Room, Screenshot als JPEG im App-Verzeichnis.

Fällt ein einzelner Experte, eine Persona, der Moderator oder die Heatmap aus, läuft die Analyse weiter. Für Moderator und Heatmap gibt es einen Ersatz, der direkt aus den Befunden rechnet. Nur ein abgelehnter API-Key, eine nicht erreichbare Seite oder der Ausfall aller Experten brechen ab.

### Entscheidungen, die von der Spezifikation abweichen oder sie auslegen

* **Gesamtscore**: Die App berechnet ihn selbst als gewichteten Mittelwert (`ScoreCalculator`, Gewichte in `assets/config/weights.json`). Der Moderator rechnet nicht. So ist der Score reproduzierbar und testbar.
* **Top 5**: sortiert nach Wirkung minus Aufwand, bei Gleichstand nach Wirkung.
* **Heatmap**: Die Gauß-Glättung läuft auf einem kleinen Raster (`HeatGrid`, reines Kotlin, getestet). Das Ergebnis wird als Bitmap gestreckt und gefiltert über den Screenshot gelegt. Optisch entspricht das einer Glättung auf dem Canvas, kostet aber kaum Speicher.
* **JSON-Robustheit**: `JsonExtractor` holt das erste parsebare Objekt aus der Antwort. Scheitert das, folgt genau ein Reparaturaufruf (`assets/prompts/repair.txt`).
* **Texte**: Alle UI-Texte stehen in `strings.xml`. Inhalte der Analyse selbst (Befunde der Technik-Prüfungen, Prompts) stehen im Code beziehungsweise in assets, weil sie Daten sind und keine Oberfläche.

## Konfiguration

| Datei | Zweck |
|---|---|
| `assets/prompts/common.txt` | Regeln und JSON-Schema für alle Experten |
| `assets/prompts/experts/*.txt` | Rolle und Fachgebiet je Experte |
| `assets/prompts/moderator.txt` | Moderator |
| `assets/prompts/personas_generate.txt`, `persona_run.txt` | Personas |
| `assets/prompts/heatmap.txt` | Heatmap-Schätzung |
| `assets/prompts/repair.txt` | JSON-Reparatur |
| `assets/config/weights.json` | Gewichte der Kategorien für den Gesamtscore |
| `assets/config/pricing.json` | Preise pro Million Tokens für die Kostenanzeige |

In `pricing.json` sind für `claude-sonnet-5-5` 2 US-Dollar Eingabe und 10 US-Dollar Ausgabe pro Million Tokens hinterlegt (Stand November 2026). Für andere Modelle bitte ergänzen, sonst greift der Standardwert.

Mit 5 Personas entstehen 14 Aufrufe. Die Schätzung auf dem Startbildschirm geht von der maximalen Screenshot-Länge aus und liegt deshalb meist über dem tatsächlichen Verbrauch, der im Tab "Übersicht" steht.

## Tests

`app/src/test`:

* `JsonExtractorTest`: Code-Zäune, Text um das JSON, Klammern in Zeichenketten, Dezimalzahlen als Score, abgeschnittene Antworten
* `ScoreCalculatorTest`: Gewichtung, Priorisierung, Ersatzmaßnahmen, Kostenschätzung
* `TechRulesTest`: Title, Description, Überschriften, JSON-LD, Alt-Texte, Linktexte, noindex, Canonical, Open Graph, Render-Vergleich, robots.txt
* `AnalysisHelpersTest`: URL-Prüfung, Persona-Aggregation, Heatmap-Raster und Farbverlauf

## Bekannte Grenzen

* **API-Key auf dem Gerät.** Für einen Prototyp vertretbar. Für eine Veröffentlichung gehört der Key hinter ein eigenes Backend.
* **Screenshot aus unsichtbarer WebView.** Die WebView hängt an keinem Fenster. Auf den meisten Geräten zeichnet sie trotzdem. Bleibt das Bild weiß: WebView in `PageLoader` an die Activity hängen (z. B. als 1 Pixel großes, durchsichtiges Kind des Content-Views) und erst danach zeichnen.
* **Lazy Loading.** Die Seite wird einmal per Skript durchgescrollt. Inhalte, die auf `IntersectionObserver` in einem sichtbaren Fenster warten, laden unter Umständen nicht.
* **Lange Seiten** werden nach 5 Segmenten abgeschnitten (mobil etwa 5200 CSS-Pixel). Die Übersicht weist darauf hin.
* **Desktop-Ansicht** ist eine Näherung: Desktop-User-Agent und breiter Viewport, auf Geräten mit hoher Pixeldichte schmaler als 1280 CSS-Pixel.
* **Cookie-Banner und Overlays** werden nicht weggeklickt und landen im Screenshot. Die Experten bewerten dann auch das Banner.
* **Kein Login.** Nur öffentlich erreichbare Seiten. Private IP-Bereiche und localhost werden abgelehnt, eine DNS-Auflösung auf interne Adressen wird nicht geprüft.
* **Heatmap und Personas sind Schätzungen eines Sprachmodells.** Sie ersetzen weder Eye-Tracking noch Tests mit echten Menschen. Die UI sagt das an beiden Stellen.
* **Kontrast** wird aus Screenshot und berechneten Farbwerten geschätzt, nicht gemessen. Transparente Hintergründe werden nicht bis zum sichtbaren Elternelement aufgelöst.
* **Ladezeit** ist grob: Antwortzeit des Servers plus Zeit bis `onPageFinished` in der WebView. Keine Core Web Vitals.
* **Zeitlimit.** Nach 180 Sekunden bricht die Analyse ab (`AnalysisController.TIMEOUT_MS`). Bis dahin verbrauchte Tokens sind bezahlt, ein Teilergebnis wird nicht gespeichert.
* **Rate Limits.** Bis zu 6 Aufrufe laufen gleichzeitig. Bei 429 oder 5xx wartet der Client und versucht es zweimal erneut. Bei niedrigen API-Limits `parallelCalls` in `LlmRunner` senken.
* **Verlauf.** Room speichert das komplette Ergebnis als JSON-Spalte, ohne Migrationen (`fallbackToDestructiveMigration`).
* **JSON-LD** wird nur auf gültiges JSON und vorhandene Typen geprüft, nicht gegen die Pflichtfelder von schema.org.
