#!/bin/bash

# Script to create an admin user in the database
# This script creates a default admin user with ADMIN role
# Default credentials: admin@housingplatform.com / admin123

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default database connection parameters
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-housing_platform}"
DB_USER="${DB_USER:-postgres}"

echo -e "${GREEN}Creating admin user...${NC}"
echo "Database: $DB_NAME"
echo "Host: $DB_HOST:$DB_PORT"
echo ""

# Check if psql is available
if ! command -v psql &> /dev/null; then
    echo -e "${RED}Error: psql command not found. Please install PostgreSQL client tools.${NC}"
    exit 1
fi

# Prompt for password if not set
if [ -z "$DB_PASSWORD" ]; then
    echo -n "Enter database password for user '$DB_USER': "
    read -s DB_PASSWORD
    echo ""
fi

# Export password for psql
export PGPASSWORD="$DB_PASSWORD"

# Execute SQL script
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$(dirname "$0")/create-admin-user.sql"

# Unset password
unset PGPASSWORD

echo ""
echo -e "${GREEN}Admin user creation completed!${NC}"
echo ""
echo -e "${YELLOW}Default credentials:${NC}"
echo "  Email: admin@housingplatform.com"
echo "  Password: admin123"
echo ""
echo -e "${YELLOW}IMPORTANT: Change the password after first login!${NC}"
