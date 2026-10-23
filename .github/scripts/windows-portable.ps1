# Packs the Windows app into the two portable downloads of a release:
#   ADEU-LLM-<version>-Windows.exe  – one file; unpacks itself on the first start (no admin rights)
#   ADEU-LLM-<version>-Windows.zip  – the app folder; unpack anywhere and start "ADEU LLM.exe"
param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$app = Resolve-Path 'desktop/build/compose/binaries/main/app/ADEU LLM'
$out = New-Item -ItemType Directory -Force -Path 'dist'
$zip = Join-Path $out "ADEU-LLM-$Version-Windows.zip"
$exe = Join-Path $out "ADEU-LLM-$Version-Windows.exe"
Remove-Item -Force -ErrorAction SilentlyContinue $zip, $exe

[System.IO.Compression.ZipFile]::CreateFromDirectory($app, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)

# The C# compiler of the .NET Framework (part of Windows) builds the single-file starter with the ZIP inside.
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$work = New-Item -ItemType Directory -Force -Path 'desktop/build/launcher'
$source = Join-Path $work 'Launcher.cs'
(Get-Content -Raw -Encoding UTF8 'desktop/launcher/Launcher.cs').Replace('@VERSION@', $Version) |
    Set-Content -Encoding UTF8 $source
Copy-Item -Force $zip (Join-Path $work 'app.zip')
Push-Location $work
try {
    & $csc /nologo /optimize+ /target:winexe "/out:$exe" "/win32icon:$((Resolve-Path '../../icons/app.ico'))" `
        /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll `
        /resource:app.zip,app.zip Launcher.cs
    if ($LASTEXITCODE -ne 0) { throw "csc failed with $LASTEXITCODE" }
} finally {
    Pop-Location
}
Get-ChildItem $out | Format-Table Name, @{ n = 'MB'; e = { [math]::Round($_.Length / 1MB, 1) } }
