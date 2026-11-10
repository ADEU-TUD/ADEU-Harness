# Lizenzbedingungen

Diese Bedingungen gelten für den Harness „Lora“ (dieses Repository) und für die beiden von uns trainierten Modelle
**Lora Namensschutz** (`lora-namensschutz-pytales.*`) und **Lora Befehle** (`lora-befehle-pytales.*`). Urheber:
PyTales / Dominik Töpper.

## 1. Nutzungsrecht
Die Nutzung dieses Harnesses sowie der bereitgestellten Modelle ist für alle dauerhaft kostenlos.

## 2. Modifikationen und Weiterverbreitung
Es ist strikt untersagt, den Code oder die Modelle ohne vorherige schriftliche Genehmigung des Urhebers (PyTales / Dominik Töpper) zu modifizieren, zu verändern oder in abgeänderter Form zu verbreiten. 
Die Weiterverbreitung (Distribution) der originalen oder modifizierten Dateien auf anderen Plattformen, Repositories oder Webseiten ist nicht gestattet. Nutzende müssen auf dieses originale Repository verweisen.
Für Anfragen bezüglich Modifikationen oder kommerzieller Integrationen kontaktieren Sie bitte: dominik.toepper@tu-dresden.de

Unberührt bleiben die Rechte, die die Nutzungsbedingungen von GitHub für öffentliche Repositories einräumen (Ansehen und
Forken innerhalb von GitHub).

## 3. Bestandteile Dritter
Die Bedingungen in Abschnitt 2 gelten für die Arbeit von PyTales: den eigenen Code, die eigenen Änderungen und die daraus
entstandenen Werke als Ganzes. Bestandteile Dritter bleiben unter ihren eigenen Lizenzen; deren Rechte werden durch diese
Bedingungen weder eingeschränkt noch erweitert.

- **Basismodelle**: Lora Namensschutz beruht auf `deepset/gbert-base` von deepset GmbH (MIT-Lizenz), Lora Befehle auf
  `distilbert/distilbert-base-german-cased` von Hugging Face (Apache License 2.0). Beide Basismodelle wurden von uns
  feinabgestimmt und verändert. Die Lizenztexte der Basismodelle liegen den Modellkarten und den Modelldateien bei
  (`tools/pii/modell/LIZENZ-gbert-base.txt`, `tools/befehle/modell/LIZENZ-distilbert-base-german-cased.txt`). Die
  unveränderten Basismodelle können weiterhin nach deren Lizenz genutzt werden. Die Urheber der Basismodelle haben Lora
  Namensschutz und Lora Befehle weder geprüft noch gebilligt.
- **Bibliotheken und weitere Modelle** (z. B. Compose, JNA, sherpa-onnx, ONNX Runtime, YuNet): unter ihren eigenen
  Lizenzen, wie sie mit ihnen ausgeliefert werden.

## 4. Gewährleistung
Harness und Modelle werden ohne Gewähr bereitgestellt. Die Modelle können Fehler machen (übersehene oder fälschlich
ersetzte Namen, falsch erkannte Befehle); im Zweifel prüft ein Mensch das Ergebnis.
