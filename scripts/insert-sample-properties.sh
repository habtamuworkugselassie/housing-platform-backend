#!/bin/bash

# Script to insert sample properties into the database
# Usage: ./insert-sample-properties.sh [database_name] [username] [password] [host] [port]

DB_NAME="${1:-housing_platform}"
DB_USER="${2:-housing_platform}"
DB_PASS="${3:-housing_platform}"
DB_HOST="${4:-localhost}"
DB_PORT="${5:-5432}"

echo "Inserting sample properties into database: $DB_NAME"
echo "Host: $DB_HOST:$DB_PORT"
echo "User: $DB_USER"
echo ""

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SQL_FILE="$SCRIPT_DIR/insert-sample-properties.sql"

if [ ! -f "$SQL_FILE" ]; then
    echo "Error: SQL file not found: $SQL_FILE"
    exit 1
fi

# Execute the SQL script
PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SQL_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Successfully inserted sample properties!"
else
    echo ""
    echo "✗ Error inserting sample properties"
    exit 1
fi
