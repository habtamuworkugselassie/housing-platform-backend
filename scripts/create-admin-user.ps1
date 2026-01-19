# PowerShell script to create an admin user in the database
# This script creates a default admin user with ADMIN role
# Default credentials: admin@housingplatform.com / admin123

param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "housing_platform",
    [string]$DbUser = "postgres",
    [string]$DbPassword = ""
)

$ErrorActionPreference = "Stop"

Write-Host "Creating admin user..." -ForegroundColor Green
Write-Host "Database: $DbName"
Write-Host "Host: $DbHost:$DbPort"
Write-Host ""

# Prompt for password if not provided
if ([string]::IsNullOrEmpty($DbPassword)) {
    $securePassword = Read-Host "Enter database password for user '$DbUser'" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $DbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

# Set PGPASSWORD environment variable
$env:PGPASSWORD = $DbPassword

try {
    # Get the script directory
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $sqlFile = Join-Path $scriptDir "create-admin-user.sql"
    
    # Execute SQL script
    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $sqlFile
    
    Write-Host ""
    Write-Host "Admin user creation completed!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Default credentials:" -ForegroundColor Yellow
    Write-Host "  Email: admin@housingplatform.com"
    Write-Host "  Password: admin123"
    Write-Host ""
    Write-Host "IMPORTANT: Change the password after first login!" -ForegroundColor Yellow
}
catch {
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}
finally {
    # Clear password from environment
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
