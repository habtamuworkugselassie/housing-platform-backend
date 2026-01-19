# PowerShell script to insert sample properties into the database
# Usage: .\insert-sample-properties.ps1 [database_name] [username] [password] [host] [port]

param(
    [string]$DatabaseName = "housing_platform",
    [string]$Username = "housing_platform",
    [string]$Password = "housing_platform",
    [string]$Host = "localhost",
    [int]$Port = 5432
)

Write-Host "Inserting sample properties into database: $DatabaseName" -ForegroundColor Cyan
Write-Host "Host: ${Host}:${Port}" -ForegroundColor Cyan
Write-Host "User: $Username" -ForegroundColor Cyan
Write-Host ""

# Get the directory where the script is located
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SqlFile = Join-Path $ScriptDir "insert-sample-properties.sql"

if (-not (Test-Path $SqlFile)) {
    Write-Host "Error: SQL file not found: $SqlFile" -ForegroundColor Red
    exit 1
}

# Set PGPASSWORD environment variable for psql
$env:PGPASSWORD = $Password

# Execute the SQL script
$psqlPath = "psql"
$connectionString = "-h $Host -p $Port -U $Username -d $DatabaseName -f `"$SqlFile`""

try {
    & $psqlPath $connectionString.Split(' ')
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✓ Successfully inserted sample properties!" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "✗ Error inserting sample properties" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Error executing psql: $_" -ForegroundColor Red
    exit 1
} finally {
    # Clear password from environment
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
