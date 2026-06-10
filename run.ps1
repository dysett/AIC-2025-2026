$ErrorActionPreference = 'Stop'

try {
    $root = Split-Path -Parent $MyInvocation.MyCommand.Path

    # Переходимо в папку проєкту, щоб SQLite відкривав базу data/zlagoda.db саме тут.
    Set-Location $root

    # Перед запуском програми компілюємо всі Java-файли з UTF-8.
    & (Join-Path $root 'compile.ps1')

    # Classpath містить скомпільовані класи з src і драйвер SQLite JDBC.
    $cp = (Join-Path $root 'src') + ';' + (Join-Path $root 'lib\sqlite-jdbc-3.53.1.0.jar')

    # Запускаємо головний клас Swing-застосунку.
    java -cp $cp zlagoda.Main
    if ($LASTEXITCODE -ne 0) {
        throw "Java завершилась з кодом $LASTEXITCODE"
    }
} catch {
    Write-Host ""
    Write-Host "Помилка запуску ZLAGODA AIS:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    Read-Host "Натисніть Enter, щоб закрити це вікно"
}
