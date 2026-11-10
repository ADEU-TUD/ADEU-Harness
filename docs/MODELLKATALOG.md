# Modellkatalog der lokalen Modelle (AP-106)

Stand 10.11.2026 (1.0.67). Quelle der Wahrheit ist `app/src/main/java/io/github/thepytales/llmhub/local/LocalCatalog.kt`;
diese Seite fasst zusammen, was dort steht und warum.

## „– Lora Edition“

Kuratierte Fremdmodelle heißen **„<Originalname> – Lora Edition“**. Das heißt: von uns ausgewählt, als fertige
quantisierte Datei übernommen, auf Geräten gemessen und für Lora eingestellt (Kontextlänge, Vorlage). **Die Gewichte sind
unverändert**; Basismodell, Urheber und Lizenz sind die des Originals, die Urheber haben Lora weder geprüft noch gebilligt.
Die App zeigt das in der **Modellkarte** jedes Modells (*Einstellungen › Modelle auf diesem Gerät › Modellkarte*).
`License.md` gilt nur für unsere eigenen Werke (Lora Namensschutz, Lora Befehle). Bezug: Hugging Face an festem Commit,
geprüft mit SHA-256; ein eigener Spiegel auf Hugging Face ist geplant, aber noch nicht eingerichtet.

## Modelle

Alle Lizenzen geprüft am 10.11.2026 über die Modellkarten auf Hugging Face (Original und quantisierte Fassung):
**Apache-2.0** bei allen – auch Gemma 4. Kein Llama. Devstral Small 2 (Basis Apache-2.0) wurde nicht aufgenommen,
weil die quantisierte Fassung als „other“ ausgewiesen ist.

| Modell | Zweck | Datei (Quantisierung) | GB | Original | Urheber |
| --- | --- | --- | --- | --- | --- |
| Qwen 3.5 0.8B – Lora Edition | ALLROUND | `unsloth/Qwen3.5-0.8B-GGUF` @ `6ab461498e` (Q8_0) | 0.81 | Qwen/Qwen3.5-0.8B | Qwen-Team (Alibaba Cloud) |
| Qwen 3.5 2B – Lora Edition | ALLROUND, MAIL, CODE | `unsloth/Qwen3.5-2B-GGUF` @ `f6d5376be1` (Q4_K_M) | 1.28 | Qwen/Qwen3.5-2B | Qwen-Team (Alibaba Cloud) |
| Gemma 4 E2B – Lora Edition | TEXT | `ggml-org/gemma-4-E2B-it-GGUF` @ `6938c58e69` (Q4_0) | 2.84 | google/gemma-4-E2B-it | Google DeepMind |
| Qwen 3.5 4B – Lora Edition | ALLROUND, MAIL, CODE | `unsloth/Qwen3.5-4B-GGUF` @ `e87f176479` (Q4_K_M) | 2.74 | Qwen/Qwen3.5-4B | Qwen-Team (Alibaba Cloud) |
| Qwen 3.5 4B (genauer) – Lora Edition | ALLROUND, MAIL | `unsloth/Qwen3.5-4B-GGUF` @ `e87f176479` (Q8_0) | 4.48 | Qwen/Qwen3.5-4B | Qwen-Team (Alibaba Cloud) |
| Gemma 4 E4B – Lora Edition | TEXT | `ggml-org/gemma-4-E4B-it-GGUF` @ `a69f018d76` (Q4_0) | 4.59 | google/gemma-4-E4B-it | Google DeepMind |
| Qwen 2.5 Coder 7B – Lora Edition | CODE | `unsloth/Qwen2.5-Coder-7B-Instruct-GGUF` @ `0ecf118595` (Q4_K_M) | 4.68 | Qwen/Qwen2.5-Coder-7B-Instruct | Qwen-Team (Alibaba Cloud) |
| Qwen 3.5 9B – Lora Edition | ALLROUND, MAIL | `unsloth/Qwen3.5-9B-GGUF` @ `3885219b68` (Q4_K_M) | 5.68 | Qwen/Qwen3.5-9B | Qwen-Team (Alibaba Cloud) |
| Qwen 3.5 9B (genauer) – Lora Edition | ALLROUND, MAIL | `unsloth/Qwen3.5-9B-GGUF` @ `3885219b68` (Q8_0) | 9.53 | Qwen/Qwen3.5-9B | Qwen-Team (Alibaba Cloud) |
| Gemma 4 12B – Lora Edition | TEXT | `ggml-org/gemma-4-12B-it-GGUF` @ `a29b2a6b58` (Q4_0) | 7.22 | google/gemma-4-12B-it | Google DeepMind |
| Qwen 2.5 Coder 14B – Lora Edition | CODE | `unsloth/Qwen2.5-Coder-14B-Instruct-GGUF` @ `388f3f2027` (Q4_K_M) | 8.99 | Qwen/Qwen2.5-Coder-14B-Instruct | Qwen-Team (Alibaba Cloud) |
| Gemma 4 26B A4B – Lora Edition | TEXT | `ggml-org/gemma-4-26B-A4B-it-GGUF` @ `9cf8c8db0f` (Q4_0) | 14.62 | google/gemma-4-26B-A4B-it | Google DeepMind |
| Qwen 3.6 27B – Lora Edition | ALLROUND, MAIL | `unsloth/Qwen3.6-27B-GGUF` @ `82d411acf4` (Q4_K_M) | 16.82 | Qwen/Qwen3.6-27B | Qwen-Team (Alibaba Cloud) |
| Gemma 4 31B – Lora Edition | TEXT | `unsloth/gemma-4-31B-it-GGUF` @ `c1ac76e99d` (Q4_K_M) | 18.32 | google/gemma-4-31B-it | Google DeepMind |
| Qwen 3 Coder 30B A3B – Lora Edition | CODE | `unsloth/Qwen3-Coder-30B-A3B-Instruct-GGUF` @ `b17cb02dd8` (Q4_K_M) | 18.56 | Qwen/Qwen3-Coder-30B-A3B-Instruct | Qwen-Team (Alibaba Cloud) |
| Qwen 3.6 35B A3B – Lora Edition | ALLROUND, MAIL, CODE | `ggml-org/Qwen3.6-35B-A3B-GGUF` @ `7878dc9a90` (Q4_K_M) | 20.42 | Qwen/Qwen3.6-35B-A3B | Qwen-Team (Alibaba Cloud) |

