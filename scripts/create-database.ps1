# PowerShell script to create PostgreSQL database and user for Housing Platform
# Usage: .\scripts\create-database.ps1

$ErrorActionPreference = "Stop"

$DB_NAME = "housing_platform"
$DB_USER = "housing_platform"
$DB_PASSWORD = "housing_platform"
$DB_HOST = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$DB_PORT = if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }

Write-Host "Creating PostgreSQL database and user for Housing Platform..." -ForegroundColor Yellow

# Check if psql is available
try {
    $null = Get-Command psql -ErrorAction Stop
} catch {
    Write-Host "Error: psql command not found. Please install PostgreSQL client tools." -ForegroundColor Red
    exit 1
}

# Prompt for PostgreSQL superuser
if (-not $env:PGUSER) {
    $PGUSER = Read-Host "Enter PostgreSQL superuser (default: postgres)"
    if ([string]::IsNullOrWhiteSpace($PGUSER)) {
        $PGUSER = "postgres"
    }
} else {
    $PGUSER = $env:PGUSER
}

# Prompt for password if not set
if (-not $env:PGPASSWORD) {
    $securePassword = Read-Host "Enter PostgreSQL superuser password" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $PGPASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

$env:PGPASSWORD = $PGPASSWORD

# Create user if it doesn't exist
Write-Host "Creating user '$DB_USER'..." -ForegroundColor Yellow
$userExists = psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d postgres -tAc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'"
if ($userExists -eq "1") {
    Write-Host "User '$DB_USER' already exists" -ForegroundColor Yellow
} else {
    psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d postgres -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "User '$DB_USER' created successfully" -ForegroundColor Green
    } else {
        Write-Host "Failed to create user" -ForegroundColor Red
        exit 1
    }
}

# Create database if it doesn't exist
Write-Host "Creating database '$DB_NAME'..." -ForegroundColor Yellow
$dbExists = psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'"
if ($dbExists -eq "1") {
    Write-Host "Database '$DB_NAME' already exists" -ForegroundColor Yellow
} else {
    psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Database '$DB_NAME' created successfully" -ForegroundColor Green
    } else {
        Write-Host "Failed to create database" -ForegroundColor Red
        exit 1
    }
}

# Grant privileges
Write-Host "Granting privileges..." -ForegroundColor Yellow
psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d $DB_NAME -c "GRANT ALL ON SCHEMA public TO $DB_USER;"
psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d $DB_NAME -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $DB_USER;"
psql -h $DB_HOST -p $DB_PORT -U $PGUSER -d $DB_NAME -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $DB_USER;"

Write-Host "`nDatabase setup completed successfully!" -ForegroundColor Green
Write-Host "`nDatabase Details:" -ForegroundColor Cyan
Write-Host "  Name: $DB_NAME"
Write-Host "  User: $DB_USER"
Write-Host "  Password: $DB_PASSWORD"
Write-Host "  Host: $DB_HOST"
Write-Host "  Port: $DB_PORT"
Write-Host "`nYou can now update your application.yml with these credentials."
