$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $root 'compile.ps1')
$cp = (Join-Path $root 'src') + ';' + (Join-Path $root 'lib\sqlite-jdbc-3.53.1.0.jar')
java -cp $cp zlagoda.SmokeTest