## Stufen und Zwecke

Je Stufe und Zweck nimmt die App das stärkste Modell, das in den Speicher passt und laut Vorhersage (AP-105) mindestens
**8 Wörter/s** schreibt und das **erste Wort in höchstens 2 s** liefert; ein Spezialist zählt für seinen eigenen Zweck
1,5-fach. Auf dem echten Gerät rechnet die App mit der *gemessenen* Vorhersage (Kurzmessung, *Messen*) – die Wahl kann
dort also anders ausfallen als in dieser Tabelle. Der **Allrounder** ist der Standard; Texte, Code und Postfach kommen als
Spezialist dazu, wenn der Zweck gewählt ist (Dialog vom ersten Start, *Modelle auf diesem Gerät*; ohne Wahl aus der
Rolle: Verwaltung → Postfach, Lehre/Forschung → Texte), und werden nur bei Bedarf geladen.

Bezugsgeräte (`LocalCatalog.steps()`): PCs ohne Grafik mit DDR4-3200 bzw. DDR5-5600 in zwei Kanälen, Grafikkarten mit
32 GB RAM (GTX 1650, RTX 3050, 4060, 3060, 5080, 4090), Macs (M2, M4, M1 Pro, M4 Pro, M3 Max), Android-Handys. Werte:
**Vorhersage**, nicht gemessen (Test `LocalCatalogStepsTest`, erzeugt diese Tabelle).

