#!/bin/bash

# Script to create a banking user and bank organization
# This script executes the SQL script to create a sample banker user
# Usage: ./scripts/create-banking-user.sh

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Creating Banking User and Bank Organization...${NC}"

# Get database connection details from environment or use defaults
DB_NAME="${DB_NAME:-housing_platform}"
DB_USER="${DB_USER:-housing_platform}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

# Check if psql is available
if ! command -v psql &> /dev/null; then
    echo "Error: psql command not found. Please install PostgreSQL client tools."
    exit 1
fi

# Execute the SQL script
echo -e "${BLUE}Executing SQL script...${NC}"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$(dirname "$0")/create-banking-user.sql"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Banking user and bank organization created successfully!${NC}"
    echo ""
    echo "Login credentials:"
    echo "  Email: banker@example.com"
    echo "  Password: password123"
    echo ""
    echo "You can now log in to the platform as a banker."
else
    echo "Error: Failed to create banking user. Please check the error messages above."
    exit 1
fi