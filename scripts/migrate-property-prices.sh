#!/bin/bash

# Migration Script Wrapper: Migrate Property Prices to Dual Currency Format
# This script runs the SQL migration to convert property prices from the old
# single 'price' column to the new dual currency format (price_etb, price_usd)

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-housing_platform}"
DB_USER="${DB_USER:-postgres}"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/migrate-property-prices.sql"

echo -e "${GREEN}Property Price Migration Script${NC}"
echo "=================================="
echo ""
echo "Database Configuration:"
echo "  Host: ${DB_HOST}"
echo "  Port: ${DB_PORT}"
echo "  Database: ${DB_NAME}"
echo "  User: ${DB_USER}"
echo ""

# Check if SQL file exists
if [ ! -f "$SQL_FILE" ]; then
    echo -e "${RED}Error: SQL file not found: ${SQL_FILE}${NC}"
    exit 1
fi

# Prompt for password if PGPASSWORD is not set
if [ -z "$PGPASSWORD" ]; then
    echo -n "Enter database password for ${DB_USER}: "
    read -s PGPASSWORD
    echo ""
    export PGPASSWORD
fi

# Test database connection
echo "Testing database connection..."
if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${RED}Error: Cannot connect to database${NC}"
    exit 1
fi

echo -e "${GREEN}Connection successful!${NC}"
echo ""

# Confirm before proceeding
echo -e "${YELLOW}This will migrate property prices from the old 'price' column"
echo "to the new dual currency format (price_etb, price_usd).${NC}"
echo ""
read -p "Do you want to continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Migration cancelled."
    exit 0
fi

echo ""
echo "Running migration..."
echo ""

# Run the migration
if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SQL_FILE"; then
    echo ""
    echo -e "${GREEN}Migration completed successfully!${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}Migration failed!${NC}"
    exit 1
fi
