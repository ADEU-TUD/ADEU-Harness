# Starts the single-file EXE like a colleague would (it unpacks to %LOCALAPPDATA% and starts the app),
# lets the app open every screen and create documents, and fails if anything went wrong.
param([Parameter(Mandatory = $true)][string]$Exe)
$ErrorActionPreference = 'Stop'
$dir = Join-Path (Get-Location) 'selftest'
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $dir
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$result = Join-Path $dir 'selftest.txt'

Start-Process -FilePath (Resolve-Path $Exe) -ArgumentList @('--selftest', "`"$dir`"") -Wait
$deadline = (Get-Date).AddMinutes(8)
while (-not (Test-Path $result) -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 3 }
Start-Sleep -Seconds 2

$log = Join-Path $dir 'data\log.txt'
if (Test-Path $log) { Copy-Item $log (Join-Path $dir 'log.txt') }
if (-not (Test-Path $result)) {
    if (Test-Path $log) { Get-Content $log -Tail 80 }
    throw 'Der Selbsttest hat kein Ergebnis geschrieben (App nicht gestartet oder abgestürzt).'
}
Get-Content $result
if (-not (Select-String -Path $result -SimpleMatch 'ERGEBNIS: OK' -Quiet)) {
    if (Test-Path $log) { Get-Content $log -Tail 80 }
    throw 'Selbsttest fehlgeschlagen.'
}
