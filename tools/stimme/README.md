# Sprachausgabe lokal (AP-111)

**Kein Training** – eine fertige deutsche Stimme wird ausgewählt, gemessen und ausgeliefert. Plan: `docs/ARBEITSPLAN.md`
› AP-111.

## Wahl

| Stimme | Lizenz der Aufnahmen | Größe | Entscheidung |
| --- | --- | --- | --- |
| **Piper „thorsten“ medium** (de_DE, männlich) | Thorsten-Voice, **CC0** | 63 MB + 0,7 MB Aussprache | gewählt |
| Piper „thorsten“ high | CC0 | 110 MB | langsamer, kaum besser hörbar auf Laptop-Lautsprechern (nicht gemessen) |
| Piper „kerstin“ low (weiblich) | CC0 | 63 MB | Kandidat für eine zweite Stimme (Rest) |
| Piper „eva_k“, „ramona“, „karlsson“, „pavoque“ | M-AILABS bzw. unklar („See URL“) | – | ausgeschlossen, Lizenz nicht klar frei |
| Kokoro (Apache-2.0) | – | – | kein Deutsch |

- Datei: das für sherpa-onnx umgewandelte Modell `csukuangfj/vits-piper-de_DE-thorsten-medium` (Hugging Face, fester
  Commit `da8bed78…`, mit den Metadaten, die sherpa-onnx braucht), dazu **nur die deutschen Teile von espeak-ng-data**
  (`phontab`, `phonindex`, `phondata`, `intonations`, `de_dict`, `lang/gmw/de`) statt des ganzen Ordners – jede Datei mit
  SHA-256 (`audio/LocalVoice.kt`). Piper selbst: MIT; espeak-ng (Aussprache): GPL-3.0, eigene Dateien.
- Abspielen über dieselbe sherpa-onnx 1.13.8, die die App für die Spracherkennung mitbringt (die pip-Fassung 2.0.1 hat
  kein espeak eingebaut und erzeugt mit diesen Dateien nichts).

## Messung (Cloud-Rechner, zwei Kerne, sherpa-onnx 1.13.8)

| Text | Rechenzeit | Ton |
| --- | --- | --- |
| Laden der Stimme | 1,2 s (einmalig; der Sprachmodus lädt beim Zuhören vor) | – |
| „Gern.“ | 40 ms | 0,56 s |
| Satz mit 67 Zeichen | 234 ms | 3,25 s |
| Satz mit 134 Zeichen | 446 ms | 7,34 s |

Echtzeitfaktor ≈ 0,07. Der Sprachmodus spricht den ersten fertigen Satz; der kommt also deutlich unter 0,5 s.

In der App gemessen (`LocalVoiceTest`, Kotlin, sherpa-onnx-JNI der App, gleicher Rechner):

```
Laden und erstes Wort: 1165 ms
 215 ms für 3.38 s Ton: Die Abrechnung muss innerhalb von sechs Monaten eingereicht werden.
  66 ms für 1.14 s Ton: Ja, das geht.
 204 ms für 3.59 s Ton: Der Antrag für die Hilfskraft muss vier Wochen vor Beginn vorliegen.
 121 ms für 2.16 s Ton: Am Wochenende ist die Bibliothek geschlossen.
```

`STIMME=<Ordner> STIMME_NATIVE=<sherpa-onnx-Bibliotheken> ./gradlew -p desktop test --tests '*LocalVoiceTest*'`
→ `desktop/build/stimme/bericht.txt`.
