# Bilder verstehen lokal – Messkorpus (AP-110)

`messen.py` erzeugt beim Start einen festen Korpus **erfundener** Bilder (keine echten Screenshots, Fotos oder Briefe) und
misst ein lokales Modell mit seinem Bildteil (mmproj) über llama-server:

| Art | Inhalt | Frage | geprüft |
| --- | --- | --- | --- |
| Screenshot | Fehlermeldung eines Programms | Welche Fehlermeldung? | Kernwort der Meldung |
| Tabellenfoto | Raumplan, leicht gedreht und verrauscht | Welcher Raum am …? | Raum |
| Scan | Einladung (A4, Rauschen, leicht schief) | Bis wann Rückmeldung? | Datum |
| Diagramm | Balkendiagramm mit vier Bereichen | Höchster Balken? | Bereich |

Je Art 3 Bilder im **Entwicklungsteil** und 3 im **Prüfteil** (andere Startwerte). Der Prüfteil wird erst gemessen, wenn
die Wahl feststeht, und nie zur Wahl benutzt. `--bilder <ordner>` legt die erzeugten Bilder zum Ansehen ab.

```bash
pip install pillow
python3 -I tools/bildverstehen/messen.py --llama-server <…/llm/runtime/llama-server> \
    --gguf <…/llm/models/Qwen3.5-4B-Q4_K_M.gguf> --mmproj <…/llm/models/Qwen3.5-4B-mmproj-F16.gguf>
```

Ziel (AP-110): brauchbare Antworten im Prüfteil, ein Bild auf einem Office-PC unter 15 s. **Noch nicht gemessen** – in
der Cloud-Sitzung darf llama-server nicht laufen. Die Vorhersage der App (`LocalVision.secondsPerImage`) sagt für einen
Office-PC ohne Grafikkarte 10 s (Qwen 3.5 0.8B) bis 70 s (Qwen 3.5 9B) je Bild – das Ziel ist dort voraussichtlich nur
mit den kleinsten Modellen erreichbar.
