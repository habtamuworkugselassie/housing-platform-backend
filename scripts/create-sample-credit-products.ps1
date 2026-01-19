# PowerShell script to create sample credit products and financing offers
# This script executes the SQL script to create sample data

param(
    [string]$DbName = "housing_platform",
    [string]$DbUser = "housing_platform",
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432"
)

$ErrorActionPreference = "Stop"

# Get the directory where the script is located
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SqlFile = Join-Path $ScriptDir "create-sample-credit-products.sql"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Creating Sample Credit Products and Financing Offers" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Database: $DbName"
Write-Host "User: $DbUser"
Write-Host "Host: ${DbHost}:${DbPort}"
Write-Host "SQL File: $SqlFile"
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Check if SQL file exists
if (-not (Test-Path $SqlFile)) {
    Write-Host "Error: SQL file not found at $SqlFile" -ForegroundColor Red
    exit 1
}

# Check if psql is available
$psqlPath = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psqlPath) {
    Write-Host "Error: psql command not found. Please ensure PostgreSQL client tools are installed." -ForegroundColor Red
    exit 1
}

# Execute the SQL script
$env:PGPASSWORD = Read-Host "Enter database password for user $DbUser" -AsSecureString | ConvertFrom-SecureString -AsPlainText
$env:PGPASSWORD = $env:PGPASSWORD

try {
    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $SqlFile
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Green
        Write-Host "Successfully created sample credit products and financing offers!" -ForegroundColor Green
        Write-Host "==========================================" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Red
        Write-Host "Error: Failed to create sample data" -ForegroundColor Red
        Write-Host "==========================================" -ForegroundColor Red
        exit 1
    }
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
