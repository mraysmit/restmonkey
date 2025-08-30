#!/usr/bin/env pwsh
# PowerShell script to remove emoji icons from TinyRest codebase
# Works on Windows PowerShell, PowerShell Core, and cross-platform

Write-Host "Removing emoji icons from TinyRest codebase..." -ForegroundColor Yellow

# Files to process
$filesToProcess = @(
    'src/main/java/dev/mars/tinyrest/TinyRest.java',
    'docs/README.md',
    'docs/LOGGING.md',
    'docs/LOGGING_EXAMPLES.md'
)

foreach ($file in $filesToProcess) {
    if (Test-Path $file) {
        Write-Host "Processing: $file" -ForegroundColor Cyan

        # Read file content
        $content = Get-Content $file -Raw -Encoding UTF8

        # Remove common emojis using Unicode ranges
        $cleanContent = $content -replace '[\u{1F300}-\u{1F9FF}]', ''  # Misc Symbols and Pictographs
        $cleanContent = $cleanContent -replace '[\u{2600}-\u{26FF}]', ''   # Misc Symbols
        $cleanContent = $cleanContent -replace '[\u{2700}-\u{27BF}]', ''   # Dingbats

        # Replace Unicode arrows with ASCII equivalents
        $cleanContent = $cleanContent -replace '\u{2192}', '->'  # →
        $cleanContent = $cleanContent -replace '\u{2190}', '<-'  # ←

        # Write back to file
        Set-Content $file $cleanContent -Encoding UTF8

        Write-Host "  Cleaned $file" -ForegroundColor Green
    } else {
        Write-Host "  File not found: $file" -ForegroundColor Yellow
    }
}

Write-Host "Emoji removal complete!" -ForegroundColor Green
Write-Host "Run 'mvn test' to verify everything still works." -ForegroundColor Cyan
