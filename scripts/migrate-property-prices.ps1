# Migration Script Wrapper: Migrate Property Prices to Dual Currency Format
# This PowerShell script runs the SQL migration to convert property prices from the old
# single 'price' column to the new dual currency format (price_etb, price_usd)

param(
    [string]$Host = "localhost",
    [int]$Port = 5432,
    [string]$Database = "housing_platform",
    [string]$User = "postgres",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"

# Colors for output
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SqlFile = Join-Path $ScriptDir "migrate-property-prices.sql"

Write-Host "Property Price Migration Script" -ForegroundColor Green
Write-Host "=================================="
Write-Host ""
Write-Host "Database Configuration:"
Write-Host "  Host: $Host"
Write-Host "  Port: $Port"
Write-Host "  Database: $Database"
Write-Host "  User: $User"
Write-Host ""

# Check if SQL file exists
if (-not (Test-Path $SqlFile)) {
    Write-ColorOutput Red "Error: SQL file not found: $SqlFile"
    exit 1
}

# Prompt for password if not provided
if ([string]::IsNullOrEmpty($Password)) {
    $SecurePassword = Read-Host "Enter database password for $User" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
    $Password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

# Set PGPASSWORD environment variable
$env:PGPASSWORD = $Password

# Test database connection
Write-Host "Testing database connection..."
try {
    $testQuery = "SELECT 1;" | psql -h $Host -p $Port -U $User -d $Database 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Connection failed"
    }
    Write-Host "Connection successful!" -ForegroundColor Green
} catch {
    Write-ColorOutput Red "Error: Cannot connect to database"
    exit 1
}

Write-Host ""

# Confirm before proceeding
Write-Host "This will migrate property prices from the old 'price' column" -ForegroundColor Yellow
Write-Host "to the new dual currency format (price_etb, price_usd)." -ForegroundColor Yellow
Write-Host ""
$confirm = Read-Host "Do you want to continue? (yes/no)"

if ($confirm -ne "yes") {
    Write-Host "Migration cancelled."
    exit 0
}

Write-Host ""
Write-Host "Running migration..."
Write-Host ""

# Run the migration
try {
    Get-Content $SqlFile | psql -h $Host -p $Port -U $User -d $Database
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "Migration completed successfully!" -ForegroundColor Green
        exit 0
    } else {
        throw "Migration failed"
    }
} catch {
    Write-Host ""
    Write-ColorOutput Red "Migration failed!"
    exit 1
} finally {
    # Clear password from environment
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
