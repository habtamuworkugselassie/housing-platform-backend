#!/bin/bash

# Script to create sample credit products and financing offers
# This script executes the SQL script to create sample data

set -e

# Default database connection parameters
DB_NAME="${DB_NAME:-housing_platform}"
DB_USER="${DB_USER:-housing_platform}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/create-sample-credit-products.sql"

echo "=========================================="
echo "Creating Sample Credit Products and Financing Offers"
echo "=========================================="
echo "Database: ${DB_NAME}"
echo "User: ${DB_USER}"
echo "Host: ${DB_HOST}:${DB_PORT}"
echo "SQL File: ${SQL_FILE}"
echo "=========================================="
echo ""

# Check if SQL file exists
if [ ! -f "$SQL_FILE" ]; then
    echo "Error: SQL file not found at $SQL_FILE"
    exit 1
fi

# Execute the SQL script
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SQL_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "Successfully created sample credit products and financing offers!"
    echo "=========================================="
else
    echo ""
    echo "=========================================="
    echo "Error: Failed to create sample data"
    echo "=========================================="
    exit 1
fi
