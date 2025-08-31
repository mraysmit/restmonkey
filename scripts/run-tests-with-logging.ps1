#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Runs RESTMonkey tests with enhanced logging output.

.DESCRIPTION
    This script runs Maven tests for RESTMonkey with comprehensive logging enabled.
    It shows detailed test execution, HTTP traffic, and server behavior.

.PARAMETER TestClass
    Optional. Specific test class to run (e.g., "UsersApiTest")

.PARAMETER TestMethod
    Optional. Specific test method to run (e.g., "UsersApiTest#listUsers")

.PARAMETER LogLevel
    Optional. Log level for RESTMonkey components (TRACE, DEBUG, INFO, WARN, ERROR). Default: INFO

.PARAMETER ShowLogFile
    Optional. Show the detailed log file after test execution

.EXAMPLE
    ./run-tests-with-logging.ps1
    Runs all tests with INFO logging

.EXAMPLE
    ./run-tests-with-logging.ps1 -TestClass "UsersApiTest"
    Runs only UsersApiTest with INFO logging

.EXAMPLE
    ./run-tests-with-logging.ps1 -TestMethod "UsersApiTest#listUsers" -LogLevel "DEBUG"
    Runs specific test method with DEBUG logging

.EXAMPLE
    ./run-tests-with-logging.ps1 -ShowLogFile
    Runs all tests and shows the detailed log file afterward
#>

param(
    [string]$TestClass,
    [string]$TestMethod,
    [ValidateSet("TRACE", "DEBUG", "INFO", "WARN", "ERROR")]
    [string]$LogLevel = "INFO",
    [switch]$ShowLogFile
)

# Colors for output
$Green = "`e[32m"
$Blue = "`e[34m"
$Yellow = "`e[33m"
$Red = "`e[31m"
$Reset = "`e[0m"

Write-Host "${Blue}RESTMonkey Test Runner with Enhanced Logging${Reset}" -ForegroundColor Blue
Write-Host "${Blue}===========================================${Reset}" -ForegroundColor Blue

# Build Maven command
$mvnCmd = "mvn test -q"

if ($TestClass) {
    $mvnCmd += " -Dtest=$TestClass"
    Write-Host "${Yellow}Running test class: $TestClass${Reset}"
} elseif ($TestMethod) {
    $mvnCmd += " -Dtest=$TestMethod"
    Write-Host "${Yellow}Running test method: $TestMethod${Reset}"
} else {
    Write-Host "${Yellow}Running all tests${Reset}"
}

Write-Host "${Yellow}Log level: $LogLevel${Reset}"
Write-Host ""

# Create log directory if it doesn't exist
$logDir = "target/test-logs"
if (!(Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

# Run tests
Write-Host "${Blue}Executing tests...${Reset}"
Write-Host ""

try {
    Invoke-Expression $mvnCmd
    $exitCode = $LASTEXITCODE
    
    if ($exitCode -eq 0) {
        Write-Host ""
        Write-Host "${Green}All tests passed successfully!${Reset}" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "${Red}Some tests failed (exit code: $exitCode)${Reset}" -ForegroundColor Red
    }
} catch {
    Write-Host "${Red}Error running tests: $_${Reset}" -ForegroundColor Red
    exit 1
}

# Show log file if requested
if ($ShowLogFile -and (Test-Path "$logDir/RESTMonkey-tests.log")) {
    Write-Host ""
    Write-Host "${Blue}Detailed log file contents:${Reset}" -ForegroundColor Blue
    Write-Host "${Blue}===========================${Reset}" -ForegroundColor Blue
    Get-Content "$logDir/RESTMonkey-tests.log"
}

Write-Host ""
Write-Host "${Blue}Log files available in: $logDir${Reset}" -ForegroundColor Blue
