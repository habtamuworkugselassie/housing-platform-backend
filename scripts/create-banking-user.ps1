# PowerShell script to create a banking user and bank organization
# This script executes the SQL script to create a sample banker user
# Usage: .\scripts\create-banking-user.ps1

param(
    [string]$DbName = "housing_platform",
    [string]$DbUser = "housing_platform",
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432"
)

$ErrorActionPreference = "Stop"

Write-Host "Creating Banking User and Bank Organization..." -ForegroundColor Blue

# Get the script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlScript = Join-Path $scriptDir "create-banking-user.sql"

# Check if SQL script exists
if (-not (Test-Path $sqlScript)) {
    Write-Host "Error: SQL script not found at $sqlScript" -ForegroundColor Red
    exit 1
}

# Check if psql is available
$psqlPath = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psqlPath) {
    Write-Host "Error: psql command not found. Please install PostgreSQL client tools." -ForegroundColor Red
    exit 1
}

# Build connection string
$env:PGPASSWORD = if ($env:PGPASSWORD) { $env:PGPASSWORD } else { "housing_platform" }

Write-Host "Executing SQL script..." -ForegroundColor Blue

try {
    $result = & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $sqlScript 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Banking user and bank organization created successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "Login credentials:" -ForegroundColor Yellow
        Write-Host "  Email: banker@example.com"
        Write-Host "  Password: password123"
        Write-Host ""
        Write-Host "You can now log in to the platform as a banker." -ForegroundColor Green
    } else {
        Write-Host "Error: Failed to create banking user." -ForegroundColor Red
        Write-Host $result
        exit 1
    }
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
} finally {
    # Clear password from environment
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}