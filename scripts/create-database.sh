#!/bin/bash

# Script to create PostgreSQL database and user for Housing Platform
# Usage: ./scripts/create-database.sh

set -e

DB_NAME="housing_platform"
DB_USER="housing_platform"
DB_PASSWORD="housing_platform"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Creating PostgreSQL database and user for Housing Platform...${NC}"

# Check if PostgreSQL is installed
if ! command -v psql &> /dev/null; then
    echo -e "${RED}Error: psql command not found. Please install PostgreSQL client tools.${NC}"
    exit 1
fi

# Check if PostgreSQL server is running
if ! pg_isready -h "$DB_HOST" -p "$DB_PORT" &> /dev/null; then
    echo -e "${RED}Error: PostgreSQL server is not running on $DB_HOST:$DB_PORT${NC}"
    echo "Please start PostgreSQL server and try again."
    exit 1
fi

# Prompt for PostgreSQL superuser (if not set)
if [ -z "$PGUSER" ]; then
    read -p "Enter PostgreSQL superuser (default: postgres): " PGUSER
    PGUSER=${PGUSER:-postgres}
fi

export PGPASSWORD="${PGPASSWORD:-}"

# Create user if it doesn't exist
echo -e "${YELLOW}Creating user '$DB_USER'...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d postgres -tc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'" | grep -q 1 || \
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d postgres -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}User '$DB_USER' created successfully${NC}"
else
    echo -e "${YELLOW}User '$DB_USER' may already exist${NC}"
fi

# Create database if it doesn't exist
echo -e "${YELLOW}Creating database '$DB_NAME'...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 || \
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Database '$DB_NAME' created successfully${NC}"
else
    echo -e "${YELLOW}Database '$DB_NAME' may already exist${NC}"
fi

# Grant privileges
echo -e "${YELLOW}Granting privileges...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d "$DB_NAME" -c "GRANT ALL ON SCHEMA public TO $DB_USER;"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d "$DB_NAME" -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO $DB_USER;"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$PGUSER" -d "$DB_NAME" -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $DB_USER;"

echo -e "${GREEN}✓ Database setup completed successfully!${NC}"
echo ""
echo "Database Details:"
echo "  Name: $DB_NAME"
echo "  User: $DB_USER"
echo "  Password: $DB_PASSWORD"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo ""
echo "You can now update your application.yml with these credentials."
