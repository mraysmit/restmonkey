#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Demonstrates TinyRest splash screen and startup behavior.

.DESCRIPTION
    This script shows various TinyRest startup scenarios to demonstrate
    the splash screen and different configuration behaviors.

.PARAMETER ShowAll
    Show all demo scenarios (default: just successful startup)

.EXAMPLE
    ./demo-splash.ps1
    Shows successful TinyRest startup with splash screen

.EXAMPLE
    ./demo-splash.ps1 -ShowAll
    Shows all startup scenarios including error cases
#>

param(
    [switch]$ShowAll
)

# Colors for output
$Green = "`e[32m"
$Blue = "`e[34m"
$Yellow = "`e[33m"
$Red = "`e[31m"
$Reset = "`e[0m"

Write-Host "${Blue}TinyRest Splash Screen Demo${Reset}" -ForegroundColor Blue
Write-Host "${Blue}===========================${Reset}" -ForegroundColor Blue
Write-Host ""

# Build the JAR if it doesn't exist
if (!(Test-Path "target/tinyrest-1.0.0-SNAPSHOT.jar")) {
    Write-Host "${Yellow}Building TinyRest JAR...${Reset}"
    mvn package -q
    Write-Host ""
}

Write-Host "${Green}1. Successful Startup with Configuration${Reset}" -ForegroundColor Green
Write-Host "${Blue}Command: java -jar target/tinyrest-1.0.0-SNAPSHOT.jar src/test/resources/tinyrest.yaml${Reset}"
Write-Host ""

# Start TinyRest and let it run for a few seconds to show startup
$process = Start-Process -FilePath "java" -ArgumentList "-jar", "target/tinyrest-1.0.0-SNAPSHOT.jar", "src/test/resources/tinyrest.yaml" -PassThru -NoNewWindow

Start-Sleep -Seconds 3

# Kill the process
Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "${Green}Server started successfully! (killed after 3 seconds)${Reset}" -ForegroundColor Green

if ($ShowAll) {
    Write-Host ""
    Write-Host "${Red}2. Error: No Configuration File${Reset}" -ForegroundColor Red
    Write-Host "${Blue}Command: java -jar target/tinyrest-1.0.0-SNAPSHOT.jar${Reset}"
    Write-Host ""
    
    java -jar target/tinyrest-1.0.0-SNAPSHOT.jar
    
    Write-Host ""
    Write-Host "${Red}3. Error: Non-existent Configuration File${Reset}" -ForegroundColor Red
    Write-Host "${Blue}Command: java -jar target/tinyrest-1.0.0-SNAPSHOT.jar missing.yaml${Reset}"
    Write-Host ""
    
    java -jar target/tinyrest-1.0.0-SNAPSHOT.jar missing.yaml
}

Write-Host ""
Write-Host "${Blue}Splash screen features:${Reset}" -ForegroundColor Blue
Write-Host "  - ASCII art banner with project name"
Write-Host "  - Project description and purpose"
Write-Host "  - Version information with Java version"
Write-Host "  - Copyright notice"
Write-Host "  - Shows before any configuration processing"
Write-Host "  - Appears in all startup scenarios (success and error)"
Write-Host ""
Write-Host "${Green}Demo complete!${Reset}" -ForegroundColor Green
