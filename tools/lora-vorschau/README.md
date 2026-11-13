# Lora-Vorschau (AP-122)

Werkzeuge, um Loras Aussehen und Animationen vor einer Freigabe als Filme anzuschauen: jede der 51 Aktionen im
Vergleich (links alt, rechts neu), dazu Startseite, Arbeit, Nahaufnahme, kleine Größe und Logo. Die Vorschau ist eine
private Seite: <https://claude.ai/artifact/SQbVtroPNFVWHrrFfE8TdR> (nur für den Hauptnutzer sichtbar; Kennung
`cdbfd55d-f7a9-4b94-9943-2fe94d9e9390`).

## Ablauf

1. **Neu rendern** (dieser Zweig, braucht ffmpeg, etwa 4 Minuten, 55 Filme je Design):
   `LORA_PREVIEW_DIR=/pfad/neu ./gradlew --no-daemon -p desktop test --tests '*LoraPreviewTest*'`
2. **Alt rendern** (Stand von `main`, einmalig): `git worktree add /pfad/main origin/main`, die Datei
   `desktop/src/test/kotlin/io/github/thepytales/llmhub/LoraPreviewTest.kt` dorthin kopieren und dort denselben Befehl mit
   `LORA_PREVIEW_DIR=/pfad/alt` ausführen. Soll statt `main` der vorige Entwurf links stehen, dessen Filme aufheben.
3. **Schneiden**: `tools/lora-vorschau/schnitt.sh /pfad/alt /pfad/neu /pfad/seite` (etwa 3 Minuten, rund 34 MB).
4. **Logo**: `./gradlew -p desktop test --tests '*LogoTest*'` schreibt `desktop/build/icons/app.png` – als
   `logo-new.png` neben die Seite legen; `logo-old.png` ist `desktop/icons/app.png` von `main`.
5. **Seite aktualisieren**: `seite.html` ist nur der Inhalt. Als `index.html` braucht sie davor `<!DOCTYPE html>`,
   `<html lang="de"><head>` mit `<meta charset>` und `<meta name="viewport">`, dann alles bis `</style>`, `</head><body>`,
   den Rest und `</body></html>`. Zusammen mit `light/*.mp4`, `dark/*.mp4`, `logo-old.png`, `logo-new.png` (113 Dateien,
   rund 34 MB) mit dem Werkzeug zum Veröffentlichen aus Dateien unter der Kennung oben neu veröffentlichen (vorher den
   Stand der Seite abfragen, dessen Versionsnummer mitgeben). Dateien, die gleich geblieben sind, kann man weglassen.
   Findet eine Sitzung die Seite nicht, bleibt nur eine neue Seite – dann die Adresse hier und im Arbeitsplan ändern.
   Die Texte je Aktion stehen in `seite.html` (Liste `GROUPS`).

## Prüfen ohne Filme

- **Bildstreifen** (schnellster Weg, Sekunden, kein ffmpeg): `LORA_STRIP_DIR=/pfad ./gradlew -p desktop cleanTest test
  --tests '*LoraPreviewTest.strips*'` schreibt je vier Aktionen ein Bild mit sechs Momenten der Runde. Feiner:
  `LORA_PREVIEW_ONLY=WALK LORA_STRIP_N=18 LORA_STRIP_FROM=0 LORA_STRIP_TO=0.5` (18 Momente der ersten Hälfte),
  `LORA_STRIP_W=520` (größer), `LORA_STRIP_LOOK=dark`. `cleanTest` ist nötig, sonst überspringt Gradle den Lauf.
- `./gradlew -p desktop test --tests '*LoraFigureTest*'` schreibt Bildbögen aller Aktionen nach
  `desktop/build/lora.png` und `lora-dark.png` (je Aktion eine Zeile) und prüft, dass nichts über den Rand ragt.
- Einzelne Aktionen: `LORA_PREVIEW_ONLY=WALK,SEARCH` vor dem Befehl aus Schritt 1; Bildfolgen daraus mit
  `ffmpeg -i WALK.mkv -vf "fps=7,scale=240:-1,tile=8x4" -frames:v 1 walk.png`.