| Stufe | Allrounder | Texte | Code | Postfach |
| --- | --- | --- | --- | --- |
| RAM 8 GB (DDR4, 4 Kerne) | Qwen 3.5 2B · 12 W/s | Gemma 4 E2B · 13 W/s | Qwen 3.5 2B · 12 W/s | Qwen 3.5 2B · 12 W/s |
| RAM 12 GB (DDR4) | Qwen 3.5 2B · 13 W/s | Gemma 4 E2B · 15 W/s | Qwen 3.5 2B · 13 W/s | Qwen 3.5 2B · 13 W/s |
| RAM 16 GB (DDR4) | Qwen 3.5 2B · 13 W/s | Gemma 4 E2B · 15 W/s | Qwen 3.5 2B · 13 W/s | Qwen 3.5 2B · 13 W/s |
| RAM 16 GB (DDR5) | Qwen 3.5 4B · 12 W/s | Gemma 4 E4B · 14 W/s | Qwen 3.5 4B · 12 W/s | Qwen 3.5 4B · 12 W/s |
| RAM 24 GB (DDR5) | Qwen 3.5 4B · 12 W/s | Gemma 4 E4B · 14 W/s | Qwen 3.5 4B · 12 W/s | Qwen 3.5 4B · 12 W/s |
| RAM 32 GB (DDR5) | Qwen 3.5 4B · 12 W/s | Gemma 4 E4B · 14 W/s | Qwen 3.5 4B · 12 W/s | Qwen 3.5 4B · 12 W/s |
| RAM 48 GB (DDR5) | Qwen 3.6 35B A3B · 16 W/s | Gemma 4 26B A4B · 12 W/s | Qwen 3 Coder 30B A3B · 14 W/s | Qwen 3.6 35B A3B · 16 W/s |
| RAM 64 GB (DDR5) | Qwen 3.6 35B A3B · 16 W/s | Gemma 4 26B A4B · 12 W/s | Qwen 3 Coder 30B A3B · 14 W/s | Qwen 3.6 35B A3B · 16 W/s |
| Grafik 4 GB | Qwen 3.5 4B · 12 W/s | Gemma 4 E4B · 14 W/s | Qwen 3.5 4B · 12 W/s | Qwen 3.5 4B · 12 W/s |
| Grafik 6 GB | Qwen 3.5 4B · 28 W/s | Gemma 4 E4B · 14 W/s | Qwen 3.5 4B · 28 W/s | Qwen 3.5 4B · 28 W/s |
| Grafik 8 GB | Qwen 3.5 9B · 19 W/s | Gemma 4 E4B · 46 W/s | Qwen 2.5 Coder 7B · 23 W/s | Qwen 3.5 9B · 19 W/s |
| Grafik 12 GB | Qwen 3.5 9B · 25 W/s | Gemma 4 12B · 19 W/s | Qwen 2.5 Coder 14B · 16 W/s | Qwen 3.5 9B · 25 W/s |
| Grafik 16 GB | Qwen 3.5 9B (genauer) · 39 W/s | Gemma 4 12B · 52 W/s | Qwen 2.5 Coder 14B · 42 W/s | Qwen 3.5 9B (genauer) · 39 W/s |
| Grafik 24 GB | Qwen 3.6 27B · 23 W/s | Gemma 4 31B · 21 W/s | Qwen 3 Coder 30B A3B · 166 W/s | Qwen 3.6 27B · 23 W/s |
| Mac M2 16 GB | Qwen 3.5 9B · 8,4 W/s | Gemma 4 E4B · 21 W/s | Qwen 2.5 Coder 7B · 10 W/s | Qwen 3.5 9B · 8,4 W/s |
| Mac M4 24 GB | Qwen 3.5 9B · 10,0 W/s | Gemma 4 E4B · 25 W/s | Qwen 2.5 Coder 7B · 12 W/s | Qwen 3.5 9B · 10,0 W/s |
| Mac M1 Pro 32 GB | Qwen 3.5 9B (genauer) · 9,4 W/s | Gemma 4 26B A4B · 34 W/s | Qwen 2.5 Coder 14B · 10 W/s | Qwen 3.5 9B (genauer) · 9,4 W/s |
| Mac M4 Pro 48 GB | Qwen 3.6 35B A3B · 57 W/s | Gemma 4 26B A4B · 45 W/s | Qwen 3 Coder 30B A3B · 50 W/s | Qwen 3.6 35B A3B · 57 W/s |
| Mac M3 Max 64 GB | Qwen 3.6 27B · 9,6 W/s | Gemma 4 31B · 8,8 W/s | Qwen 3 Coder 30B A3B · 68 W/s | Qwen 3.6 27B · 9,6 W/s |
| Android 8 GB | Qwen 3.5 2B · 9,4 W/s | Gemma 4 E2B · 11 W/s | Qwen 3.5 2B · 9,4 W/s | Qwen 3.5 2B · 9,4 W/s |
| Android 12 GB | Qwen 3.5 2B · 9,4 W/s | Gemma 4 E2B · 11 W/s | Qwen 3.5 2B · 9,4 W/s | Qwen 3.5 2B · 9,4 W/s |
| Android 16 GB | Qwen 3.5 2B · 9,4 W/s | Gemma 4 E2B · 11 W/s | Qwen 3.5 2B · 9,4 W/s | Qwen 3.5 2B · 9,4 W/s |

88 von 88 Feldern erreichen die Vorhersage ≥ 8 Wörter/s und erstes Wort ≤ 2 s; ⚠ = schnellstes passendes Modell.

## Warum so (vorläufig)

- **Allrounder und Postfach: Qwen 3.5/3.6** (Apache-2.0) – zuverlässige Werkzeugaufrufe (Postfach, Lora), gutes Deutsch,
  lückenlose Leiter von 0,8B bis 27B dicht und 35B A3B (Expertenmodell, schreibt auf der CPU wie ein 3B-Modell). Für
  Lora Postfach (AP-104) bleibt die Basis Apache-2.0.
- **Texte: Gemma 4** (Apache-2.0) – nach Ruf und bisheriger Nutzung das beste Deutsch der offenen Modelle seiner Größe.
- **Code: Qwen 2.5 Coder 7B/14B und Qwen 3 Coder 30B A3B** (Apache-2.0); Qwen 2.5 Coder 3B fällt heraus (eigene Lizenz
  „qwen-research“).
- **Feinere Quantisierung** (8 Bit) für Qwen 3.5 4B und 9B, wo Grafikspeicher übrig ist.
- **Noch nicht auf dem Messkorpus gemessen** (`tools/modellwahl/`, 48 erfundene Aufgaben): in der Cloud darf llama-server
  nicht laufen. Fertig ist AP-106 erst, wenn je Stufe die Kandidaten auf einem Gerät der Stufe im Entwicklungsteil
  verglichen sind und die Wahl hier begründet steht.
