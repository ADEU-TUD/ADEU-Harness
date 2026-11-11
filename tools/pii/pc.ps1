# Training des Erkennungsmodells auf dem eigenen PC (AP-67, Stufen c und d) – Windows, Grafikkarte von NVIDIA.
#
# Aufruf in PowerShell im Ordner des Repos:
#     powershell -ExecutionPolicy Bypass -File tools\pii\pc.ps1
#
# Was passiert (alles im Ordner tools\pii, nichts wird installiert außerhalb davon):
#   1. Python-Umgebung tools\pii\.venv mit PyTorch für CUDA 12.8 (nötig für RTX 50xx) und den Paketen aus requirements.txt
#   2. 30 000 erfundene Trainingstexte (gen.py) – die Daten bleiben auf dem PC
#   3. Training der drei Kandidaten (je 4 Epochen, ein paar Minuten je Modell)
#   4. Vergleich auf dem Entwicklungsteil des Messkorpus, Export des besten als ONNX/int8 mit Modellkarte
#   5. Veröffentlichen (AP-117, tools\modelle\veroeffentlichen.py): nur bei erreichten Zielen auf den Branch pii-modell
#
# Voraussetzung: Python 3.11 oder neuer (python.org, „Add to PATH“), aktueller NVIDIA-Treiber, Internet, git mit
# Anmeldung bei GitHub (Schreibrecht auf ADEU-TUD/Lora).

$ErrorActionPreference = "Stop"
$env:PYTHONUTF8 = "1"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }

Step "Python suchen"
$py = $null
foreach ($cand in @("py -3.12", "py -3.13", "py -3.11", "py -3", "python")) {
    $parts = $cand.Split(" ")
    try {
        $v = & $parts[0] $parts[1..($parts.Length - 1)] -c "import sys; print(sys.version_info >= (3, 11))" 2>$null
        if ($v -eq "True") { $py = $parts; break }
    } catch { }
}
if (-not $py) { throw "Kein Python 3.11+ gefunden. Bitte von python.org installieren (Haken bei 'Add to PATH')." }
Write-Host "Python: $($py -join ' ')"

Step "Grafikkarte"
try { nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader } catch { Write-Host "nvidia-smi nicht gefunden – Treiber prüfen." -ForegroundColor Yellow }

$venv = Join-Path $here ".venv"
$vpy = Join-Path $venv "Scripts\python.exe"
if (-not (Test-Path $vpy)) {
    Step "Python-Umgebung anlegen (.venv)"
    & $py[0] $py[1..($py.Length - 1)] -m venv $venv
}
Step "Pakete installieren (PyTorch mit CUDA 12.8, Transformers, ONNX) – beim ersten Mal einige Minuten"
& $vpy -m pip install --upgrade pip
& $vpy -m pip install torch --index-url https://download.pytorch.org/whl/cu128
& $vpy -m pip install -r requirements.txt
& $vpy -c "import torch; assert torch.cuda.is_available(), 'PyTorch sieht keine Grafikkarte'; print('CUDA:', torch.cuda.get_device_name(0), '| bf16:', torch.cuda.is_bf16_supported())"

Step "Trainingstexte erzeugen"
& $vpy gen.py check
& $vpy gen.py make -n 30000

# Kandidaten: Lizenz MIT bzw. Apache-2.0 (geprüft am 09.11.2026), feste Versionen.
$candidates = @(
    @{ basis = "deepset/gbert-base"; rev = "d50cb1df9660ff2de1af8a5362d322b3d5a1a28a"; name = "gbert-base" },
    @{ basis = "deepset/gelectra-base"; rev = "bda804fa52cf5c8205159fb622a436291f88b38f"; name = "gelectra-base" },
    @{ basis = "distilbert/distilbert-base-german-cased"; rev = "6f96562de9886779ce7d48810dff8f5c4afb6d0b"; name = "distilbert-base-german-cased" }
)
foreach ($c in $candidates) {
    Step "Training $($c.basis)"
    & $vpy train.py --basis $c.basis --revision $c.rev --epochen 4 --batch 32 --aus "modelle\$($c.name)"
    if ($LASTEXITCODE -ne 0) { Write-Host "Training von $($c.basis) fehlgeschlagen – weiter mit dem nächsten." -ForegroundColor Yellow }
}

Step "Vergleich (Entwicklungsteil, wie in der App geplant)"
$best = $null; $bestScore = -1000
foreach ($c in $candidates) {
    $f = "modelle\$($c.name)\bericht.json"
    if (-not (Test-Path $f)) { continue }
    $r = Get-Content $f -Raw -Encoding UTF8 | ConvertFrom-Json
    $d = if ($r.entwicklung_geplant) { $r.entwicklung_geplant } else { $r.entwicklung_modell }
    $score = [double]$d.gefunden - 2 * [double]$d.falsch_ersetzt
    Write-Host ("{0,-32} gefunden {1,5} %   fälschlich ersetzt {2,5} %   Güte {3,6:N1}" -f $c.name, $d.gefunden, $d.falsch_ersetzt, $score)
    if ($score -gt $bestScore) { $bestScore = $score; $best = $c }
}
if (-not $best) { throw "Kein Modell fertig trainiert." }

Step "Export von $($best.name) (ONNX, int8, Modellkarte)"
& $vpy export.py "modelle\$($best.name)\best"

$onnx = Join-Path $here "modelle\$($best.name)\onnx"
$bericht = Join-Path $here "modelle\$($best.name)\bericht.json"

# AP-117: veröffentlichen – nur bei erreichten Zielen (Prüfteil), auf den Branch pii-modell auf GitHub, mit Modellkarte,
# Lizenzen und Messbericht; schreibt tools\pii\modell\fassung.json. Braucht git mit Anmeldung bei GitHub.
Step "Veröffentlichen (nur bei erreichten Zielen)"
& $vpy (Join-Path $here "..\modelle\veroeffentlichen.py") namensschutz $onnx --bericht $bericht
$code = $LASTEXITCODE

Step "Fertig"
Write-Host "Bestes Modell: $($best.basis)"
Write-Host "Ordner: $onnx"
Write-Host ""
if ($code -eq 0) {
    Write-Host "Neue Fassung veröffentlicht. tools\pii\modell\fassung.json ist geschrieben." -ForegroundColor Green
    Write-Host "Nächster Schritt (für Claude): die Fassung in PiiModelInfo.VERSIONS eintragen (Ausgabe oben), PR, grüner Check."
} elseif ($code -eq 2) {
    Write-Host "Ziele verfehlt – nichts veröffentlicht. Den Messbericht ($onnx\messung.json, $bericht) an Claude geben." -ForegroundColor Yellow
} else {
    Write-Host "Veröffentlichen fehlgeschlagen (siehe oben, z. B. git nicht angemeldet). Erneut: " -ForegroundColor Yellow
    Write-Host "  $vpy tools\modelle\veroeffentlichen.py namensschutz $onnx --bericht $bericht"
}
