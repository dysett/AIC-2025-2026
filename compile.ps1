$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $root 'lib\sqlite-jdbc-3.53.1.0.jar'
$sources = Get-ChildItem -Path (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -cp $jar $sources
Write-Host "Compiled Java classes under src"
