# Hinweise für KI-Assistenten

- **Neue Version / Update veröffentlichen:** README › *Neue Version veröffentlichen (Kurzanleitung, auch für
  KI-Assistenten)*. Die Versionsnummer immer ausdrücklich angeben (letzte Version + 1), „Was ist neu“ kurz und
  verständlich.
- Das öffentliche Repo `ADEU-TUD/ADEU-Harness-Updater` nicht direkt bearbeiten: Releases kommen aus dem Workflow, seine
  README aus `.github/updater/README.md`.
- **Arbeitsplan:** Geplante Erweiterungen mit Arbeitspaketen (`AP-xx`), Grundsätzen und Entscheidungen stehen in
  `docs/ARBEITSPLAN.md`, gegliedert in *Jetzt / Danach / Später / Ideen*. Als Nächstes kommt das erste offene Paket unter
  *Jetzt*. Status dort pflegen (☐/◐/☑), wenn ein Paket begonnen oder fertig wird.
- **Design-Arbeitsplan:** Alles zu Aussehen, Bedienbarkeit und Nutzerfreundlichkeit steht in
  `docs/DESIGN-ARBEITSPLAN.md`, Pakete heißen **`APD-xx`**. Spricht der Hauptnutzer vom „Design-Arbeitsplan“ oder von
  „APD“ („arbeite Pakete aus dem Design-Arbeitsplan ab“), ist **diese Datei** gemeint – nicht `docs/ARBEITSPLAN.md`.
  Als Nächstes kommt dort das erste offene Paket unter *Jetzt*; Grundsätze und Prüfmaßstab der Datei gelten. Neue
  Design-Pakete bekommen die nächste freie APD-Nummer und stehen nur dort; Status (☐/◐/☑) dort pflegen. Jede
  Design-Stufe zeigt dem Hauptnutzer vor PR und Merge Vorher/Nachher-Bilder aus Tests auf einer Vergleichsseite.
- **TU Dresden Corporate Design ist unveränderlich:** Die Vorlagen im CD der TU Dresden und alles, was die App im
  TUD-Stil erzeugt (TU Vorlagen, Briefe, Dokumente, Folien, Designs; `docs/TudBrand.kt`, `DocStyle.TUD`,
  `creative/TudAssets.kt`, `data/BuiltInTemplates.kt`, `resources/tud/`), werden **nie** verändert – auch nicht bei
  Design-Arbeiten. Veränderbar ist nur die Oberfläche der App drumherum.
- **Kotlin-Schalter `-Xskip-metadata-version-check`** (seit AP-118, `desktop/build.gradle.kts` im Block `kotlin { … }`):
  nötig, weil JediTerm 3.76 (Terminal des Server-Terminals) mit Kotlin 2.4 gebaut ist, die App aber mit Kotlin 2.0.21
  baut; ohne ihn bricht der Desktop-Build mit „Module was compiled with an incompatible version of Kotlin. The binary
  version of its metadata is 2.4.0, expected version is 2.0.0“ ab. Folgen bei Problemen: Der Schalter versteckt solche
  Versionsfehler **für alle Bibliotheken** – ein `NoSuchMethodError`/`NoClassDefFoundError` aus `kotlin.*` zur Laufzeit
  (v. a. im Server-Terminal) kommt sehr wahrscheinlich daher. Prüfen: `ServerTerminalTest.jediTermOnlyCallsFunctionsOfOurKotlinStdlib`
  (liest den Bytecode von JediTerm). Bei einem JediTerm-Update diesen Test laufen lassen; bei einem Update von Kotlin auf
  ≥ 2.3 (liest Metadaten 2.4) den Schalter und `exclude(group = "org.jetbrains.kotlin")` an JediTerm wieder entfernen.
  Im Code des App-Projekts keine Kotlin-Klassen von JediTerm verwenden (z. B. `TerminalTextBuffer`), nur die Java-API.
