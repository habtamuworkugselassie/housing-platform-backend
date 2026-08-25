# PowerShell script to create or promote a SUPER_ADMIN user.
#
# SUPER_ADMIN is the tier that manages platform admins and issues sponsor-company logins
# (Admin -> Organizations -> Accounts). It grants both the `admin` and `super_admin` scopes,
# so it reaches every admin screen on its own.
#
# Usage:
#   .\create-super-admin.ps1 -Email boss@example.com -FirstName Ada -LastName Lovelace
#   .\create-super-admin.ps1 -Email boss@example.com -PromoteOnly
#   .\create-super-admin.ps1 -Email boss@example.com -PasswordHash '$2a$10$...'

param(
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$FirstName = "Super",
    [string]$LastName = "Admin",
    [string]$Password = "",
    [string]$PasswordHash = "",
    [switch]$PromoteOnly,
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbName = "housing_platform",
    [string]$DbUser = "postgres",
    [string]$DbPassword = ""
)

$ErrorActionPreference = "Stop"

Write-Host "Granting SUPER_ADMIN to $Email" -ForegroundColor Green
Write-Host "Database: $DbName on ${DbHost}:$DbPort"
Write-Host ""

if (-not $PromoteOnly -and [string]::IsNullOrEmpty($PasswordHash)) {
    if ([string]::IsNullOrEmpty($Password)) {
        $securePassword = Read-Host "Password (min 8 chars, 1 upper, 1 lower, 1 digit)" -AsSecureString
        $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $Password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
    }

    # Same rule the API enforces, checked before the value reaches the database.
    if ($Password.Length -lt 8 -or
        $Password -cnotmatch '[a-z]' -or
        $Password -cnotmatch '[A-Z]' -or
        $Password -notmatch '[0-9]') {
        Write-Host "Error: password needs at least 8 characters, one uppercase, one lowercase and one digit." -ForegroundColor Red
        exit 1
    }

    # BCrypt cost 10, matching Spring's default BCryptPasswordEncoder.
    # Windows has no built-in BCrypt, so this leans on python's bcrypt module.
    $env:SUPER_ADMIN_PASSWORD = $Password
    try {
        $PasswordHash = & python -c "import bcrypt, os; print(bcrypt.hashpw(os.environ['SUPER_ADMIN_PASSWORD'].encode(), bcrypt.gensalt(10)).decode())"
    } catch {
        $PasswordHash = ""
    } finally {
        Remove-Item Env:\SUPER_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    }

    if ([string]::IsNullOrEmpty($PasswordHash)) {
        Write-Host "Error: could not generate a BCrypt hash." -ForegroundColor Red
        Write-Host "Install it with:  pip install bcrypt" -ForegroundColor Yellow
        Write-Host "Or pass a hash you generated elsewhere:  -PasswordHash '<hash>'" -ForegroundColor Yellow
        exit 1
    }
}

if ([string]::IsNullOrEmpty($DbPassword)) {
    $secureDbPassword = Read-Host "Database password for user '$DbUser'" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureDbPassword)
    $DbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}
$env:PGPASSWORD = $DbPassword

try {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $sqlFile = Join-Path $scriptDir "create-super-admin.sql"

    & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName `
        -v ON_ERROR_STOP=1 `
        -v "email=$Email" `
        -v "first_name=$FirstName" `
        -v "last_name=$LastName" `
        -v "password_hash=$PasswordHash" `
        -f $sqlFile

    Write-Host ""
    Write-Host "Done." -ForegroundColor Green
    if (-not $PromoteOnly) {
        Write-Host ""
        Write-Host "Sign in with the email above and the password you entered." -ForegroundColor Yellow
        Write-Host "An already signed-in session must sign out and back in to pick up the new scope." -ForegroundColor Yellow
    }
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
