# Database Setup Scripts

This directory contains scripts to create the PostgreSQL database and user for the Housing Platform.

## Database Credentials

- **Database Name**: `housing_platform`
- **Username**: `housing_platform`
- **Password**: `housing_platform`

## Available Scripts

### 1. Insert Sample Properties

```bash
./scripts/insert-sample-properties.sh
```

**Description:**
Inserts sample properties, organizations, and agents into the database for testing purposes.

**Usage:**
```bash
./scripts/insert-sample-properties.sh [database_name] [username] [password] [host] [port]
```

**Default Values:**
- Database: `housing_platform`
- Username: `housing_platform`
- Password: `housing_platform`
- Host: `localhost`
- Port: `5432`

**Example:**
```bash
./scripts/insert-sample-properties.sh housing_platform housing_platform housing_platform localhost 5432
```

**What it creates:**
- A sample real estate company (if it doesn't exist)
- A sample realtor user and agent
- 6 sample properties with various types (Apartment, Villa, Townhouse, Land, Condominium, House)
- Properties with different statuses (Available, Reserved)
- Properties with different construction statuses (Ready to Move, Under Construction, Planned)

**Note:** The script is idempotent - it checks if the organization exists before creating it, so it's safe to run multiple times.

### 2. Create Banking User

```bash
./scripts/create-banking-user.sh
```

**Description:**
Creates a sample banking user with BANKER role and a bank organization for testing the banking module.

**Usage:**
```bash
./scripts/create-banking-user.sh
```

**Environment Variables:**
- `DB_NAME` - Database name (default: `housing_platform`)
- `DB_USER` - Database user (default: `housing_platform`)
- `DB_HOST` - Database host (default: `localhost`)
- `DB_PORT` - Database port (default: `5432`)

**Example:**
```bash
DB_HOST=localhost DB_PORT=5432 ./scripts/create-banking-user.sh
```

**What it creates:**
- A banker user with email: `banker@example.com`
- Password: `password123`
- BANKER role assigned
- A bank organization named "Sample Bank"
- Links the user as primary contact of the bank

**Note:** The script is idempotent - it checks if the user and organization exist before creating them, so it's safe to run multiple times.

**PowerShell (Windows):**
```powershell
.\scripts\create-banking-user.ps1
```

**SQL Script:**
```bash
psql -U housing_platform -d housing_platform -f scripts/create-banking-user.sql
```

### 3. Create Sample Credit Products and Financing Offers

```bash
./scripts/create-sample-credit-products.sh
```

**Description:**
Creates sample credit products and financing offers linked to properties and buildings for testing the banking module.

**Prerequisites:**
- Must run `create-banking-user.sql` first to create the bank organization

**Usage:**
```bash
./scripts/create-sample-credit-products.sh
```

**Environment Variables:**
- `DB_NAME` - Database name (default: `housing_platform`)
- `DB_USER` - Database user (default: `housing_platform`)
- `DB_HOST` - Database host (default: `localhost`)
- `DB_PORT` - Database port (default: `5432`)

**Example:**
```bash
DB_HOST=localhost DB_PORT=5432 ./scripts/create-sample-credit-products.sh
```

**What it creates:**
- 3 credit products:
  - Home Purchase Loan (8.5% interest, ₵50,000 - ₵5,000,000)
  - Construction Loan (9.25% interest, ₵100,000 - ₵10,000,000)
  - Material Financing (10% interest, ₵20,000 - ₵2,000,000)
- 4 financing offers:
  - Home Purchase Loan linked to a property (with special 8% rate)
  - Construction Loan linked to a building
  - Material Financing linked to a building
  - General Home Purchase Loan (not linked to specific property/building)
- Creates sample property and building if they don't exist

**Note:** The script is idempotent - it checks if data exists before creating, so it's safe to run multiple times.

**PowerShell (Windows):**
```powershell
.\scripts\create-sample-credit-products.ps1
```

**SQL Script:**
```bash
psql -U housing_platform -d housing_platform -f scripts/create-sample-credit-products.sql
```

### 4. Migrate Property Prices to Dual Currency Format

```bash
./scripts/migrate-property-prices.sh
```

**Description:**
Migrates existing property prices from the old single `price` column to the new dual currency format (`price_etb` and `price_usd`). This script handles the migration safely and is idempotent (safe to run multiple times).

**Prerequisites:**
- Database must have the `price_etb` and `price_usd` columns (created by migration V11)
- Properties table must exist

**Usage:**
```bash
./scripts/migrate-property-prices.sh
```

**Environment Variables:**
- `DB_HOST` - Database host (default: `localhost`)
- `DB_PORT` - Database port (default: `5432`)
- `DB_NAME` - Database name (default: `housing_platform`)
- `DB_USER` - Database user (default: `postgres`)
- `PGPASSWORD` - Database password (will prompt if not set)

**Example:**
```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=housing_platform DB_USER=housing_platform ./scripts/migrate-property-prices.sh
```

**What it does:**
- Creates `price_etb` and `price_usd` columns if they don't exist
- Migrates prices from the old `price` column based on currency field (if exists)
- If currency is 'ETB' or NULL, migrates to `price_etb`
- If currency is 'USD', migrates to `price_usd`
- If no currency column exists, migrates all prices to `price_etb` (default)
- Adds constraint to ensure at least one price is provided
- Provides detailed migration summary

**Note:** The script is idempotent - it checks existing data before migrating, so it's safe to run multiple times. It will only migrate prices that haven't been migrated yet.

**PowerShell (Windows):**
```powershell
.\scripts\migrate-property-prices.ps1
```

**SQL Script:**
```bash
psql -U housing_platform -d housing_platform -f scripts/migrate-property-prices.sql
```

### 5. Bash Script (Linux/macOS) - Database Creation

```bash
./scripts/create-database.sh
```

**Requirements:**
- PostgreSQL client tools installed (`psql`, `pg_isready`)
- PostgreSQL server running
- Superuser access (default: `postgres`)

**Environment Variables:**
- `DB_HOST` - PostgreSQL host (default: `localhost`)
- `DB_PORT` - PostgreSQL port (default: `5432`)
- `PGUSER` - PostgreSQL superuser (default: `postgres`)
- `PGPASSWORD` - PostgreSQL superuser password

**Example:**
```bash
DB_HOST=localhost DB_PORT=5432 ./scripts/create-database.sh
```

### 6. SQL Script

```bash
psql -U postgres -f scripts/create-database.sql
```

**Requirements:**
- PostgreSQL client tools installed
- Superuser access

**Note:** This script uses PostgreSQL's `\gexec` command which requires PostgreSQL 9.6+.

### 7. PowerShell Script (Windows)

```powershell
.\scripts\create-database.ps1
```

**Requirements:**
- PostgreSQL client tools installed
- PowerShell 5.1 or later
- Superuser access

## Manual Setup

If you prefer to set up the database manually, run these SQL commands:

```sql
-- Connect as superuser (e.g., postgres)
psql -U postgres

-- Create user
CREATE USER housing_platform WITH PASSWORD 'housing_platform';

-- Create database
CREATE DATABASE housing_platform OWNER housing_platform;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE housing_platform TO housing_platform;

-- Connect to the database
\c housing_platform

-- Grant schema privileges
GRANT ALL ON SCHEMA public TO housing_platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO housing_platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO housing_platform;
```

## Docker Setup

If you're using Docker Compose, the database is automatically created. However, you can still use these scripts to create additional databases or users.

## Troubleshooting

### Permission Denied
- Ensure you're running the script with appropriate permissions
- Use `sudo` if needed (Linux/macOS)
- Run PowerShell as Administrator (Windows)

### Connection Refused
- Ensure PostgreSQL server is running
- Check if the host and port are correct
- Verify firewall settings

### User Already Exists
- The scripts check if the user/database exists before creating
- If they already exist, the script will continue without error

### Database Already Exists
- The scripts are idempotent - safe to run multiple times
- If the database exists, it will skip creation

## Updating Application Configuration

After running the script, update your `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/housing_platform
    username: housing_platform
    password: housing_platform
```

### 8. Create Admin User

**create-admin-user.sql / create-admin-user.sh / create-admin-user.ps1**

Creates a default admin user with ADMIN role for accessing the admin portal.

**Default Credentials:**
- Email: `admin@housingplatform.com`
- Password: `admin123`

**Usage:**

```bash
# Linux/Mac
./scripts/create-admin-user.sh

# Or with custom database settings
DB_HOST=localhost DB_PORT=5432 DB_NAME=housing_platform DB_USER=postgres ./scripts/create-admin-user.sh
```

```powershell
# Windows PowerShell
.\scripts\create-admin-user.ps1

# Or with custom database settings
.\scripts\create-admin-user.ps1 -DbHost localhost -DbPort 5432 -DbName housing_platform -DbUser postgres
```

```sql
# Direct SQL execution
psql -U postgres -d housing_platform -f scripts/create-admin-user.sql
```

**What it creates:**
- An admin user with email: `admin@housingplatform.com`
- Password: `admin123` (BCrypt hashed)
- ADMIN role assigned
- User status: ACTIVE
- Email verified: TRUE

**Note:** The script is idempotent - it checks if the admin user exists before creating, so it's safe to run multiple times.

**Important:** Change the default password after first login!

### 9. Create or Promote a Super Admin

`SUPER_ADMIN` is the tier that manages platform admins and issues sponsor-company logins
(Admin → Organizations → **Accounts**). It grants both the `admin` and `super_admin` token
scopes, so a super admin reaches every admin screen on its own — you do **not** also need to
give them the `ADMIN` role.

> **Deploy first.** `SUPER_ADMIN` only exists from migration **V51** onwards. Inserting the role
> into a database whose backend predates it will break that user's login: `User.roles` is an
> EAGER `@Enumerated(EnumType.STRING)` collection, so Hibernate throws
> `No enum constant UserRole.SUPER_ADMIN` every time it loads the account. Deploy the new
> backend (which runs V51 automatically) *before* running this script.
>
> V51 already promotes the longest-standing `ADMIN` account, so after a deploy you usually have
> a super admin without running anything. Use this script for *additional* super admins, or if
> the automatic pick was the wrong person.

```bash
# Linux/Mac — prompts for anything you leave out
./create-super-admin.sh

# Create a new super admin (prompts for the password only)
./create-super-admin.sh boss@example.com Ada Lovelace

# Promote an existing user, leaving their current password alone
./create-super-admin.sh --promote-only boss@example.com

# Windows PowerShell
.\create-super-admin.ps1 -Email boss@example.com -FirstName Ada -LastName Lovelace
.\create-super-admin.ps1 -Email boss@example.com -PromoteOnly
```

The script hashes the password with BCrypt cost 10 (matching Spring's default
`BCryptPasswordEncoder`) using `python3 -c 'import bcrypt'`, falling back to `htpasswd`. If
neither is installed, generate the hash yourself and call the SQL directly:

```bash
# Generate a hash
python3 -c "import bcrypt;print(bcrypt.hashpw(b'YourPassword1', bcrypt.gensalt(10)).decode())"
htpasswd -bnBC 10 "" 'YourPassword1' | tr -d ':\n'

# Then run the SQL with it (pass password_hash='' to promote without changing the password)
psql -U "$DB_USER" -d "$DB_NAME" \
     -v email=boss@example.com -v first_name=Ada -v last_name=Lovelace \
     -v password_hash='$2a$10$...' \
     -f create-super-admin.sql
```

The script is idempotent — re-run it to reset the password or re-grant the role. If the account
was already signed in, it must sign out and back in for the new `super_admin` scope to appear in
its token.
